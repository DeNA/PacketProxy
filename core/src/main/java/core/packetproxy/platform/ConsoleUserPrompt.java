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
package packetproxy.platform;

import static packetproxy.util.Logging.err;

/** 非対話環境（Gulp 等）向け。確認は行わず、テーブル再作成は拒否する。 */
public class ConsoleUserPrompt implements UserPrompt {

	@Override
	public boolean confirmTableRecreate(String tableName, String message) {
		err("Table schema mismatch for %s (recreate declined in non-interactive mode): %s", tableName, message);
		return false;
	}
}
