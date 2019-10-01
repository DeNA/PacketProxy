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
package packetproxy.common;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.RowFilter;
import javax.swing.RowFilter.ComparisonType;
import javax.swing.table.DefaultTableModel;

import org.apache.commons.collections4.map.HashedMap;

import packetproxy.gui.GUIHistory;
import packetproxy.model.Packet;
import packetproxy.model.Packets;

/**
 * Filterのパーサー
 * 文法は以下の通り
 * <Expr>        ::= <OrExpr>
 * <OrExpr>      ::= <AndExpr> | <AndExpr> '||' <OrExpr>
 * <AndEexpr>    ::= <PrimaryExpr> | <PrimaryExpr> '&&' <AndExpr>
 * <PrimaryExpr> ::= <Lhs> <Operator> <Rhs> | '(' <Expr> ')'
 * <Lhs>         ::= HashedMapのkeys
 * <Rhs>         ::= [^&|()]+
 * <Operator>    ::= '=~' | '==' | '>=' | '<=' | '!~' | '!='
 */
public class FilterTextParser {
	String str;
	int index;
	private static HashedMap<String, Integer> columnMapper = new HashedMap<String, Integer>() {
		{ put("id", 0); }
		{ put("request", 1); }
		{ put("response", 2); }
		{ put("length", 3); }
		{ put("client_ip", 4); }
		{ put("client_port", 5); }
		{ put("server_ip", 6); }
		{ put("server_port", 7); }
		{ put("time", 8); }
		{ put("resend", 9); }
		{ put("modified", 10); }
		{ put("type", 11); }
		{ put("encode", 12); }
		{ put("group", 13); }
		{ put("full_text", 14); }
		{ put("full_text_i", 15); }
	};

	FilterTextParser(String str) {
		this.str = str;
		index = 0;
	}
	public static RowFilter<Object, Object> parse(String str) throws ParseException, Exception {
		return new FilterTextParser(str).expr();
	}
	private RowFilter<Object, Object> expr() throws Exception {
		return orExpr();
	}
	private RowFilter<Object, Object> orExpr() throws Exception {
		ArrayList<RowFilter<Object, Object>> filters = new ArrayList<RowFilter<Object, Object>>();
		filters.add(andExpr());
		while (getNextChar() == '|') {
			index++;
			if (getNextChar() != '|') { throw new ParseException("| token is missing", index); }
			index++;
			filters.add(andExpr());
		}
		if (filters.size() == 1) { return filters.get(0); }
		return RowFilter.orFilter(filters);
	}
	private RowFilter<Object, Object> andExpr() throws Exception {
		ArrayList<RowFilter<Object, Object>> filters = new ArrayList<RowFilter<Object, Object>>();
		filters.add(primaryExpr());
		while (getNextChar() == '&') {
			index++;
			if (getNextChar() != '&') { throw new ParseException("& token is missing", index); }
			index++;
			filters.add(primaryExpr());
		}
		if (filters.size() == 1) { return filters.get(0); }
		return RowFilter.andFilter(filters);
	}
	private RowFilter<Object, Object> primaryExpr() throws Exception {
		if (getNextChar() == '(') {
			index++;
			RowFilter<Object, Object> ret = expr();
			if (getNextChar() != ')') { throw new ParseException(") token is missing", index); }
			index++;
			return ret;
		}

		String lhs = "";
		while (true) {
			char c = getNextChar();
			if (!Character.isAlphabetic(c) && c != '_') { break; }
			lhs += c;
			index++;
		}
		if (!columnMapper.containsKey(lhs)) { throw new java.text.ParseException("column name in invalid: " + lhs, index); }
		int column = (int)columnMapper.get(lhs);

		String operator = "" + getNextChar();
		index++;
		if (index >= str.length()) { throw new ParseException("unexpected end", index); }
		char c = str.charAt(index);
		if (c =='=' || c == '~') {
			operator += c;
			index++;
		}

		String rhs = "";
		if (index >= str.length()) { throw new ParseException("unexpected end",  index); }
		while (index < str.length()) {
			c = str.charAt(index);
			if (c == '(' || c == ')' || c == '|' || c =='&') { break; }
			rhs += c;
			index++;
		}
		rhs = rhs.trim();

		RowFilter<Object, Object> filter = null;
		if (operator.equals("!~") || operator.equals("!=")) {
			filter = RowFilter.notFilter(generateRequestRowFilter(rhs, column));
		} else if (operator.equals("=~") || operator.equals("==")) {

			if (column == columnMapper.get("full_text_i")) {
				filter = generateFullTextRowFilter_i(rhs);
			}else if (column == columnMapper.get("full_text")) {
				filter = generateFullTextRowFilter(rhs);
			} else {
				filter = generateRequestRowFilter(rhs, column);
			}
		} else if (operator.equals("<=")) {
			filter = RowFilter.numberFilter(ComparisonType.BEFORE, Integer.parseInt(rhs), column);
		} else if (operator.equals(">=")) {
			filter = RowFilter.numberFilter(ComparisonType.AFTER, Integer.parseInt(rhs), column);
		} else {
			throw new ParseException("operator is invalid: " + operator, index);
		}
		return filter;
	}
	private char getNextChar() throws Exception {
		char c = '\0';
		while (index < str.length()) {
			c = str.charAt(index);
			if (!Character.isWhitespace(c)) { break; }
			index++;
		}
		return c;
	}

