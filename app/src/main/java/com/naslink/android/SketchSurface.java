package com.naslink.android;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** A stable hand-inked surface. The contour never changes between redraws. */
final class SketchSurface extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final int color;
    private int alpha = 255;
    SketchSurface(float density, int color) { this.density=density; this.color=color; }
    private Path contour(RectF r, float drift) {
        float x=r.left,y=r.top,w=r.width(),h=r.height(),k=Math.min(12*density,Math.min(w,h)/4);
        Path p=new Path();
        p.moveTo(x+k,y+drift);
        p.cubicTo(x+w*.30f,y-1.2f*density,x+w*.67f,y+2*density,x+w-k,y);
        p.quadTo(x+w+drift,y,x+w,y+k);
        p.cubicTo(x+w-1.5f*density,y+h*.35f,x+w+drift,y+h*.67f,x+w-1*density,y+h-k);
        p.quadTo(x+w,y+h+drift,x+w-k,y+h);
        p.cubicTo(x+w*.66f,y+h-1*density,x+w*.32f,y+h+1.3f*density,x+k,y+h-1*density);
        p.quadTo(x-drift,y+h,x,y+h-k);
        p.cubicTo(x+1.5f*density,y+h*.64f,x-drift,y+h*.32f,x,y+k);
        p.quadTo(x,y-1*density,x+k,y+drift);p.close();return p;
    }
    @Override public void draw(Canvas canvas) {
        RectF r=new RectF(getBounds());r.inset(4*density,4*density);
        if(r.width()<=0 || r.height()<=0)return;
        Path path=contour(r,.7f*density);
        paint.setColor(color);paint.setAlpha(alpha);paint.setStyle(Paint.Style.FILL);canvas.drawPath(path,paint);
        // An uneven translucent marker pass near the edge, clipped to the paper.
        canvas.save();canvas.clipPath(path);
        paint.setColor(0x0b323a40);paint.setAlpha(Math.round(alpha*.045f));paint.setStrokeWidth(5*density);paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(contour(r,2*density),paint);
        if(color!=0xfffffdf4) {
            paint.setColor(0xffffffff);paint.setAlpha(Math.round(alpha*.11f));paint.setStrokeWidth(1.3f*density);paint.setStrokeCap(Paint.Cap.ROUND);
            int strokes=Math.min(96,(int)(r.width()/(9*density)));
            for(int i=0;i<strokes;i++) {float x=r.left+i*9*density;float y=r.top+((i*17)%37)*density;canvas.drawLine(x,y,x+12*density,y-8*density,paint);}
        }
        canvas.restore();
        paint.setColor(0xff4c2718);paint.setAlpha(alpha);paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(2.3f*density);canvas.drawPath(path,paint);
        paint.setStrokeWidth(.65f*density);paint.setAlpha(Math.round(alpha*.55f));canvas.drawPath(contour(r,1.8f*density),paint);
    }
    @Override public void setAlpha(int value) { alpha=value;invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
