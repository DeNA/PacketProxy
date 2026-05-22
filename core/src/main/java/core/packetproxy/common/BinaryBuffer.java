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

import static packetproxy.util.Logging.errWithStackTrace;
import static packetproxy.util.Logging.log;

import org.apache.commons.lang3.ArrayUtils;

public class BinaryBuffer {
	/*
	 * static public void main(String[] args) {
	 * try {
	 * BinaryBuffer a = new BinaryBuffer("hello, world".getBytes());
	 * a.remove(1, 1);
	 * Logging.log(a.toString());
	 * BinaryBuffer b = new BinaryBuffer("hello, world".getBytes());
	 * b.insert(1, "aa".getBytes());
	 * Logging.log(b.toString());
	 * BinaryBuffer c = new BinaryBuffer("hello, world".getBytes());
	 * c.insert(1, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
	 * Logging.log(c.toString());
	 * } catch (Exception e) {
	 * errWithStackTrace(e);
	 * }
	 * }
	 */

	private int buffer_capacity = 4096;
	private byte[] buffer = new byte[buffer_capacity];
	private int data_size = 0;
	private int data_size_in_utf8 = 0;

	public BinaryBuffer() {
	}

	public BinaryBuffer(byte[] in) throws Exception {
		insert(0, in);
	}

	@Override
	public String toString() {
		try {

			String msg = String.format("capacity: %d, data: %d, data_utf8: %d\n", buffer_capacity, data_size,
					data_size_in_utf8);
			// Binary b = new Binary(ArrayUtils.subarray(buffer, 0, data_size));
			// return msg + b.toHexString(32).toString();
			return msg;
		} catch (Exception e) {

			errWithStackTrace(e);
		}
		return "";
	}

	public byte[] toByteArray() {
		return ArrayUtils.subarray(buffer, 0, data_size);
	}

	public void reset(byte[] in) throws Exception {
		removeAll();
		insert(0, in);
	}

	public int getLength() {
		return data_size;
	}

	public int getLengthInUTF8() {
		return data_size_in_utf8;
	}

	public void removeAll() {
		data_size = 0;
		data_size_in_utf8 = 0;
	}

	public void remove(int index, int length) {
		// Logging.log(String.format("remove! length: %d", length));
		/*
		 * データをUTF-8テキストとして扱いたいときは、下記を有効にすること
		 */
		// if (buffer[index] < (byte)0x80) {
		// length = 1;
		// } else if (buffer[index] < (byte)0xc0) {
		// return;
		// } else if (buffer[index] < (byte)0xe0) {
		// length = 2;
		// } else if (buffer[index] < (byte)0xf0) {
		// length = 3;
		// } else if (buffer[index] < (byte)0xf8) {
		// length = 4;
		// } else if (buffer[index] < (byte)0xfc) {
		// length = 5;
		// } else {
		// length = 6;
		// }
		if (data_size < index + length) {

			log("[Error] Something wrong (%d < %d + %d)", data_size, index, length);
			return;
		}
		data_size_in_utf8 -= new String(buffer, index, length).length();
		System.arraycopy(buffer, index + length, buffer, index, data_size - index - length);
		data_size -= length;
	}

	public void insert(int index, byte[] in) throws Exception {
		// Logging.log("insert!");
		// Logging.log(new Binary(in).toHexString(64).toString());
		if (in == null)
			return;
		if (data_size + in.length > buffer_capacity) {

			expandBuffer(data_size + in.length);
		}
		System.arraycopy(buffer, index, buffer, index + in.length, data_size - index);
		System.arraycopy(in, 0, buffer, index, in.length);
		data_size += in.length;
		data_size_in_utf8 += new String(in).length();
	}

	private void expandBuffer(int n) {
		if (n < buffer_capacity)
			return;

		int new_buffer_capacity = buffer_capacity;
		while (new_buffer_capacity < n) {

			new_buffer_capacity *= 2;
		}

		byte[] new_buffer = new byte[new_buffer_capacity];
		System.arraycopy(buffer, 0, new_buffer, 0, buffer_capacity);

		buffer = new_buffer;
		buffer_capacity = new_buffer_capacity;
	}
}
