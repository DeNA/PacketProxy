package packetproxy.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import packetproxy.model.Database;
import packetproxy.util.CharSetUtility;

class RawTextPaneTest {

	private static final byte[] HTTP_REQUEST = "GET / HTTP/1.1\r\nHost: example\r\n\r\n"
			.getBytes(StandardCharsets.UTF_8);

	@BeforeAll
	static void setUpHeadless() throws Exception {
		System.setProperty("java.awt.headless", "true");
		var tempDb = Files.createTempFile("packetproxy-raw-text-pane-test", ".sqlite3");
		tempDb.toFile().deleteOnExit();
		Database.getInstance().openAt(tempDb.toString());
	}

	@BeforeEach
	void setUpCharset() {
		CharSetUtility.getInstance().setCharSet("UTF-8");
	}

	@Test
	void setData_clearsInitFlagAndReturnsOriginalBytes() throws Exception {
		var pane = new RawTextPane();
		SwingUtilities.invokeAndWait(() -> {
			try {

				pane.setData(HTTP_REQUEST, false);
			} catch (Exception e) {

				throw new RuntimeException(e);
			}
		});

		assertFalse(pane.init_flg);
		assertThat(pane.getData()).isEqualTo(HTTP_REQUEST);
	}

	@Test
	void fullDocumentReplace_returnsOriginalBytes() throws Exception {
		var pane = new RawTextPane();
		SwingUtilities.invokeAndWait(() -> {
			try {

				pane.setData(HTTP_REQUEST, false);
				var doc = pane.getDocument();
				var text = doc.getText(0, doc.getLength());
				doc.remove(0, doc.getLength());
				doc.insertString(0, text, null);
			} catch (Exception e) {

				throw new RuntimeException(e);
			}
		});

		assertThat(pane.getData()).isEqualTo(HTTP_REQUEST);
	}

	@Test
	void partialEdit_returnsModifiedBytes() throws Exception {
		var pane = new RawTextPane();
		SwingUtilities.invokeAndWait(() -> {
			try {

				pane.setData(HTTP_REQUEST, false);
				var doc = pane.getDocument();
				doc.remove(4, 1);
				doc.insertString(4, "X", null);
			} catch (Exception e) {

				throw new RuntimeException(e);
			}
		});

		assertThat(pane.getData()).isNotEqualTo(HTTP_REQUEST);
	}
}
