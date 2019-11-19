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

public class Http2
{
	/**
	 *  バイト列から1フレームを切り出す
	 */
	public static int parseFrameDelimiter(byte[] data) throws Exception {
		/* 未実装 */
		return data.length;
	}

	/** 
	 * ユーザが利用するユーティリティ関数。
	 * フレーム列(HEADER + DATA)をHTTPバイトデータに変換する
	 * 変換後のHTTPデータは、元のフレームに完全に戻すための情報をヘッダに付与すること（例：ストリーム番号）
	 */
	public static byte[] framesToHttp(byte[] frames) {
		return frames;
	}
	public static byte[] httpToFrames(byte[] http) {
		return http;
	}
	
	public Http2() throws Exception {
	}
	
	/**
	 * クライアントリクエストフレームをHTTP2モジュールに渡す。
	 * 本モジュールは、フレームを解析しストリームとして管理する。
	 */
	public void writeClientFrame(byte[] frame) throws Exception {
	}
	public void writeServerFrame(byte[] frame) throws Exception {
	}
	
	/**
	 * クライアントリクエストのHTTP以外のフレーム（例:SETTING）を読み出す。
	 * readClientFrameと分ける理由は、GUIに表示せず、直接サーバに送信したいため。
	 * 送信すべきフレームがなければ、nullを返す。
	 */
	public byte[] readClientControlFrames() throws Exception {
		return null;
	}
	public byte[] readServerControlFrames() throws Exception {
		return null;
	}

	/**
	 * クライアントリクエストのHTTPデータフレーム(HEADER+DATA)を読み出す。
	 * HTTPを構成するフレームがまだ溜まっていなかったら、nullを返す。
	 * ユーザは、framesToHttp()を使ってHttpに戻しGUIに表示し、httpToFramesにより元に戻してサーバに送信することになる。
	 */
	public byte[] readClientFrames() throws Exception {
		return null;
	}
	public byte[] readServerFrames() throws Exception {
		return null;
	}
}
