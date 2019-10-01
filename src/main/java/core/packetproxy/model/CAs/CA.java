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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.CertificateIssuerName;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateSubjectName;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.GeneralName;
import sun.security.x509.GeneralNames;
import sun.security.x509.SubjectAlternativeNameExtension;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import packetproxy.util.PacketProxyUtility;
import packetproxy.util.SimpleDNSName;

abstract public class CA
{
	private KeyPair keyPair;
	private String keyStoreCAPath;
	private KeyStore keyStoreCA;
	private PrivateKey keyStoreCAPrivateKey;

	private X509CertImpl keyStoreCACertRoot;
	private X509CertInfo keyStoreCACertTemplate;
	private AlgorithmId algorithmId;

	private String caRoot = "root";
	private String caTemplate = "template";
	private String newAlias = "newalias";

	private char[] password = new char[]{'t','e','s','t','t','e','s','t'};
	private char[] caPassword = new char[]{'t','e','s','t','t','e','s','t'};
	private char[] certPassword = new char[]{'t','e','s','t','t','e','s','t'};

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

		this.keyStoreCAPrivateKey = (PrivateKey) keyStoreCA.getKey(caRoot, caPassword);

		/* Issuerの取り出し */
		java.security.cert.Certificate caCert = this.keyStoreCA.getCertificate(caRoot);
		keyStoreCACertRoot = new X509CertImpl(caCert.getEncoded());
		X509CertInfo caCertInfo = (X509CertInfo) this.keyStoreCACertRoot.get(X509CertImpl.NAME + "." + X509CertImpl.INFO);
		X500Name issuer = (X500Name) caCertInfo.get(X509CertInfo.SUBJECT + "." + CertificateIssuerName.DN_NAME);

		/* Templateの取り出し */
		java.security.cert.Certificate templateCert = this.keyStoreCA.getCertificate(caTemplate);
		X509CertImpl certImpl = new X509CertImpl(templateCert.getEncoded());
		keyStoreCACertTemplate = (X509CertInfo) certImpl.get(X509CertImpl.NAME + "." + X509CertImpl.INFO);

		/* 有効期限の設定 */
		GregorianCalendar date = new GregorianCalendar();
		date.add(Calendar.DATE, -30);
		Date firstDate = date.getTime();
		Date lastDate = new Date(firstDate.getTime() + 365 * 24 * 60 * 60 * 1000L);
		CertificateValidity interval = new CertificateValidity(firstDate, lastDate);
		keyStoreCACertTemplate.set(X509CertInfo.VALIDITY, interval);

		/* Issuerの設定 */
		keyStoreCACertTemplate.set(X509CertInfo.ISSUER + "." + CertificateSubjectName.DN_NAME, issuer);

		/* publicキーの設定 */
		keyStoreCACertTemplate.set(X509CertInfo.KEY, new CertificateX509Key(this.keyPair.getPublic()));

		/* 署名アルゴリズム設定 */
		algorithmId = AlgorithmId.get(AlgorithmId.sha256WithRSAEncryption_oid.toString());
		keyStoreCACertTemplate.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algorithmId));
	}

	private KeyStore sign(String commonName, String[] domainNames) throws Exception {
		/* シリアルナンバーの設定 */
		MessageDigest digest = MessageDigest.getInstance("MD5");
		byte[] hash = digest.digest(commonName.getBytes());
		keyStoreCACertTemplate.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(new BigInteger(hash)));
		/* DNの設定 */
		String dn = String.format("CN=%s, OU=PacketProxy, O=PacketProxy, C=PacketProxy", commonName);
		keyStoreCACertTemplate.set(X509CertInfo.SUBJECT + "." + CertificateSubjectName.DN_NAME, new X500Name(dn));
		/* SANの設定 */
		try {
			GeneralNames gns = new GeneralNames(); 
			gns.add(new GeneralName(new SimpleDNSName(commonName)));
			for (String domainName : domainNames) {
				//System.out.println(domainName);
				gns.add(new GeneralName(new SimpleDNSName(domainName)));
			}
			SubjectAlternativeNameExtension san = new SubjectAlternativeNameExtension(gns);
			CertificateExtensions ext = new CertificateExtensions();
			ext.set(SubjectAlternativeNameExtension.NAME, san);
			keyStoreCACertTemplate.set(X509CertInfo.EXTENSIONS, ext);
		} catch (IOException e) {
			PacketProxyUtility.getInstance().packetProxyLogErr(e.toString());
			e.printStackTrace();
			PacketProxyUtility.getInstance().packetProxyLogErr(String.format("[ERROR] %s: ドメイン名がアルファベット以外から始まっているとDNSNameクラスが例外を吐くためSANが使えない", commonName));
			keyStoreCACertTemplate.delete(X509CertInfo.EXTENSIONS);
		}

		X509CertImpl newCert = sign(keyStoreCACertTemplate, keyStoreCAPrivateKey, algorithmId);

		/* 新しいKeyStoreを作成 */
		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(null, certPassword);
		ks.setKeyEntry(newAlias, this.keyPair.getPrivate(), certPassword, new java.security.cert.Certificate[] { newCert, this.keyStoreCACertRoot });

		return ks;
	}

	protected X509CertImpl sign(X509CertInfo keyStoreCACertTemplate, PrivateKey keyStoreCAPrivateKey, AlgorithmId algorithmId){
		/* 署名前の新しい証明書の完成 */
		X509CertImpl newCert = new X509CertImpl(keyStoreCACertTemplate);
		/* 署名！ */
		try {
			newCert.sign(keyStoreCAPrivateKey, algorithmId.getName());
		} catch (Exception e){
			e.printStackTrace();
		} 
		return newCert;
	}

	public KeyStore createKeyStore(String commonName, String[] domainNames) throws Exception {
		return sign(commonName, domainNames);
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
	
	public byte[] getCACertificate() {
		return null;
	}
}
