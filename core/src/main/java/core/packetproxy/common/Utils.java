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
package packetproxy.common;

import static packetproxy.util.Logging.err;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ArrayUtils;

public class Utils {

	public static List<byte[]> splitArray(byte[] array, int maxSubArraySize) {
		List<byte[]> list = new ArrayList<>();
		int pos = 0;
		while (pos < array.length) {

			int subArraySize = Math.min(array.length - pos, maxSubArraySize);
			list.add(ArrayUtils.subarray(array, pos, pos + subArraySize));
			pos += subArraySize;
		}
		return list;
	}

	public static int indexOf(byte[] input_data, int start_idx, int end_idx, byte[] word) {
		assert (end_idx <= input_data.length);
		for (int i = start_idx + word.length - 1; i < end_idx; i++) {

			int start_input_idx = i - word.length + 1;
			int word_idx;
			for (word_idx = 0; word_idx < word.length && start_input_idx + word_idx < input_data.length; word_idx++) {

				if (word[word_idx] != input_data[start_input_idx + word_idx]) {

					break;
				}
			}
			if (word_idx == word.length) {

				return start_input_idx;
			}
		}
		return -1;
	}

	/** OSの名前を返す */
	public enum Platform {
		WINDOWS, MAC, LINUX
	};

	public static Platform checkOS() {
		String osname = System.getProperty("os.name");
		if (osname.contains("Windows")) {

			return Platform.WINDOWS;
		} else if (osname.contains("Mac")) {

			return Platform.MAC;
		} else {

			return Platform.LINUX;
		}
	}

	/** OS判定用 */
	public static boolean isWindows() {
		return checkOS() == Platform.WINDOWS;
	}

	public static boolean isMac() {
		return checkOS() == Platform.MAC;
	}

	/** ExecuteExe関数で利用される。Macの場合monoを追加する */
	private static String[] addMonoPath(String... args) {
		List<String> cmd_array = new ArrayList<String>();
		Utils.Platform os = Utils.checkOS();
		if (os == Utils.Platform.MAC || os == Utils.Platform.LINUX) {

			cmd_array.add("mono");
		}
		for (String s : args) {

			cmd_array.add(s);
		}
		return cmd_array.toArray(new String[0]);
	}

	private static String[] toCmdArray(String... args) {
		List<String> cmd_array = new ArrayList<String>();
		for (String s : args) {

			cmd_array.add(s);
		}
		return cmd_array.toArray(new String[0]);
	}

	public static byte[] executeCmd(String... command) throws Exception {
		Process p = Runtime.getRuntime().exec(toCmdArray(command));
		InputStream in = p.getInputStream();
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int len = 0;
		while ((len = in.read(buffer, 0, 4096)) > 0) {

			bout.write(buffer, 0, len);
		}
		InputStream err = p.getErrorStream();
		ByteArrayOutputStream berr = new ByteArrayOutputStream();
		while ((len = err.read(buffer, 0, 4096)) > 0) {

			berr.write(buffer, 0, len);
		}
		if (berr.size() > 0) {

			err(berr.toString());
		}
		return bout.toByteArray();
	}

	/**
	 * EXEを実行する。MACの場合はmonoで実行する
	 *
	 * @return 標準出力に表示されたデータ
	 */
	public static byte[] executeExe(String... command) throws Exception {
		Process p = Runtime.getRuntime().exec(addMonoPath(command));
		InputStream in = p.getInputStream();
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int len = 0;
		while ((len = in.read(buffer, 0, 4096)) > 0) {

			bout.write(buffer, 0, len);
		}
		InputStream err = p.getErrorStream();
		ByteArrayOutputStream berr = new ByteArrayOutputStream();
		while ((len = err.read(buffer, 0, 4096)) > 0) {

			berr.write(buffer, 0, len);
		}
		if (berr.size() > 0) {

			err(berr.toString());
		}
		return bout.toByteArray();
	}

