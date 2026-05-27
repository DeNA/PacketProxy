/*
 * Copyright 2026 DeNA Co., Ltd.
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

import com.google.gson.annotations.SerializedName;
import java.util.List;
import packetproxy.model.*;

/** 設定エクスポート／インポート用 JSON の DTO。 */
public class ConfigDaoHub {

	@SerializedName(value = "listenPorts")
	List<ListenPort> listenPortList;

	@SerializedName(value = "servers")
	List<Server> serverList;

	@SerializedName(value = "modifications")
	List<Modification> modificationList;

	@SerializedName(value = "sslPassThroughs")
	List<SSLPassThrough> sslPassThroughList;
}
