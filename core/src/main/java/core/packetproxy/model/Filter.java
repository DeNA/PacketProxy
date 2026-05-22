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

@DatabaseTable(tableName = "filters")
public class Filter {

	@DatabaseField(generatedId = true)
	private int id;

	@DatabaseField(unique = true)
	private String name;

	@DatabaseField
	private String filter;

	public Filter() {
		// ORMLite needs a no-arg constructor
	}

	public Filter(String name, String filter) {
		this.name = name;
		this.filter = filter;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}

	public int getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getFilter() {
		return this.filter;
	}

	@Override
	public String toString() {
		return String.format("[ %s: %s ]", this.name, this.filter);
	}
}