	private static String[] addRubyPath(String... args) {
		List<String> cmd_array = new ArrayList<String>();
		Utils.Platform os = Utils.checkOS();
		if (os == Utils.Platform.MAC || os == Utils.Platform.LINUX) {

			cmd_array.add("ruby");
		}
		for (String s : args) {

			cmd_array.add(s);
			// Logging.log(s+ " ");
		}
		// Logging.log("");
		return cmd_array.toArray(new String[0]);
	}

	/**
	 * EXEを実行する。MACの場合はmonoで実行する
	 *
	 * @return 標準出力に表示されたデータ
	 */
	public static byte[] executeRuby(String... command) throws Exception {

		Process p = Runtime.getRuntime().exec(addRubyPath(command));
		InputStream in = p.getInputStream();
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int len = 0;
		while ((len = in.read(buffer, 0, 4096)) > 0) {

			bout.write(buffer, 0, len);
		}
		return Base64.decodeBase64(bout.toByteArray());
	}

	public static byte[] readfile(String filename) throws Exception {
		FileInputStream fis = new FileInputStream(filename);
		BufferedInputStream bis = new BufferedInputStream(fis);
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int len = 0;
		while ((len = bis.read(buffer, 0, 4096)) > 0) {

			bout.write(buffer, 0, len);
		}
		fis.close();
		return bout.toByteArray();
	}

	public static void deletefile(String filename) throws Exception {
		File file = new File(filename);
		if (file.exists()) {

			file.delete();
		}
	}

	public static void writefile(String filename, byte[] data) throws Exception {
		FileOutputStream fos = new FileOutputStream(filename);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		bos.write(data);
		bos.flush();
		bos.close();
	}

	public static byte[] gzip(byte[] src) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPOutputStream gzos = new GZIPOutputStream(out);
		gzos.write(src);
		gzos.flush();
		gzos.finish();
		return out.toByteArray();
	}

	public static byte[] ungzip(byte[] src) throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(src));
		byte[] buf = new byte[1024];
		while (true) {

			int len = gzis.read(buf);
			if (len < 0)
				break;

			out.write(buf, 0, len);
		}
		return out.toByteArray();
	}

	public static byte[] replaceArray(byte[] src, Range area, byte[] replacer) {
		return replaceArray(src, area.getPositionStart(), area.getPositionEnd(), replacer);
	}

	public static byte[] replaceArray(byte[] src, int start_idx, int end_idx, byte[] replacer) {
		byte[] head = ArrayUtils.subarray(src, 0, start_idx);
		byte[] tail = ArrayUtils.subarray(src, end_idx, src.length);
		return ArrayUtils.addAll(head, ArrayUtils.addAll(replacer, tail));
	}

	public static byte[] replaceBinary(byte[] data, byte[] binPattern, byte[] binReplaced) {
		int idx = 0;
		while (idx < data.length) {

			if ((idx = Utils.indexOf(data, idx, data.length, binPattern)) < 0) {

				return data;
			}
			byte[] front_data = ArrayUtils.subarray(data, 0, idx);
			byte[] back_data = ArrayUtils.subarray(data, idx + binPattern.length, data.length);
			data = ArrayUtils.addAll(front_data, binReplaced);
			data = ArrayUtils.addAll(data, back_data);
			idx += binReplaced.length;
		}
		return data;
	}

	public static byte[] getSelectedCharacters(byte[] src, int start_idx, int end_idx) {
		return ArrayUtils.subarray(src, start_idx, end_idx);
	}

	public static boolean isPrintable(byte[] data) {
		for (byte b : data) {

			if (b < 32)
				return false;
			if (b > 126)
				return false;
		}
		return true;
	}

	public static boolean supportedJava() {
		return executedByJDK();
	}

	public static boolean executedByJDK() {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		return compiler != null;
	}

	public static boolean javaVersionIs1_8() {
		String version = System.getProperty("java.version");
		return version.matches("1\\.8\\..*");
	}
}
