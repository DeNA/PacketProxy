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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.commons.lang3.ArrayUtils;

import packetproxy.common.StringUtils;
import packetproxy.model.Packet;

public abstract class Encoder
{
	public abstract String getName();
	public abstract int checkDelimiter(byte[] input_data) throws Exception;
	public int checkRequestDelimiter(byte[] input_data) throws Exception { return checkDelimiter(input_data); }
	public int checkResponseDelimiter(byte[] input_data) throws Exception { return checkDelimiter(input_data); }
	public abstract byte[] decodeServerResponse(byte[] input_data) throws Exception;
	public abstract byte[] encodeServerResponse(byte[] input_data) throws Exception;
	public abstract byte[] decodeClientRequest(byte[] input_data) throws Exception;
	public abstract byte[] encodeClientRequest(byte[] input_data) throws Exception;
	
	protected ByteArrayOutputStream clientInputData = new ByteArrayOutputStream();
	protected ByteArrayOutputStream serverInputData = new ByteArrayOutputStream();
	/* 溜める */
	public void clientRequestArrived(byte[] input_data) throws Exception { clientInputData.write(input_data); }
	public void serverResponseArrived(byte[] input_data) throws Exception { serverInputData.write(input_data); }
	/* 画面に表示せず、送信パターン (画面に表示したくないコントロールデータの送信をしたいとき利用) */
	public byte[] passThroughClientRequest() throws Exception { return null; }
	public byte[] passThroughServerResponse() throws Exception { return null; }
	/* 画面に表示せず、送信しないパターン (まだデータが溜まっていない状態が存在するとき利用) */
	public byte[] clientRequestAvailable() throws Exception { byte[] ret = clientInputData.toByteArray(); clientInputData.reset(); return ret; }
	public byte[] serverResponseAvailable() throws Exception { byte[] ret = serverInputData.toByteArray(); serverInputData.reset(); return ret; }
	
	/**
	 * 再送するときに、新しいコネクションを利用するか、それとも既存のコネクションを利用するかの使い分け
	 * true: 新しいコネクションを利用する (Default)
	 * false: 既存のコネクションを利用する
	 */
	public boolean useNewConnectionForResend() {
		return true;
	}

	/**
	 * パケットのheadlineを返す。履歴ウィンドウで利用されます。
	 * 文字化けすると重たくなるのでデフォルトではASCIIで表示可能な部分のみ表示する
	 */
	public String getSummarizedRequest(Packet packet) {
		byte[] data = packet.getDecodedData();
		byte[] prefix = ArrayUtils.subarray(data, 0, Math.min(100, data.length));
		prefix = StringUtils.toAscii(prefix);
		return new String(prefix);
	}
	public String getSummarizedResponse(Packet packet) {
		byte[] data = packet.getDecodedData();
		byte[] prefix = ArrayUtils.subarray(data, 0, Math.min(100, data.length));
		prefix = StringUtils.toAscii(prefix);
		return new String(prefix);
	}
	
	/**
	 * 再送時のみ呼び出されます。encode関数が実行される前に実行されます。
	 */
	public byte[] procBeforeResendClientRequest(Packet packet) throws Exception {
		return packet.getModifiedData();
	}
	public byte[] procBeforeResendServerResponse(Packet packet) throws Exception {
		return packet.getModifiedData();
	}

	/**
	 * client_packet, server_packetは変更禁止、読み込みのみで使う事
	 */
	public byte[] decodeServerResponse(Packet client_packet, Packet server_packet) throws Exception {
		return decodeServerResponse(server_packet);
	}
	public byte[] encodeServerResponse(Packet client_packet, Packet server_packet) throws Exception {
		return encodeServerResponse(server_packet);
	}

	public byte[] decodeServerResponse(Packet server_packet) throws Exception {
		return decodeServerResponse(server_packet.getReceivedData());
	}
	public byte[] encodeServerResponse(Packet server_packet) throws Exception {
		return encodeServerResponse(server_packet.getModifiedData());
	}

	public byte[] decodeClientRequest(Packet client_packet) throws Exception {
		return decodeClientRequest(client_packet.getReceivedData());
	}
	public byte[] encodeClientRequest(Packet client_packet) throws Exception {
		return encodeClientRequest(client_packet.getModifiedData());
	}

	/**
	 * PacketのContentTypeを返す
	 */
	public String getContentType(Packet client_packet, Packet server_packet) throws Exception {
		return getContentType(server_packet.getDecodedData());
	}
	public String getContentType(byte[] input_data) throws Exception {
		return "";
	}
	public void setGroupId(Packet packet) throws Exception {
	}
	
	/* 今の所、クライアントはスマホ等バッファが少ない可能性があるため、クライアント側だけフロー制御 */
	public boolean isFlowControlled() {
		return false;
	}
	public void putToFlowControlledQueue(byte[] frames) throws Exception {
	}
	public InputStream getFlowControlledInputStream() {
		return null;
	}
}
