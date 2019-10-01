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
package packetproxy.util;

import java.io.IOException;

import sun.security.util.DerOutputStream;
import sun.security.x509.GeneralNameInterface;

public class SimpleDNSName implements GeneralNameInterface {
    private String name;

    public SimpleDNSName(String name) throws IOException {
        this.name = name;
    }
    public int getType() {
        return GeneralNameInterface.NAME_DNS;
    }
    public String getName() {
        return name;
    }
    public void encode(DerOutputStream out) throws IOException {
        out.putIA5String(name);
    }
    public String toString() {
        return "DNSName: " + name;
    }
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof SimpleDNSName))
            return false;
        return name.equalsIgnoreCase(((SimpleDNSName)obj).name);
    }
    public int hashCode() {
        return name.toUpperCase().hashCode();
    }
    public int constrains(GeneralNameInterface inputName) throws UnsupportedOperationException {
        return GeneralNameInterface.NAME_DNS;
    }
    public int subtreeDepth() throws UnsupportedOperationException {
        int sum = 1;
        for (int i = name.indexOf('.'); i >= 0; i = name.indexOf('.', i + 1)) {
            ++sum;
        }
        return sum;
    }
}
