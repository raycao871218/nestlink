package com.naslink.android;

import java.security.*;

/** Only replace the exact upstream seed definition found unreachable during diagnosis. */
public final class BootstrapPolicy {
    private BootstrapPolicy() {}
    private static final String OLD="d85620dee418cd7efc5558b6a50b2a8ac34792764d074fa55d752952e48da470";
    private static final String VERIFIED="ae6e33c4d3d6f5e599dfdfe72260210242a693dce2477578433972caa3694a72";
    public static String hash(byte[] bytes) {
        try {
            StringBuilder result=new StringBuilder();
            for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return result.toString();
        } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static boolean shouldInstall(byte[] existing,byte[] candidate) {
        if(!VERIFIED.equals(hash(candidate))) throw new IllegalArgumentException("内置根节点定义校验失败");
        return existing==null || OLD.equals(hash(existing));
    }
}
