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

import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

public class CryptUtils {

	public static String md5(String target) throws Exception {
		return Hex.encodeHexString(md5(target.getBytes()));
	}

	public static byte[] md5(byte[] target) throws Exception {
		return MessageDigest.getInstance("MD5").digest(target);
	}

	public static String sha1(String target) throws Exception {
		return Hex.encodeHexString(sha1(target.getBytes()));
	}

	public static byte[] sha1(byte[] target) throws Exception {
		return MessageDigest.getInstance("SHA-1").digest(target);
	}

	public static String sha256(String target) throws Exception {
		return Hex.encodeHexString(sha256(target.getBytes()));
	}

	public static byte[] sha256(byte[] target) throws Exception {
		return MessageDigest.getInstance("SHA-256").digest(target);
	}

	/*TODO test methods */

	public static String encryptECBPKCS5(byte[] key, byte[] cipherText) throws Exception {
		return encryptECB("PKCS5", key, cipherText);
	}

	public static String encryptECBISO10126(byte[] key, byte[] cipherText) throws Exception {
		return encryptECB("ISO10126", key, cipherText);
	}

	public static String encryptCBCPKCS5(byte[] key, byte[] iv, byte[] cipherText) throws Exception {
		return encryptCBC("PKCS5", key, iv, cipherText);
	}

	public static String encryptCBCISO10126(byte[] key, byte[] iv, byte[] cipherText) throws Exception {
		return encryptCBC("ISO10126", key, iv, cipherText);
	}

	/*
	 * decrypt methods
	 * TODO test methods
	 */
	public static String decryptECBPKCS5(byte[] key, byte[] cipherText) throws Exception {
		return decryptECB("PKCS5", key, cipherText).toString();
	}

	public static String decryptECBISO10126(byte[] key, byte[] cipherText) throws Exception {
		return decryptECB("ISO10126", key, cipherText).toString();
	}

	public static String decryptCBCPKCS5(byte[] key, byte[] cipherText) throws Exception {
		return decryptCBC("PKCS5", key, cipherText);
	}

	public static String decryptCBCISO10126(byte[] key, byte[] cipherText) throws Exception {
		return decryptCBC("ISO10126", key, cipherText);
	}

