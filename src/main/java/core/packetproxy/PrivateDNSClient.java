package packetproxy;

import org.xbill.DNS.Address;
import org.xbill.DNS.SystemResolverConfig;

import java.net.InetAddress;
import java.net.UnknownHostException;

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

    // システムのDNS設定が、PacketProxyのDNSサーバが設定されているときtrueになる
    private static boolean dnsLooping() {
        try {
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

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static InetAddress getByName(String name) throws UnknownHostException {
        return dnsLooping() ? Address.getByName(name) : InetAddress.getByName(name);
    }

    public static InetAddress[] getAllByName(String name) throws UnknownHostException {
        return dnsLooping() ? Address.getAllByName(name) : InetAddress.getAllByName(name);
    }

}
