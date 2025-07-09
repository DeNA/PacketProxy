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
package packetproxy.http1;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import packetproxy.common.StringUtils;
import packetproxy.http.Http;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

public class Http1StreamingResponse {
	private ByteArrayOutputStream clientInput = new ByteArrayOutputStream();
	private ByteArrayOutputStream serverInput = new ByteArrayOutputStream();

	private ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	private ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
	private boolean headerReceived = false;

	public int checkRequestDelimiter(byte[] data) throws Exception {
		return Http.parseHttpDelimiter(data);
	}
	public void clientRequestArrived(byte[] data) throws Exception {
		clientInput.write(data);
	}
	public byte[] passThroughClientRequest() throws Exception {
		return null;
	};
	public byte[] clientRequestAvailable() throws Exception {
		byte[] ret = clientInput.toByteArray();
		clientInput.reset();
		return ret;
	}
	public byte[] decodeClientRequest(byte[] input_data) throws Exception {
		return input_data;
	}
	public byte[] encodeClientRequest(byte[] input_data) throws Exception {
		return input_data;
	}

	public int checkResponseDelimiter(byte[] data) throws Exception {
		return data.length;
	}
	public void serverResponseArrived(byte[] data) throws Exception {
		serverInput.write(data);
	}
	public byte[] passThroughServerResponse() throws Exception {
		buffer.write(serverInput.toByteArray());
		if (headerReceived == false) {
			int endOfHeader = StringUtils.binaryFind(buffer.toByteArray(), "\r\n\r\n".getBytes());
			if (endOfHeader > 0) {
				byte[] header = ArrayUtils.subarray(buffer.toByteArray(), 0, endOfHeader + 2);
				byte[] body = ArrayUtils.subarray(buffer.toByteArray(), endOfHeader + 4, buffer.size());
				byte[] newHeader = ArrayUtils.addAll(header,
						String.format("X-PacketProxy-HTTP1-UUID: %s\r\n\r\n", StringUtils.randomUUID()).getBytes());
				byte[] newHttp = ArrayUtils.addAll(newHeader, body);
				buffer.reset();
				buffer.write(newHttp);
				headerBuffer.write(newHeader);
				headerReceived = true;
			}
		} else {
			Http http;
			int delim = Http.parseHttpDelimiter(buffer.toByteArray());
			if (delim > 0) {
				byte[] httpData = ArrayUtils.subarray(buffer.toByteArray(), 0, delim);
				byte[] remaining = ArrayUtils.subarray(buffer.toByteArray(), delim, buffer.size());
				headerReceived = false;
				buffer.reset();
				buffer.write(remaining);
				http = Http.create(httpData);
			} else {
				http = Http.create(buffer.toByteArray());
			}
			Thread guiHistoryUpdater = new Thread(new Runnable() {
				public void run() {
					try {
						if (http.getBody() != null && http.getBody().length > 0) {
							List<Packet> packets = Packets.getInstance()
									.queryFullText(http.getFirstHeader("X-PacketProxy-HTTP1-UUID"));
							for (Packet packet : packets) {
								Packet p = Packets.getInstance().query(packet.getId());
								p.setDecodedData(http.toByteArray());
								p.setModifiedData(http.toByteArray());
								Packets.getInstance().update(p);
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			guiHistoryUpdater.start();
		}
		byte[] out = serverInput.toByteArray();
		serverInput.reset();
		return out;
	}
	public byte[] serverResponseAvailable() throws Exception {
		if (headerBuffer.size() == 0) {
			return null;
		}
		byte[] out = headerBuffer.toByteArray();
		headerBuffer.reset();
		return out;
	}
	public byte[] decodeServerResponse(byte[] input_data) throws Exception {
		return input_data;
	}
	public byte[] encodeServerResponse(byte[] input_data) throws Exception {
		return null;
	}

}
