package packetproxy.model;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.nio.charset.Charset;
import packetproxy.util.CharSetUtility;

public class RegexParam {
	private int packetId;
	private String name;
	private Pattern regex;
	private String value;

	public RegexParam(int packetId, String name, String regex) {
		this.packetId = packetId;
		this.name = name;
		this.regex = Pattern.compile(regex);
		this.value = "";
	}

	public int getPacketId() {
		return this.packetId;
	}

	public String getName() {
		return this.name;
	}

	public String getRegex() {
		return this.regex.pattern();
	}

	public String getValue() {
		return this.value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public void setValue(OneShotPacket oneshot) {
		byte[] dataByte = oneshot.getData();
		CharSetUtility charSetUtility = CharSetUtility.getInstance();
		String encoding = charSetUtility.guessCharSetFromHttpHeader(dataByte);
		if (encoding == "") {
			encoding = charSetUtility.guessCharSetFromMetatag(dataByte);
		}
		if (encoding == "") {
			encoding = "utf-8";
		}

		String data = new String(dataByte, Charset.forName(encoding));
		Matcher matcher = this.regex.matcher(data);
		if (matcher.find()) {
			this.value = matcher.group(1);
		}
	}

	public OneShotPacket applyToPacket(OneShotPacket oneshot) throws Exception {
		byte[] data = oneshot.getData();
		CharSetUtility charSetUtility = CharSetUtility.getInstance();
		String encoding = charSetUtility.guessCharSetFromHttpHeader(data);
		if (encoding == "") {
			charSetUtility.guessCharSetFromMetatag(data);
		}
		if (encoding == "") {
			encoding = "utf-8";
		}

		String dataStr = new String(data, Charset.forName(encoding));
		Pattern pat = Pattern.compile("\\$\\{" + this.name + "\\}\\$");
		Matcher matcher = pat.matcher(dataStr);
		if (matcher.find()) {
			dataStr = matcher.replaceAll(this.value);
		}

		data = dataStr.getBytes(encoding);
		oneshot.setData(data);

		return oneshot;
	}
}
