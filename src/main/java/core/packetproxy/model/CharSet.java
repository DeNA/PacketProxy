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

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "charsets")
public class CharSet {

	@DatabaseField(generatedId = true)
	private int id;
	@DatabaseField(uniqueCombo = true)
	private String charsetname;

	public CharSet() {
		// ORMLite needs a no-arg constructor
	}

	public CharSet(String charsetname) {
		initialize(charsetname);
	}

	private void initialize(String charsetname) {
		this.charsetname = charsetname;
	}

	@Override
	public String toString() {
		return this.charsetname;
	}

	public String getCharSetName() {
		return charsetname;
	}

	public void setCharsetName(String charsetname) {
		this.charsetname = charsetname;
	}
}
