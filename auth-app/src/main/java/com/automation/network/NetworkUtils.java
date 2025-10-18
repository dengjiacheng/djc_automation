package com.automation.network;

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
 * 网络工具类
 * 用于获取设备IP地址等网络信息
 */
public class NetworkUtils {
    private static final String TAG = "NetworkUtils";

    /**
     * 获取设备本地IP地址（优先WiFi，其次移动网络）
     * @param context 应用上下文
     * @return IP地址字符串，获取失败返回null
     */
    public static String getLocalIpAddress(Context context) {
        try {
            // 先尝试获取WiFi IP
            String wifiIp = getWifiIpAddress(context);
            if (wifiIp != null && !wifiIp.isEmpty()) {
                return wifiIp;
            }

            // WiFi未连接，尝试获取移动网络IP
            return getMobileIpAddress();
        } catch (Exception e) {
            Log.e(TAG, "获取本地IP失败", e);
            return null;
        }
    }

    /**
     * 获取WiFi IP地址
     */
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

            // 将int类型IP转为字符串
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

    /**
     * 获取移动网络IP地址（适用于移动数据连接）
     */
    private static String getMobileIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        // 过滤IPv6地址，只返回IPv4
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

    /**
     * 检查网络是否可用
     */
    public static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return false;
            }
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (Exception e) {
            Log.e(TAG, "检查网络状态失败", e);
            return false;
        }
    }
}
