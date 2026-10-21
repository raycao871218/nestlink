package com.naslink.android;

import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.*;

/** Shared, offline QR decoder for camera frames, picked images and JVM tests. */
public final class QrDecoder {
    private QrDecoder() {}
    public static String decode(LuminanceSource source) throws NotFoundException {
        Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET,"UTF-8");
        for(int attempt=0;attempt<2;attempt++) {
            try { return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(attempt==0?source:source.invert())),hints).getText(); }
            catch(ReaderException ignored) {}
        }
        throw NotFoundException.getNotFoundInstance();
    }
    public static String camera(byte[] y,int width,int height) throws NotFoundException {
        if(width<1||height<1||(long)width*height>y.length) throw NotFoundException.getNotFoundInstance();
        return decode(new PlanarYUVLuminanceSource(y,width,height,0,0,width,height,false));
    }
    public static String image(int[] pixels,int width,int height) throws NotFoundException {
        return decode(new RGBLuminanceSource(width,height,pixels));
    }
}
