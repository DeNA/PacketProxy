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
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
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

	@Override
	public void regenerateCA() throws Exception {
	    new File(ksPath).delete();
	    generateKeyStore(ksPath);
	    super.load(ksPath);
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

		String x500Name = String.format("C=PacketProxy, ST=PacketProxy, L=PacketProxy, O=PacketProxy, OU=PacketProxy CA, CN=PacketProxy per-user CA (%x)", serialNumber);
		Date from = new Date();
		Calendar cal = Calendar.getInstance();
		cal.setTime(from);
		cal.add(Calendar.YEAR, 30);
		Date to = cal.getTime();

		X509v3CertificateBuilder caRootBuilder = new X509v3CertificateBuilder(
				new X500Name(x500Name),
				BigInteger.valueOf(serialNumber),
				from,
				to,
				new X500Name(x500Name),
				SubjectPublicKeyInfo.getInstance(CAKeyPair.getPublic().getEncoded()));
        
		/* CA: X509 Extensionsの設定（CA:true, pathlen:0) */
		caRootBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0)); 
		
        AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
        AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
        ContentSigner signer = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(PrivateKeyFactory.createKey(CAKeyPair.getPrivate().getEncoded()));
        X509CertificateHolder signedRoot = caRootBuilder.build(signer);
		
		// 新しいKeyStoreの生成
		KeyStore newks = KeyStore.getInstance("JKS");
		newks.load(null, password);
		
		// 証明書と秘密鍵の登録
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		newks.setKeyEntry(
				"root",
				CAKeyPair.getPrivate(),
				password,
				new Certificate[]{ certFactory.generateCertificate(new ByteArrayInputStream(signedRoot.getEncoded())) });
		
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
}
