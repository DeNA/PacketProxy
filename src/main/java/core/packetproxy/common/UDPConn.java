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
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.output.ByteArrayOutputStream;

public class UDPConn {

	private final PipeEndpoint pipe;
	private final InetSocketAddress addr;
	private final RawEndpoint rawEndpoint;
	private final RawEndpoint proxyRawEndpoint;
	private volatile boolean closed;

	public UDPConn(InetSocketAddress addr) throws Exception {
		this.addr = addr;
		this.pipe = new PipeEndpoint(addr);
		this.rawEndpoint = this.pipe.getRawEndpoint();
		this.proxyRawEndpoint = this.pipe.getProxyRawEndpoint();
		this.closed = false;
	}

	public void put(byte[] data, int offset, int length) throws Exception {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		bout.write(data, offset, length);
		put(bout.toByteArray());
		bout.close();
	}

	public void put(byte[] data) throws Exception {
		OutputStream os = rawEndpoint.getOutputStream();
		os.write(data);
		os.flush();
	}

	public void getAutomatically(final BlockingQueue<DatagramPacket> queue) throws Exception {
		ExecutorService executor = Executors.newSingleThreadExecutor();
		Callable<Void> recvTask = new Callable<Void>() {

			public Void call() throws Exception {
				while (!closed) {

					InputStream is = rawEndpoint.getInputStream();
					byte[] buf = new byte[4096];
					int len = is.read(buf);
					if (len < 0) {

						return null;
					}
					DatagramPacket recvPacket = new DatagramPacket(buf, len, addr);
					queue.put(recvPacket);
				}
				return null;
			}
		};
		executor.submit(recvTask);
	}

	public Endpoint getEndpoint() throws Exception {
		return proxyRawEndpoint;
	}

	public void close() throws Exception {
		if (closed) {

			return;
		}
		closed = true;
		try {

			rawEndpoint.getInputStream().close();
		} catch (Exception ignored) {
		}
		try {

			rawEndpoint.getOutputStream().close();
		} catch (Exception ignored) {
		}
		try {

			proxyRawEndpoint.getInputStream().close();
		} catch (Exception ignored) {
		}
		try {

			proxyRawEndpoint.getOutputStream().close();
		} catch (Exception ignored) {
		}
	}
}
