package com.naslink.android;

import android.content.Context;
import android.net.*;
import android.net.wifi.*;

final class CurrentNetworkReader {
    @SuppressWarnings("deprecation")
    static String ssid(Context context,Network network) {
        try {
            ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
            NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            if(caps==null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "";
            if(android.os.Build.VERSION.SDK_INT>=29 && caps.getTransportInfo() instanceof WifiInfo) {
                String name=HomeNetworkSettings.unquote(((WifiInfo)caps.getTransportInfo()).getSSID());
                if(!name.isEmpty()) return name;
            }
            // API 26 fallback and location-redacted callback snapshots.
            WifiManager wifi=context.getApplicationContext().getSystemService(WifiManager.class);
            return wifi==null?"":HomeNetworkSettings.unquote(wifi.getConnectionInfo().getSSID());
        } catch(SecurityException e) { return ""; }
    }
    static boolean onSubnet(ConnectivityManager cm,Network network,String nas) {
        LinkProperties links=cm.getLinkProperties(network);
        if(links!=null) for(LinkAddress ip:links.getLinkAddresses())
            if(HomeNetworkSettings.sameSubnet(nas,ip.getAddress().getHostAddress(),ip.getPrefixLength())) return true;
        return false;
    }
}
