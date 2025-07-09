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
package packetproxy.model.CAs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import packetproxy.CertCacheManager;
import packetproxy.common.Utils;

public class PacketProxyCAPerUser extends CA {
	private static final String name = "PacketProxy per-user CA";
	private static final String desc = "PacketProxy per-user CA";
	private static final char[] password = "testtest".toCharArray();
	private static final String ksPath = Paths.get(System.getProperty("user.home") + "/.packetproxy/certs/user.ks")
			.toString();

	public PacketProxyCAPerUser() throws Exception {
		if (!new File(ksPath).exists()) {
			generateKeyStore(ksPath);
		}
		super.load(ksPath);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getUTF8Name() {
		return desc;
	}

	@Override
	public String toString() {
		return "PacketProxy per-user CA [name=" + name + ", desc=" + desc + "]";
	}

	@Override
	public void regenerateCA() throws Exception {
		new File(ksPath).delete();
		generateKeyStore(ksPath);
		super.load(ksPath);
		CertCacheManager.clearCache();
	}

	private void generateKeyStore(String ksPath) throws Exception {
		KeyStore ks;
		KeyPair CAKeyPair = super.genRSAKeyPair();

		// 各ユーザ用のキーストアを作るためのテンプレートを取得
		try (InputStream input = this.getClass().getResourceAsStream("/certificates/user.ks")) {
			ks = KeyStore.getInstance("JKS");
			ks.load(input, password);
		}

		int serialNumber = 0;
		do {
			serialNumber = SecureRandom.getInstance("SHA1PRNG").nextInt();
		} while (serialNumber <= 0);

		String x500Name = String.format(
				"C=PacketProxy, ST=PacketProxy, L=PacketProxy, O=PacketProxy, OU=PacketProxy CA, CN=PacketProxy per-user CA (%x)",
				serialNumber);
		Date from = new Date();
		Calendar cal = Calendar.getInstance();
		cal.setTime(from);
		cal.add(Calendar.YEAR, 30);
		Date to = cal.getTime();

		X509v3CertificateBuilder caRootBuilder = new X509v3CertificateBuilder(new X500Name(x500Name),
				BigInteger.valueOf(serialNumber), from, to, new X500Name(x500Name),
				SubjectPublicKeyInfo.getInstance(CAKeyPair.getPublic().getEncoded()));

		/* CA: X509 Extensionsの設定（CA:true, pathlen:0) */
		caRootBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));

		AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
		AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
		ContentSigner signer = new BcRSAContentSignerBuilder(sigAlgId, digAlgId)
				.build(PrivateKeyFactory.createKey(CAKeyPair.getPrivate().getEncoded()));
		X509CertificateHolder signedRoot = caRootBuilder.build(signer);

		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		registerCertificateAndPrivateKeyToKeyStore(
				certFactory.generateCertificate(new ByteArrayInputStream(signedRoot.getEncoded())),
				CAKeyPair.getPrivate());
	}

	private void registerCertificateAndPrivateKeyToKeyStore(Certificate certificate, PrivateKey privateKey)
			throws Exception {
		// 新しいKeyStoreの生成
		KeyStore newks = KeyStore.getInstance("JKS");
		newks.load(null, password);

		// 証明書と秘密鍵の登録
		newks.setKeyEntry("root", privateKey, password, new Certificate[]{certificate});

		File newksfile = new File(ksPath);
		newksfile.getParentFile().mkdirs();
		newksfile.createNewFile();
		newksfile.setWritable(false, false);
		newksfile.setWritable(true);
		newksfile.setReadable(false, false);
		newksfile.setReadable(true);
		FileOutputStream fos = new FileOutputStream(ksPath);
		newks.store(fos, password);
	}

	public void importPEM(String certificatePath, String privateKeyPath) throws Exception {
		InputStream is = Files.newInputStream(Paths.get(certificatePath));
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		Certificate certificate = cf.generateCertificate(is);
		byte[] b = Utils.readfile(privateKeyPath);
		String s = new String(b).replaceAll("-----.+?-----", "").replaceAll("\\r?\\n", "");
		// PKCS#8のRSA鍵のみ対応
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(s));
		KeyFactory kf = KeyFactory.getInstance("RSA");
		registerCertificateAndPrivateKeyToKeyStore(certificate, kf.generatePrivate(keySpec));
		super.load(ksPath);
		CertCacheManager.clearCache();
	}

	public void importDER(String certificatePath, String privateKeyPath) throws Exception {
		InputStream is = Files.newInputStream(Paths.get(certificatePath));
		CertificateFactory cf = CertificateFactory.getInstance("X.509");
		Certificate certificate = cf.generateCertificate(is);
		byte[] b = Utils.readfile(privateKeyPath);
		// PKCS#8のRSA鍵のみ対応
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(b);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		registerCertificateAndPrivateKeyToKeyStore(certificate, kf.generatePrivate(keySpec));
		super.load(ksPath);
		CertCacheManager.clearCache();
	}

	public void importP12(String p12Path, char[] password) throws Exception {
		InputStream inStream = Files.newInputStream(Paths.get(p12Path));
		KeyStore ks = KeyStore.getInstance("PKCS12");
		ks.load(inStream, password);
		String alias = ks.aliases().nextElement();
		Certificate certificate = ks.getCertificate(alias);
		PrivateKey privateKey = (PrivateKey) ks.getKey(alias, password);
		registerCertificateAndPrivateKeyToKeyStore(certificate, privateKey);
		super.load(ksPath);
		CertCacheManager.clearCache();
	}

	@Override
	public boolean isExportable() {
		return true;
	}

	@Override
	public void exportCertificatePEM(String certificatePath) throws Exception {
		InputStream is = new FileInputStream(ksPath);
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(is, password);
		Certificate caRoot = ks.getCertificate("root");
		String s = "-----BEGIN CERTIFICATE-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(caRoot.getEncoded())
				+ "\n-----END CERTIFICATE-----";
		FileOutputStream fos = new FileOutputStream(certificatePath);
		fos.write(s.getBytes());
		fos.close();
	}

	@Override
	public void exportCertificateDER(String certificatePath) throws Exception {
		InputStream is = new FileInputStream(ksPath);
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(is, password);
		Certificate caRoot = ks.getCertificate("root");
		FileOutputStream fos = new FileOutputStream(certificatePath);
		fos.write(caRoot.getEncoded());
		fos.close();
	}

	@Override
	public void exportPrivateKeyPEM(String privateKeyPath) throws Exception {
		InputStream is = new FileInputStream(ksPath);
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(is, password);
		PrivateKey privateKey = (PrivateKey) ks.getKey("root", password);
		String s = "-----BEGIN PRIVATE KEY-----\n"
				+ Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey.getEncoded())
				+ "\n-----END PRIVATE KEY-----";
		FileOutputStream fos = new FileOutputStream(privateKeyPath);
		fos.write(s.getBytes());
		fos.close();
	}

	@Override
	public void exportPrivateKeyDER(String privateKeyPath) throws Exception {
		InputStream is = new FileInputStream(ksPath);
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(is, password);
		PrivateKey privateKey = (PrivateKey) ks.getKey("root", password);
		FileOutputStream fos = new FileOutputStream(privateKeyPath);
		fos.write(privateKey.getEncoded());
		fos.close();
	}

	@Override
	public void exportP12(String p12Path, char[] enteredPassword) throws Exception {
		InputStream is = new FileInputStream(ksPath);
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(is, password);
		Certificate caRoot = ks.getCertificate("root");
		PrivateKey privateKey = (PrivateKey) ks.getKey("root", password);
		KeyStore newks = KeyStore.getInstance("PKCS12");
		newks.load(null, enteredPassword);
		newks.setKeyEntry("root", privateKey, enteredPassword, new Certificate[]{caRoot});
		FileOutputStream fos = new FileOutputStream(p12Path);
		newks.store(fos, enteredPassword);
		fos.close();
	}
}
