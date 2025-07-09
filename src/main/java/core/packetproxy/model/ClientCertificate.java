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
package packetproxy.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

/**
 * Certificate Model for Client Certificate Authentication
 */
@DatabaseTable(tableName = "clientCertificates")
public class ClientCertificate {
	@DatabaseField(generatedId = true)
	private int id;
	@DatabaseField
	private Boolean enabled;
	@DatabaseField(uniqueCombo = true)
	private Type type;
	@DatabaseField(uniqueCombo = true)
	private int serverId;
	@DatabaseField
	private String subject;
	@DatabaseField
	private String issuer;
	@DatabaseField(uniqueCombo = true)
	private String path;
	@DatabaseField
	private String storePassword;
	@DatabaseField
	private String keyPassword;

	public ClientCertificate() {
		// ORMLite needs a no-arg constructor
	}
	public ClientCertificate(Type type, Server server, String subject, String issuer, String path, String storePassword,
			String keyPassword) {
		this.enabled = false;
		this.type = type;
		this.serverId = server.getId();
		this.subject = subject;
		this.issuer = issuer;
		this.path = path;
		this.storePassword = storePassword;
		this.keyPassword = keyPassword;
	}

	/**
	 * Convert from Client Certificate file into this model
	 *
	 * @param type:
	 *            Certificate Type (e.g. PKCS#12, JKS)
	 * @param server:
	 *            Applied Server
	 * @param path:
	 *            Certificate Path on File System
	 * @param storePassword:
	 *            Password for Certificate
	 * @param keyPassword:
	 *            Password for Private Key
	 * @return Model for Client Certificate
	 * @throws Exception:
	 *             - IOException - FileNotFoundException - KeyStoreException -
	 *             NoSuchAlgorithmException - CertificateException
	 */
	public static ClientCertificate convert(Type type, Server server, String path, char[] storePassword,
			char[] keyPassword) throws Exception {

		// Load KeyStore
		FileInputStream fis = new FileInputStream(path);
		KeyStore ks = KeyStore.getInstance(type.getText());
		ks.load(fis, storePassword);
		fis.close();

		// Get an alias of FIRST ONLY!!!
		String alias = ks.aliases().nextElement();

		// Extract CommonName and Issuer
		X509Certificate crt = (X509Certificate) ks.getCertificate(alias);
		String subject = getCommonName(crt.getSubjectDN().getName());
		String issuer = crt.getIssuerDN().getName();

		return new ClientCertificate(type, server, subject, issuer, path, String.valueOf(storePassword),
				String.valueOf(keyPassword));
	}

	/**
	 * Load for getting KeyManager[] from this model
	 *
	 * @return KeyManager for Client Certificate
	 * @throws Exception:
	 *             - IOException - KeyStoreException - CertificateException -
	 *             NoSuchAlgorithmException - UnrecoverableKeyException
	 */
	public KeyManager[] load() throws Exception {
		// Load KeyStore
		FileInputStream fis = new FileInputStream(path);
		KeyStore keyStore = KeyStore.getInstance(type.getText());
		keyStore.load(fis, storePassword.toCharArray());
		fis.close();

		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
		kmf.init(keyStore, keyPassword.toCharArray());

		return kmf.getKeyManagers();
	}

	/**
	 * Get CommonName from SubjectDN. If regex capture is failed, return inputted
	 * SubjectDN.
	 *
	 * @param subject:
	 *            SubjectDN
	 * @return cn or subject
	 */
	private static String getCommonName(String subject) {
		Pattern pattern = Pattern.compile("CN=(.*?),");
		Matcher matcher = pattern.matcher(subject);

		if (matcher.find() && matcher.group(1) != "")
			return matcher.group(1);
		return subject;
	}

	public enum Type {
		P12("PKCS12"), JKS("JKS"),
		// BKS("BKS"),
		;

		private final String text;

		Type(final String t) {
			this.text = t;
		}

		public static Type getTypeFromText(final String t) {
			for (Type type : Type.values()) {
				if (Objects.equals(type.getText(), t)) {
					return type;
				}
			}
			return null;
		}

		public String getText() {
			return this.text;
		}
	}

	// Getter / Setter
	public int getId() {
		return id;
	}
	public Boolean getEnabled() {
		return enabled;
	}
	public void setEnabled(Boolean enabled) {
		this.enabled = enabled;
	}
	public int getServerId() {
		return this.serverId;
	}
	public void setServerId(int serverId) {
		this.serverId = serverId;
	}
	public Type getType() {
		return this.type;
	}
	public void setType(Type type) {
		this.type = type;
	}
	public String getSubject() {
		return this.subject;
	}
	public void setSubject(String subject) {
		this.subject = subject;
	}
	public String getIssuer() {
		return this.issuer;
	}
	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public String getStorePassword() {
		return storePassword;
	}
	public void setStorePassword(String storePassword) {
		this.storePassword = storePassword;
	}
	public String getKeyPassword() {
		return keyPassword;
	}
	public void setKeyPassword(String keyPassword) {
		this.keyPassword = keyPassword;
	}
	public boolean isEnabled() {
		return this.enabled;
	}
	public void setEnabled() {
		this.enabled = true;
	}
	public void setDisabled() {
		this.enabled = false;
	}
	public Server getServer() throws Exception {
		return Servers.getInstance().query(this.serverId);
	}
	public String getServerName() throws Exception {
		Server server = Servers.getInstance().query(this.serverId);
		return server != null ? server.toString() : "";
	}

	@Override
	public int hashCode() {
		return this.getId();
	}

	public boolean equals(ClientCertificate obj) {
		return this.getId() == obj.getId() ? true : false;
	}
}
