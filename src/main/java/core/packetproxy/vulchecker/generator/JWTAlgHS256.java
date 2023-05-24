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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class JWTAlgHS256 extends JWTBase64 {

    private String key;

    public JWTAlgHS256(String jwtString, String key) throws Exception {
        super(jwtString);
        this.key = key;
    }

    @Override
    protected String createSignature(String input) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        Mac hs256 = Mac.getInstance("HmacSHA256");
        hs256.init(keySpec);
        byte[] mac = hs256.doFinal(input.getBytes());
        return Base64.encodeBase64URLSafeString(mac);
    }
}
