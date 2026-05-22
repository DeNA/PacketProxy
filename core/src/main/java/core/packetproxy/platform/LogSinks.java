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

/** {@link LogSink} の実行時登録先。 */
public final class LogSinks {

	private static volatile LogSink sink;

	private LogSinks() {
	}

	public static void set(LogSink logSink) {
		sink = logSink;
	}

	public static void clear() {
		sink = null;
	}

	public static void append(String message) {
		var current = sink;
		if (current != null) {
			current.append(message);
		}
	}

	public static void appendErr(String message) {
		var current = sink;
		if (current != null) {
			current.appendErr(message);
		}
	}
}
