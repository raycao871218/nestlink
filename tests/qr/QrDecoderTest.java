import com.naslink.android.QrDecoder;
import com.naslink.android.QrPayload;
import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.*;

public final class QrDecoderTest {
    static final String TEXT="harborlink://join?network=8056c2e21c000001&gateway=192.0.2.2&port=7890&moons=";
    public static void main(String[] args) throws Exception {
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);hints.put(EncodeHintType.MARGIN,4);
        BitMatrix matrix=new QRCodeWriter().encode(TEXT,BarcodeFormat.QR_CODE,480,480,hints);
        int n=matrix.getWidth();int[] pixels=new int[n*n];byte[] y=new byte[n*n];
        for(int row=0;row<n;row++) for(int col=0;col<n;col++){boolean dark=matrix.get(col,row);pixels[row*n+col]=dark?0xff000000:0xffffffff;y[row*n+col]=(byte)(dark?0:255);}
        for(int turn=0;turn<4;turn++) {
            if(!TEXT.equals(QrDecoder.image(pixels,n,n))||!TEXT.equals(QrDecoder.camera(y,n,n))) throw new AssertionError("Rotation "+turn);
            int[] next=new int[n*n];byte[] nextY=new byte[n*n];for(int row=0;row<n;row++) for(int col=0;col<n;col++){next[col*n+n-1-row]=pixels[row*n+col];nextY[col*n+n-1-row]=y[row*n+col];}pixels=next;y=nextY;
        }
        for(int k=0;k<y.length;k++) y[k]=(byte)(255-(y[k]&255));if(!TEXT.equals(QrDecoder.camera(y,n,n))) throw new AssertionError("Inverted");
        try {QrDecoder.camera(new byte[100],480,480);throw new AssertionError();}catch(NotFoundException expected){}
        try {QrDecoder.image(new int[480*480],480,480);throw new AssertionError();}catch(NotFoundException expected){}
        if(!QrPayload.parse(TEXT).gateway.equals("192.0.2.2")) throw new AssertionError();
        System.out.println("PASS camera/photo decode, all 4 rotations, inverted QR, missing and truncated input");
    }
}
