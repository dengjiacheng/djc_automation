package com.automation.infrastructure.system;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

/**
 * Helper utilities for inspecting device network state.
 */
public final class NetworkInspector {

    private static final String TAG = "NetworkInspector";

    private NetworkInspector() {
        // no-op
    }

    public static String getLocalIpAddress(Context context) {
        try {
            String wifiIp = getWifiIpAddress(context);
            if (wifiIp != null && !wifiIp.isEmpty()) {
                return wifiIp;
            }
            return getMobileIpAddress();
        } catch (Exception e) {
            Log.e(TAG, "获取本地IP失败", e);
            return null;
        }
    }

    public static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        } catch (Exception e) {
            Log.e(TAG, "检查网络状态失败", e);
            return false;
        }
    }

    private static String getWifiIpAddress(Context context) {
        try {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) {
                return null;
            }
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            if (wifiInfo == null) {
                return null;
            }
            int ipInt = wifiInfo.getIpAddress();
            if (ipInt == 0) {
                return null;
            }
            return String.format("%d.%d.%d.%d",
                    (ipInt & 0xff),
                    (ipInt >> 8 & 0xff),
                    (ipInt >> 16 & 0xff),
                    (ipInt >> 24 & 0xff));
        } catch (Exception e) {
            Log.e(TAG, "获取WiFi IP失败", e);
            return null;
        }
    }

    private static String getMobileIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;
                        if (isIPv4) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取移动网络IP失败", e);
        }
        return null;
    }
}
