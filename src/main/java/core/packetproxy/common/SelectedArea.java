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

public class SelectedArea
{
	private int position_start;
	private int position_end;
	
	public SelectedArea(int position_start, int position_end) {
		this.position_start = position_start;
		this.position_end   = position_end;
	}
	
	public int getPositionStart() {
		return position_start;
	}

	public int getPositionEnd() {
		return position_end;
	}
	
	public int getLength() {
		return position_end - position_start;
	}
}
