/*
 * Copyright 2023 DeNA Co., Ltd.
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

package packetproxy.vulchecker.generator;

import org.apache.commons.codec.binary.Base64;
import packetproxy.common.JWTBase64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;

public class JWTAlgRS256 extends JWTBase64 {

    private RSAPrivateKey privateKey;

    public JWTAlgRS256(String jwtString, byte[] pkcs8PrivateKey) throws Exception {
        super(jwtString);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pkcs8PrivateKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        this.privateKey = (RSAPrivateKey) kf.generatePrivate(keySpec);
    }

    public JWTAlgRS256(String jwtString, RSAPrivateKey privateKey) {
        super(jwtString);
        this.privateKey = privateKey;
    }

    @Override
    protected String createSignature(String input) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(this.privateKey);
        signer.update(input.getBytes(StandardCharsets.UTF_8));
        byte[] sign = signer.sign();
        return Base64.encodeBase64URLSafeString(sign);
    }
}
