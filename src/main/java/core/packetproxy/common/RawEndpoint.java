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

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class RawEndpoint implements Endpoint
{
	private InputStream input;
	private OutputStream output;
	private InetSocketAddress addr;
	
	public RawEndpoint(InetSocketAddress addr, InputStream input, OutputStream output) throws Exception {
		this.addr = addr;
		this.input = input;
		this.output = output;
	}
	
	@Override
	public InetSocketAddress getAddress() {
		return addr;
	}
	
	@Override
	public InputStream getInputStream() throws Exception {
		return input;
	}

	@Override
	public OutputStream getOutputStream() throws Exception {
		return output;
	}

	@Override
	public int getLocalPort() {
		return 0;
	}
	
	@Override
	public String getName() {
		return null;
	}
}
