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

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class Deflate {
    public byte[] decompress(byte[] data){
        Inflater decompresser = new Inflater();
        decompresser.setInput(data);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] result = new byte[100000];
        int length = 0;
        try {
            while (!decompresser.finished()) {
                length = decompresser.inflate(result);
                if (length > 0) {
                    os.write(result, 0, length);
                } else {
                    break;
                }
            }
        } catch (DataFormatException e) {
            e.printStackTrace();
        }
        return os.toByteArray();
    }

    public byte[] compress(byte[] data){
        Deflater compresser = new Deflater();
        compresser.setInput(data);
        compresser.finish();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] result = new byte[100000];
        int length = 0;
        while(!compresser.finished()) {
            length = compresser.deflate(result);
            os.write(result, 0, length);
        }
        return os.toByteArray();
    }
}
