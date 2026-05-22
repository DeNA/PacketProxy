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

/** UI や CLI が実装する、ユーザー確認ダイアログの抽象化。 */
public interface UserPrompt {

	/**
	 * スキーマ不一致時にテーブル再作成の可否をユーザーに尋ねる。
	 *
	 * @return 再作成してよい場合は true
	 */
	boolean confirmTableRecreate(String tableName, String message);
}
