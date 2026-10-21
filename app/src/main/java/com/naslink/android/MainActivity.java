package com.naslink.android;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Everyday controls stay separate from opt-in diagnostics. Health is owned by the service. */
public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final int paper=Color.rgb(10,19,30), card=Color.rgb(20,34,49), ink=Color.rgb(239,246,250);
    private final int muted=Color.rgb(147,167,185), mint=Color.rgb(113,231,203), amber=Color.rgb(255,198,120);
    private TextView title,subtitle,lan,internet,profile,device,diagnostics,mode;
    private LinearLayout developerPanel;
    private Button action;
    private HarborView harbor;
    private final Runnable refresh=new Runnable() { public void run() { render(); handler.postDelayed(this,1000); } };
    private SharedPreferences settings() { return getSharedPreferences("connection",0); }
    private SharedPreferences status() { return getSharedPreferences("status",0); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(paper); getWindow().setNavigationBarColor(paper);
        // Preserve an earlier install's explicitly configured proxy port.
        if(!settings().contains("port")) {
            int port=7890;
            try { port=Integer.parseInt(getPreferences(0).getString("port","7890")); } catch(NumberFormatException ignored) {}
            settings().edit().putInt("port",port).apply();
        }
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(paper);
        LinearLayout root=column(); root.setPadding(dp(24),dp(18),dp(24),dp(28)); scroll.addView(root);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("H  /  HARBORLINK",14,mint); brand.setLetterSpacing(.12f); brand.setTypeface(null,Typeface.BOLD);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button settings=button("设置",card,ink); settings.setTextSize(13); header.addView(settings,new LinearLayout.LayoutParams(dp(72),dp(44))); settings.setOnClickListener(v -> showSettings());
        add(root,header,0);
        add(root,text("一个连接，回到家。",28,ink),26);
        add(root,text("内网与互联网，都在这里。",14,muted),7);
        harbor=new HarborView(); LinearLayout.LayoutParams hero=new LinearLayout.LayoutParams(-1,dp(184)); hero.topMargin=dp(20); root.addView(harbor,hero);
        title=text("尚未连接",26,ink); title.setGravity(Gravity.CENTER); title.setTypeface(null,Typeface.BOLD); add(root,title,6);
        subtitle=text("准备好后，轻触下方开始。",14,muted); subtitle.setGravity(Gravity.CENTER); add(root,subtitle,8);
        action=button("连接回家",mint,paper); add(root,action,24); action.setOnClickListener(v -> {
            if(TunnelService.alive) startService(new Intent(this,TunnelService.class).setAction(TunnelService.STOP));
            else connect();
        });
        LinearLayout paths=panel(); add(root,paths,20);
        lan=pathRow(paths,"家中网络","待连接"); internet=pathRow(paths,"互联网出口","待连接");
        LinearLayout destination=panel(); add(root,destination,12);
        add(destination,text("我的连接",12,muted),0); profile=text("尚未设置家庭网络",15,ink); add(destination,profile,6);
        device=text("",13,amber); device.setTextIsSelectable(true); add(root,device,12);
        mode=text("归港 · 私有连接，自在访问",12,muted); mode.setGravity(Gravity.CENTER); add(root,mode,22);
        developerPanel=panel(); add(root,developerPanel,18);
        add(developerPanel,text("开发者诊断",15,mint),0);
        Button retry=button("重新检测连接",paper,ink); add(developerPanel,retry,8); retry.setOnClickListener(v -> {
            if(TunnelService.alive) startService(new Intent(this,TunnelService.class).setAction(TunnelService.RECHECK));
        });
        diagnostics=text("",12,muted); diagnostics.setTextIsSelectable(true); diagnostics.setTypeface(Typeface.MONOSPACE); add(developerPanel,diagnostics,12);
        setContentView(scroll); render();
    }
    private TextView pathRow(LinearLayout parent,String label,String value) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,dp(7),0,dp(7));
        row.addView(text(label,14,ink),new LinearLayout.LayoutParams(0,-2,1)); TextView result=text(value,13,muted); row.addView(result); add(parent,row,0); return result;
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(refresh); refresh.run(); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void render() {
        boolean active=TunnelService.alive;
        SharedPreferences s=status(); String phase=active?s.getString("phase","joining"):"disconnected";
        boolean ok=active && phase.equals("connected");
        boolean busy=active && (phase.equals("joining")||phase.equals("validating")||phase.equals("reconnecting"));
        String heading="尚未连接", description="准备好后，轻触下方开始。";
        if(active) {
            switch(phase) {
                case "connected": heading="已连接"; description="家中网络与互联网均已验证。"; break;
                case "validating": heading="正在确认连接"; description="自动检测家中网络与互联网出口…"; break;
                case "authorization": heading="等待设备授权"; description="请在 ZeroTier 管理端授权下方设备。"; break;
                case "degraded": heading="连接待恢复"; description="部分连接未通过检测，正在自动重试。"; break;
                case "reconnecting": heading="正在重新连接"; description="网络发生变化，正在恢复连接…"; break;
                default: heading="正在连接回家"; description="首次连接需要在管理端授权设备。";
            }
        } else if(s.getString("message","").matches(".*(失败|错误|撤销).*")) {
            description="连接已停止，请重试或在开发者模式查看原因。";
        }
        title.setText(heading); title.setTextColor(ok?mint:ink); subtitle.setText(description);
        harbor.update(ok,busy,phase.equals("degraded")||phase.equals("authorization"));
        action.setText(active?(busy?"取消连接":"断开连接"):"连接回家");
        action.setBackgroundTintList(ColorStateList.valueOf(active?card:mint)); action.setTextColor(active?ink:paper);
        setPath(lan,active,s.getBoolean("lanReady",false),phase);
        setPath(internet,active,s.getBoolean("internetReady",false),phase);
        String network=settings().getString("network","");
        profile.setText(network.isEmpty()?"尚未设置家庭网络":"家庭网络 · 已配置");
        String node=s.getString("node","");
        device.setVisibility(active && (phase.equals("authorization")||phase.equals("joining")) && !node.isEmpty()?View.VISIBLE:View.GONE);
        device.setText("设备 ID："+node+"  ·  供管理端授权");
        boolean dev=settings().getBoolean("developer",false); developerPanel.setVisibility(dev?View.VISIBLE:View.GONE);
        mode.setText(dev?"开发者模式 · HarborLink 0.4.0":"归港 · 私有连接，自在访问");
        if(dev) diagnostics.setText("状态："+s.getString("message","")+"\n设备："+node+"\n地址："+s.getString("address","")+"\n"+s.getString("healthLog","")+"\n"+s.getString("moons","")+"\n"+s.getString("diagnostics",""));
        else diagnostics.setText("");
    }
    private void setPath(TextView view,boolean active,boolean ready,String phase) {
        view.setText(!active?"待连接":ready?"●  已验证":phase.equals("degraded")?"○  待恢复":"○  检测中");
        view.setTextColor(active && ready?mint:active && phase.equals("degraded")?amber:muted);
    }
    private void connect() {
        if(settings().getString("network","").isEmpty()||settings().getString("gateway","").isEmpty()) { showSettings(); return; }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},42); return;
        }
        requestVpn();
    }
    private void requestVpn() { Intent permission=VpnService.prepare(this); if(permission!=null) startActivityForResult(permission,41); else startTunnel(); }
    private void startTunnel() { startForegroundService(new Intent(this,TunnelService.class)); handler.postDelayed(this::render,250); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) { super.onRequestPermissionsResult(request,permissions,results); if(request==42) requestVpn(); }
    @Override protected void onActivityResult(int request,int result,Intent data) { super.onActivityResult(request,result,data); if(request==41 && result==RESULT_OK) startTunnel(); }
    private void showSettings() {
        ScrollView scroll=new ScrollView(this); LinearLayout body=column(); body.setPadding(dp(22),dp(8),dp(22),dp(16)); scroll.addView(body);
        add(body,text("连接你的家庭网络",14,muted),0);
        EditText network=field(body,"ZeroTier 网络 ID",settings().getString("network",""));
        EditText gateway=field(body,"NAS 的 ZeroTier IPv4",settings().getString("gateway",""));
        EditText port=field(body,"NAS 代理端口",String.valueOf(settings().getInt("port",7890))); port.setInputType(2);
        EditText moons=field(body,"Moon（可选，每行一个 ID）",settings().getString("moons","")); moons.setSingleLine(false); moons.setMinLines(2); moons.setGravity(Gravity.TOP);
        add(body,text("Moon 可填写 World ID 或 World ID + Seed ID。修改网络参数前请先断开。",12,muted),8);
        for(EditText field:new EditText[]{network,gateway,port,moons}) field.setEnabled(!TunnelService.alive);
        Switch developer=new Switch(this); developer.setText("开发者模式"); developer.setTextColor(ink); developer.setTextSize(15); developer.setChecked(settings().getBoolean("developer",false)); add(body,developer,22);
        add(body,text("开启后显示连接日志、节点路径与诊断工具。",12,muted),5);
        developer.setOnCheckedChangeListener((v,checked) -> { settings().edit().putBoolean("developer",checked).apply(); if(!checked) status().edit().remove("diagnostics").apply(); render(); });
        String id=status().getString("node","");
        if(!id.isEmpty()) { TextView identity=text("设备 ID："+id,12,muted); identity.setTextIsSelectable(true); add(body,identity,18); }
        add(body,text("HarborLink · 归港  0.4.0\n一个 VPN，连接内网与 NAS 代理出口。",12,muted),18);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("连接设置").setView(scroll).setPositiveButton("保存",null).setNegativeButton("关闭",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            if(TunnelService.alive) { dialog.dismiss(); return; }
            try {
                String net=network.getText().toString().trim(); if(!net.matches("[0-9a-fA-F]{16}")||net.equals("0000000000000000")) throw new IllegalArgumentException("网络 ID 应为非零的 16 位十六进制。");
                String nas=gateway.getText().toString().trim(); ConnectionSettings.ipv4(nas);
                int number=Integer.parseInt(port.getText().toString().trim()); if(number<1||number>65535) throw new IllegalArgumentException("端口范围为 1–65535。");
                String moon=MoonSettings.format(MoonSettings.parse(moons.getText().toString()));
                settings().edit().putString("network",net).putString("gateway",nas).putInt("port",number).putString("moons",moon).apply(); dialog.dismiss(); render();
            } catch(Exception e) { Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show(); }
        })); dialog.show();
    }
    private EditText field(LinearLayout parent,String label,String value) {
        add(parent,text(label,12,muted),16); EditText input=new EditText(this); input.setSingleLine(true); input.setTextSize(15); input.setTextColor(ink); input.setHintTextColor(muted); input.setHint(label); input.setText(value); input.setInputType(1|0x80000); input.setPadding(dp(12),dp(10),dp(12),dp(10)); input.setBackground(background(paper,12)); add(parent,input,6); return input;
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout panel() { LinearLayout v=column(); v.setPadding(dp(18),dp(14),dp(18),dp(14)); v.setBackground(background(card,18)); return v; }
    private GradientDrawable background(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView text(String value,int size,int color) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(2),1); return t; }
    private Button button(String value,int color,int foreground) { Button b=new Button(this); b.setText(value); b.setTextSize(16); b.setAllCaps(false); b.setTextColor(foreground); b.setMinHeight(dp(54)); b.setBackground(background(color,16)); b.setStateListAnimator(null); return b; }
    private void add(LinearLayout parent,View child,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(top); parent.addView(child,p); }
    private final class HarborView extends View {
        private final Paint paint=new Paint(3); private boolean connected,busy,warning;
        HarborView() { super(MainActivity.this); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void update(boolean c,boolean b,boolean w) { if(c!=connected||b!=busy||w!=warning) { connected=c;busy=b;warning=w;invalidate(); } }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float cx=getWidth()/2f,cy=getHeight()/2f,r=dp(69); int accent=warning?amber:mint;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(Color.rgb(28,53,67));canvas.drawCircle(cx,cy,r+dp(17),paint);
            paint.setColor(Color.rgb(35,64,76));paint.setStrokeWidth(dp(2));canvas.drawCircle(cx,cy,r,paint);
            if(connected||busy) { paint.setColor(accent); paint.setStrokeWidth(dp(3)); paint.setStrokeCap(Paint.Cap.ROUND); float rotation=busy?(SystemClock.uptimeMillis()%2400)*360f/2400:-90; canvas.drawArc(cx-r,cy-r,cx+r,cy+r,rotation,connected?360:95,false,paint); }
            paint.setStyle(Paint.Style.FILL);paint.setColor(card);canvas.drawCircle(cx,cy,r-dp(10),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(connected?mint:ink);
            Path home=new Path();home.moveTo(cx-dp(25),cy-dp(2));home.lineTo(cx,cy-dp(24));home.lineTo(cx+dp(25),cy-dp(2));home.moveTo(cx-dp(19),cy-dp(5));home.lineTo(cx-dp(19),cy+dp(23));home.lineTo(cx+dp(19),cy+dp(23));home.lineTo(cx+dp(19),cy-dp(5));canvas.drawPath(home,paint);
            canvas.drawRoundRect(cx-dp(5),cy+dp(6),cx+dp(5),cy+dp(23),dp(2),dp(2),paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(accent);canvas.drawCircle(cx+dp(56),cy+dp(43),dp(6),paint);
            if(busy) postInvalidateDelayed(40);
        }
    }
}
