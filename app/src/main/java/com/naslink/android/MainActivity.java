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
    private Bundle draftSettings;
    private EditText editNetwork,editGateway,editPort,editMoons;
    private AlertDialog settingsDialog;
    private Button moonSummary;
    private AlertDialog moonDialog;
    private LinearLayout moonRows;
    private java.util.ArrayList<MoonSettings.Moon> visibleMoons;
    private String moonUiStamp="";
    private final Runnable refresh=new Runnable() { public void run() { render(); handler.postDelayed(this,1000); } };
    private SharedPreferences settings() { return getSharedPreferences("connection",0); }
    private SharedPreferences status() { return getSharedPreferences("status",0); }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if(saved!=null) draftSettings=saved.getBundle("draftSettings");
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
    @Override protected void onSaveInstanceState(Bundle out) {
        if(settingsDialog!=null && settingsDialog.isShowing()) captureDraft();
        out.putBundle("draftSettings",draftSettings);super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void render() {
        refreshMoonConnectivity();
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
        mode.setText(dev?"开发者模式 · HarborLink 0.5.1":"归港 · 私有连接，自在访问");
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
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==41 && result==RESULT_OK) startTunnel();
        if(request==63) {
            showSettings();
            if(result==RESULT_OK && data!=null) {
                try { confirmQr(QrPayload.parse(data.getStringExtra("qr"))); }
                catch(IllegalArgumentException e) {Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
            }
        }
    }
    private void captureDraft() {
        if(editNetwork==null) return;
        draftSettings=new Bundle();draftSettings.putString("network",editNetwork.getText().toString());
        draftSettings.putString("gateway",editGateway.getText().toString());draftSettings.putString("port",editPort.getText().toString());
        draftSettings.putString("moons",editMoons.getText().toString());
    }
    private void scanNetwork() {
        if(TunnelService.alive) {Toast.makeText(this,"请先断开连接，再扫码修改网络。",Toast.LENGTH_LONG).show();return;}
        captureDraft();settingsDialog.dismiss();startActivityForResult(new Intent(this,ScanActivity.class),63);
    }
    private void confirmQr(QrPayload payload) {
        String summary="ZeroTier 网络：\n"+payload.network;
        if(payload.gateway!=null) summary+="\n\nNAS 网关："+payload.gateway;
        if(payload.port!=null) summary+="\n代理端口："+payload.port;
        if(payload.moons!=null) summary+="\nMoon："+(payload.moons.isEmpty()?"无（将清空已有设置）":payload.moons);
        summary+="\n\n确认后填入设置，保存并连接即可入网。首次仍需管理端授权。";
        new AlertDialog.Builder(this).setTitle("识别到入网配置").setMessage(summary)
            .setPositiveButton("填入设置",(dialog,which) -> {
                if(TunnelService.alive) return;
                editNetwork.setText(payload.network);
                if(payload.gateway!=null) editGateway.setText(payload.gateway);
                if(payload.port!=null) editPort.setText(String.valueOf(payload.port));
                if(payload.moons!=null) editMoons.setText(payload.moons);
            }).setNegativeButton("取消",null).show();
    }
    private void showNetworkCode() {
        try {
            String value=QrPayload.encode(editNetwork.getText().toString().trim(),editGateway.getText().toString().trim(),Integer.parseInt(editPort.getText().toString().trim()),MoonSettings.format(MoonSettings.parse(editMoons.getText().toString())));
            java.util.Map<com.google.zxing.EncodeHintType,Object> hints=new java.util.EnumMap<>(com.google.zxing.EncodeHintType.class);
            hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET,"UTF-8");hints.put(com.google.zxing.EncodeHintType.MARGIN,3);
            com.google.zxing.common.BitMatrix matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(value,com.google.zxing.BarcodeFormat.QR_CODE,768,768,hints);
            Bitmap bitmap=Bitmap.createBitmap(768,768,Bitmap.Config.ARGB_8888);int[] pixels=new int[768*768];
            for(int y=0;y<768;y++) for(int x=0;x<768;x++) pixels[y*768+x]=matrix.get(x,y)?Color.BLACK:Color.WHITE;
            bitmap.setPixels(pixels,0,768,0,0,768,768);
            LinearLayout body=column();body.setPadding(dp(20),0,dp(20),dp(12));ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);image.setContentDescription("家庭网络入网二维码");body.addView(image,new LinearLayout.LayoutParams(-1,dp(270)));
            add(body,text("包含网络 ID、NAS 网关与 Moon 配置。请用另一台设备扫码；首次入网仍需管理端授权。",13,muted),12);
            new AlertDialog.Builder(this).setTitle("家庭网络入网码").setView(body).setPositiveButton("关闭",null).show();
        } catch(Exception e) {Toast.makeText(this,"请先填写有效的网络 ID、NAS 网关、端口与 Moon 设置。",Toast.LENGTH_LONG).show();}
    }
    private void showSettings() {
        ScrollView scroll=new ScrollView(this); LinearLayout body=column(); body.setPadding(dp(22),dp(8),dp(22),dp(16)); scroll.addView(body);
        add(body,text("连接你的家庭网络",14,muted),0);
        LinearLayout qrActions=new LinearLayout(this);
        Button scan=button("扫码入网",paper,mint),code=button("显示入网码",paper,ink);
        qrActions.addView(scan,new LinearLayout.LayoutParams(0,dp(50),1));
        LinearLayout.LayoutParams codeParams=new LinearLayout.LayoutParams(0,dp(50),1);codeParams.leftMargin=dp(8);qrActions.addView(code,codeParams);add(body,qrActions,14);
        scan.setEnabled(!TunnelService.alive);scan.setOnClickListener(v -> scanNetwork());code.setOnClickListener(v -> showNetworkCode());
        EditText network=editNetwork=field(body,"ZeroTier 网络 ID",settings().getString("network",""));
        EditText gateway=editGateway=field(body,"NAS 的 ZeroTier IPv4",settings().getString("gateway",""));
        EditText port=editPort=field(body,"NAS 代理端口",String.valueOf(settings().getInt("port",7890))); port.setInputType(2);
        EditText moons=editMoons=new EditText(this);moons.setText(settings().getString("moons",""));
        moonSummary=button("Moon 节点",paper,ink);add(body,moonSummary,18);moonSummary.setOnClickListener(v -> manageMoons());
        moons.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence value,int start,int count,int after) {}
            public void onTextChanged(CharSequence value,int start,int before,int count) {updateMoonSummary();}
            public void afterTextChanged(android.text.Editable value) {}
        });updateMoonSummary();
        if(draftSettings!=null) {
            network.setText(draftSettings.getString("network",""));gateway.setText(draftSettings.getString("gateway",""));
            port.setText(draftSettings.getString("port","7890"));moons.setText(draftSettings.getString("moons",""));draftSettings=null;
        }
        add(body,text("修改连接参数前请先断开。Moon 为可选配置。",12,muted),8);
        for(EditText field:new EditText[]{network,gateway,port,moons}) field.setEnabled(!TunnelService.alive);
        Switch developer=new Switch(this); developer.setText("开发者模式"); developer.setTextColor(ink); developer.setTextSize(15); developer.setChecked(settings().getBoolean("developer",false)); add(body,developer,22);
        add(body,text("开启后显示连接日志、节点路径与诊断工具。",12,muted),5);
        developer.setOnCheckedChangeListener((v,checked) -> { settings().edit().putBoolean("developer",checked).apply(); if(!checked) status().edit().remove("diagnostics").apply(); render(); });
        String id=status().getString("node","");
        if(!id.isEmpty()) { TextView identity=text("设备 ID："+id,12,muted); identity.setTextIsSelectable(true); add(body,identity,18); }
        add(body,text("HarborLink · 归港  0.5.1\n一个 VPN，连接内网与 NAS 代理出口。",12,muted),18);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("连接设置").setView(scroll).setPositiveButton("保存",null).setNegativeButton("关闭",null).create();
        settingsDialog=dialog;
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
    private boolean moonSnapshotFresh() {
        long checked=status().getLong("moonCheckedAt",0),age=SystemClock.elapsedRealtime()-checked;
        return TunnelService.alive && checked>0 && age>=0 && age<=15000;
    }
    private org.json.JSONObject moonInfo(MoonSettings.Moon moon) {
        if(!moonSnapshotFresh()) return null;
        try {
            org.json.JSONObject info=new org.json.JSONObject(status().getString("moonConnectivity","{}")).optJSONObject(Long.toUnsignedString(moon.world,16));
            return info!=null && Long.toUnsignedString(moon.seed,16).equals(info.optString("seed"))?info:null;
        } catch(org.json.JSONException e) {return null;}
    }
    private void updateMoonSummary() {
        if(moonSummary==null||editMoons==null) return;
        try {
            java.util.List<MoonSettings.Moon> nodes=MoonSettings.parse(editMoons.getText().toString());int online=0;
            for(MoonSettings.Moon moon:nodes) {org.json.JSONObject info=moonInfo(moon);if(info!=null && "online".equals(info.optString("state"))) online++;}
            String summary=nodes.isEmpty()?"未配置":nodes.size()+" 个";
            if(!nodes.isEmpty()&&moonSnapshotFresh()) summary+=" · "+online+" 可达";
            moonSummary.setText("Moon 节点 · "+summary+"  ›");
        } catch(IllegalArgumentException e) {moonSummary.setText("Moon 节点   ·   待检查   ›");}
    }
    private void refreshMoonConnectivity() {
        updateMoonSummary();
        if(moonDialog==null||!moonDialog.isShowing()||moonRows==null||visibleMoons==null) return;
        String stamp=TunnelService.alive+"/"+moonSnapshotFresh()+"/"+status().getString("moonConnectivity","")+"/"+settings().getBoolean("developer",false);
        if(!stamp.equals(moonUiStamp)) {moonUiStamp=stamp;renderMoonRows(moonRows,visibleMoons);}
    }
    private void addMoonConnectivity(LinearLayout info,MoonSettings.Moon moon) {
        String label="未连接";int color=muted;
        if(TunnelService.alive) {
            org.json.JSONObject item=moonInfo(moon);
            if(item==null) label="检测中…";
            else switch(item.optString("state")) {
                case "online": int delay=item.optInt("latency",-1);label="● 连接正常"+(delay>0?" · "+delay+" ms":" · 延迟待测");color=mint;break;
                case "no_path": label="○ 暂无可用路径";color=amber;break;
                case "unconfirmed": label="○ 尚未确认为 Moon";break;
                case "waiting": label="○ 等待当前网络回包";break;
                default: label="○ 尚未发现";
            }
        }
        add(info,text(label,12,color),4);
    }
    private void manageMoons() {
        java.util.List<MoonSettings.Moon> existing;
        try {existing=MoonSettings.parse(editMoons.getText().toString());}
        catch(IllegalArgumentException e) {Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();return;}
        final java.util.ArrayList<MoonSettings.Moon> nodes=new java.util.ArrayList<>(existing);
        ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(dp(22),dp(6),dp(22),dp(14));scroll.addView(body);
        add(body,text("连接时自动更新节点路径与延迟。",13,muted),0);
        LinearLayout list=column();add(body,list,14);
        renderMoonRows(list,nodes);
        EditText addId=field(body,"添加节点","");addId.setHint("Moon ID");addId.setEnabled(!TunnelService.alive);
        EditText seed=null;
        if(settings().getBoolean("developer",false)) {seed=field(body,"Seed ID（可选）","");seed.setEnabled(!TunnelService.alive);}
        final EditText seedInput=seed;
        Button add=button("添加 Moon",paper,mint);add(body,add,12);add.setEnabled(!TunnelService.alive);
        add.setOnClickListener(v -> {
            try {
                String entry=addId.getText().toString().trim();
                if(seedInput!=null&&!seedInput.getText().toString().trim().isEmpty()) entry+=" "+seedInput.getText().toString().trim();
                if(entry.isEmpty()) throw new IllegalArgumentException("请填写 Moon ID。");
                java.util.List<MoonSettings.Moon> parsed=MoonSettings.parse(MoonSettings.format(nodes)+"\n"+entry);
                nodes.clear();nodes.addAll(parsed);editMoons.setText(MoonSettings.format(nodes));addId.setText("");if(seedInput!=null) seedInput.setText("");
                renderMoonRows(list,nodes);
                ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(addId.getWindowToken(),0);
            } catch(IllegalArgumentException e) {Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
        });
        add(body,text(TunnelService.alive?"连接中仅可查看；修改前请先断开。":"添加或移除后，保存连接设置即可生效。",12,muted),12);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Moon 节点").setView(scroll).setPositiveButton("完成",null).create();
        moonDialog=dialog;moonRows=list;visibleMoons=nodes;moonUiStamp="";
        dialog.setOnDismissListener(v -> {if(moonDialog==dialog) {moonDialog=null;moonRows=null;visibleMoons=null;}});
        dialog.show();refreshMoonConnectivity();
    }
    private void renderMoonRows(LinearLayout list,java.util.ArrayList<MoonSettings.Moon> nodes) {
        list.removeAllViews();
        if(nodes.isEmpty()) {add(list,text("尚未添加 Moon 节点",14,muted),8);return;}
        for(MoonSettings.Moon moon:new java.util.ArrayList<>(nodes)) {
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(7),0,dp(7));
            LinearLayout info=column();TextView label=text(moon.displayId(),15,ink);label.setTypeface(Typeface.MONOSPACE);add(info,label,0);
            addMoonConnectivity(info,moon);
            if(settings().getBoolean("developer",false)) add(info,text("World: "+Long.toUnsignedString(moon.world,16)+"\nSeed: "+String.format(java.util.Locale.ROOT,"%010x",moon.seed),11,muted),4);
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button remove=button("移除",paper,muted);remove.setTextSize(12);row.addView(remove,new LinearLayout.LayoutParams(dp(62),dp(42)));remove.setEnabled(!TunnelService.alive);
            remove.setOnClickListener(v -> {nodes.remove(moon);editMoons.setText(MoonSettings.format(nodes));renderMoonRows(list,nodes);});add(list,row,0);
        }
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
