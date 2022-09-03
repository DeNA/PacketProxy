/*
 * Copyright 2022 DeNA Co., Ltd.
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

package packetproxy;

import org.xbill.DNS.Address;
import org.xbill.DNS.SystemResolverConfig;
import packetproxy.util.PacketProxyUtility;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class PrivateDNSClient {

    private static boolean isLoopbackAddress(String addr) {
        return addr.equals("127.0.0.1") ||
                addr.equals("0:0:0:0:0:0:0:1") ||
                addr.equals("::1");
    }

    private static boolean isLocalAddress(String addr) throws UnknownHostException {
        String localAddr = InetAddress.getLocalHost().getHostAddress();
        return addr.equals(localAddr);
    }

    private static boolean dnsLooping(String serverName) throws Exception {
        return dnsLoopDetectedInDnsServer() || dnsLoopDetectedInEtcHosts(serverName);
    }

    // システムのDNS設定が、PacketProxyのDNSサーバが設定されているときtrueになる
    private static boolean dnsLoopDetectedInDnsServer() throws Exception {
        if (!PrivateDNS.getInstance().isRunning()) {
            return false;
        }

        // current system dns server setting
        SystemResolverConfig systemResolver = new SystemResolverConfig();
        String dnsServer = systemResolver.server();

        if (isLoopbackAddress(dnsServer)) {
            return true;
        }
        if (isLocalAddress(dnsServer)) {
            return true;
        }
        return false;
    }

    public static boolean dnsLoopDetectedInEtcHosts(String serverName) throws Exception {
        if (PacketProxyUtility.getInstance().isMac() || PacketProxyUtility.getInstance().isUnix()) {
            return dnsLoopingFromHostsLines(Files.readAllLines(Paths.get("/etc/hosts")), serverName);
        } else {
            return false;
        }
    }

    public static boolean dnsLoopingFromHostsLines(List<String> fileLines, String serverName) {
        return fileLines.stream()
                .map(line -> line.contains("#") ? line.substring(0, line.indexOf("#")) : line)
                .filter(line -> line.contains(serverName))
                .anyMatch(line -> {
                    try {
                        String addr = line.split(" ")[0];
                        if (isLoopbackAddress(addr) || isLocalAddress(addr)) {
                            return true;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return false;
                });
    }

    public static InetAddress getByName(String serverName) throws Exception {
        return dnsLooping(serverName) ? Address.getByName(serverName) : InetAddress.getByName(serverName);
    }

    public static InetAddress[] getAllByName(String serverName) throws Exception {
        return dnsLooping(serverName) ? Address.getAllByName(serverName) : InetAddress.getAllByName(serverName);
    }

}
