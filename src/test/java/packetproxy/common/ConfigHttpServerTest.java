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
package packetproxy.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fi.iki.elonen.NanoHTTPD.IHTTPSession;
import fi.iki.elonen.NanoHTTPD.Method;
import fi.iki.elonen.NanoHTTPD.Response;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

public class ConfigHttpServerTest {

	private static final String TOKEN = "secret-token";

	private ConfigHttpUiActions uiActions;
	private ConfigSettingsWriter settingsWriter;
	private ConfigHttpServer server;

	@BeforeEach
	public void setUp() {
		uiActions = mock(ConfigHttpUiActions.class);
		settingsWriter = mock(ConfigSettingsWriter.class);
		server = new ConfigHttpServer("127.0.0.1", 0, TOKEN, uiActions, settingsWriter);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"wrong-token"})
	public void serve_rejectsUnauthorizedToken(String authorization) throws Exception {
		var session = mockSession(Method.POST, "/config", authorization, null);

		var response = server.serve(session);

		assertEquals(Response.Status.UNAUTHORIZED, response.getStatus());
		verify(uiActions, never()).confirmOverwriteConfig();
		verify(settingsWriter, never()).applyFromJson(anyString());
	}

	@Test
	public void serve_postConfig_rejectsWhenConfirmDenied() throws Exception {
		when(uiActions.confirmOverwriteConfig()).thenReturn(false);
		var session = mockSession(Method.POST, "/config", TOKEN, "{\"servers\":[]}");

		var response = server.serve(session);

		assertEquals(Response.Status.UNAUTHORIZED, response.getStatus());
		verify(uiActions).confirmOverwriteConfig();
		verify(settingsWriter, never()).applyFromJson(anyString());
	}

	@Test
	public void serve_postConfig_appliesJsonWhenConfirmAccepted() throws Exception {
		when(uiActions.confirmOverwriteConfig()).thenReturn(true);
		var postData = "{\"servers\":[]}";
		var session = mockSession(Method.POST, "/config", TOKEN, postData);

		var response = server.serve(session);

		assertEquals(Response.Status.OK, response.getStatus());
		assertEquals("application/json", response.getMimeType());
		assertEquals("{\"status\": \"ok\"}", readBody(response));
		verify(uiActions).confirmOverwriteConfig();
		verify(settingsWriter).applyFromJson(postData);
	}

	private static IHTTPSession mockSession(Method method, String uri, String authorization, String postData)
			throws Exception {
		var session = mock(IHTTPSession.class);
		when(session.getMethod()).thenReturn(method);
		when(session.getUri()).thenReturn(uri);
		Map<String, String> headers = new HashMap<>();
		if (authorization != null) {
			headers.put("authorization", authorization);
		}
		when(session.getHeaders()).thenReturn(Collections.unmodifiableMap(headers));
		if (postData != null) {
			doAnswer(invocation -> {
				Map<String, String> map = invocation.getArgument(0);
				map.put("postData", postData);
				return null;
			}).when(session).parseBody(any());
		}
		return session;
	}

	private static String readBody(Response response) throws Exception {
		InputStream data = response.getData();
		if (data == null) {
			return null;
		}
		return new String(data.readAllBytes(), StandardCharsets.UTF_8);
	}
}