	// encrypt detail
	/**
	 * このメソッドはAES ECBPKCS5の暗号化方式を再現するためのもので、引数は秘密鍵と元となる文章を渡せば暗号化されたhex文字列が出力されます
	 *
	 * @param key
	 *            secret key for encrypt AES ECB
	 * @param cipherText
	 *            origin text
	 * @return
	 * @throws Exception
	 */
	public static String encryptECB(byte[] key, byte[] cipherText) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(1, new SecretKeySpec(key, "AES"));
		return new String(Base64.encodeBase64(cipher.doFinal(cipherText)));
	}

	// flexible about padding
	/**
	 * このメソッドはAES
	 * ECBの暗号化方式を再現するためのもので、引数はpadding方式、秘密鍵と元となる文章を渡せば暗号化されたhex文字列が出力されます
	 *
	 * @param padding
	 * @param key
	 * @param cipherText
	 * @return
	 * @throws Exception
	 */
	public static String encryptECB(String padding, byte[] key, byte[] cipherText) throws Exception {
		String style = "AES/ECB/" + padding + "Padding";
		Cipher cipher = Cipher.getInstance(style);
		cipher.init(1, new SecretKeySpec(key, "AES"));
		return new String(Base64.encodeBase64(cipher.doFinal(cipherText)));
	}

	/*
	* //TODO delete
	public static String encryptCBCISO10126(byte [] key, byte [] iv, byte [] input) throws Exception {
	Cipher cipher = Cipher.getInstance("AES/CBC/ISO10126Padding");
	if( iv != null) {
	Logging.log("not null");
	}else{
	iv = cipher.getIV();
	}
	IvParameterSpec ivSpec = new IvParameterSpec(iv);
	Logging.log("ivSpec="+ivSpec);
	SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
	Logging.log("keySpec="+keySpec);
	cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
	byte[] encrypted = cipher.doFinal(input);
	byte[] result = new byte[iv.length+encrypted.length];
	System.arraycopy(iv, 0, result, 0, iv.length);
	Logging.log("iv="+toHex(iv));
	System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
	
	return toHex(result);
	}
	*/

	// flexible about padding
	public static String encryptCBC(String padding, byte[] key, byte[] iv, byte[] input) throws Exception {
		String style = "AES/CBC/" + padding + "Padding";
		Cipher cipher = Cipher.getInstance(style);
		if (iv != null) {

			// Logging.log("not null");
		} else {

			iv = cipher.getIV();
		}
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		// Logging.log("ivSpec="+ivSpec);
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
		// Logging.log("keySpec="+keySpec);
		cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
		byte[] encrypted = cipher.doFinal(input);
		byte[] result = new byte[iv.length + encrypted.length];
		System.arraycopy(iv, 0, result, 0, iv.length);
		// Logging.log("iv="+toHex(iv));
		System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);

		return toHex(result);
	}

	/* Decrypt
	 * */
	public static byte[] decrypt(byte[] key, byte[] originalText) throws Exception {
		Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
		cipher.init(2, new SecretKeySpec(key, "AES"));
		return cipher.doFinal(originalText);
	}

	// flexible about padding
	public static byte[] decryptECB(String padding, byte[] key, byte[] originalText) throws Exception {
		String style = "AES/ECB/" + padding + "Padding";
		Cipher cipher = Cipher.getInstance(style);
		cipher.init(2, new SecretKeySpec(key, "AES"));
		return cipher.doFinal(originalText);
	}

	/*
	 * //TODO delete
	 public static String decryptCBCISO10126(byte [] key, byte [] input) throws Exception {
	 SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
	 Cipher cipher = Cipher.getInstance("AES/CBC/ISO10126Padding");
	 byte[] iv = new byte[16];
	 byte[] cipherByte = new byte[input.length-16];
	 System.arraycopy(input, 0, iv, 0, 16);
	//	savedIv = toHex(iv);
	System.arraycopy(input, 16, cipherByte, 0, input.length-16);
	IvParameterSpec ivSpec = new IvParameterSpec(iv);
	cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
	Logging.log(new String(input));
	return new String(cipher.doFinal(cipherByte),"UTF-8");
	
	 }
	 */

	// flexible about padding
	public static String decryptCBC(String padding, byte[] key, byte[] input) throws Exception {
		String style = "AES/CBC/" + padding + "Padding";
		Cipher cipher = Cipher.getInstance(style);
		SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
		byte[] iv = new byte[16];
		byte[] cipherByte = new byte[input.length - 16];
		System.arraycopy(input, 0, iv, 0, 16);
		// savedIv = toHex(iv);
		System.arraycopy(input, 16, cipherByte, 0, input.length - 16);
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
		// Logging.log(new String(input));
		return new String(cipher.doFinal(cipherByte), "UTF-8");
	}

	/*TODO delete
	public static String AES(String encrypt_decrypt, String mode, String padding, byte[] key, byte[] iv, byte[] cipherText) throws Exception {
	String result = "";
	if (encrypt_decrypt == "encrypt") {
	if (mode == "ECB") {
	if (padding.length() != 0) {
	result = encryptECB(padding,key,cipherText);
	}
	}
	
	if (mode == "CBC") {
	if (padding.length() != 0) {
	result = encryptCBC(padding,key,iv,cipherText);
	}
	}
	
	} else if (encrypt_decrypt == "decrypt") {
	if (mode == "ECB") {
	if (padding.length() != 0) {
	result = new String(decrypt(padding,key,cipherText));
	}
	}
	
	if (mode == "CBC") {
	if (padding.length() != 0) {
	result = decryptCBC(padding,key,cipherText);
	}
	}
	}
	
	return result;
	}
	
	
	*/

	/*TODO delete
	 * 	//encrypt logic
	 public static String encryptAES(String mode, String padding, byte[] key, byte[] iv, byte[] cipherText) throws Exception {
	 String result = "";
	 if (mode == "ECB") {
	 if (padding.length() != 0) {
	 result = encryptECB(padding,key,cipherText);
	 }
	 }
	
	 if (mode == "CBC") {
	 if (padding.length() != 0) {
	 result = encryptCBC(padding,key,iv,cipherText);
	 }
	 }
	 return result;
	 }
	
	*/

	/*
	 　　　　//TODO delete
	//decrypt logic
	public static String decryptAES(String mode, String padding, byte[] key, byte[] cipherText) throws Exception {
	String result = "";
	if (mode == "ECB") {
	if (padding.length() != 0) {
	result = new String(decrypt(padding,key,cipherText));
	}
	}
	
	if (mode == "CBC") {
	if (padding.length() != 0) {
	result = decryptCBC(padding,key,cipherText);
	}
	}
	return result;
	}
	
	*/

	// byte<=>hex
	public static byte[] toByte(String hex) {
		byte[] bytes = new byte[hex.length() / 2];
		for (int index = 0; index < bytes.length; index++) {

			bytes[index] = (byte) Integer.parseInt(hex.substring(index * 2, (index + 1) * 2), 16);
		}
		return bytes;
	}

	public static String toHex(byte bytes[]) {
		StringBuffer strbuf = new StringBuffer(bytes.length * 2);
		for (int index = 0; index < bytes.length; index++) {

			int bt = bytes[index] & 0xff;
			if (bt < 0x10) {

				strbuf.append("0");
			}
			strbuf.append(Integer.toHexString(bt));
		}
		return strbuf.toString();
	}
}
