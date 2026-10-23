package com.naslink.android;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Nine-sliced approved brushwork; center pixels contain no screenshot labels. */
final class SketchSurface extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final int color;
    private final String surface;
    private int alpha=255;
    SketchSurface(float density,int color){this(density,color,"panel");}
    SketchSurface(float density,int color,String surface){this.color=color;this.surface=surface;}
    @Override public void draw(Canvas canvas){
        int resource=surface.equals("frame")?R.drawable.approved_frame:color==0xffdce9bc?R.drawable.approved_success:color==0xffffd34c?R.drawable.approved_button:R.drawable.approved_panel;
        Bitmap b=ApprovedArt.bitmap(resource);Rect bounds=getBounds();
        int cap=surface.equals("frame")?30:22;
        float scale=bounds.width()/(float)b.getWidth();
        float dx=Math.min(bounds.width()/3f,cap*scale),dy=Math.min(bounds.height()/3f,cap*scale);
        int[] sx={0,cap,b.getWidth()-cap,b.getWidth()},sy={0,cap,b.getHeight()-cap,b.getHeight()};
        float[] xx={bounds.left,bounds.left+dx,bounds.right-dx,bounds.right},yy={bounds.top,bounds.top+dy,bounds.bottom-dy,bounds.bottom};
        paint.setAlpha(alpha);
        if(!surface.equals("frame")&&color!=0xffdce9bc&&color!=0xffffd34c&&color!=0xfffffdf4&&color!=0xfffffbed)
            paint.setColorFilter(new PorterDuffColorFilter(color,PorterDuff.Mode.MULTIPLY));
        else paint.setColorFilter(null);
        for(int y=0;y<3;y++)for(int x=0;x<3;x++)canvas.drawBitmap(b,new Rect(sx[x],sy[y],sx[x+1],sy[y+1]),new RectF(xx[x],yy[y],xx[x+1],yy[y+1]),paint);
    }
    @Override public void setAlpha(int a){alpha=a;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
