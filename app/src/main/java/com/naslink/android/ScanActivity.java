package com.naslink.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.Camera;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Camera and photo scanning stay on device; no Play Services or external scanner required. */
@SuppressWarnings("deprecation")
public final class ScanActivity extends Activity implements TextureView.SurfaceTextureListener {
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean();
    private TextureView preview;
    private TextView hint;
    private Camera camera;
    private boolean resumed,readingImage,finished;
    private int width,height,orientation;
    private long lastFrame;
    private final int paper=Color.rgb(255,248,240),mint=Color.rgb(66,109,89);
    @Override protected void attachBaseContext(Context base) {super.attachBaseContext(AppLanguage.wrap(base));}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);getWindow().setStatusBarColor(paper);getWindow().setNavigationBarColor(Build.VERSION.SDK_INT>=27?paper:0xff4c3c37);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(paper);
        TextView title=label(getString(R.string.ui_092_find_your_little_nest),23);title.setPadding(dp(24),dp(24),dp(24),dp(12));root.addView(title);
        hint=label(getString(R.string.ui_093_place_a_network_qr_code_in_the_frame_then),14);hint.setTextColor(0xff806c62);hint.setPadding(dp(24),0,dp(24),dp(18));root.addView(hint);
        FrameLayout view=new FrameLayout(this);preview=new TextureView(this);preview.setSurfaceTextureListener(this);view.addView(preview,new FrameLayout.LayoutParams(-1,-1));view.addView(new GuideView(),new FrameLayout.LayoutParams(-1,-1));root.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout buttons=new LinearLayout(this);buttons.setPadding(dp(18),dp(14),dp(18),dp(20));
        Button back=button(getString(R.string.ui_094_back));buttons.addView(back,new LinearLayout.LayoutParams(0,dp(54),1));back.setOnClickListener(v -> finish());
        Button photo=button(getString(R.string.ui_095_choose_a_photo));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(54),1);p.leftMargin=dp(10);buttons.addView(photo,p);photo.setOnClickListener(v -> pickPhoto());root.addView(buttons);
        setContentView(root);
        if(saved!=null) readingImage=saved.getBoolean("readingImage",false);
        if(saved==null && checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.CAMERA},61);
    }
    @Override protected void onSaveInstanceState(Bundle out) { out.putBoolean("readingImage",readingImage);super.onSaveInstanceState(out); }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {super.onConfigurationChanged(config);closeCamera();openCamera();}
    @Override protected void onResume() { super.onResume();resumed=true;openCamera(); }
    @Override protected void onPause() { resumed=false;closeCamera();super.onPause(); }
    @Override protected void onDestroy() { finished=true;closeCamera();decoder.shutdownNow();super.onDestroy(); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==61) {
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) openCamera();
            else hint.setText(getString(R.string.ui_096_camera_access_wasn_t_granted_you_can_still));
        }
    }
    private void openCamera() {
        if(!resumed||finished||readingImage||camera!=null||!preview.isAvailable()) return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { hint.setText(getString(R.string.ui_097_allow_camera_access_or_choose_a_photo_to_s));return; }
        try {
            int id=-1;Camera.CameraInfo info=new Camera.CameraInfo();
            for(int n=0;n<Camera.getNumberOfCameras();n++) {Camera.getCameraInfo(n,info);if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK) {id=n;break;} }
            if(id<0) throw new IOException("No rear camera");
            camera=Camera.open(id);Camera.Parameters parameters=camera.getParameters();
            Camera.Size best=null;
            for(Camera.Size size:parameters.getSupportedPreviewSizes()) if(best==null||Math.abs(size.width*size.height-640*480)<Math.abs(best.width*best.height-640*480)) best=size;
            if(best==null) throw new IOException("No preview size");
            width=best.width;height=best.height;parameters.setPreviewSize(width,height);parameters.setPreviewFormat(ImageFormat.NV21);
            java.util.List<String> focus=parameters.getSupportedFocusModes();
            if(focus!=null && focus.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);
            int rotation=getWindowManager().getDefaultDisplay().getRotation()*90;
            orientation=(info.orientation-rotation+360)%360;camera.setDisplayOrientation(orientation);
            camera.setPreviewTexture(preview.getSurfaceTexture());transform();
            camera.setPreviewCallback((bytes,source) -> frame(bytes,source));camera.startPreview();
            hint.setText(getString(R.string.ui_093_place_a_network_qr_code_in_the_frame_then));
        } catch(Exception e) { closeCamera();hint.setText(getString(R.string.ui_098_the_camera_is_unavailable_try_choosing_a_p)); }
    }
    private void transform() {
        if(preview.getWidth()==0||preview.getHeight()==0||width==0||height==0) return;
        float aspect=(orientation%180==0?(float)width/height:(float)height/width),view=(float)preview.getWidth()/preview.getHeight();
        Matrix matrix=new Matrix();matrix.setScale(aspect>view?aspect/view:1,aspect<view?view/aspect:1,preview.getWidth()/2f,preview.getHeight()/2f);preview.setTransform(matrix);
    }
    private void frame(byte[] bytes,Camera source) {
        if(finished||readingImage||SystemClock.elapsedRealtime()-lastFrame<250||!busy.compareAndSet(false,true)) return;
        lastFrame=SystemClock.elapsedRealtime();final int w=width,h=height;
        if(bytes==null||(long)w*h>bytes.length) {busy.set(false);return;}
        byte[] y=Arrays.copyOf(bytes,w*h);
        try { decoder.execute(() -> {
            try { String value=QrDecoder.camera(y,w,h);QrPayload.parse(value);runOnUiThread(() -> {if(resumed && source==camera && !readingImage) result(value);}); }
            catch(IllegalArgumentException e) {runOnUiThread(() -> {if(resumed) hint.setText(getString(R.string.ui_099_that_isn_t_a_supported_network_code_try_an));});}
            catch(Exception ignored) {}
            finally {busy.set(false);}
        }); } catch(RejectedExecutionException e) {busy.set(false);}
    }
    private void closeCamera() { Camera old=camera;camera=null;if(old!=null) {try {old.setPreviewCallback(null);old.stopPreview();}catch(Exception ignored){}old.release();} }
    private void pickPhoto() {
        if(finished||readingImage) return;readingImage=true;closeCamera();
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try {startActivityForResult(pick,62);} catch(ActivityNotFoundException e) {readingImage=false;hint.setText(getString(R.string.ui_100_no_image_picker_is_available_on_this_devic));openCamera();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(request!=62) return;
        if(result!=RESULT_OK||data==null||data.getData()==null) {readingImage=false;openCamera();return;}
        final Uri uri=data.getData();hint.setText(getString(R.string.ui_101_reading_your_photo_on_this_device));
        decoder.execute(() -> {
            Bitmap bitmap=null;
            try {
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                try(InputStream input=getContentResolver().openInputStream(uri)) {BitmapFactory.decodeStream(input,null,options);}
                if(options.outWidth<=0||options.outHeight<=0) throw new IOException("Invalid image");
                options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>2048) options.inSampleSize*=2;
                options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                try(InputStream input=getContentResolver().openInputStream(uri)) {bitmap=BitmapFactory.decodeStream(input,null,options);}
                if(bitmap==null) throw new IOException("No bitmap");
                int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
                String value=QrDecoder.image(pixels,bitmap.getWidth(),bitmap.getHeight());QrPayload.parse(value);
                runOnUiThread(() -> {readingImage=false;result(value);});
            } catch(Exception e) {runOnUiThread(() -> {readingImage=false;hint.setText(getString(R.string.ui_102_we_couldn_t_find_a_network_qr_code_in_that));openCamera();});}
            finally {if(bitmap!=null) bitmap.recycle();}
        });
    }
    private void result(String value) {if(finished||isDestroyed()) return;finished=true;closeCamera();setResult(RESULT_OK,new Intent().putExtra("qr",value));finish();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface,int width,int height) {openCamera();}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width,int height) {transform();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {closeCamera();return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
    private int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String text,int size) {TextView v=new TextView(this);v.setText(text);v.setTextColor(0xff4c3c37);v.setTextSize(size);return v;}
    private Button button(String text) {Button b=new Button(this);b.setText(text);b.setTextColor(mint);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffffe7dc));return b;}
    private final class GuideView extends View {
        private final Paint paint=new Paint(3);
        GuideView() {super(ScanActivity.this);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas) {float side=Math.min(getWidth(),getHeight())*.72f,l=(getWidth()-side)/2,t=(getHeight()-side)/2,r=l+side,b=t+side,c=dp(24);paint.setColor(0x990a131e);canvas.drawRect(0,0,getWidth(),t,paint);canvas.drawRect(0,b,getWidth(),getHeight(),paint);canvas.drawRect(0,t,l,b,paint);canvas.drawRect(r,t,getWidth(),b,paint);paint.setColor(mint);paint.setStrokeWidth(dp(3));for(float[] line:new float[][]{{l,t,l+c,t},{l,t,l,t+c},{r,t,r-c,t},{r,t,r,t+c},{l,b,l+c,b},{l,b,l,b-c},{r,b,r-c,b},{r,b,r,b-c}}) canvas.drawLine(line[0],line[1],line[2],line[3],paint);}
    }
}
