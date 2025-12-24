package helper;

import java.net.InetAddress;

public class CheckInternet {
    public static boolean isInternetAvailable() {
        try {
            InetAddress address = InetAddress.getByName("8.8.8.8");
            return address.isReachable(2000); // 2s timeout
        } catch (Exception e) {
            return false;
        }
    }
}