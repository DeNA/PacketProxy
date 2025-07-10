/*
 * Copyright 2022 DeNA Co., Ltd.
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

package packetproxy.http3.helper;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import packetproxy.http.Http;

public class Http3TestHelper {

	public static MetaData generateTestMetaData() throws Exception {
		String httpStr = "POST / HTTP/3\nhost: example.com\nhoge: fuga\ncontent-type: application/json\n\n{\"name\":\"taro\",\"age\":20}";

		Http http = Http.create(httpStr.getBytes());
		HttpFields.Mutable jettyHttpFields = HttpFields.build();
		http.getHeader().getFields().forEach(field -> {

			jettyHttpFields.add(field.getName(), field.getValue());
		});
		return new MetaData.Request(http.getMethod(), HttpURI.from(http.getURL(80, false)), HttpVersion.HTTP_3,
				jettyHttpFields);
	}

}
