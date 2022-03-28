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
package packetproxy.encode;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

public class EncodeXMPP extends Encoder
{
	public EncodeXMPP(String ALPN) {
		super(ALPN);
	}

	@Override
	public String getName() {
		return "XMPP";
	}

	public boolean useNewConnectionForResend() {
		return false;
	}

	@Override
	public int checkDelimiter(byte[] input_data) throws Exception {
		return input_data.length;
	}

	@Override
	public byte[] decodeClientRequest(byte[] input_data) throws Exception {
		return xmlLint(input_data);
	}

	@Override
	public byte[] encodeClientRequest(byte[] input_data) throws Exception {
		return input_data;
	}

	@Override
	public byte[] decodeServerResponse(byte[] input_data) throws Exception {
		return xmlLint(input_data);
	}

	@Override
	public byte[] encodeServerResponse(byte[] input_data) throws Exception {
		return input_data;
	}

	private class IgnoreErrorMsgHandler implements ErrorHandler {
		@Override
		public void warning(SAXParseException ex) throws SAXException { }
		@Override
		public void error(SAXParseException ex) throws SAXException { throw ex; }
		@Override
		public void fatalError(SAXParseException ex) throws SAXException { throw ex; }
	}

	private byte[] xmlLint(byte[] data) {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setValidating(false);
			DocumentBuilder db = dbf.newDocumentBuilder();
			db.setErrorHandler(new IgnoreErrorMsgHandler());
			InputSource is = new InputSource(new StringReader(new String(data)));
			Document doc = db.parse(is);
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
			StreamResult result = new StreamResult(new StringWriter());
			DOMSource source = new DOMSource(doc);
			transformer.transform(source, result);
			return result.getWriter().toString().getBytes(StandardCharsets.UTF_8);
		} catch (Exception e) {
			return data;
		}
	}
}
