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
package packetproxy;

import packetproxy.platform.SpoofingIPSource;

public class DNSSpoofingIPGetter {

	private final SpoofingIPSource source;

	public DNSSpoofingIPGetter(SpoofingIPSource source) {
		this.source = source;
	}

	public boolean isAuto() {
		return source.isAuto();
	}

	public String get() {
		return source.get();
	}

	public String get6() {
		return source.get6();
	}

	public String getInt() {
		return source.getInt();
	}
}
