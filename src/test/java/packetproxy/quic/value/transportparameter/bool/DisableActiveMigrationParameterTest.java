package packetproxy.quic.value.transportparameter.bool;

import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Test;

class DisableActiveMigrationParameterTest {

	@Test
	public void smoke() {
		DisableActiveMigrationParameter param = new DisableActiveMigrationParameter();
		System.out.println(Hex.encodeHexString(param.getBytes()));
	}

}
