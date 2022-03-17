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

import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.Utils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class HttpHeader {
	private static final byte[] crLf = new byte[]{13, 10};
	private static final byte[] cr = new byte[]{13};
	private static final byte[] lf = new byte[]{10};

	private String statusLine;
	private List<HeaderField> fields;
	private String newLineSymbol;
	
	// TODO そのうちprivateに出来るように色々分離
	static public int calcHeaderSize(byte[] data){
		for (int i = 0; i < data.length; i++) {
			if (i <= data.length - 4 &&
					data[i] == '\r' &&
					data[i + 1] == '\n' &&
					data[i + 2] == '\r' &&
					data[i + 3] == '\n') {
				return i + 4;
			}
			if (i <= data.length - 2 && data[i] == '\n' && data[i + 1] == '\n') {
				return i + 2;
			}
		}
		return -1;
	}

	// first lineとheaderの末尾を確認する
	static public boolean isHTTPHeader(byte[] data) {
		// Headerサイズは正
		if (calcHeaderSize(data) == -1) { return false; }
		// first line取得
		int index = ArrayUtils.indexOf(data, (byte)'\n');
		if (index < 0) { return false; }
		if (index > 0 && data[index - 1] == '\r') { index--; }
		byte[] line = Arrays.copyOfRange(data, 0, index);
		// first lineに制御文字は無い
		for (int i = 0; i < line.length; i++) {
			if (line[i] < 0x20 || 0x7f <= line[i]) { return false; }
		}
		// first lineはスペース区切りでmethod pas, HTTP/?.?になってる
		String[] strs = new String(line).split(" ");
		if (strs.length != 3) { return false; }
		if (!strs[2].matches("HTTP/[0-9.]+")) { return false; }
		return true;
	}
	
	public Optional<HeaderField> getHeader(String name){
		return fields.stream().filter(h -> h.getName().equalsIgnoreCase(name)).findFirst();
	}

	public Optional<String> getValue(String name){
		Optional<HeaderField> value = getHeader(name);
		if(value.isPresent()){
			return Optional.of(value.get().getValue());
		}
		return Optional.ofNullable(null);
	}

	public List<HeaderField> getAll(String name){
		return fields.stream()
				.filter(h -> h.getName().equalsIgnoreCase(name))
				.collect(Collectors.toList());
	}

	public List<String> getAllValue(String name){
		return getAll(name).stream().map(h -> h.getValue()).collect(Collectors.toList());
	}

	public List<HeaderField> getFields() {
		return fields;
	}

	public void update(String name, String value){
		fields.removeIf(h -> h.getName().equalsIgnoreCase(name));
		fields.add(new HeaderField(name, value));
	}
	
	public void removeAll(String name){
		fields.removeIf(h -> h.getName().equalsIgnoreCase(name));
	}

	public void removeMatches(String regex){
		fields.removeIf(h -> h.getName().matches(regex));
	}
	
	public HttpHeader(byte[] rawHttp){
		newLineSymbol = lookUpNewLineSymbol(rawHttp);
		String newLineStr = newLineSymbol;
		// パケットの先頭に改行が含まれているパターンがあるので回避
		if (Utils.indexOf(rawHttp, 0, newLineSymbol.length(), newLineSymbol.getBytes()) >= 0) {
			rawHttp = ArrayUtils.subarray(rawHttp, newLineSymbol.length(), rawHttp.length);
		}
		byte[] headerDelim = (newLineStr + newLineStr).getBytes(StandardCharsets.UTF_8);
		int headerPos = Utils.indexOf(rawHttp, 0, rawHttp.length, headerDelim);
		if (headerPos < 0) {
			headerPos = rawHttp.length;
		}
		String header = toUTF8(ArrayUtils.subarray(rawHttp, 0, headerPos));
		List<String> lines = Arrays.asList(header.split(newLineStr));
		statusLine = lines.get(0);
		fields = lines.subList(1, lines.size()).stream().map(HeaderField::new).collect(Collectors.toList());
	}

	public String getStatusline(){
		return statusLine;
	}
	
	public byte[] toByteArray(){
		return fields.stream().map(h -> h.toString())
				.collect(Collectors.joining(newLineSymbol, "", newLineSymbol)).getBytes();
	}
	
	private String toUTF8(byte[] raw){
		try {
			return new String(raw, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "";
	}
	
	private String lookUpNewLineSymbol(byte[] input){
		boolean hasCR = ArrayUtils.contains(input, (byte)13);
		boolean hasLF = ArrayUtils.contains(input, (byte)10);
		if(hasCR && hasLF)return toUTF8(crLf);
		if(hasCR)return toUTF8(cr);
		return toUTF8(lf);
	}

}
