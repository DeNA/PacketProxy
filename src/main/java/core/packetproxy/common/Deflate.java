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

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Deflate {

	public byte[] decompress(byte[] data) {
		Inflater decompressor = new Inflater();
		decompressor.setInput(data);
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] result = new byte[100000];
		int length = 0;
		try {

			while (!decompressor.finished()) {

				length = decompressor.inflate(result);
				if (length > 0) {

					os.write(result, 0, length);
				} else {

					break;
				}
			}
		} catch (DataFormatException e) {

			errWithStackTrace(e);
		}
		return os.toByteArray();
	}

	public byte[] compress(byte[] data) {
		Deflater compressor = new Deflater();
		compressor.setInput(data);
		compressor.finish();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] result = new byte[100000];
		int length = 0;
		while (!compressor.finished()) {

			length = compressor.deflate(result);
			os.write(result, 0, length);
		}
		return os.toByteArray();
	}
}
