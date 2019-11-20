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
package packetproxy.http2.frames;

public class DataFrame extends Frame {

    static protected Type TYPE = Type.DATA; 
    
    public DataFrame(Frame frame) throws Exception {
		super(frame);
		parsePayload();
    }

	public DataFrame(byte[] data) throws Exception {
		super(data);
		parsePayload();
	}

	private void parsePayload() throws Exception {
	}
	
	@Override
	public byte[] toHttp1() throws Exception {
		return payload;
	}
	
	@Override
	public String toString() {
		return super.toString() + new String(payload);
	}

}
