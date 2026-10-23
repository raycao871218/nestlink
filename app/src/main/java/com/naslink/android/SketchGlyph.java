package com.naslink.android;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small hand-inked utility symbols; labels and touch targets remain native. */
final class SketchGlyph extends Drawable {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String kind;
    private int alpha=255;
    private final int size;
    SketchGlyph(float density,String kind,int dp) {this.kind=kind;size=Math.round(density*dp);}
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    private void line(Canvas c,float x,float y,float xx,float yy){c.drawLine(x,y,xx,yy,p);}
    @Override public void draw(Canvas c) {
        int art=ApprovedArt.glyphArt(kind);
        if(art!=0){Paint image=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);image.setAlpha(alpha);ApprovedArt.fit(c,art,new RectF(getBounds()),android.view.Gravity.CENTER_HORIZONTAL,image);return;}
        c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/32f,getBounds().height()/32f);
        p.setColor(0xff4c2718);p.setAlpha(alpha);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.3f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(kind) {
            case "power": c.drawArc(5,5,27,28,-48,278,false,p);line(c,16,3,15.6f,16);break;
            case "gear": {
                Path q=new Path();for(int i=0;i<32;i++){double a=i*Math.PI/16;float r=(i%4==0||i%4==3)?14:11.7f;float x=16+(float)Math.cos(a)*r,y=16+(float)Math.sin(a)*r;if(i==0)q.moveTo(x,y);else q.lineTo(x,y);}q.close();c.drawPath(q,p);c.drawCircle(16,16,4.8f,p);break;
            }
            case "book": {Path q=new Path();q.moveTo(3,6);q.cubicTo(7,4,12,5,16,8);q.cubicTo(21,4,26,4,29,6);q.lineTo(29,27);q.cubicTo(24,25,20,25,16,28);q.cubicTo(12,25,7,25,3,27);q.close();c.drawPath(q,p);line(c,16,8,16,28);break;}
            case "qr": c.drawRoundRect(3,3,12,12,1,1,p);c.drawRoundRect(20,3,29,12,1,1,p);c.drawRoundRect(3,20,12,29,1,1,p);line(c,21,20,27,20);line(c,20,27,20,29);line(c,28,26,28,29);break;
            case "nas": c.drawRoundRect(4,3,28,29,5,4,p);line(c,12,10,12,19);line(c,20,10,20,19);c.drawCircle(12,24,0.8f,p);c.drawCircle(20,24,0.8f,p);break;
            case "ready": p.setStyle(Paint.Style.FILL);p.setColor(0xffbfd487);c.drawCircle(16,16,13,p);p.setStyle(Paint.Style.STROKE);p.setColor(0xff354715);p.setStrokeWidth(3);line(c,9,16,14,21);line(c,14,21,23,11);break;
            case "warning": p.setColor(0xffba613d);c.drawCircle(16,16,12,p);line(c,16,9,16,17);c.drawCircle(16,23,0.5f,p);break;
            default: p.setColor(0xffb0a38b);c.drawCircle(16,16,12,p);line(c,10,16,22,16);
        }
        c.restore();
    }
    @Override public void setAlpha(int a){alpha=a;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
