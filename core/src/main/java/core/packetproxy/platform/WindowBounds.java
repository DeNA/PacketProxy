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
package packetproxy.platform;

/**
 * Main window position and size without depending on
 * {@code java.awt.Rectangle}.
 */
public record WindowBounds(int x, int y, int width, int height) {

	public int centeredDialogX(int dialogWidth) {
		return x + width / 2 - dialogWidth / 2;
	}

	public int centeredDialogY(int dialogHeight) {
		return y + height / 2 - dialogHeight / 2;
	}
}
