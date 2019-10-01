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
package packetproxy.gui;

import javax.swing.text.Element;
import javax.swing.text.FlowView;
import javax.swing.text.ParagraphView;
import javax.swing.text.View;

// 高速化の為のクラス
// http://java-sl.com/JEditorPanePerformance.html
public class CustomParagraphView extends ParagraphView {

	final private static int MAX_VIEW_SIZE = 100;
	public CustomParagraphView(Element elem) {
		super(elem);
		strategy = new CustomFlowStrategy();
	}

	private static class CustomFlowStrategy extends FlowStrategy {
        protected View createView(FlowView fv, int startOffset, int spanLeft, int rowIndex) {
            View res=super.createView(fv, startOffset, spanLeft, rowIndex);
            if (res.getEndOffset() - res.getStartOffset() > MAX_VIEW_SIZE) {
                res = res.createFragment(startOffset, startOffset + MAX_VIEW_SIZE);
            }
            return res;
        }
	}

}
