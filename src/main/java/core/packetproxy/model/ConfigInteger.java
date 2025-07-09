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
package packetproxy.model;

public class ConfigInteger {
	private String key;
	private Configs configs;
	private Config config;

	public ConfigInteger(String key) throws Exception {
		this.key = key;
		configs = Configs.getInstance();
		config = configs.query(key);
		if (config == null) {
			configs.create(new Config(key, "0"));
		}
	}

	public int getInteger() throws Exception {
		config = configs.query(key);
		return Integer.parseInt(config.getValue());
	}

	public void setInteger(int value) throws Exception {
		config = configs.query(key);
		config.setValue(Integer.toString(value));
		configs.update(config);
	}
}
