/*
 * Copyright 2025 DeNA Co., Ltd.
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
package packetproxy.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import packetproxy.gui.GUILog;

public class Logging {

	private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
	private static final GUILog guiLog = GUILog.getInstance();

	private Logging() {
	}

	public static void log(String format, Object... args) {
		LocalDateTime now = LocalDateTime.now();
		String ns = dtf.format(now);
		String ss = ns + "       " + String.format(format, args);
		System.out.println(ss);
		guiLog.append(ss);
	}

	public static void err(String format, Object... args) {
		LocalDateTime now = LocalDateTime.now();
		String ns = dtf.format(now);
		String ss = ns + "       " + String.format(format, args);
		System.err.println(ss);
		guiLog.appendErr(ss);
	}

	public static void errWithStackTrace(Throwable e) {
		err(e.getMessage());
		StackTraceElement[] stackTrace = e.getStackTrace();
		for (var element : stackTrace) {
			err(element.toString());
		}
	}
}
