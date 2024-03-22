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
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;

abstract public class CA
{
	private KeyPair keyPair;
	private String keyStoreCAPath;
	private KeyStore keyStoreCA;
	private PrivateKey keyStoreCAPrivateKey;

	private X509CertificateHolder caRootHolder;
	private X500Name templateIssuer;
	private Date templateFrom;
	private Date templateTo;
	private SubjectPublicKeyInfo templatePubKey;

	private String aliasRoot = "root";
	private String aliasServer = "newalias";
	private char[] password = "testtest".toCharArray();

	protected CA() {
	}
	protected void load(String keyStoreCAPath) throws Exception {
		this.keyPair = genRSAKeyPair();
		this.keyStoreCAPath = keyStoreCAPath;
		try (InputStream input = new FileInputStream(this.keyStoreCAPath)) {
			initKeyStoreCA(input);
		}
	}
	protected void loadFromResource(String keyStoreCAPath) throws Exception {
		this.keyPair = genRSAKeyPair();
		this.keyStoreCAPath = keyStoreCAPath;
		try (InputStream input = this.getClass().getResourceAsStream(this.keyStoreCAPath)) {
			initKeyStoreCA(input);
		}
	}
	protected KeyPair genRSAKeyPair() throws Exception {
		KeyPairGenerator kpg = null;
		kpg = KeyPairGenerator.getInstance("RSA");
		kpg.initialize(2048);
		KeyPair kp = kpg.genKeyPair();
		return kp;
	}
	private void initKeyStoreCA(InputStream input) throws Exception {
		this.keyStoreCA = KeyStore.getInstance("JKS");
		this.keyStoreCA.load(input, password);

		this.keyStoreCAPrivateKey = (PrivateKey) keyStoreCA.getKey(aliasRoot, password);

		/* RootのSubject(Issuer)の取り出し */
		Certificate caRootCert = keyStoreCA.getCertificate(aliasRoot);
		caRootHolder = new X509CertificateHolder(caRootCert.getEncoded());

		/* 有効期限の設定 */
		Date from = new Date();
		Calendar cal = Calendar.getInstance();
		cal.setTime(from);
		cal.add(Calendar.YEAR, 1);
		Date to = cal.getTime();

		/* Templateの設定 */
		templateIssuer = caRootHolder.getSubject();
		templateFrom = from;
		templateTo = to;
		templatePubKey = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
	}

	public KeyStore createKeyStore(String commonName, String[] domainNames) throws Exception {
		/* シリアルナンバーの設定 */
		MessageDigest digest = MessageDigest.getInstance("MD5");
		byte[] hash = digest.digest(commonName.getBytes());
		BigInteger templateSerial = new BigInteger(hash);

		/* Subjectの設定 */
		X500Name templateSubject =  new X500Name(createSubject(commonName));

		/* Builderの生成 */
		X509v3CertificateBuilder serverBuilder = new X509v3CertificateBuilder(
				templateIssuer,
				templateSerial,
				templateFrom,
				templateTo,
				templateSubject,
				templatePubKey);

		/* SANの設定 */
		ArrayList<ASN1Encodable> sans = new ArrayList<>();
		sans.add(new GeneralName(GeneralName.dNSName, createCNforSAN(commonName)));
		/*
		 Fix: SubjectCN = SANに変更
		 Reason: SANに全てのサーバが入っていると、HTTP2通信のとき、1つのHTTP2コネクション内に複数サーバ宛のストリームが含まれてしまうケースがあるため
		*/
		//for (String domainName : domainNames) {
		// sans.add(new GeneralName(GeneralName.dNSName, domainName));
		//}
		DERSequence subjectAlternativeNames = new DERSequence(sans.toArray(new ASN1Encodable[sans.size()]));
		serverBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAlternativeNames);

		// 署名
		X509CertificateHolder serverHolder = serverBuilder.build(createSigner());

		/* 新しいKeyStoreを作成 */
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(null, password);
		ks.setKeyEntry(
				aliasServer,
				keyPair.getPrivate(),
				password,
				new java.security.cert.Certificate[] {
						certFactory.generateCertificate(new ByteArrayInputStream(serverHolder.getEncoded())),
						certFactory.generateCertificate(new ByteArrayInputStream(caRootHolder.getEncoded()))
				});

		return ks;
	}
	
	protected String createSubject(String commonName) {
		return String.format("C=PacketProxy, ST=PacketProxy, L=PacketProxy, O=PacketProxy, OU=PacketProxy, CN=%s", commonName);
	}
	
	protected String createCNforSAN(String commonName) {
		return commonName;
	}

	protected ContentSigner createSigner() throws Exception {
		AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find("SHA256withRSA");
		AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
		return new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(PrivateKeyFactory.createKey(keyStoreCAPrivateKey.getEncoded()));
	}

	public String getName() {
		return "Unknown CA";
	}

	public String getUTF8Name() {
		return "Unknown CA";
	}

	public String toString() {
		return "Unknown CA";
	}

	// export可能なCAのとき、継承して実装すること
	public byte[] getCACertificate() {
		return null;
	}

	public void regenerateCA() throws Exception {
		throw new RuntimeException("Not Implemented.");
	}
}