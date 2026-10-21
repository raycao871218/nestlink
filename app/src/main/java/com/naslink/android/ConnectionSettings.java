package com.naslink.android;

import java.net.*;

public final class ConnectionSettings {
    private ConnectionSettings() {}
    public static Inet4Address ipv4(String value) throws UnknownHostException {
        String[] parts=value.split("\\.",-1); if(parts.length!=4) throw new UnknownHostException("请填写 IPv4 地址");
        byte[] ip=new byte[4];
        for(int n=0;n<4;n++) {
            if(!parts[n].matches("0|[1-9][0-9]{0,2}")) throw new UnknownHostException("无效 IPv4 地址");
            int v=Integer.parseInt(parts[n]); if(v>255) throw new UnknownHostException("无效 IPv4 地址"); ip[n]=(byte)v;
        }
        return (Inet4Address)InetAddress.getByAddress(ip);
    }
}
