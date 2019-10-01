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

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;

public class PipeEndpoint
{
	private PipeStream proxy_to_user;
	private PipeStream user_to_proxy;
	private InetSocketAddress addr;
	
	class PipeStream
	{
		private final int PIPE_SIZE = 2048;	// mtuが1500なのでデフォルトの1024だと、UDPのパケットが途中で切れて通信できなくなる
		private PipedInputStream input;
		private PipedOutputStream output;
		
		public PipeStream() throws Exception {
			output = new PipedOutputStream();
			input = new PipedInputStream(output, PIPE_SIZE);
		}
		public PipedOutputStream getOutputStream() {
			return output;
		}
		public PipedInputStream getInputStream() {
			return input;
		}
	}
	
	public PipeEndpoint(InetSocketAddress addr) throws Exception
	{
		this.proxy_to_user = new PipeStream();
		this.user_to_proxy = new PipeStream();
		this.addr = addr;
	}
	
	public RawEndpoint getRawEndpoint() throws Exception {
		return new RawEndpoint(addr, proxy_to_user.getInputStream(), user_to_proxy.getOutputStream());
	}

	public RawEndpoint getProxyRawEndpoint() throws Exception {
		return new RawEndpoint(addr, user_to_proxy.getInputStream(), proxy_to_user.getOutputStream());
	}
}
