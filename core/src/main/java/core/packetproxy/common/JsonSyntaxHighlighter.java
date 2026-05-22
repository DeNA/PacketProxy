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
package packetproxy.common;

import java.awt.Color;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class JsonSyntaxHighlighter {

	private StyledDocument document;

	// JSON syntax highlighting styles
	private Style keyStyle;
	private Style stringStyle;
	private Style numberStyle;
	private Style booleanStyle;
	private Style nullStyle;
	private Style punctuationStyle;
	private Style defaultStyle;

	private enum TokenType {
		KEY, STRING_VALUE, NUMBER, BOOLEAN_NULL, PUNCTUATION, WHITESPACE, UNKNOWN
	}

	private enum ParseState {
		NORMAL, IN_STRING, WAITING_FOR_VALUE, IN_NUMBER, IN_LITERAL, ESCAPE, ESCAPE_UNICODE
	}

	private static class HttpBodyInfo {
		String body;
		int bodyStartOffset;

		HttpBodyInfo(String body, int bodyStartOffset) {
			this.body = body;
			this.bodyStartOffset = bodyStartOffset;
		}
	}

	public JsonSyntaxHighlighter(StyledDocument document) {
		this.document = document;
		initializeStyles();
	}

	private void initializeStyles() {
		// 元のテキストフォーマットを保持するため、フォント属性は設定しない
		defaultStyle = document.addStyle("default", null);

		keyStyle = document.addStyle("key", null);
		StyleConstants.setForeground(keyStyle, new Color(0x92, 0x00, 0x6B));
		StyleConstants.setBold(keyStyle, true);

		// 文字列の色を濃い緑に設定（視認性向上のため）
		stringStyle = document.addStyle("string", null);
		StyleConstants.setForeground(stringStyle, new Color(0x00, 0x64, 0x00));

		numberStyle = document.addStyle("number", null);
		StyleConstants.setForeground(numberStyle, new Color(0x00, 0x00, 0xFF));

		booleanStyle = document.addStyle("boolean", null);
		StyleConstants.setForeground(booleanStyle, new Color(0x00, 0x00, 0xFF));

		nullStyle = document.addStyle("null", null);
		StyleConstants.setForeground(nullStyle, new Color(0x80, 0x80, 0x80));
		StyleConstants.setBold(nullStyle, true);

		punctuationStyle = document.addStyle("punctuation", null);
		StyleConstants.setForeground(punctuationStyle, new Color(0x00, 0x00, 0x00));
		StyleConstants.setBold(punctuationStyle, true);
	}

	public void applyHighlightingIfJson() {
		try {
			String text = document.getText(0, document.getLength());

			// HTTPボディまたはSSEフォーマット検出してハイライト適用
			HttpBodyInfo bodyInfo = extractHttpBody(text);
			if (bodyInfo != null && (isValidJson(bodyInfo.body) || isServerSentEvents(bodyInfo.body))) {
				if (isServerSentEvents(bodyInfo.body)) {
					applyServerSentEventsHighlighting(text, bodyInfo);
				} else {
					applyJsonSyntaxHighlightingToHttpBody(text, bodyInfo);
				}
			} else if (isValidJson(text)) {
				applyJsonSyntaxHighlighting(text);
			} else if (isServerSentEvents(text)) {
				applyServerSentEventsHighlighting(text, new HttpBodyInfo(text, 0));
			}

		} catch (BadLocationException e) {
		}
	}

	public void applyJsonSyntaxHighlighting() {
		try {
			String text = document.getText(0, document.getLength());
			applyJsonSyntaxHighlighting(text);
		} catch (BadLocationException e) {
			// Silently ignore highlighting errors
		}
	}

	private void applyJsonSyntaxHighlighting(String text) throws BadLocationException {
		highlightWithJsonParser(document, text);
	}

	private boolean isValidJson(String text) {
		text = text.trim();
		return (text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"));
	}

	private HttpBodyInfo extractHttpBody(String text) {
		String[] delimiters = {"\r\n\r\n", "\n\n", "\r\r"};

		for (String delimiter : delimiters) {
			int delimiterIndex = text.indexOf(delimiter);
			if (delimiterIndex >= 0) {
				int bodyStartOffset = delimiterIndex + delimiter.length();
				if (bodyStartOffset < text.length()) {
					String body = text.substring(bodyStartOffset);
					return new HttpBodyInfo(body, bodyStartOffset);
				}
			}
		}

		return null;
	}

	private void applyJsonSyntaxHighlightingToHttpBody(String fullText, HttpBodyInfo bodyInfo)
			throws BadLocationException {
		highlightWithJsonParserAtOffset(document, bodyInfo.body, bodyInfo.bodyStartOffset);
	}

	private void highlightWithJsonParser(StyledDocument doc, String text) throws BadLocationException {
		highlightWithJsonParserAtOffset(doc, text, 0);
	}

	private void highlightWithJsonParserAtOffset(StyledDocument doc, String text, int offset)
			throws BadLocationException {
		if (text.isEmpty())
			return;

		ParseState state = ParseState.NORMAL;
		TokenType currentTokenType = TokenType.UNKNOWN;
		int tokenStart = 0;
		boolean expectingKey = false;
		int braceDepth = 0;
		int unicodeEscapeRemaining = 0;

		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			char nextChar = (i + 1 < text.length()) ? text.charAt(i + 1) : '\0';

			switch (state) {
				case NORMAL :
					if (Character.isWhitespace(c)) {
						// Skip whitespace
						continue;
					} else if (c == '{') {
						applyStyle(doc, offset + i, 1, punctuationStyle);
						braceDepth++;
						expectingKey = true;
					} else if (c == '}') {
						applyStyle(doc, offset + i, 1, punctuationStyle);
						braceDepth--;
						expectingKey = false;
					} else if (c == '[' || c == ']') {
						applyStyle(doc, offset + i, 1, punctuationStyle);
						expectingKey = false;
					} else if (c == ',') {
						applyStyle(doc, offset + i, 1, punctuationStyle);
						expectingKey = braceDepth > 0;
					} else if (c == ':') {
						applyStyle(doc, offset + i, 1, punctuationStyle);
						expectingKey = false;
						state = ParseState.WAITING_FOR_VALUE;
					} else if (c == '"') {
						tokenStart = i;
						currentTokenType = expectingKey ? TokenType.KEY : TokenType.STRING_VALUE;
						state = ParseState.IN_STRING;
					} else if (Character.isDigit(c) || c == '-') {
						tokenStart = i;
						currentTokenType = TokenType.NUMBER;
						state = ParseState.IN_NUMBER;
					} else if (isLiteralStart(c)) {
						tokenStart = i;
						currentTokenType = TokenType.BOOLEAN_NULL;
						state = ParseState.IN_LITERAL;
					}
					break;

				case IN_STRING :
					if (c == '\\') {
						state = ParseState.ESCAPE;
					} else if (c == '"') {
						// End of string
						int length = i - tokenStart + 1;
						Style style = (currentTokenType == TokenType.KEY) ? keyStyle : stringStyle;
						applyStyle(doc, offset + tokenStart, length, style);
						state = ParseState.NORMAL;
						expectingKey = false;
					}
					break;

				case ESCAPE :
					// Handle escape sequences
					if (c == 'u') {
						// Unicode escape sequence \\uXXXX
						state = ParseState.ESCAPE_UNICODE;
						unicodeEscapeRemaining = 4;
					} else if (isValidEscapeChar(c)) {
						// Valid escape sequence (\", \\, \/, \b, \f, \n, \r, \t)
						state = ParseState.IN_STRING;
					} else {
						// Invalid escape sequence - treat as regular character
						state = ParseState.IN_STRING;
					}
					break;

				case ESCAPE_UNICODE :
					// Handle Unicode escape sequence \\uXXXX
					if (isHexDigit(c)) {
						unicodeEscapeRemaining--;
						if (unicodeEscapeRemaining <= 0) {
							state = ParseState.IN_STRING;
						}
					} else {
						// Invalid Unicode escape - go back to string parsing
						state = ParseState.IN_STRING;
					}
					break;

				case WAITING_FOR_VALUE :
					if (!Character.isWhitespace(c)) {
						i--; // Reprocess this character in NORMAL state
						state = ParseState.NORMAL;
					}
					break;

				case IN_NUMBER :
					if (!Character.isDigit(c) && c != '.' && c != 'e' && c != 'E' && c != '+' && c != '-') {
						// End of number
						int length = i - tokenStart;
						applyStyle(doc, offset + tokenStart, length, numberStyle);
						state = ParseState.NORMAL;
						i--; // Reprocess this character
					}
					break;

				case IN_LITERAL :
					if (!Character.isLetter(c)) {
						// End of literal (true/false/null)
						int length = i - tokenStart;
						String literal = text.substring(tokenStart, i);
						if ("true".equals(literal) || "false".equals(literal)) {
							applyStyle(doc, offset + tokenStart, length, booleanStyle);
						} else if ("null".equals(literal)) {
							applyStyle(doc, offset + tokenStart, length, nullStyle);
						}
						state = ParseState.NORMAL;
						i--; // Reprocess this character
					}
					break;
			}
		}

		// Handle end of text
		if (state == ParseState.IN_NUMBER) {
			int length = text.length() - tokenStart;
			applyStyle(doc, offset + tokenStart, length, numberStyle);
		} else if (state == ParseState.IN_LITERAL) {
			int length = text.length() - tokenStart;
			String literal = text.substring(tokenStart);
			if ("true".equals(literal) || "false".equals(literal)) {
				applyStyle(doc, offset + tokenStart, length, booleanStyle);
			} else if ("null".equals(literal)) {
				applyStyle(doc, offset + tokenStart, length, nullStyle);
			}
		}
	}

	private boolean isLiteralStart(char c) {
		return c == 't' || c == 'f' || c == 'n';
	}

	private boolean isValidEscapeChar(char c) {
		// Valid JSON escape characters: " \ / b f n r t
		return c == '"' || c == '\\' || c == '/' || c == 'b' || c == 'f' || c == 'n' || c == 'r' || c == 't';
	}

	private boolean isHexDigit(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	private void applyStyle(StyledDocument doc, int start, int length, Style style) {
		if (start >= 0 && length > 0 && start + length <= doc.getLength()) {
			doc.setCharacterAttributes(start, length, style, false);
		}
	}

	private boolean isServerSentEvents(String text) {
		return text.contains("data:") && text.matches("(?s).*data:\\s*\\{.*\\}.*");
	}

	private void applyServerSentEventsHighlighting(String fullText, HttpBodyInfo bodyInfo) throws BadLocationException {
		String[] lines = bodyInfo.body.split("\n");
		int currentOffset = bodyInfo.bodyStartOffset;

		for (String line : lines) {
			String trimmedLine = line.trim();
			if (trimmedLine.startsWith("data:")) {
				String dataValue = trimmedLine.substring(5).trim();
				if (isValidJson(dataValue)) {
					int dataStart = fullText.indexOf(dataValue, currentOffset);
					if (dataStart >= 0) {
						highlightWithJsonParserAtOffset(document, dataValue, dataStart);
					}
				}
			}
			currentOffset += line.length() + 1;
		}
	}
}
