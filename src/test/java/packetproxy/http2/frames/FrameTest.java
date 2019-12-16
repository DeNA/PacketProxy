package packetproxy.http2.frames;

import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

public class FrameTest {

	@Test
	public void extraTest() throws Exception {
		byte[] frameData = Hex.decodeHex("00000e0148000000010000000541414141414242424242".toCharArray());
		Frame frame = new Frame(frameData);
		byte[] bbbbb = frame.getExtra();
		System.out.println(new String(bbbbb));
	}

	@Test
	public void removeExtraTest() throws Exception {
		byte[] frameData = Hex.decodeHex("00000e0148000000010000000541414141414242424242".toCharArray());
		Frame frame = new Frame(frameData);
		frame.removeExtra();
		System.out.println(new String(Hex.encodeHex(frame.toByteArray())));
	}

	@Test
	public void saveExtraTest() throws Exception {
		byte[] frameData = Hex.decodeHex("00000e0148000000010000000541414141414242424242".toCharArray());
		Frame frame = new Frame(frameData);
		frame.removeExtra();
		frame.saveExtra("0123456789".getBytes());
		System.out.println(new String(Hex.encodeHex(frame.toByteArray())));
	}

	@Test
	public void toByteArrayWithExtraDataTest() throws Exception {
		byte[] frameData = Hex.decodeHex("00000e0148000000010000000541414141414242424242".toCharArray());
		Frame frame = new Frame(frameData);
		frame.removeExtra();
		frame.saveExtra("0123456789".getBytes());
		System.out.println(new String(Hex.encodeHex(frame.toByteArrayWithoutExtra())));
	}

}
