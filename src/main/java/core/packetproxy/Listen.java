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
package packetproxy;

import packetproxy.model.ListenPort;

public class Listen {
	private ListenPort listen_info;
	private Proxy proxy;

	public ListenPort getListenInfo() {
		return listen_info;
	}
	public void close() throws Exception {
		if (proxy != null) {
			proxy.close();
			DuplexManager.getInstance().closeAndClearDuplex(listen_info.getPort());
		}
	}
	public Listen(ListenPort listen_info) throws Exception {
		this.listen_info = listen_info;
		proxy = ProxyFactory.create(listen_info);
		proxy.start();
	}
}
