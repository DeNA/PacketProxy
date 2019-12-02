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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.InetSocketAddress;

public class WrapEndpoint extends SSLSocketEndpoint
{
	InputStream inputstream;
	
	public WrapEndpoint(SSLSocketEndpoint ep, byte[] lookaheadBuffer) throws Exception {
		super(ep);
		ByteArrayInputStream bais = new ByteArrayInputStream(lookaheadBuffer);
		this.inputstream = new SequenceInputStream(bais, super.getInputStream());
	}

	@Override
	public InputStream getInputStream() throws Exception {
		return inputstream;
	}

	@Override
	public OutputStream getOutputStream() throws Exception {
		return super.getOutputStream();
	}

	@Override
	public InetSocketAddress getAddress() {
		return super.getAddress();
	}

	@Override
	public String getName() {
		return null;
	}
}
