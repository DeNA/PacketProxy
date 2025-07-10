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
package packetproxy.http;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QueryString {

	private List<QueryParameter> params;

	public QueryString(String query) {
		params = Arrays.asList(query.split("&")).stream().map(QueryParameter::new).collect(Collectors.toList());
	}

	public String toString() {
		return params.stream().map(QueryParameter::toString).collect(Collectors.joining("&"));
	}

	public Optional<QueryParameter> getParam(String name) {
		return params.stream().filter(p -> p.getName().equals(name)).findFirst();
	}

	public Optional<String> getValue(String name) {
		Optional<QueryParameter> param = getParam(name);
		if (param.isPresent()) {

			return Optional.of(param.get().getValue());
		} else {

			return Optional.ofNullable(null);
		}
	}

	public Stream<QueryParameter> filter(Predicate<? super QueryParameter> predicate) {
		return params.stream().filter(predicate);
	}

	public <R> Stream<R> map(Function<? super QueryParameter, ? extends R> mapper) {
		return params.stream().map(mapper);
	}

	public void update(String name, String value) {
		params.stream().filter(p -> p.getName().equals(name)).findFirst().ifPresent(p -> p.setValue(value));
	}
}