	private static RowFilter<Object,Object> generateRequestRowFilter(String searchWord, int column) throws Exception {
		return new RequestRowFilter(searchWord, new int[] { column });
	}
	private static class RequestRowFilter extends MyGeneralFilter {
		Set<Long> groupIds;
		String searchWord;
		int already_analyzed_row_num = 0;
		public RequestRowFilter(String searchWord, int[] columns) throws Exception {
			super(columns);
			this.searchWord = searchWord;
			this.groupIds = new HashSet<Long>();
		}
		@Override
		protected boolean include(javax.swing.RowFilter.Entry<? extends Object, ? extends Object> value, int index) {
			if (!ValidPattern(this.searchWord)) { return false; }
			Object v = value.getValue(columnMapper.get("group"));
			if (v instanceof Long) {
				// 検索に必要なGroupIdを得る
				try {
					DefaultTableModel table = GUIHistory.getInstance().getTableModel();
					if (already_analyzed_row_num < table.getRowCount()) {
						for (int i = already_analyzed_row_num; i < table.getRowCount(); i++) {
							String data = (String) table.getValueAt(i, index);
							if (data != null && data.matches(String.format(".*%s.*", this.searchWord))) {
								this.groupIds.add((Long)table.getValueAt(i, columnMapper.get("group")));
							}
						}
						already_analyzed_row_num = table.getRowCount();
					} else {
						String data = (String) value.getValue(index);
						if (data != null && data.matches(String.format(".*%s.*", this.searchWord))) {
							this.groupIds.add((Long)value.getValue(columnMapper.get("group")));
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
				return groupIds.stream().anyMatch(g -> g.equals(v));
			}
			return false;
		}
	}

	//case sensitive full text search
	private static RowFilter<Object,Object> generateFullTextRowFilter(String searchWord) throws Exception {
		FullTextRowFilter fullTextRowFilter = new FullTextRowFilter(searchWord, new int[] { columnMapper.get("group") });
		List<Packet> packets = Packets.getInstance().queryFullText(searchWord);
		packets.stream().forEach(p -> fullTextRowFilter.groupIds.add(p.getGroup()));
		return fullTextRowFilter;
	}

	//case insensitive full text search
	private static RowFilter<Object,Object> generateFullTextRowFilter_i(String searchWord) throws Exception {
		FullTextRowFilter fullTextRowFilter = new FullTextRowFilter(searchWord, new int[] { columnMapper.get("group") });
		List<Packet> packets = Packets.getInstance().queryFullText_i(searchWord);
		packets.stream().forEach(p -> fullTextRowFilter.groupIds.add(p.getGroup()));
		return fullTextRowFilter;
	}
	private static class FullTextRowFilter extends MyGeneralFilter {
		Set<Long> groupIds;
		String searchWord;
		long already_analyzed_row_num = 0;
		public FullTextRowFilter(String searchWord, int[] columns) throws Exception {
			super(columns);
			this.searchWord = searchWord;
			this.groupIds = new TreeSet<Long>();
		}
		@Override
		protected boolean include(javax.swing.RowFilter.Entry<? extends Object, ? extends Object> value, int index) {
			if (!ValidPattern(this.searchWord)) { return false; }
			Object v = value.getValue(index);
			if (v instanceof Long) {
				return groupIds.contains(v);
			}
			return false;
		}
	}

	private static abstract class MyGeneralFilter extends RowFilter<Object,Object> {
		private int[] columns;
		MyGeneralFilter(int[] columns) {
			this.columns = columns;
		}
		public boolean include(Entry<? extends Object,? extends Object> value){
			int count = value.getValueCount();
			if (columns.length > 0) {
				for (int i = columns.length - 1; i >= 0; i--) {
					int index = columns[i];
					if (index < count) {
						if (include(value, index)) {
							return true;
						}
					}
				}
			}
			else {
				while (--count >= 0) {
					if (include(value, count)) {
						return true;
					}
				}
			}
			return false;
		}
		protected abstract boolean include(Entry<? extends Object,? extends Object> value, int index);
	}

	private static boolean ValidPattern(String searchWord) {
		try {
			Pattern.compile(searchWord);
		} catch (PatternSyntaxException exception) {
			return false;
		}
		return true;
	}
}
