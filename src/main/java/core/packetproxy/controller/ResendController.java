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
package packetproxy.controller;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

import packetproxy.Duplex;
import packetproxy.DuplexFactory;
import packetproxy.DuplexManager;
import packetproxy.EncoderManager;
import packetproxy.common.I18nString;
import packetproxy.encode.Encoder;
import packetproxy.http.Http;
import packetproxy.model.OneShotPacket;
import packetproxy.model.Packet;
import packetproxy.util.PacketProxyUtility;

public class ResendController
{
	private static ResendController instance;
	public static ResendController getInstance() throws Exception {
		if (instance == null) {
			instance = new ResendController();
		}
		return instance;
	}

	private ResendController() throws Exception {
	}

	/**
	 * レスポンスを受け取って処理する必要がないとき用
	 */
	public void resend(OneShotPacket oneshot) throws Exception {
		resend(oneshot, 1);
	}

	/**
	 * レスポンスを受け取って処理する必要がないとき用
	 */
	public void resend(OneShotPacket oneshot, int count) throws Exception {
		resend(oneshot, count, false);
	}

	/**
	 * レスポンスを受け取って処理する必要がないとき用
	 */
	public void resend(OneShotPacket oneshot, int count, boolean wait) throws Exception {
		SwingWorker<Object, OneShotPacket> worker;
		worker = new ResendWorker(oneshot, count);
		worker.execute();
		if (wait && count != 1) {
			try {
				// InterceptでForward x 20した時に先に本体が処理されると困るので待つ
				worker.get(20000, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * レスポンスを受け取って処理する必要があるとき用
	 * ResendUsingNewConnectionを無名クラスでextendsしてprocessでList<OneShotPacket>受け取る
	 * @param worker
	 */
	public void resend(ResendWorker worker) {
		worker.execute();
	}

	static public class ResendWorker extends SwingWorker<Object, OneShotPacket> {
		private PacketProxyUtility util;
		int count;
		OneShotPacket oneshot;
		OneShotPacket[] oneshots;

		// 同じパケットを複数再送
		public ResendWorker(OneShotPacket oneshot, int count) {
			this.oneshot = oneshot;
			this.count = count;
			this.oneshots = null;
		}

		// 異なるパケットを複数再送
		public ResendWorker(OneShotPacket[] oneshots) {
			this.oneshot = null;
			this.count = 0;
			this.oneshots = oneshots;
		}

		@Override
		protected Object doInBackground() throws Exception {
			try {
				ArrayList<DataToBeSend> list = new ArrayList<DataToBeSend>();
				if (this.oneshot != null && this.count > 0) {
					for (int i = 0; i < this.count; i++) {
						DataToBeSend sendData = new DataToBeSend(this.oneshot, result -> {
							publish(result);
						});
						list.add(sendData);
					}
				} else if (this.oneshots != null && this.oneshots.length > 0) {
					for (OneShotPacket os : this.oneshots) {
						DataToBeSend sendData = new DataToBeSend(os, result -> {
							publish(result);
						});
						list.add(sendData);
					}
				} else {
					util.packetProxyLogErr("Resend packet is wrong!");
					return null;
				}
				list.stream().parallel().forEach(sendData -> {
					try {
						sendData.send();
					} catch (Exception e) {
						e.printStackTrace();
					}
				});
			} catch (SocketTimeoutException e) {
				PacketProxyUtility.getInstance().packetProxyLogErr("Resend Connection is timeout!");
				PacketProxyUtility.getInstance().packetProxyLogErr("All resend packets are dropped.");
				e.printStackTrace();
				return null;
			}
			return null;
		}

		private class DataToBeSend {
			private Duplex duplex;
			private OneShotPacket oneshot;
			private byte[] preparedData;
			private Consumer<OneShotPacket> onReceived;
			private boolean isSync;

			public DataToBeSend(OneShotPacket oneshot, Consumer<OneShotPacket> onReceived) throws Exception {
				this.oneshot = oneshot;
				this.onReceived = onReceived;
				Encoder encoder = EncoderManager.getInstance().createInstance(oneshot.getEncoder());
				if (encoder.useNewConnectionForResend() == true) {
					this.duplex = DuplexFactory.createDuplexSyncFromOneShotPacket(this.oneshot);
					this.isSync = false;
				} else {
					Duplex original_duplex = DuplexManager.getInstance().getDuplex(oneshot.getConn());
					if (original_duplex == null) {
						PacketProxyUtility.getInstance().packetProxyLogErr(I18nString.get("[Error] tried to resend packets, but the connection was already closed."));
						return;
					}
					this.duplex = DuplexFactory.createDuplexFromOriginalDuplex(original_duplex, this.oneshot);
					this.isSync = true;
				}	
				this.preparedData = this.duplex.prepareFastSend(this.oneshot.getData());
			}

			public void send() throws Exception {
				if (duplex == null)
					return;

				this.duplex.execFastSend(this.preparedData);
				if (isSync) {
					OneShotPacket result = new OneShotPacket(
							oneshot.getId(),
							oneshot.getListenPort(),
							oneshot.getClient(),
							oneshot.getServer(),
							oneshot.getServerName(),
							oneshot.getUseSSL(),
							I18nString.get("In case that packets were resend to already connected socket, results can't be displayed in this window. See the history window instead.").getBytes(),
							oneshot.getEncoder(),
							Packet.Direction.SERVER,
							oneshot.getConn(),
							oneshot.getGroup());
					this.onReceived.accept(result);
				} else {
					byte[] data = duplex.receive();
					/* 100 Continue 対策 */
					if (oneshot.getEncoder().equals("HTTP") ||
							EncoderManager.getInstance().createInstance(oneshot.getEncoder()).getClass().getSuperclass().getName().equals("packetproxy.encode.EncodeHTTPBase")) {
						while (new Http(data).getStatusCode().equals("100")) {
							data = duplex.receive();
						}
					}

					OneShotPacket result = new OneShotPacket(
							oneshot.getId(),
							oneshot.getListenPort(),
							oneshot.getClient(),
							oneshot.getServer(),
							oneshot.getServerName(),
							oneshot.getUseSSL(),
							data,
							oneshot.getEncoder(),
							Packet.Direction.SERVER,
							oneshot.getConn(),
							oneshot.getGroup());

					this.onReceived.accept(result);
				}
			}
		}
	}

}
