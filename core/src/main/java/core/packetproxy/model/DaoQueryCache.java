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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

// Dao読み込みのキャッシュを行う
// 書き込み時はclearを実行する事
// 並列実行時に壊れる可能性あり
public class DaoQueryCache<T> {

	private HashMap<String, HashMap<Integer, List<T>>> query_cache;

	public DaoQueryCache() {
		clear();
	}

	public void clear() {
		query_cache = new HashMap();
	}

	public List<T> query(String type, Object query) {
		if (!query_cache.containsKey(type)) {

			return null;
		}
		return query_cache.get(type).get(query.hashCode());
	}

	public void set(String type, Object query, T result) {
		ArrayList<T> results = new ArrayList<T>(Arrays.asList(result));
		set(type, query, results);
	}

	public void set(String type, Object query, List<T> results) {
		if (!query_cache.containsKey(type)) {

			query_cache.put(type, new HashMap());
		}
		query_cache.get(type).put(query.hashCode(), results);
	}
}
