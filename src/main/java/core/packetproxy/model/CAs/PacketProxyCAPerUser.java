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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;

import sun.security.x509.AlgorithmId;
import sun.security.x509.BasicConstraintsExtension;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

public class PacketProxyCAPerUser extends CA {
	private static final String name = "PacketProxy per-user CA";
	private static final String desc = "PacketProxy per-user CA";
	private static final char[] password = "testtest".toCharArray();
	private static final String ksPath = Paths.get(System.getProperty("user.home") + "/.packetproxy/certs/user.ks").toString();

	public PacketProxyCAPerUser() throws Exception {
		if (! new File(ksPath).exists()) {
			generateKeyStore(ksPath);
		}
		super.load(ksPath);
	}
	
	public void generateKeyStore(String ksPath) throws Exception {
		KeyStore ks;
		KeyPair CAKeyPair = super.genRSAKeyPair();
		KeyPair TemplateKeyPair = super.genRSAKeyPair();
		
		// 各ユーザ用のキーストアを作るためのテンプレートを取得
		try (InputStream input = this.getClass().getResourceAsStream("/certificates/user.ks")) {
			ks = KeyStore.getInstance("JKS");
			ks.load(input, password);
		}
		
		// CA: 公開鍵の入れ替え
		Certificate caRoot = ks.getCertificate("root");
		X509CertImpl caRootImpl = new X509CertImpl(caRoot.getEncoded()); 
		X509CertInfo caRootInfo = (X509CertInfo) caRootImpl.get(X509CertImpl.NAME + "." + X509CertImpl.INFO);
		caRootInfo.set(X509CertInfo.KEY, new CertificateX509Key(CAKeyPair.getPublic())); /* publicキーの設定 */
		// CA: シリアルナンバー入れ替え
		int serialNumber = 0;
		do {
			serialNumber = SecureRandom.getInstance("SHA1PRNG").nextInt();
		} while (serialNumber <= 0);
		caRootInfo.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(serialNumber));
		// CA: Subject入れ替え
		caRootInfo.set(X509CertInfo.SUBJECT, new X500Name(
				String.format("PacketProxy per-user CA (%x)", serialNumber),
				"PacketProxy CA", "packetproxy", "packetproxy", "packetproxy", "packetproxy"));
		// CA: Issuer入れ替え
		caRootInfo.set(X509CertInfo.ISSUER, new X500Name(
				String.format("PacketProxy per-user CA (%x)", serialNumber),
				"PacketProxy CA", "packetproxy", "packetproxy", "packetproxy", "packetproxy"));
		
		/* CA: X509 Extensionsの設定（CA:true, pathlen:0) */
		CertificateExtensions ext = new CertificateExtensions();
		ext.set(BasicConstraintsExtension.NAME, new BasicConstraintsExtension(true, true, 0));
		caRootInfo.set(X509CertInfo.EXTENSIONS, ext);

		// Template: 公開鍵の入れ替え
		Certificate Template = ks.getCertificate("template");
		X509CertImpl TemplateImpl = new X509CertImpl(Template.getEncoded()); 
		X509CertInfo TemplateInfo = (X509CertInfo) TemplateImpl.get(X509CertImpl.NAME + "." + X509CertImpl.INFO);
		TemplateInfo.set(X509CertInfo.KEY, new CertificateX509Key(TemplateKeyPair.getPublic())); /* publicキーの設定 */

		// CA: Template: 署名
		AlgorithmId algorithmId = AlgorithmId.get(AlgorithmId.sha256WithRSAEncryption_oid.toString());
		caRootImpl = sign(caRootInfo, CAKeyPair.getPrivate(), algorithmId);
		TemplateImpl = sign(TemplateInfo, CAKeyPair.getPrivate(), algorithmId);

		// 新しいKeyStoreの生成
		KeyStore newks = KeyStore.getInstance("JKS");
		newks.load(null, password);
		
		// 証明書と秘密鍵の登録
		newks.setKeyEntry("root", CAKeyPair.getPrivate(), password, new Certificate[]{caRootImpl});
		newks.setKeyEntry("template", TemplateKeyPair.getPrivate(), password, new Certificate[]{TemplateImpl});
		
		File newksfile = new File(ksPath);
		newksfile.getParentFile().mkdirs();
		newksfile.createNewFile();
		newksfile.setWritable(false, false);
		newksfile.setWritable(true);
		newksfile.setReadable(false, false);
		newksfile.setReadable(true);
		try (FileOutputStream fos = new FileOutputStream(ksPath)) {
		    newks.store(fos, password);
		}
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
	public KeyStore createKeyStore(String commonName, String[] domainNames) throws Exception {
		return super.createKeyStore(commonName, domainNames);
	}

	@Override
	public String toString() {
		return "PacketProxy per-user CA [name=" + name + ", desc=" + desc + "]";
	}

	@Override
	public byte[] getCACertificate() {
		try (InputStream input = new FileInputStream(ksPath)) {
			KeyStore ks = KeyStore.getInstance("JKS");
			ks.load(input, password);
			Certificate caRoot = ks.getCertificate("root");
			return caRoot.getEncoded();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
