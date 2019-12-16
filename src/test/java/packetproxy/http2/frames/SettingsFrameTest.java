package packetproxy.http2.frames;

import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

public class SettingsFrameTest {

	@Test
	public void smoke() throws Exception {
		byte[] data = Hex.decodeHex("000012040000000000000300000064000400100000000600004000".toCharArray());
		SettingsFrame sf = new SettingsFrame(data);
		System.out.println(sf);
	}

}
