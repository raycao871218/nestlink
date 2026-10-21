package com.naslink.android;

import java.util.*;

/** Saved orbit specifications, independent of Android and of native cached moon files. */
public final class MoonSettings {
    private MoonSettings() {}
    public static final class Moon {
        public final long world, seed;
        Moon(long world,long seed) { this.world=world; this.seed=seed; }
        public String line() { return String.format(Locale.ROOT,"%016x %010x",world,seed); }
    }
    public static List<Moon> parse(String text) {
        LinkedHashMap<Long,Moon> result=new LinkedHashMap<>();
        int line=0;
        for(String raw:text.split("\\r?\\n",-1)) {
            line++;
            String entry=raw.trim(); if(entry.isEmpty()) continue;
            String[] parts=entry.split("[\\s,]+");
            try {
                if(parts.length<1 || parts.length>2) throw new IllegalArgumentException("每行填写 Moon ID 和可选的 Seed ID");
                long world=hex(parts[0],16,"Moon ID");
                long seed;
                if(parts.length==2) seed=hex(parts[1],10,"Seed ID");
                else {
                    if(Long.compareUnsigned(world,0xffffffffffL)>0) throw new IllegalArgumentException("此 Moon ID 需要单独填写 Seed ID");
                    seed=world;
                }
                Moon previous=result.get(world);
                if(previous!=null && previous.seed!=seed) throw new IllegalArgumentException("同一个 Moon ID 填写了不同 Seed ID");
                result.put(world,new Moon(world,seed));
                if(result.size()>8) throw new IllegalArgumentException("最多配置 8 个 Moon");
            } catch(IllegalArgumentException e) { throw new IllegalArgumentException("第 "+line+" 行："+e.getMessage()); }
        }
        return Collections.unmodifiableList(new ArrayList<>(result.values()));
    }
    private static long hex(String value,int max,String label) {
        String normalized=value.replaceFirst("^(0x|0X)","");
        if(!normalized.matches("[a-fA-F0-9]{1,"+max+"}")) throw new IllegalArgumentException(label+" 必须为最多 "+max+" 位十六进制数字，不能填 IP 或端口");
        long number=Long.parseUnsignedLong(normalized,16);
        if(number==0) throw new IllegalArgumentException(label+" 不能为零");
        return number;
    }
    public static String format(List<Moon> moons) {
        StringBuilder result=new StringBuilder();
        for(Moon moon:moons) { if(result.length()>0) result.append('\n'); result.append(moon.line()); }
        return result.toString();
    }
}
