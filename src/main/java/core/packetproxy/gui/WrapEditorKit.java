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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.ComponentView;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.IconView;
import javax.swing.text.LabelView;
import javax.swing.text.Segment;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import org.apache.commons.lang3.ArrayUtils;

public class WrapEditorKit extends StyledEditorKit {

	private static final long serialVersionUID = 1L;
	private char[] savedBuf;
	private byte[] savedData;
	private ViewFactory defaultFactory = new WrapColumnFactory();

	public WrapEditorKit(byte[] data) {
		savedBuf = new char[]{};
		savedData = data;
	}

	public byte[] getData() {
		return savedData;
	}

	public ViewFactory getViewFactory() {
		return defaultFactory;
	}

	@Override
	public void read(Reader in, Document doc, int pos) throws IOException, BadLocationException {
		char[] buff = new char[4096];
		int nch;
		AttributeSet attr = getInputAttributes();
		savedBuf = new char[]{};
		while ((nch = in.read(buff, 0, buff.length)) != -1) {

			doc.insertString(pos, new String(buff, 0, nch), attr);
			savedBuf = ArrayUtils.addAll(savedBuf, ArrayUtils.subarray(buff, 0, nch));
			pos += nch;
		}
	}

	@Override
	public void write(Writer out, Document doc, int pos, int len) throws IOException, BadLocationException {
		if ((pos < 0) || ((pos + len) > doc.getLength())) {

			throw new BadLocationException("DefaultEditorKit.write", pos);
		}

		Segment data = new Segment();
		int nleft = len;
		int offs = pos;

		while (nleft > 0) {

			int n = Math.min(nleft, 4096);
			doc.getText(offs, n, data);
			out.write(data.array, data.offset, data.count);
			offs += n;
			nleft -= n;
		}
	}

	class WrapColumnFactory implements ViewFactory {

		public View create(Element elem) {
			String kind = elem.getName();
			if (kind != null) {

				if (kind.equals(AbstractDocument.ContentElementName)) {

					return new WrapLabelView(elem);
				} else if (kind.equals(AbstractDocument.ParagraphElementName)) {

					return new CustomParagraphView(elem);
				} else if (kind.equals(AbstractDocument.SectionElementName)) {

					return new BoxView(elem, View.Y_AXIS);
				} else if (kind.equals(StyleConstants.ComponentElementName)) {

					return new ComponentView(elem);
				} else if (kind.equals(StyleConstants.IconElementName)) {

					return new IconView(elem);
				}
			}
			return new LabelView(elem);
		}
	}

	class WrapLabelView extends LabelView {

		public WrapLabelView(Element elem) {
			super(elem);
		}

		public float getMinimumSpan(int axis) {
			switch (axis) {

				case View.X_AXIS :
					return 0;
				case View.Y_AXIS :
					return super.getMinimumSpan(axis);
				default :
					throw new IllegalArgumentException("Invalid axis: " + axis);
			}
		}
	}

}
