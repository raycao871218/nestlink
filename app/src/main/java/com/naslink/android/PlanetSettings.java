package com.naslink.android;

import java.io.*;

/** Structural validation of the C25519 World format used by the bundled engine. */
public final class PlanetSettings {
    private PlanetSettings() {}
    public static final int MAX_SIZE=16384;
    public static byte[] read(InputStream input) throws IOException {
        if(input==null) throw new IOException("planet_invalid");
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        byte[] buffer=new byte[1024]; int count;
        while((count=input.read(buffer))!=-1) {
            if(output.size()+count>MAX_SIZE) throw new IOException("planet_invalid");
            output.write(buffer,0,count);
        }
        byte[] bytes=output.toByteArray(); validate(bytes); return bytes;
    }
    public static void validate(byte[] bytes) throws IOException {
        try {
            if(bytes.length>MAX_SIZE || bytes.length<178 || bytes[0]!=1) throw new IOException();
            DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes));
            input.skipBytes(177); // type, world ID, timestamp, public signing key, signature
            int roots=input.readUnsignedByte();
            if(roots<1 || roots>4) throw new IOException();
            for(int root=0;root<roots;root++) {
                input.readFully(new byte[5]);
                if(input.readUnsignedByte()!=0) throw new IOException();
                input.readFully(new byte[64]);
                if(input.readUnsignedByte()!=0) throw new IOException(); // public identities only
                int endpoints=input.readUnsignedByte();
                if(endpoints>32) throw new IOException();
                for(int endpoint=0;endpoint<endpoints;endpoint++) {
                    int type=input.readUnsignedByte();
                    if(type!=4 && type!=6) throw new IOException();
                    input.readFully(new byte[type==4?6:18]);
                }
            }
            if(input.available()!=0) throw new IOException();
        } catch(IOException e) { throw new IOException("planet_invalid"); }
    }
    public static boolean shouldReplace(String applied,String selected,boolean custom,byte[] current,byte[] bundled) {
        if(current==null) return true;
        if(applied!=null) return !applied.equals(selected);
        return custom || selected.contains(":") || BootstrapPolicy.shouldInstall(current,bundled);
    }
}
