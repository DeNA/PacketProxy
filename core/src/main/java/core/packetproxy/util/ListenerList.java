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
package packetproxy.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Type-safe listener list without depending on
 * {@code javax.swing.event.EventListenerList}.
 */
public final class ListenerList {

	private final Map<Class<?>, List<Object>> listenersByType = new HashMap<>();

	public <T> void add(Class<T> listenerType, T listener) {
		listenersByType.computeIfAbsent(listenerType, key -> new ArrayList<>()).add(listener);
	}

	public <T> List<T> listeners(Class<T> listenerType) {
		var raw = listenersByType.get(listenerType);
		if (raw == null) {
			return List.of();
		}
		var result = new ArrayList<T>(raw.size());
		for (var listener : raw) {
			result.add(listenerType.cast(listener));
		}
		return result;
	}
}
