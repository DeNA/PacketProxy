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
package packetproxy.encode;

import packetproxy.http.Http;
import packetproxy.model.Packet;

public class EncodeHTTP extends Encoder
{
	private boolean originalHeaderHasGzip;
	public EncodeHTTP()
	{
		originalHeaderHasGzip = false;
	}
	
	@Override
	public String getName()
	{
		return "HTTP";
	}

	@Override
	public String getSummarizedRequest(Packet packet)
	{
		String summary = "";
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) { return ""; }
		try {
			byte[] data = (packet.getDecodedData().length > 0) ?
			packet.getDecodedData() : packet.getModifiedData();
			Http http = new Http(data);
			String method = http.getMethod();
			String url = http.getURL(packet.getServerPort());
			summary = method + " " + url;
		} catch (Exception e) {
			e.printStackTrace();
			summary = "Headlineを生成できません・・・";
		}
		return summary;
	}

	@Override
	public String getSummarizedResponse(Packet packet)
	{
		String summary = "";
		if (packet.getDecodedData().length == 0 && packet.getModifiedData().length == 0) { return ""; }
		try {
			byte[] data = (packet.getDecodedData().length > 0) ?
			packet.getDecodedData() : packet.getModifiedData();
			Http http = new Http(data);
			String statusCode = http.getStatusCode();
			summary = statusCode;
		} catch (Exception e) {
			e.printStackTrace();
			summary = "Headlineを生成できません・・・";
		}
		return summary;
	}


	@Override
	public int checkDelimiter(byte[] input) throws Exception
	{
		return Http.parseHttpDelimiter(input);
	}

	@Override
	public byte[] decodeServerResponse(byte[] input_data) throws Exception
	{
		Http http = new Http(input_data);
		originalHeaderHasGzip = http.isGzipEncoded();
		return http.toByteArray();
	}

	@Override
	public byte[] encodeServerResponse(byte[] input_data) throws Exception
	{
		Http http = new Http(input_data);
		if(originalHeaderHasGzip){
			http.encodeBodyByGzip();
		}
		return http.toByteArray();
	}

	@Override
	public byte[] decodeClientRequest(byte[] input_data) throws Exception
	{
		Http http = new Http(input_data);
		return http.toByteArray();
	}
	
	@Override
	public byte[] encodeClientRequest(byte[] input_data) throws Exception
	{
		Http http = new Http(input_data);
		return http.toByteArray();
	}
	
	@Override
	public String getContentType(byte[] input_data) throws Exception
	{
		Http http = new Http(input_data);
		return http.getFirstHeader("Content-Type");
	}
}
