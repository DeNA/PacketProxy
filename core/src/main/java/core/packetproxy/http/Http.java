/*
 * Copyright 2019 DeNA Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package packetproxy.http;

import static packetproxy.util.Logging.errWithStackTrace;
import static packetproxy.util.Logging.log;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.collections4.map.MultiValueMap;
import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.PrivateDNSClient;
import packetproxy.common.Parameter;
import packetproxy.common.Utils;

public class Http {

	private HttpHeader header;
	private HttpHeader originalHeader;
	private byte[] rawBody;
	private String statusCode;
	private String method;
	private String proxyHost;
	private int proxyPort = 0;
	private String path;
	private QueryString queryString;
	private String version;
	// private MultiValueMap<String,String> header;
	// private MultiValueMap<String,Parameter> bodyParams;
	// private ArrayList<String> header_order;
	private byte[] body;
	// private boolean flag_https = false;
	private boolean flag_request = false;
	private boolean flag_proxy = false;
	private boolean flag_proxy_ssl = false;
	private boolean flag_disable_proxy_format_url = false;
	private boolean flag_disable_content_length = false;
	private boolean flag_dont_touch_content_length = false;

	public static Http create(byte[] data) throws Exception {
		return new Http(data, false);
	}

	public static Http createWithoutTouchingContentLength(byte[] data) throws Exception {
		return new Http(data, true);
	}

	/*
	 * public static void main(String args[])
	 * {
	 * PacketProxyUtility util = PacketProxyUtility.getInstance();
	 * try {
	 *
	 * String test1 =
	 * "GET http://www.example.com/a/b/c.html?abc=h%20oge HTTP/1.1\r\nHost: www.example.com\r\nContent-Length: 4\r\nContent-Encoding: aaa\r\na: b\r\na: c\r\n\r\nbody"
	 * ;
	 * //String test1 =
	 * "GET http://www.example.com/a/b/c.html? HTTP/1.1\r\nHost: www.example.com\r\nContent-Length: 4\r\nContent-Encoding: aaa\r\na: b\r\na: c\r\n\r\nbody"
	 * ;
	 * //String test =
	 * "GET /a/b/c.html?abc=h%20oge HTTP/1.1\r\nHost: www.example.com\r\n\r\nbody";
	 * String test = "HTTP/1.1 100 Continue\r\n\r\n";
	 * //Logging.log(test1.length());
	 * //test1 += "POST /aaa/bbb HTTP/1.1";
	 * //Logging.log(test1.length());
	 * Http http = new Http(test1.getBytes());
	 * byte[] body = http.getBody();
	 * util.packetProxyLog(new String(body));
	 * util.packetProxyLog("%s:%d\n", http.getProxyHost(), http.getProxyPort());
	 * util.packetProxyLog(http.getQueryAsString());
	 * util.packetProxyLog(http.getPath());
	 * List<Parameter> params = Arrays.asList("a=1", "e=2", "c=3", "a=2", "aa=1",
	 * "b=2").stream().map((s) -> new Parameter(s)).collect(Collectors.toList());
	 * http.setBodyParams(params);
	 * util.packetProxyLog(new String(http.getBody(), "UTF-8"));
	 * MultiValueMap<String, Parameter> map = http.getBodyParams();
	 * List<String> list = http.getBodyParamsOrder();
	 *
	 * http.getBodyParamsOrder().stream().forEach(s ->
	 * util.packetProxyLog((map.get(s).toString())));
	 * // Logging.log("-----");
	 * // Logging.log(new String(http.toByteArray()));
	 * // Logging.log(parseHttpDelimiter(test1.getBytes()));
	 * // Logging.log(new String(http.toByteArray()));
	 *
	 * } catch (Exception e) {
	 * errWithStackTrace(e);
	 * }
	 * }
	 */

	static final Pattern CONTINUE_PATTERN = Pattern.compile("HTTP/1.1 100 Continue\r?\n\r?\n",
			Pattern.CASE_INSENSITIVE);
	static final Pattern PLAIN_PATTERN = Pattern.compile("\nContent-Length *: *([0-9]+)", Pattern.CASE_INSENSITIVE);
	static final Pattern CHUNKED_PATTERN = Pattern.compile("\nTransfer-Encoding *: *chunked", Pattern.CASE_INSENSITIVE);
	static final Pattern GZIP_PATTERN = Pattern.compile("\nContent-Encoding *: *gzip", Pattern.CASE_INSENSITIVE);
	static final Pattern ZSTD_PATTERN = Pattern.compile("\nContent-Encoding *: *zstd", Pattern.CASE_INSENSITIVE);
	static final Pattern BR_PATTERN = Pattern.compile("\nContent-Encoding *: *br", Pattern.CASE_INSENSITIVE);

	// TODO header系作業をHttpHeaderに分離
	public static int parseHttpDelimiter(byte[] data) throws Exception {
		int header_size = HttpHeader.calcHeaderSize(data);
		if (header_size == -1) {

			return -1;
		}

		byte[] header = ArrayUtils.subarray(data, 0, header_size);
		String header_str = new String(header, "UTF-8");

		Matcher continue_matcher = CONTINUE_PATTERN.matcher(header_str);
		if (continue_matcher.find()) {

			header_size = continue_matcher.end();
			return header_size;
		}

		Matcher plain_matcher = PLAIN_PATTERN.matcher(header_str);
		int content_length;
		if (plain_matcher.find()) {

			content_length = Integer.parseInt(plain_matcher.group(1));
		} else {

			content_length = 0;
		}

		Matcher matcher = CHUNKED_PATTERN.matcher(header_str);
		if (matcher.find()) {

			byte[] body = ArrayUtils.subarray(data, header_size, data.length);
			byte[] finishFlag = "0\r\n\r\n".getBytes();
			if (body.length < finishFlag.length) {

				return -1;
			}
			if (Arrays.compare(finishFlag, 0, finishFlag.length, body, body.length - finishFlag.length,
					body.length) != 0) {

				return -1;
			}
			body = getChankedHttpBody(body);
			if (body == null)
				return -1;
		} else if (content_length == 0) {

			if (GZIP_PATTERN.matcher(header_str).find()) {

				try {

					byte[] body = ArrayUtils.subarray(data, header_size, data.length);
					gunzip(body);
				} catch (Exception e1) {

					return -1;
				}
			} else if (ZSTD_PATTERN.matcher(header_str).find()) {

				try {

					byte[] body = ArrayUtils.subarray(data, header_size, data.length);
					zstd_decompress(body);
				} catch (Exception e1) {

					return -1;
				}
			} else if (BR_PATTERN.matcher(header_str).find()) {

				try {

					byte[] body = ArrayUtils.subarray(data, header_size, data.length);
					br_decompress(body);
				} catch (Exception e1) {

					return -1;
				}
			}
		}

		if (content_length == 0) {

			return data.length;
		}

		if (data.length < header_size + content_length) {

			return -1;
		}
		return header_size + content_length;
	}

	public static boolean isHTTP(byte[] data) {
		return HttpHeader.isHTTPHeader(data);
	}

	private Http(byte[] data, boolean withoutTouchingContentLength) throws Exception {
		if (withoutTouchingContentLength) {

			this.dontTouchContentLength();
		}
		queryString = new QueryString("");
		header = new HttpHeader(data);
		originalHeader = new HttpHeader(data);
		analyzeStatusLine(header.getStatusline());
		rawBody = getHttpBody(data);
		body = getCookedBody(header, rawBody);
	}

	public HttpHeader getHeader() {
		return header;
	}

	// public boolean isHTTPS() {
	// return flag_https;
	// }

	public String getServerName() {
		return proxyHost;
	}

	public int getServerPort() {
		return proxyPort;
	}

	public boolean isProxy() {
		return flag_proxy;
	}

	public boolean isProxySsl() {
		return flag_proxy_ssl;
	}

	public byte[] getBody() {
		return body;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getPath() {
		return this.path;
	}

	public String getMethod() {
		return this.method;
	}

	public String getHost() {
		return header.getValue("Host").orElse(null);
	}

	public void setQuery(String query) {
		this.queryString = new QueryString(query);
	}

	public void setQuery(QueryString query) {
		this.queryString = query;
	}

	public QueryString getQuery() {
		return this.queryString;
	}

	public String getQueryAsString() {
		return this.queryString.toString();
	}

	public String getStatusCode() {
		return this.statusCode;
	}

	public void setBody(byte[] body) {
		this.body = body != null ? body : new byte[]{};
	}

	public void disableContentLength() {
		this.flag_disable_content_length = true;
	}

	/* Content-Lengthには触らない */
	/* HEADのレスポンス等で利用 */
	public void dontTouchContentLength() {
		this.flag_dont_touch_content_length = true;
	}

	public void disableProxyFormatUrl() {
		this.flag_disable_proxy_format_url = true;
	}

	private byte[] getCookedBody(HttpHeader header, byte[] rawBody) throws Exception {
		byte[] cookedBody = rawBody;

		{
			String headerName = "Transfer-Encoding";
			Optional<String> enc = header.getValue(headerName);

			if (enc.isPresent() && enc.get().equalsIgnoreCase("chunked")) {

				header.removeAll(headerName);
				cookedBody = getChankedHttpBodyFussy(cookedBody);
				if (cookedBody == null)
					return null;
			}
		}

		{
			String headerName = "Content-Encoding";
			Optional<String> enc = header.getValue(headerName);

			if (enc.isPresent() && enc.get().equalsIgnoreCase("gzip")) {

				cookedBody = gunzip(cookedBody);
				header.removeAll(headerName);
				if (cookedBody == null)
					return null;
			} else if (enc.isPresent() && enc.get().equalsIgnoreCase("zstd")) {

				cookedBody = zstd_decompress(cookedBody);
				header.removeAll(headerName);
				if (cookedBody == null)
					return null;
			} else if (enc.isPresent() && enc.get().equalsIgnoreCase("br")) {

				cookedBody = br_decompress(cookedBody);
				header.removeAll(headerName);
				if (cookedBody == null)
					return null;
			}
		}

		if (!this.flag_dont_touch_content_length) {

			header.removeAll("Content-Length");
		}
		return cookedBody;
	}

	public String getURL(int port, boolean use_ssl) {
		if (version.equals("HTTP/2") || version.equals("HTTP/3")) {

			return getURI(use_ssl);
		} else {
			/* HTTP/1.1 */

			String query = (getQueryAsString() != null && getQueryAsString().length() > 0)
					? "?" + getQueryAsString()
					: "";
			String path = getPath();
			String host = header.getValue("Host").orElse(null);
			String protocol = (use_ssl ? "https" : "http");
			return String.format("%s://%s%s%s", protocol, host, path, query);
		}
	}

	private String getURI(boolean use_ssl) {
		String authority = "unknown";
		if (version.equals("HTTP/2")) {

			authority = getFirstHeader("X-PacketProxy-HTTP2-Host");
		} else if (version.equals("HTTP/3")) {

			authority = getFirstHeader("x-packetproxy-http3-host");
		}
		String scheme = (use_ssl ? "https" : "http");
		String path = getPath();
		String query = getQueryAsString();
		String queryStr = (query != null && query.length() > 0) ? "?" + query : "";
		return scheme + "://" + authority + path + queryStr;
	}

	public byte[] toByteArray() throws Exception {
		byte[] result = null;
		byte[] newLine = new String("\r\n").getBytes();
		String statusLine = header.getStatusline();

		if (flag_request) {

			// String query = (getQuery() != null && getQuery().length() > 0) ?
			// "?"+URLEncoder.encode(getQuery(),"utf-8") : "";
			String query = (getQueryAsString() != null && getQueryAsString().length() > 0)
					? "?" + getQueryAsString()
					: "";
			if (this.isProxy() && this.flag_disable_proxy_format_url == false) {

				String proxyPort = (getServerPort() > 0) ? ":" + String.valueOf(getServerPort()) : "";
				statusLine = String.format("%s http://%s%s%s%s %s", this.method, getServerName(), proxyPort, getPath(),
						query, this.version);
			} else {

				statusLine = String.format("%s %s%s %s", this.method, this.path, query, this.version);
			}
		}

		result = ArrayUtils.addAll(result, statusLine.getBytes());
		result = ArrayUtils.addAll(result, newLine);
		if (!flag_request && this.statusCode != null && this.statusCode.equals("100")) {

			// 100 Continueの場合は、Content-Lengthがいらないのですぐに返す
			result = ArrayUtils.addAll(result, newLine);
			return result;
		}
		result = ArrayUtils.addAll(result, header.toByteArray());

		if (body.length == 0 && flag_request && this.getHost() != null && (this.getHost().equals("dpoint.jp")
				|| this.getHost().equals("id.smt.docomo.ne.jp") || this.getHost().equals("cfg.smt.docomo.ne.jp"))) {

			// 特定サイトでは Content-Length: 0をつけるとうまく動かないので例外処理する
		} else if (this.flag_disable_content_length) {

			// content-lengthがいらないと明示的に指定したケース
		} else if (this.flag_dont_touch_content_length) {

			// content-lengthを触らないと明示的に指定したケース
		} else {

			// Content-Typeがないパターンでも必ずContent-Lengthはつけるべき
			// if (header.containsKey("Content-Type")) {
			result = ArrayUtils.addAll(result, String.format("Content-Length: %d", body.length).getBytes());
			result = ArrayUtils.addAll(result, newLine);
			// }
		}

		result = ArrayUtils.addAll(result, newLine);
		return ArrayUtils.addAll(result, body);
	}

	public HttpHeader getOriginalHeader() {
		return originalHeader;
	}

	public boolean isGzipEncoded() {
		return getOriginalHeader().getValue("Content-Encoding").orElse("").equalsIgnoreCase("gzip");
	}

	public void encodeBodyByGzip() throws Exception {
		body = gzip(body);
		header.update("Content-Encoding", "gzip");
	}

	static final Pattern STATUS_LINE_PATTERN = Pattern.compile("([^ ]+) +([^ ]+) +([^ ]+)$");

	private void analyzeRequestStatusLine(String status_line) throws Exception {
		Matcher matcher = STATUS_LINE_PATTERN.matcher(status_line);
		if (matcher.find()) {

			this.method = matcher.group(1).trim();
			this.version = matcher.group(3).trim();
			if (this.method.startsWith("CONNECT")) {

				String urlStr = matcher.group(2).trim();
				URL url = new URL("https://" + urlStr + "/");
				this.proxyHost = url.getHost();
				this.proxyPort = (url.getPort() > 0) ? url.getPort() : 443;
				if (this.proxyPort == 80) {

					// websocketとかは平文だけどCONNECTが来る事があるので80番ポートは平文と決め打ち
					flag_proxy = true;
				} else if (this.proxyPort == 443) {

					flag_proxy_ssl = true;
				} else {

					// 多分httpsだけど、httpだと原因を探すのが大変になるので一応エラー出力しておく
					flag_proxy_ssl = true;
					log("%s can't distinguish HTTP or HTTPS, but use HTTPS", status_line);
				}
			} else {

				String urlStr = matcher.group(2).trim();
				if (urlStr.startsWith("http")) {

					flag_proxy = true;
					URL url = new URL(urlStr);
					this.proxyHost = url.getHost();
					this.proxyPort = (url.getPort() > 0) ? url.getPort() : 80;
					this.path = url.getPath();
					if (url.getQuery() != null) {

						this.queryString = new QueryString(url.getQuery());
					}
				} else {
					/* normal */

					URL url = new URL("http://example.com" + urlStr);
					this.path = url.getPath();
					if (url.getQuery() != null) {

						this.queryString = new QueryString(url.getQuery());
					}
				}
			}
		}
	}

	static final Pattern HTTP_URL_PATTERN = Pattern.compile("http://[^/]+");

	@SuppressWarnings("unused")
	private String replaceStatusLineToNonProxyStyte(String status_line) throws Exception {
		String result = status_line;
		Matcher matcher = HTTP_URL_PATTERN.matcher(status_line);
		if (matcher.find()) {

			result = matcher.replaceAll("");
		}
		return result;
	}

	static final Pattern STATUS_LINE_PATTERN2 = Pattern.compile("[^ ]+ +([^ ]+) +([a-z0-9A-Z ]+)$");

	private void analyzeResponseStatusLine(String status_line) throws Exception {
		Matcher matcher = STATUS_LINE_PATTERN2.matcher(status_line);
		if (matcher.find()) {

			this.statusCode = matcher.group(1).trim();
			// if (matcher.group(2).trim().equals("Connection established")) {
			// //flag_httpsは使ってない
			// flag_https = true;
			// }
		}
	}

	static final Pattern STATUS_LINE_PATTERN3 = Pattern.compile("^([^ ]+)");

	private void analyzeStatusLine(String status_line) throws Exception {
		Matcher matcher = STATUS_LINE_PATTERN3.matcher(status_line);
		if (matcher.find()) {

			if (matcher.group(1).trim().startsWith("HTTP")) {

				analyzeResponseStatusLine(status_line);
			} else {

				flag_request = true;
				analyzeRequestStatusLine(status_line);
			}
		}
	}

	private static byte[] getHttpBody(byte[] input_data) throws Exception {
		byte[][] search_words = {new String("\r\n\r\n").getBytes(), new String("\n\n").getBytes(),
				new String("\r\r").getBytes()};
		for (byte[] search_word : search_words) {

			int idx;
			if ((idx = Utils.indexOf(input_data, 0, input_data.length, search_word)) < 0) {

				continue;
			}
			return ArrayUtils.subarray(input_data, idx + search_word.length, input_data.length);
		}
		return new byte[]{};
	}

	private static byte[] zstd_decompress(byte[] input_data) throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(input_data);
		ZstdCompressorInputStream zstdIn = new ZstdCompressorInputStream(in);
		return IOUtils.toByteArray(zstdIn);
	}

	private static byte[] zstd_compress(byte[] input_data) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ZstdCompressorOutputStream zstdOut = new ZstdCompressorOutputStream(out);
		zstdOut.write(input_data);
		return out.toByteArray();
	}

	private static byte[] br_decompress(byte[] input_data) throws Exception {
		ByteArrayInputStream in = new ByteArrayInputStream(input_data);
		BrotliCompressorInputStream brIn = new BrotliCompressorInputStream(in);
		return IOUtils.toByteArray(brIn);
	}

	private static byte[] gunzip(byte[] input_data) throws Exception {
		if (input_data.length == 0) {

			return input_data;
		}
		try {

			ByteArrayInputStream in = new ByteArrayInputStream(input_data);
			GZIPInputStream gzin = new GZIPInputStream(in);
			return IOUtils.toByteArray(gzin);
		} catch (Exception e) {

			/* Streaming Responseサポートのため、中途半端なgzipを展開しないといけないケースが多々ある */
			byte[] zipped = input_data;
			InputStream in = new GZIPInputStream(new ByteArrayInputStream(zipped));
			byte[] inflates = new byte[zipped.length * 10];
			int inflatesLength = 0;
			ByteArrayOutputStream unzipped = new ByteArrayOutputStream();
			try {

				while ((inflatesLength = in.read(inflates, 0, inflates.length)) > 0) {

					unzipped.write(ArrayUtils.subarray(inflates, 0, inflatesLength));
				}
			} catch (Exception e1) {

			}
			return unzipped.toByteArray();
		}
	}

	private static byte[] gzip(byte[] input_data) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPOutputStream gout = new GZIPOutputStream(out);
		gout.write(input_data);
		gout.close();
		return out.toByteArray();
	}

	private static byte[] getChankedHttpBodyFussy(byte[] input_data) throws Exception {
		// TODO 改行コードの対応
		byte[] search_word = new String("\r\n").getBytes();
		int index = 0;
		int start_index = 0;
		byte[] body = new byte[0];
		while ((index = Utils.indexOf(input_data, start_index, input_data.length, search_word)) >= 0) {

			try {

				byte[] chank_header = ArrayUtils.subarray(input_data, start_index, index);
				String chank_length_str = new String(chank_header, "UTF-8").replaceAll("^0+([^0].*)$", "$1");
				int chank_length = Integer.parseInt(chank_length_str.trim(), 16);
				if (chank_length == 0) {

					return body;
				}
				byte[] chank = ArrayUtils.subarray(input_data, index + search_word.length,
						index + search_word.length + chank_length);
				body = ArrayUtils.addAll(body, chank);
				start_index = index + search_word.length * 2 + chank_length;
			} catch (Exception e) {

				return body;
			}
		}
		return body;
	}

	private static byte[] getChankedHttpBody(byte[] input_data) throws Exception {
		// TODO 改行コードの対応
		byte[] search_word = new String("\r\n").getBytes();
		int index = 0;
		int start_index = 0;
		byte[] body = new byte[0];
		while ((index = Utils.indexOf(input_data, start_index, input_data.length, search_word)) >= 0) {

			try {

				byte[] chank_header = ArrayUtils.subarray(input_data, start_index, index);
				String chank_length_str = new String(chank_header, "UTF-8").replaceAll("^0+([^0].*)$", "$1");
				int chank_length = Integer.parseInt(chank_length_str.trim(), 16);
				if (chank_length == 0) {

					return body;
				}
				byte[] chank = ArrayUtils.subarray(input_data, index + search_word.length,
						index + search_word.length + chank_length);
				body = ArrayUtils.addAll(body, chank);
				start_index = index + search_word.length * 2 + chank_length;
			} catch (Exception e) {

				return null;
			}
		}
		return null;
	}

	public InetSocketAddress getServerAddr() throws Exception {
		return new InetSocketAddress(PrivateDNSClient.getByName(proxyHost), proxyPort);
	}

	public List<String> getBodyParamsOrder() {
		String body;
		try {

			body = new String(getBody(), "UTF-8");
		} catch (UnsupportedEncodingException e) {

			errWithStackTrace(e);
			return null;
		}
		List<String> pairs = Arrays.asList(body.split("&"));
		Set<String> usedName = new HashSet<>();
		List<String> names = pairs.stream().map(p -> p.split("=")[0]).collect(Collectors.toList());

		return names.stream().filter(n -> !usedName.contains(n)).peek(usedName::add).collect(Collectors.toList());
	}

	public MultiValueMap<String, Parameter> getBodyParams() {
		String body;
		try {

			body = new String(getBody(), "UTF-8");
		} catch (UnsupportedEncodingException e) {

			errWithStackTrace(e);
			return null;
		}
		String[] pairs = body.split("&");
		MultiValueMap<String, Parameter> nameToParams = new MultiValueMap<>();
		for (String param : pairs) {

			Parameter p = new Parameter(param);
			nameToParams.put(p.getName(), p);
		}
		return nameToParams;
	}

	public void setBodyParams(List<Parameter> params) {
		List<String> paramStrings = params.stream().map(e -> e.toString()).collect(Collectors.toList());
		setBody(String.join("&", paramStrings).getBytes());
		return;
	}

	@SuppressWarnings("deprecation")
	public String getCookie(String key) {
		List<String> cookies = getHeader().getAllValue("Cookie");
		Map<String, String> cookieMap;
		cookieMap = cookies.stream().flatMap(v -> Arrays.stream(v.split(";\\s*"))).map(kv -> kv.split("="))
				.collect(Collectors.toMap(kv -> URLDecoder.decode(kv[0]), kv -> URLDecoder.decode(kv[1])));
		return cookieMap.get(key);
	}

	public String getOverrideHttpMethod() {
		String tmp = this.getFirstHeader("X-HTTP-Method-Override");
		return tmp.isEmpty() ? this.method : tmp;
	}

	// 以下非推奨 互換性のため
	public String getFirstHeader(String key) {
		return header.getValue(key).orElse("");
	}

	public void removeHeader(String key) {
		header.removeAll(key);
	}

	public void updateHeader(String key, String value) {
		header.update(key, value);
	}

	public void removeMatches(String regex) {
		header.removeMatches(regex);
	}

	public List<String> getHeader(String key) {
		return header.getAllValue(key);
	}

	public boolean isRequest() {
		return flag_request;
	}
}
