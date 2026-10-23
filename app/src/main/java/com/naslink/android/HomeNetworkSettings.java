package com.naslink.android;

import java.nio.charset.StandardCharsets;

/** Device-local home policy; never included in join codes. */
public final class HomeNetworkSettings {
    public static void validate(boolean enabled,String ssid,String nas,int port) {
        if(!enabled) return;
        if(ssid.trim().isEmpty() || ssid.getBytes(StandardCharsets.UTF_8).length>32 || ssid.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("home_ssid_invalid");
        if(!isPrivate(nas)) throw new IllegalArgumentException("home_nas_invalid");
        if(port<1 || port>65535) throw new IllegalArgumentException("home_port_invalid");
    }
    public static boolean isPrivate(String ip) {
        try {
            byte[] b=ConnectionSettings.ipv4(ip).getAddress();
            return (b[0]&255)==10 || (b[0]&255)==172 && (b[1]&255)>=16 && (b[1]&255)<=31 || (b[0]&255)==192 && (b[1]&255)==168;
        } catch(Exception e) { return false; }
    }
    public static boolean sameSubnet(String nas,String address,int prefix) {
        if(prefix<1 || prefix>30) return false;
        try {
            int a=EthernetBridge.ip(ConnectionSettings.ipv4(nas).getAddress(),0);
            int b=EthernetBridge.ip(ConnectionSettings.ipv4(address).getAddress(),0);
            int mask=-1 << (32-prefix);
            return a!=b && (a&mask)==(b&mask);
        } catch(Exception e) { return false; }
    }
    public static String unquote(String ssid) {
        if(ssid==null || "<unknown ssid>".equals(ssid)) return "";
        return ssid.length()>=2 && ssid.startsWith("\"") && ssid.endsWith("\"") ? ssid.substring(1,ssid.length()-1) : ssid;
    }
}
