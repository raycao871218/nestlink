package com.naslink.android;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Parses connection data only. Never opens a URL or performs network requests. */
public final class QrPayload {
    public final String network,gateway,moons;
    public final Integer port;
    private QrPayload(String network,String gateway,Integer port,String moons) {
        this.network=id(network);this.gateway=gateway;this.port=port;this.moons=moons;
    }
    private static String id(String value) {
        if(value==null||!value.matches("[0-9a-fA-F]{16}")||value.equals("0000000000000000")) throw invalid();
        return value.toLowerCase(Locale.ROOT);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("二维码中没有有效的 ZeroTier 入网信息。"); }
    public static QrPayload parse(String raw) {
        if(raw==null||raw.length()>4096) throw invalid();
        String value=raw.trim();
        if(value.matches("[0-9a-fA-F]{16}")) return new QrPayload(value,null,null,null);
        try {
            URI uri=new URI(value);
            if(uri.getFragment()!=null||uri.getUserInfo()!=null||uri.getPort()!=-1) throw invalid();
            String scheme=uri.getScheme(),host=uri.getHost(),path=uri.getPath();
            if("zerotier".equalsIgnoreCase(scheme)&&uri.getRawQuery()==null) {
                if(path.isEmpty()||path.equals("/")) return new QrPayload(uri.getRawAuthority(),null,null,null);
                if("network".equalsIgnoreCase(host)&&path.matches("/[0-9a-fA-F]{16}/?")) return new QrPayload(path.substring(1,17),null,null,null);
            }
            if("https".equalsIgnoreCase(scheme)&&uri.getRawQuery()==null &&
                ("my.zerotier.com".equalsIgnoreCase(host)||"central.zerotier.com".equalsIgnoreCase(host)) && path.matches("/networks?/[0-9a-fA-F]{16}/?")) {
                String[] pieces=path.split("/");return new QrPayload(pieces[2],null,null,null);
            }
            if(!"harborlink".equalsIgnoreCase(scheme)||!"join".equalsIgnoreCase(host)||!(path.isEmpty()||path.equals("/"))||uri.getRawQuery()==null) throw invalid();
            Map<String,String> fields=new HashMap<>();
            for(String part:uri.getRawQuery().split("&",-1)) {
                String[] pair=part.split("=",2);if(pair.length!=2) throw invalid();
                String key=URLDecoder.decode(pair[0],"UTF-8"),text=URLDecoder.decode(pair[1],"UTF-8");
                if(!Arrays.asList("network","gateway","port","moons").contains(key)||fields.put(key,text)!=null) throw invalid();
            }
            String nas=fields.get("gateway"),moon=fields.get("moons");Integer port=null;
            if(nas!=null) ConnectionSettings.ipv4(nas);
            if(fields.containsKey("port")) {
                String number=fields.get("port");if(!number.matches("[0-9]{1,5}")) throw invalid();
                port=Integer.valueOf(number);if(port<1||port>65535) throw invalid();
            }
            if(moon!=null) moon=MoonSettings.format(MoonSettings.parse(moon));
            return new QrPayload(fields.get("network"),nas,port,moon);
        } catch(Exception ex) { throw invalid(); }
    }
    public static String encode(String network,String gateway,int port,String moons) {
        String value="harborlink://join?network="+id(network)+"&gateway="+escape(gateway)+"&port="+port+"&moons="+escape(moons);
        parse(value);return value;
    }
    private static String escape(String value) {
        if(value==null) throw invalid();
        try { return URLEncoder.encode(value,"UTF-8"); } catch(java.io.UnsupportedEncodingException e) { throw new AssertionError(e); }
    }
}
