package com.naslink.android;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.*;
import java.util.HashMap;

/** Exact approved illustration fragments, used inside real native controls. */
final class ApprovedArt {
    private static Resources resources;
    private static final HashMap<Integer,Bitmap> images=new HashMap<>();
    static void initialize(Resources r){resources=r;}
    static Bitmap bitmap(int id){Bitmap b=images.get(id);if(b==null){b=BitmapFactory.decodeResource(resources,id);images.put(id,b);}return b;}
    static int textArt(String value){
        switch(value){
            case "家庭网络":case "家中网络":return R.drawable.approved_family;
            case "互联网出口":return R.drawable.approved_internet;
            case "可用":return R.drawable.approved_available;
            case "当前网络":return R.drawable.approved_current;
            case "家庭 NAS":return R.drawable.approved_home_nas;
            case "通过 ZeroTier 安全连接":return R.drawable.approved_profile_hint;
            case "连接指南":case "连接指南  ›":case "连接指南  ⌄":return R.drawable.approved_guide;
            case "配置、授权与故障排查":return R.drawable.approved_guide_hint;
            case "断开连接":return R.drawable.approved_disconnect;
            case "连接":return R.drawable.approved_connect;
            default:return 0;
        }
    }
    static int glyphArt(String kind){
        switch(kind){
            case "gear":return R.drawable.approved_gear;
            case "power":return R.drawable.approved_power;
            case "book":return R.drawable.approved_book;
            case "nas":return R.drawable.approved_nas;
            case "ready":return R.drawable.approved_ready;
            case "chevron":return R.drawable.approved_chevron;
            default:return 0;
        }
    }
    static void fit(Canvas canvas,int id,RectF area,int gravity,Paint paint){
        Bitmap b=bitmap(id);float s=Math.min(area.width()/b.getWidth(),area.height()/b.getHeight());
        float w=b.getWidth()*s,h=b.getHeight()*s;
        float x=(gravity&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.CENTER_HORIZONTAL?area.centerX()-w/2:area.left;
        canvas.drawBitmap(b,null,new RectF(x,area.centerY()-h/2,x+w,area.centerY()+h/2),paint);
    }
    static boolean drawLabel(Canvas c,TextView v){
        // Large accessibility text and arbitrary/dynamic strings retain native layout.
        if(resources.getConfiguration().fontScale>1.2f)return false;
        String value=v.getText().toString();int id=textArt(value);
        boolean profile=value.equals("家庭 NAS\n通过 ZeroTier 安全连接");
        if(id==0&&!profile)return false;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);p.setAlpha(v.isEnabled()?255:100);
        int left=v.getCompoundPaddingLeft(),right=v.getWidth()-v.getCompoundPaddingRight();
        RectF area=new RectF(left,v.getPaddingTop(),right,v.getHeight()-v.getPaddingBottom());
        if(value.equals("已连接"))area=new RectF(0,0,v.getWidth(),v.getHeight());
        else if(!profile){float h=Math.min(area.height(),v.getTextSize()*1.35f);area.top=area.centerY()-h/2;area.bottom=area.top+h;}
        Drawable d=v.getCompoundDrawables()[0];
        if(d!=null&&!value.equals("已连接")){c.save();c.translate(v.getPaddingLeft(),(v.getHeight()-d.getBounds().height())/2f);d.draw(c);c.restore();}
        Drawable rightIcon=v.getCompoundDrawables()[2];
        if(rightIcon!=null){c.save();c.translate(v.getWidth()-v.getPaddingRight()-rightIcon.getBounds().width(),(v.getHeight()-rightIcon.getBounds().height())/2f);if(value.endsWith("⌄"))c.rotate(90,rightIcon.getBounds().width()/2f,rightIcon.getBounds().height()/2f);rightIcon.draw(c);c.restore();}
        if(profile){
            float mid=area.top+area.height()*.52f;
            fit(c,R.drawable.approved_home_nas,new RectF(area.left,area.top,area.right,mid),Gravity.LEFT,p);
            fit(c,R.drawable.approved_profile_hint,new RectF(area.left,mid,area.right,area.bottom),Gravity.LEFT,p);
        }else fit(c,id,area,v.getGravity(),p);
        return true;
    }
    static final class Label extends TextView {
        Label(Context c){super(c);}
        @Override protected void onDraw(Canvas c){if(!drawLabel(c,this))super.onDraw(c);}
    }
    static class Action extends Button {
        Action(Context c){super(c);}
        @Override protected void onDraw(Canvas c){if(!drawLabel(c,this))super.onDraw(c);}
    }
}
