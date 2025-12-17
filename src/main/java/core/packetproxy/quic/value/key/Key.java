/*
 * Copyright 2022 DeNA Co., Ltd.
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

package packetproxy.quic.value.key;

import at.favre.lib.hkdf.HKDF;
import java.nio.ByteBuffer;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.codec.binary.Hex;

@NonFinal
@Value
@AllArgsConstructor
public class Key {

	protected static final byte[] STATIC_SALT_V1 = new byte[]{(byte) 0x38, (byte) 0x76, (byte) 0x2c, (byte) 0xf7,
			(byte) 0xf5, (byte) 0x59, (byte) 0x34, (byte) 0xb3, (byte) 0x4d, (byte) 0x17, (byte) 0x9a, (byte) 0xe6,
			(byte) 0xa4, (byte) 0xc8, (byte) 0x0c, (byte) 0xad, (byte) 0xcc, (byte) 0xbb, (byte) 0x7f, (byte) 0x0a};

	public static byte[] hkdfExpandLabel(byte[] secret, String labelStr, String contextStr, short length) {
		byte[] label = String.format("tls13 %s", labelStr).getBytes();
		byte[] context = contextStr.getBytes();
		HKDF hkdf = HKDF.fromHmacSha256();
		ByteBuffer hkdfLabel = ByteBuffer.allocate(2 + 1 + label.length + 1 + context.length);
		hkdfLabel.putShort(length);
		hkdfLabel.put((byte) label.length);
		hkdfLabel.put(label);
		hkdfLabel.put((byte) context.length);
		hkdfLabel.put(context);
		return hkdf.expand(secret, hkdfLabel.array(), length);
	}

	public static Key of(byte[] secret) {
		byte[] key = hkdfExpandLabel(secret, "quic key", "", (short) 16);
		byte[] iv = hkdfExpandLabel(secret, "quic iv", "", (short) 12);
		byte[] hp = hkdfExpandLabel(secret, "quic hp", "", (short) 16);
		return new Key(secret, key, iv, hp);
	}

	byte[] secret;
	byte[] key;
	byte[] iv;
	byte[] hp;

	public byte[] getMaskForHeaderProtection(byte[] sample) throws Exception {
		SecretKeySpec keySpec = new SecretKeySpec(this.hp, "AES");
		Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, keySpec);
		return cipher.doFinal(sample);
	}

	public byte[] aesGCM(int cipherMode, byte[] packetNumber, byte[] payload, byte[] associatedData) throws Exception {
		byte[] nonce = getNonce(packetNumber);
		SecretKeySpec keySpec = new SecretKeySpec(this.key, "AES");
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, nonce);
		cipher.init(cipherMode, keySpec, gcmParameterSpec);
		cipher.updateAAD(associatedData);
		return cipher.doFinal(payload);
	}

	public byte[] decryptPayload(byte[] packetNumber, byte[] encryptPayload, byte[] associatedData) throws Exception {
		return aesGCM(Cipher.DECRYPT_MODE, packetNumber, encryptPayload, associatedData);
	}

	public byte[] encryptPayload(byte[] packetNumber, byte[] payload, byte[] associatedData) throws Exception {
		return aesGCM(Cipher.ENCRYPT_MODE, packetNumber, payload, associatedData);
	}

	private byte[] getNonce(byte[] packetNumber) {
		byte[] nonce = new byte[12];
		for (int i = 0; i < 12; i++) {

			nonce[i] = this.iv[i];
			if (i >= nonce.length - packetNumber.length) {

				nonce[i] ^= packetNumber[i - (nonce.length - packetNumber.length)];
			}
		}
		return nonce;
	}

	@Override
	public String toString() {
		return String.format("Key(secret=%s, key=%s, iv=%s, hp=%s)", Hex.encodeHexString(this.secret),
				Hex.encodeHexString(this.key), Hex.encodeHexString(this.iv), Hex.encodeHexString(this.hp));
	}
}
