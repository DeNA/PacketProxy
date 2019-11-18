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
package packetproxy.http2;

import packetproxy.http.Http;

public class Http2
{
	public static int parseFrameDelimiter(byte[] data) throws Exception
	{
		/* 未実装 */
		return data.length;
	}

	public Http2() throws Exception
	{
	}
	
	/* クライアントからのリクエストフレームを1つ読み込む */
	public void writeClientFrame(byte[] frame) throws Exception {
		/* 未実装 */
	}

	/* サーバからのレスポンスフレームを1つ読み込む */
	public void writeServerFrame(byte[] frame) throws Exception {
		/* 未実装 */
	}
	
	/* クライアントリクエストのHTTP以外のデータ（例SETTINGフレーム）を返す */
	/* 理由は、GUIに表示せず、直接サーバに送りたいため */
	public byte[] readClientControlFrame() throws Exception {
		/* 未実装 */
		return null;
	}

	/* サーバレスポンスのHTTP以外のデータ（例SETTINGフレーム）を返す */
	/* 理由は、GUIに表示せず、直接クライアントに送りたいため */
	public byte[] readServerControlFrame() throws Exception {
		/* 未実装 */
		return null;
	}

	/* クライアントリクエストのHTTPデータを返す */
	/* HTTPデータには、元にフレームに戻せるだけの情報をヘッダに付与する必要がある */
	/* まだフレームが溜まっていなかったら、nullを返す */
	public Http readClientRequest() throws Exception {
		/* 未実装 */
		return new Http("".getBytes());
	}
	public void writeClientRequest(Http http) throws Exception {
		/* 未実装 */
	}

	/* サーバレスポンスのHTTPデータを返す */
	/* HTTPデータには、元にフレームに戻せるだけの情報をヘッダに付与する必要がある */
	/* まだフレームが溜まっていなかったら、nullを返す */
	public Http readServerResponse() throws Exception {
		/* 未実装 */
		return new Http("".getBytes());
	}
	public void writeServerResponse(Http http) throws Exception {
		/* 未実装 */
	}
}
