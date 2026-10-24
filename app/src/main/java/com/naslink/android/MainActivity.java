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
    private final int paper=Color.rgb(255,251,237), card=Color.rgb(255,253,244), ink=Color.rgb(76,39,24);
    private final int muted=Color.rgb(126,107,85), mint=Color.rgb(89,106,48), amber=Color.rgb(150,97,34);
    private final int peach=Color.rgb(255,211,76);
    private Typeface inkTypeface;
    private Typeface networkTypeface;
    private TextView networkNameHeading;
    private ProgressBar connectionProgress;
    private boolean restoreLanguageSettings;
    private TextView title,subtitle,lan,internet,lanLabel,device,diagnostics,mode;
    private LinearLayout developerPanel;
    private Button action;
    private Button copyDevice;
    private Bundle draftSettings;
    private EditText editNetwork,editGateway,editPort,editMoons,editHomeSsid,editHomeNas,editHomePort;
    private Switch homeEnabled;
    private TextView homeNetworkInfo;
    private AlertDialog settingsDialog;
    private String customPlanet="",planetRevision="";
    private TextView planetSummary;
    private Button moonSummary;
    private AlertDialog moonDialog;
    private LinearLayout moonRows;
    private java.util.ArrayList<MoonSettings.Moon> visibleMoons;
    private String moonUiStamp="";
    private final Runnable refresh=new Runnable() { public void run() { render(); handler.postDelayed(this,1000); } };
    private SharedPreferences settings() { return getSharedPreferences("connection",0); }
    private SharedPreferences status() { return getSharedPreferences("status",0); }
    @Override protected void attachBaseContext(Context base) {super.attachBaseContext(AppLanguage.wrap(base));}
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ApprovedArt.initialize(getResources());
        inkTypeface="zh".equals(settings().getString("language","en"))?getResources().getFont(R.font.zcool_kuaile):Typeface.create("sans-serif",Typeface.NORMAL);
        networkTypeface="zh".equals(settings().getString("language","en"))?getResources().getFont(R.font.rounded_status):inkTypeface;
        if(saved!=null) {draftSettings=saved.getBundle("draftSettings");}
        getWindow().setStatusBarColor(paper); getWindow().setNavigationBarColor(Build.VERSION.SDK_INT>=27?paper:ink);
        // Preserve an earlier install's explicitly configured proxy port.
        if(!settings().contains("port")) {
            int port=7890;
            try { port=Integer.parseInt(getPreferences(0).getString("port","7890")); } catch(NumberFormatException ignored) {}
            settings().edit().putInt("port",port).apply();
        }
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(paper);scroll.setPadding(dp(16),dp(8),dp(16),dp(10));scroll.setClipToPadding(false);
        LinearLayout root=column(); root.setPadding(dp(20),dp(14),dp(20),dp(18));root.setBackground(new SketchSurface(getResources().getDisplayMetrics().density,paper,"frame")); scroll.addView(root);
        if(getResources().getConfiguration().screenWidthDp>=700) root.setLayoutParams(new android.widget.FrameLayout.LayoutParams(dp(640),-2,Gravity.CENTER_HORIZONTAL));
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge=new ImageView(this);badge.setImageResource(R.drawable.approved_chicken);badge.setScaleType(ImageView.ScaleType.FIT_CENTER);badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams badgeLayout=new LinearLayout.LayoutParams(dp(74),dp(80));badgeLayout.rightMargin=dp(10);header.addView(badge,badgeLayout);
        if("zh".equals(settings().getString("language","en"))) {
            ImageView brand=new ImageView(this);brand.setImageResource(R.drawable.approved_brand);brand.setScaleType(ImageView.ScaleType.FIT_START);brand.setContentDescription(getString(R.string.app_name));
            header.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));
        } else {
            TextView brand=text(getString(R.string.app_name),30,ink);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        }
        Button settings=button("",paper,ink);settings.setContentDescription(getString(R.string.ui_001_settings));settings.setBackground(null);settings.setCompoundDrawablesWithIntrinsicBounds(glyph("gear",30),null,null,null);header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48))); settings.setOnClickListener(v -> showSettings());
        add(root,header,0);
        root.addView(new Space(this),new LinearLayout.LayoutParams(1,0,1));
        if("zh".equals(settings().getString("language","en"))) {
            ImageView heading=new ImageView(this);heading.setImageResource(R.drawable.approved_heading);heading.setScaleType(ImageView.ScaleType.FIT_CENTER);heading.setContentDescription(getString(R.string.ui_002_home_is_a_tap_away));
            LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(78));hp.topMargin=dp(18);root.addView(heading,hp);
        } else {
            TextView headline=text(getString(R.string.ui_002_home_is_a_tap_away),30,ink);headline.setMaxLines(2);add(root,headline,18);
        }
        LinearLayout connectionState=new LinearLayout(this);connectionState.setGravity(Gravity.CENTER);add(root,connectionState,12);
        connectionProgress=new ProgressBar(this);connectionProgress.setIndeterminate(true);connectionProgress.setIndeterminateTintList(ColorStateList.valueOf(ink));connectionProgress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);connectionState.addView(connectionProgress,new LinearLayout.LayoutParams(dp(24),dp(24)));
        title=text(getString(R.string.ui_004_ready_when_you_are),21,ink); title.setGravity(Gravity.CENTER); title.setTypeface(inkTypeface);connectionState.addView(title);
        title.setPadding(dp(16),dp(8),dp(16),dp(8)); title.setBackground(background(0xffeee8da,8));
        LinearLayout.LayoutParams titleLayout=new LinearLayout.LayoutParams(-2,dp(70));titleLayout.gravity=Gravity.CENTER_HORIZONTAL;title.setMinWidth(dp(150));title.setLayoutParams(titleLayout);title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        subtitle=text(getString(R.string.ui_005_your_little_nest_is_just_one_tap_away),14,muted); subtitle.setGravity(Gravity.CENTER); add(root,subtitle,8);
        action=primaryButton(getString(R.string.ui_006_let_s_go_home));action.setTextSize(30);action.setMinHeight(dp(100));action.setPadding(dp(36),0,dp(36),0);add(root,action,16); action.setOnClickListener(v -> {
            if(TunnelService.alive) startService(new Intent(this,TunnelService.class).setAction(TunnelService.STOP));
            else connect();
        });
        LinearLayout paths=panel(); add(root,paths,14);
        root.addView(new Space(this),root.indexOfChild(paths),new LinearLayout.LayoutParams(1,0,1));
        lan=pathRow(paths,getString(R.string.ui_007_home_network),getString(R.string.ui_008_not_connected));
        View divider=new View(this);divider.setBackgroundColor(0xffe5dcc5);paths.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        internet=pathRow(paths,getString(R.string.ui_009_internet),getString(R.string.ui_008_not_connected));
        device=text("",13,amber); device.setTextIsSelectable(true); add(root,device,12);
        copyDevice=button(getString(R.string.copy_device_id),card,mint); add(root,copyDevice,8);
        copyDevice.setOnClickListener(v -> {
            String node=status().getString("node","");
            if(!node.isEmpty()) { ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ZeroTier device ID",node)); Toast.makeText(this,R.string.device_id_copied,Toast.LENGTH_SHORT).show(); }
        });
        mode=text(getString(R.string.ui_012_one_little_tap_a_little_closer_to_home),12,muted); mode.setGravity(Gravity.CENTER); add(root,mode,22);
        developerPanel=panel(); add(root,developerPanel,18);
        add(developerPanel,text(getString(R.string.ui_013_developer_tools),15,mint),0);
        Button retry=button(getString(R.string.ui_014_check_connection_again),paper,ink); add(developerPanel,retry,8); retry.setOnClickListener(v -> {
            if(TunnelService.alive) startService(new Intent(this,TunnelService.class).setAction(TunnelService.RECHECK));
        });
        diagnostics=text("",12,muted); diagnostics.setTextIsSelectable(true); diagnostics.setTypeface(Typeface.MONOSPACE); add(developerPanel,diagnostics,12);
        setContentView(scroll); render();
        if(saved!=null && saved.getBoolean("restoreSettings",false)) handler.post(this::showSettings);
    }
    private TextView pathRow(LinearLayout parent,String label,String value) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,0,0,0);
        TextView labelView=statusText(label);if(lanLabel==null)lanLabel=labelView;row.addView(labelView,new LinearLayout.LayoutParams(0,-2,1)); TextView result=statusText(value); result.setPadding(dp(10),dp(6),dp(10),dp(6)); row.addView(result); add(parent,row,0); return result;
    }
    private TextView statusText(String value) {
        TextView view=new TextView(this);view.setText(value);view.setTextColor(ink);view.setTextSize(20);view.setTypeface(networkTypeface);view.setLineSpacing(dp(2),1);return view;
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(refresh); refresh.run(); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(settingsDialog!=null && settingsDialog.isShowing()) captureDraft();
        out.putBundle("draftSettings",draftSettings);out.putBoolean("restoreSettings",restoreLanguageSettings || (settingsDialog!=null && settingsDialog.isShowing()));super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void render() {
        refreshMoonConnectivity();
        updateNetworkNameHeading();
        boolean active=TunnelService.alive;
        SharedPreferences s=status(); String phase=active?s.getString("phase","joining"):"disconnected";
        boolean ok=active && phase.equals("connected");
        boolean busy=active && (phase.equals("joining")||phase.equals("validating")||phase.equals("reconnecting"));
        String heading=getString(R.string.ui_004_ready_when_you_are), description=getString(R.string.ui_005_your_little_nest_is_just_one_tap_away);
        if(active) {
            switch(phase) {
                case "connected": heading=getString(R.string.ui_015_home_sweet_home); description=getString(R.string.ui_016_home_network_and_internet_are_both_ready); break;
                case "validating": heading=getString(R.string.ui_017_making_sure_it_s_cozy); description=getString(R.string.ui_018_checking_your_home_network_and_internet); break;
                case "authorization": heading=getString(R.string.ui_019_waiting_for_approval); description=getString(R.string.ui_020_approve_this_device_in_your_zerotier_contr); break;
                case "degraded": heading=getString(R.string.ui_021_a_little_bump_on_the_way); description=getString(R.string.ui_022_something_isn_t_ready_yet_we_ll_keep_tryin); break;
                case "reconnecting": heading=getString(R.string.ui_023_finding_our_way_back); description=getString(R.string.ui_024_your_network_changed_reconnecting_for_you); break;
                default: heading=getString(R.string.ui_025_heading_home); description=getString(R.string.ui_026_first_visit_approve_this_device_in_your_co);
            }
        } else if(s.getString("message","").matches(".*(失败|错误|撤销).*")) {
            description=getString(R.string.ui_027_the_connection_stopped_try_again_or_check);
        }
        String homeError=s.getString("homeError","");
        if(!active && !homeError.isEmpty()) description=getString("subnet".equals(homeError)?R.string.home_subnet_error:R.string.home_failed);
        if(active && phase.equals("degraded") && s.getBoolean("homeMode",false)) description=getString(R.string.home_degraded);
        title.setText(heading);title.setCompoundDrawables(null,null,null,null);title.setTextColor(ink);title.setBackground(ok?background(0xffdce9bc,20):null);
        connectionProgress.setVisibility(active&&(busy||phase.equals("degraded"))?View.VISIBLE:View.GONE);
        subtitle.setText(description);subtitle.setVisibility((!active && homeError.isEmpty() || ok)?View.GONE:View.VISIBLE);
        boolean authorization=active && (phase.equals("authorization")||phase.equals("joining"));
        copyDevice.setVisibility(authorization && !s.getString("node","").isEmpty()?View.VISIBLE:View.GONE);
        action.setText(active?(busy?getString(R.string.ui_028_cancel_connection):getString(R.string.ui_029_disconnect)):getString(R.string.ui_006_let_s_go_home));
        action.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x22304455),background(peach,12),null)); action.setTextColor(ink);
        setPath(lan,active,s.getBoolean("lanReady",false),phase);
        setPath(internet,active,s.getBoolean("internetReady",false),phase);
        String network=settings().getString("network","");
        String nameKey="network_name_"+network.toLowerCase(java.util.Locale.ROOT);
        String networkName=network.equalsIgnoreCase(s.getString("networkNameId",""))?s.getString("networkName","").trim():"";
        if(networkName.isEmpty())networkName=settings().getString(nameKey,"");
        else if(!networkName.equals(settings().getString(nameKey,"")))settings().edit().putString(nameKey,networkName).apply();
        lanLabel.setText(networkName.isEmpty()?getString(R.string.ui_007_home_network):networkName);
        lanLabel.setTypeface(networkTypeface);lanLabel.setMaxLines(2);lanLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);lanLabel.setTooltipText(networkName);
        lanLabel.setContentDescription(networkName.isEmpty()?getString(R.string.ui_007_home_network):"ZeroTier · "+networkName);
        String node=s.getString("node","");
        device.setVisibility(active && (phase.equals("authorization")||phase.equals("joining")) && !node.isEmpty()?View.VISIBLE:View.GONE);
        device.setText(getString(R.string.device_id,node));
        boolean dev=settings().getBoolean("developer",false); developerPanel.setVisibility(dev?View.VISIBLE:View.GONE);
        mode.setVisibility(dev || settings().getBoolean("homeEnabled",false)?View.VISIBLE:View.GONE);
        mode.setText(dev?getString(R.string.ui_033_developer_mode_nestlink_0_6_0):getString(R.string.ui_012_one_little_tap_a_little_closer_to_home));
        if(settings().getBoolean("homeEnabled",false)) mode.setText(s.getBoolean("homeMode",false) && active?R.string.home_path:R.string.home_away_path);
        if(dev) diagnostics.setText(getString(R.string.ui_034_status)+s.getString("message","")+getString(R.string.ui_035_device)+node+getString(R.string.ui_036_address)+s.getString("address","")+"\n"+s.getString("healthLog","")+"\n"+s.getString("moons","")+"\n"+s.getString("diagnostics",""));
        else diagnostics.setText("");
    }
    private void setPath(TextView view,boolean active,boolean ready,String phase) {
        view.setText(!active?getString(R.string.ui_008_not_connected):ready?getString(R.string.path_available):phase.equals("degraded")?getString(R.string.ui_038_retrying):getString(R.string.ui_039_checking));
        view.setTextColor(ink);view.setBackground(null);view.setCompoundDrawablesWithIntrinsicBounds(active&&ready?null:glyph(active&&phase.equals("degraded")?"warning":"idle",30),null,null,null);view.setCompoundDrawablePadding(dp(10));
    }
    private void connect() {
        if(settings().getString("network","").isEmpty()||settings().getString("gateway","").isEmpty()) { showSettings(); return; }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},42); return;
        }
        requestVpn();
    }
    private void requestVpn() {
        if(settings().getBoolean("homeEnabled",false) && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION},44);return;
        }
        launchVpn();
    }
    private void launchVpn() { Intent permission=VpnService.prepare(this); if(permission!=null) startActivityForResult(permission,41); else startTunnel(); }
    private void startTunnel() { startForegroundService(new Intent(this,TunnelService.class)); handler.postDelayed(this::render,250); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) { super.onRequestPermissionsResult(request,permissions,results); if(request==42) requestVpn(); if(request==44) launchVpn(); if(request==43 && homeNetworkInfo!=null) readHomeNetwork(); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==41 && result==RESULT_OK) startTunnel();
        if(request==64) {
            if(result==RESULT_OK && data!=null && data.getData()!=null) {
                try {
                    if(TunnelService.alive) throw new IllegalStateException(getString(R.string.planet_disconnect));
                    byte[] bytes;
                    try(java.io.InputStream input=getContentResolver().openInputStream(data.getData())) { bytes=PlanetSettings.read(input); }
                    if(draftSettings==null) draftSettings=new Bundle();
                    draftSettings.putString("customPlanet",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP));
                    draftSettings.putString("planetRevision",java.util.UUID.randomUUID().toString());
                } catch(Exception e) { Toast.makeText(this,getString(R.string.planet_invalid),Toast.LENGTH_LONG).show(); }
            }
            showSettings();
        }
        if(request==63) {
            showSettings();
            if(result==RESULT_OK && data!=null) {
                try { confirmQr(QrPayload.parse(data.getStringExtra("qr"))); }
                catch(IllegalArgumentException e) {Toast.makeText(this,AppLanguage.inputError(this,e),Toast.LENGTH_LONG).show();}
            }
        }
    }
    private void captureDraft() {
        if(editNetwork==null) return;
        draftSettings=new Bundle();draftSettings.putString("customPlanet",customPlanet);draftSettings.putString("planetRevision",planetRevision);draftSettings.putString("network",editNetwork.getText().toString());
        draftSettings.putString("gateway",editGateway.getText().toString());draftSettings.putString("port",editPort.getText().toString());
        draftSettings.putString("moons",editMoons.getText().toString());
        if(editHomeSsid!=null) { draftSettings.putString("homeSsid",editHomeSsid.getText().toString()); draftSettings.putString("homeNas",editHomeNas.getText().toString()); draftSettings.putString("homePort",editHomePort.getText().toString()); draftSettings.putBoolean("homeEnabled",homeEnabled.isChecked()); }
    }
    private void scanNetwork() {
        captureDraft();settingsDialog.dismiss();startActivityForResult(new Intent(this,ScanActivity.class),63);
    }
    private void confirmQr(QrPayload payload) {
        String summary=getString(R.string.ui_041_zerotier_network)+payload.network;
        if(payload.gateway!=null) summary+=getString(R.string.ui_042_nas_gateway)+payload.gateway;
        if(payload.port!=null) summary+=getString(R.string.ui_043_proxy_port)+payload.port;
        if(payload.moons!=null) summary+="\nMoon："+(payload.moons.isEmpty()?getString(R.string.ui_044_none_clears_saved_moons):payload.moons);
        summary+=getString(R.string.ui_045_import_these_settings_then_save_and_connec);
        new AlertDialog.Builder(this).setTitle(getString(R.string.ui_046_found_your_network)).setMessage(summary)
            .setPositiveButton(getString(TunnelService.alive?R.string.scan_disconnect_apply:R.string.ui_047_use_these_settings),(dialog,which) -> {
                if(!TunnelService.alive) { fillQr(payload); return; }
                captureDraft();settingsDialog.dismiss();
                startService(new Intent(this,TunnelService.class).setAction(TunnelService.STOP));
                handler.post(new Runnable() {
                    private int attempts;
                    public void run() {
                        if(isFinishing()||isDestroyed())return;
                        if(TunnelService.alive) {
                            if(++attempts<50)handler.postDelayed(this,100);
                            else {showSettings();Toast.makeText(MainActivity.this,R.string.scan_stop_failed,Toast.LENGTH_LONG).show();}
                            return;
                        }
                        showSettings();fillQr(payload);
                    }
                });
            }).setNegativeButton(getString(R.string.ui_048_cancel),null).show();
    }
    private void fillQr(QrPayload payload) {
        if(TunnelService.alive)return;
        editNetwork.setText(payload.network);
        if(payload.gateway!=null)editGateway.setText(payload.gateway);
        if(payload.port!=null)editPort.setText(String.valueOf(payload.port));
        if(payload.moons!=null)editMoons.setText(payload.moons);
    }
    private String connectionHelp() {
        String phase=status().getString("phase","");
        if(TunnelService.alive&&(phase.equals("authorization")||phase.equals("joining")))return getString(R.string.guide_authorization);
        if(TunnelService.alive&&phase.equals("degraded"))return getString(R.string.guide_lan)+"\n\n"+getString(R.string.guide_internet)+"\n\n"+getString(R.string.guide_recovery);
        return getString(R.string.guide_steps);
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
            LinearLayout body=column();body.setPadding(dp(20),0,dp(20),dp(12));ImageView image=new ImageView(this);image.setImageBitmap(bitmap);image.setAdjustViewBounds(true);image.setContentDescription(getString(R.string.ui_049_home_network_qr_code));body.addView(image,new LinearLayout.LayoutParams(-1,dp(270)));
            add(body,text(getString(R.string.ui_050_scan_with_another_device_to_copy_the_netwo),13,muted),12);
            new AlertDialog.Builder(this).setTitle(getString(R.string.ui_051_share_your_little_nest)).setView(body).setPositiveButton(getString(R.string.ui_052_close),null).show();
        } catch(Exception e) {Toast.makeText(this,getString(R.string.ui_053_enter_valid_network_gateway_port_and_moon),Toast.LENGTH_LONG).show();}
    }
    private void chooseLanguage() {
        Dialog dialog=new Dialog(this);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body=column();body.setPadding(dp(24),dp(24),dp(24),dp(20));body.setBackground(background(paper,16));
        TextView heading=text("Language / 语言",23,ink);heading.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));add(body,heading,0);
        for(String selected:new String[]{"en","zh"}) {
            boolean chosen=selected.equals(settings().getString("language","en"));
            Button option=button((selected.equals("en")?"English":"简体中文")+(chosen?"  ·":""),chosen?peach:card,ink);option.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));option.setTextSize(20);option.setSelected(chosen);add(body,option,14);
            option.setOnClickListener(v -> {
                if(selected.equals(settings().getString("language","en"))) {dialog.dismiss();return;}
                captureDraft();restoreLanguageSettings=true;settings().edit().putString("language",selected).apply();dialog.dismiss();
                if(TunnelService.alive) startService(new Intent(this,TunnelService.class).setAction(TunnelService.LOCALIZE));
                recreate();
            });
        }
        Button close=button(getString(R.string.ui_052_close),paper,muted);add(body,close,20);close.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(body);dialog.show();
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(380)),-2);}
    }
    private void showSettings() {
        ScrollView scroll=new ScrollView(this); LinearLayout body=column(); body.setPadding(dp(20),dp(4),dp(20),dp(16)); scroll.addView(body);
        LinearLayout languageRow=new LinearLayout(this);languageRow.setGravity(Gravity.CENTER_VERTICAL);
        languageRow.addView(text(getString(R.string.language),19,ink),new LinearLayout.LayoutParams(0,-2,1));
        Button language=button(("zh".equals(settings().getString("language","en"))?"简体中文":"English")+"  ⌄",card,ink);
        languageRow.addView(language,new LinearLayout.LayoutParams(dp(230),dp(48)));add(body,languageRow,4);language.setOnClickListener(v -> chooseLanguage());
        Button guide=button(getString(R.string.guide_title)+"  ›",paper,ink);
        guide.setCompoundDrawablesWithIntrinsicBounds(glyph("book",24),null,null,null);guide.setCompoundDrawablePadding(dp(12));guide.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        guide.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(R.string.guide_title).setMessage(connectionHelp()+"\n\n"+getString(R.string.ui_061_disconnect_to_edit_your_connection_moon_he)+"\n\n"+getString(R.string.ui_063_show_connection_logs_peer_paths_and_diagno)).setPositiveButton(R.string.ui_052_close,null).show());
        LinearLayout qrActions=new LinearLayout(this);
        Button scan=button(getString(R.string.ui_055_scan_to_join),paper,mint),code=button(getString(R.string.ui_056_show_qr_code),paper,ink);
        qrActions.addView(scan,new LinearLayout.LayoutParams(0,dp(50),1));
        LinearLayout.LayoutParams codeParams=new LinearLayout.LayoutParams(0,dp(50),1);codeParams.leftMargin=dp(8);qrActions.addView(code,codeParams);add(body,qrActions,14);
        scan.setCompoundDrawablesWithIntrinsicBounds(glyph("qr",25),null,null,null);code.setCompoundDrawablesWithIntrinsicBounds(glyph("qr",25),null,null,null);scan.setCompoundDrawablePadding(dp(8));code.setCompoundDrawablePadding(dp(8));scan.setOnClickListener(v -> scanNetwork());code.setOnClickListener(v -> showNetworkCode());
        EditText network=editNetwork=field(body,getString(R.string.ui_057_zerotier_network_id),settings().getString("network",""),true);
        updateNetworkNameHeading();
        network.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence value,int start,int count,int after) {}
            public void onTextChanged(CharSequence value,int start,int before,int count) {updateNetworkNameHeading();}
            public void afterTextChanged(android.text.Editable value) {}
        });
        EditText gateway=editGateway=field(body,getString(R.string.ui_058_nas_zerotier_ipv4_address),settings().getString("gateway",""));
        EditText port=editPort=field(body,getString(R.string.ui_059_nas_proxy_port),String.valueOf(settings().getInt("port",7890))); port.setInputType(2);
        EditText moons=editMoons=new EditText(this);moons.setText(settings().getString("moons",""));
        moonSummary=button(getString(R.string.ui_060_moon_helpers),paper,ink);add(body,moonSummary,14);moonSummary.setOnClickListener(v -> manageMoons());
        moons.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence value,int start,int count,int after) {}
            public void onTextChanged(CharSequence value,int start,int before,int count) {updateMoonSummary();}
            public void afterTextChanged(android.text.Editable value) {}
        });updateMoonSummary();
        customPlanet=draftSettings!=null?draftSettings.getString("customPlanet",settings().getString("customPlanet","")):settings().getString("customPlanet","");
        planetRevision=draftSettings!=null?draftSettings.getString("planetRevision",settings().getString("planetRevision","")):settings().getString("planetRevision","");
        planetSummary=text("",13,muted);add(body,planetSummary,16);updatePlanetSummary();
        Button importPlanet=button(getString(R.string.planet_import),paper,ink);
        Button resetPlanet=button(getString(R.string.planet_reset),paper,ink);
        add(body,importPlanet,8);add(body,resetPlanet,8);
        add(body,text(getString(R.string.planet_help),12,muted),8);
        importPlanet.setEnabled(!TunnelService.alive);resetPlanet.setEnabled(!TunnelService.alive);
        importPlanet.setOnClickListener(v -> {
            if(TunnelService.alive) return;
            captureDraft();settingsDialog.dismiss();
            try { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),64); }
            catch(ActivityNotFoundException e) { showSettings();Toast.makeText(this,R.string.planet_picker_unavailable,Toast.LENGTH_LONG).show(); }
        });
        resetPlanet.setOnClickListener(v -> { if(!TunnelService.alive) {customPlanet="";planetRevision=java.util.UUID.randomUUID().toString();updatePlanetSummary();} });
        homeEnabled=new Switch(this);homeEnabled.setText(R.string.home_enable);homeEnabled.setTextColor(ink);homeEnabled.setTextSize(19);
        homeEnabled.setChecked(settings().getBoolean("homeEnabled",false));add(body,homeEnabled,18);
        homeNetworkInfo=text(getString(R.string.home_permission_help),13,muted);add(body,homeNetworkInfo,8);
        Button readNetwork=button(getString(R.string.home_read_network),paper,ink);add(body,readNetwork,8);
        readNetwork.setEnabled(!TunnelService.alive);
        readNetwork.setOnClickListener(v -> {
            if(checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,android.Manifest.permission.ACCESS_FINE_LOCATION},43);
            } else readHomeNetwork();
        });
        editHomeSsid=field(body,getString(R.string.home_ssid),settings().getString("homeSsid",""));
        editHomeNas=field(body,getString(R.string.home_nas_address),settings().getString("homeNas",""));
        editHomePort=field(body,getString(R.string.home_port),String.valueOf(settings().getInt("homePort",7890)));editHomePort.setInputType(2);
        add(body,text(getString(R.string.home_help),13,muted),10);
        homeEnabled.setEnabled(!TunnelService.alive);
        for(EditText field:new EditText[]{editHomeSsid,editHomeNas,editHomePort}) field.setEnabled(!TunnelService.alive);
        if(draftSettings!=null) {
            homeEnabled.setChecked(draftSettings.getBoolean("homeEnabled",settings().getBoolean("homeEnabled",false)));
            editHomeSsid.setText(draftSettings.getString("homeSsid",settings().getString("homeSsid","")));
            editHomeNas.setText(draftSettings.getString("homeNas",settings().getString("homeNas","")));
            editHomePort.setText(draftSettings.getString("homePort",String.valueOf(settings().getInt("homePort",7890))));
            network.setText(draftSettings.getString("network",""));gateway.setText(draftSettings.getString("gateway",""));
            port.setText(draftSettings.getString("port","7890"));moons.setText(draftSettings.getString("moons",""));draftSettings=null;
        }

        for(EditText field:new EditText[]{network,gateway,port,moons}) field.setEnabled(!TunnelService.alive);
        Switch developer=new Switch(this); developer.setText(getString(R.string.ui_062_developer_mode)); developer.setTextColor(ink); developer.setTextSize(19);developer.setTypeface(inkTypeface); developer.setChecked(settings().getBoolean("developer",false));developer.setPadding(dp(16),dp(12),dp(16),dp(12));developer.setBackground(background(card,12)); add(body,developer,14);

        developer.setOnCheckedChangeListener((v,checked) -> { settings().edit().putBoolean("developer",checked).apply(); if(!checked) status().edit().remove("diagnostics").apply(); render(); });
        add(body,guide,16);
        add(body,text(getString(R.string.ui_026_first_visit_approve_this_device_in_your_co),12,muted),10);
        add(body,text(getString(R.string.ui_064_nestlink_0_6_0_one_vpn_for_your_home_netwo),11,muted),10);
        LinearLayout settingsHeader=new LinearLayout(this);settingsHeader.setGravity(Gravity.CENTER_VERTICAL);settingsHeader.setPadding(dp(18),dp(12),dp(18),dp(8));
        Button back=button("‹",paper,ink);back.setTextSize(32);back.setBackground(null);back.setContentDescription(getString(R.string.ui_052_close));settingsHeader.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView heading=text(getString(R.string.ui_065_your_nest_settings),24,ink);heading.setGravity(Gravity.CENTER);heading.setTypeface(inkTypeface);settingsHeader.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button save=button(getString(R.string.ui_066_save),peach,ink);settingsHeader.addView(save,new LinearLayout.LayoutParams(dp(76),dp(48)));
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(settingsHeader).setView(scroll).setPositiveButton(getString(R.string.ui_066_save),null).setNegativeButton(getString(R.string.ui_052_close),null).create();
        back.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        settingsDialog=dialog;
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            if(TunnelService.alive) { dialog.dismiss(); return; }
            try {
                String net=network.getText().toString().trim(); if(!net.matches("[0-9a-fA-F]{16}")||net.equals("0000000000000000")) throw new IllegalArgumentException(getString(R.string.ui_067_enter_a_non_zero_16_character_hexadecimal));
                String nas=gateway.getText().toString().trim(); ConnectionSettings.ipv4(nas);
                int number=Integer.parseInt(port.getText().toString().trim()); if(number<1||number>65535) throw new IllegalArgumentException(getString(R.string.ui_068_choose_a_port_from_1_to_65535));
                String moon=MoonSettings.format(MoonSettings.parse(moons.getText().toString()));
                String homeSsid=editHomeSsid.getText().toString(),homeNas=editHomeNas.getText().toString().trim();
                int homePort=homeEnabled.isChecked()?Integer.parseInt(editHomePort.getText().toString().trim()):settings().getInt("homePort",7890);
                HomeNetworkSettings.validate(homeEnabled.isChecked(),homeSsid,homeNas,homePort);
                settings().edit().putBoolean("homeEnabled",homeEnabled.isChecked()).putString("homeSsid",homeSsid).putString("homeNas",homeNas).putInt("homePort",homePort)
                    .putString("customPlanet",customPlanet).putString("planetRevision",planetRevision).putString("network",net).putString("gateway",nas).putInt("port",number).putString("moons",moon).apply(); dialog.dismiss(); render();
            } catch(Exception e) { Toast.makeText(this,AppLanguage.inputError(this,e),Toast.LENGTH_LONG).show(); }
        })); dialog.show();
        ((View)dialog.getButton(AlertDialog.BUTTON_POSITIVE).getParent()).setVisibility(View.GONE);
        if(dialog.getWindow()!=null) {dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setDimAmount(0f);dialog.getWindow().setStatusBarColor(paper);dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(paper));}
    }
    private void updatePlanetSummary() {
        planetSummary.setText(customPlanet.isEmpty()?getString(R.string.planet_builtin):getString(R.string.planet_custom)+" · SHA-256 "+BootstrapPolicy.hash(android.util.Base64.decode(customPlanet,android.util.Base64.DEFAULT)).substring(0,12));
    }
    private void readHomeNetwork() {
        android.net.ConnectivityManager cm=getSystemService(android.net.ConnectivityManager.class);
        StringBuilder info=new StringBuilder(); boolean found=false;
        for(android.net.Network network:cm.getAllNetworks()) {
            android.net.NetworkCapabilities caps=cm.getNetworkCapabilities(network);
            if(caps==null || !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) continue;
            found=true;String name=CurrentNetworkReader.ssid(this,network);
            info.append(getString(R.string.home_ssid)).append(": ").append(name.isEmpty()?getString(R.string.home_ssid_unavailable):name).append("\n");
            if(!name.isEmpty() && !TunnelService.alive) {
                if(editHomeSsid.getText().length()==0) editHomeSsid.setText(name);
                else if(!name.equals(editHomeSsid.getText().toString())) new AlertDialog.Builder(this).setTitle(R.string.home_use_name).setMessage(name)
                    .setPositiveButton(R.string.home_use_name,(dialog,which) -> { if(!TunnelService.alive) editHomeSsid.setText(name); })
                    .setNegativeButton(android.R.string.cancel,null).show();
            }
            android.net.LinkProperties links=cm.getLinkProperties(network);
            if(links==null) continue;
            for(android.net.LinkAddress ip:links.getLinkAddresses()) if(ip.getAddress() instanceof java.net.Inet4Address) {
                int mask=ip.getPrefixLength()==0?0:-1 << (32-ip.getPrefixLength());
                String dotted=((mask>>>24)&255)+"."+((mask>>>16)&255)+"."+((mask>>>8)&255)+"."+(mask&255);
                info.append(getString(R.string.home_local_ip)).append(": ").append(ip.getAddress().getHostAddress()).append("\n").append(getString(R.string.home_mask)).append(": ").append(dotted).append("\n");
            }
            for(android.net.RouteInfo route:links.getRoutes()) if(route.isDefaultRoute() && route.getGateway() instanceof java.net.Inet4Address)
                info.append(getString(R.string.home_gateway)).append(": ").append(route.getGateway().getHostAddress()).append("\n");
        }
        homeNetworkInfo.setText(found?info.toString()+getString(R.string.home_permission_help):getString(R.string.home_no_wifi));
    }
    private boolean moonSnapshotFresh() {
        long checked=status().getLong("moonCheckedAt",0),age=SystemClock.elapsedRealtime()-checked;
        return TunnelService.alive && checked>0 && age>=0 && age<=15000;
    }
    private void updateNetworkNameHeading() {
        if(networkNameHeading==null||editNetwork==null)return;
        String name=settings().getString("network_name_"+editNetwork.getText().toString().trim().toLowerCase(java.util.Locale.ROOT),"");
        networkNameHeading.setText(getString(R.string.ui_057_zerotier_network_id)+(name.isEmpty()?"":" · "+name));
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
            String summary=nodes.isEmpty()?getString(R.string.ui_069_not_set_up):getResources().getQuantityString(R.plurals.moon_config_count,nodes.size(),nodes.size());
            if(!nodes.isEmpty()&&moonSnapshotFresh()) summary+=" · "+getString(R.string.moon_reachable_count,online);
            moonSummary.setText(getString(R.string.ui_060_moon_helpers)+" · "+summary+"  ›");
        } catch(IllegalArgumentException e) {moonSummary.setText(getString(R.string.ui_073_moon_helpers_check_settings));}
    }
    private void refreshMoonConnectivity() {
        updateMoonSummary();
        if(moonDialog==null||!moonDialog.isShowing()||moonRows==null||visibleMoons==null) return;
        String stamp=TunnelService.alive+"/"+moonSnapshotFresh()+"/"+status().getString("moonConnectivity","")+"/"+settings().getBoolean("developer",false);
        if(!stamp.equals(moonUiStamp)) {moonUiStamp=stamp;renderMoonRows(moonRows,visibleMoons);}
    }
    private void addMoonConnectivity(LinearLayout info,MoonSettings.Moon moon) {
        String label=getString(R.string.ui_074_not_connected);int color=muted;
        if(TunnelService.alive) {
            org.json.JSONObject item=moonInfo(moon);
            if(item==null) label=getString(R.string.ui_075_checking);
            else switch(item.optString("state")) {
                case "online": int delay=item.optInt("latency",-1);label=getString(R.string.ui_076_reachable)+(delay>0?" · "+delay+" ms":getString(R.string.ui_077_measuring_latency));color=mint;break;
                case "no_path": label=getString(R.string.ui_078_no_recent_reply);color=amber;break;
                case "unconfirmed": label=getString(R.string.ui_079_not_confirmed_as_a_moon);break;
                case "waiting": label=getString(R.string.ui_080_waiting_for_a_fresh_reply);break;
                default: label=getString(R.string.ui_081_not_discovered_yet);
            }
        }
        add(info,text(label,12,color),4);
    }
    private void manageMoons() {
        java.util.List<MoonSettings.Moon> existing;
        try {existing=MoonSettings.parse(editMoons.getText().toString());}
        catch(IllegalArgumentException e) {Toast.makeText(this,AppLanguage.inputError(this,e),Toast.LENGTH_LONG).show();return;}
        final java.util.ArrayList<MoonSettings.Moon> nodes=new java.util.ArrayList<>(existing);
        ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(dp(22),dp(6),dp(22),dp(14));scroll.addView(body);
        add(body,text(getString(R.string.ui_082_helper_status_and_latency_update_while_con),13,muted),0);
        LinearLayout list=column();add(body,list,14);
        renderMoonRows(list,nodes);
        EditText addId=field(body,getString(R.string.ui_083_add_a_helper),"");addId.setHint("Moon ID");addId.setEnabled(!TunnelService.alive);
        EditText seed=null;
        if(settings().getBoolean("developer",false)) {seed=field(body,getString(R.string.ui_084_seed_id_optional),"");seed.setEnabled(!TunnelService.alive);}
        final EditText seedInput=seed;
        Button add=button(getString(R.string.ui_085_add_moon),paper,mint);add(body,add,12);add.setEnabled(!TunnelService.alive);
        add.setOnClickListener(v -> {
            try {
                String entry=addId.getText().toString().trim();
                if(seedInput!=null&&!seedInput.getText().toString().trim().isEmpty()) entry+=" "+seedInput.getText().toString().trim();
                if(entry.isEmpty()) throw new IllegalArgumentException(getString(R.string.ui_086_enter_a_moon_id));
                java.util.List<MoonSettings.Moon> parsed=MoonSettings.parse(MoonSettings.format(nodes)+"\n"+entry);
                nodes.clear();nodes.addAll(parsed);editMoons.setText(MoonSettings.format(nodes));addId.setText("");if(seedInput!=null) seedInput.setText("");
                renderMoonRows(list,nodes);
                ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(addId.getWindowToken(),0);
            } catch(IllegalArgumentException e) {Toast.makeText(this,AppLanguage.inputError(this,e),Toast.LENGTH_LONG).show();}
        });
        add(body,text(TunnelService.alive?getString(R.string.ui_087_you_can_look_around_while_connected_discon):getString(R.string.ui_088_save_your_nest_settings_to_apply_these_cha),12,muted),12);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(getString(R.string.ui_060_moon_helpers)).setView(scroll).setPositiveButton(getString(R.string.ui_089_done),null).create();
        moonDialog=dialog;moonRows=list;visibleMoons=nodes;moonUiStamp="";
        dialog.setOnDismissListener(v -> {if(moonDialog==dialog) {moonDialog=null;moonRows=null;visibleMoons=null;}});
        dialog.show();refreshMoonConnectivity();
    }
    private void renderMoonRows(LinearLayout list,java.util.ArrayList<MoonSettings.Moon> nodes) {
        list.removeAllViews();
        if(nodes.isEmpty()) {add(list,text(getString(R.string.ui_090_no_moon_helpers_yet),14,muted),8);return;}
        for(MoonSettings.Moon moon:new java.util.ArrayList<>(nodes)) {
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(7),0,dp(7));
            LinearLayout info=column();TextView label=text(moon.displayId(),15,ink);label.setTypeface(Typeface.MONOSPACE);add(info,label,0);
            addMoonConnectivity(info,moon);
            if(settings().getBoolean("developer",false)) add(info,text("World: "+Long.toUnsignedString(moon.world,16)+"\nSeed: "+String.format(java.util.Locale.ROOT,"%010x",moon.seed),11,muted),4);
            row.addView(info,new LinearLayout.LayoutParams(0,-2,1));Button remove=button(getString(R.string.ui_091_remove),paper,muted);remove.setTextSize(12);row.addView(remove,new LinearLayout.LayoutParams(dp(62),dp(42)));remove.setEnabled(!TunnelService.alive);
            remove.setOnClickListener(v -> {nodes.remove(moon);editMoons.setText(MoonSettings.format(nodes));renderMoonRows(list,nodes);});add(list,row,0);
        }
    }
    private EditText field(LinearLayout parent,String label,String value) {
        return field(parent,label,value,false);
    }
    private EditText field(LinearLayout parent,String label,String value,boolean networkField) {
        TextView heading=text(label,17,ink);if(networkField)networkNameHeading=heading;add(parent,heading,14);
        EditText input=new EditText(this); input.setSingleLine(true); input.setTextSize(17); input.setTextColor(ink); input.setHintTextColor(muted); input.setHint(label); input.setText(value); input.setInputType(1|0x80000); input.setPadding(dp(14),dp(13),dp(14),dp(13)); input.setBackground(background(card,10)); add(parent,input,6); return input;
    }
    private SketchGlyph glyph(String kind,int size) {return new SketchGlyph(getResources().getDisplayMetrics().density,kind,size);}
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout panel() { LinearLayout v=column(); v.setPadding(dp(20),dp(18),dp(20),dp(18)); v.setBackground(background(card,12)); v.setElevation(0); return v; }
    private android.graphics.drawable.Drawable background(int color,int radius) {
        return new SketchSurface(getResources().getDisplayMetrics().density,color);
    }
    private TextView text(String value,int size,int color) { TextView t=new ApprovedArt.Label(this); t.setText(value); t.setTypeface(inkTypeface); t.setTextSize(size); t.setTextColor(color); t.setLineSpacing(dp(2),1); return t; }
    private Button button(String value,int color,int foreground) { return decorateButton(new ApprovedArt.Action(this),value,color,foreground); }
    private Button primaryButton(String value) {
        Button b=new ApprovedArt.Action(this) {
            private final SketchGlyph power=glyph("power",56);
            @Override protected void onDraw(Canvas canvas) {
                float icon=dp(56), gap=dp(14), width=getPaint().measureText(getText().toString());
                int art=ApprovedArt.textArt(getText().toString());
                if(art!=0&&getResources().getConfiguration().fontScale<=1.2f){Bitmap b=ApprovedArt.bitmap(art);width=b.getWidth()/(float)b.getHeight()*getTextSize()*1.35f;}
                canvas.save();canvas.translate((icon+gap)/2,0);super.onDraw(canvas);canvas.restore();
                int left=Math.round((getWidth()-width-icon-gap)/2),top=(getHeight()-dp(56))/2;
                power.setBounds(left,top,left+dp(56),top+dp(56));power.draw(canvas);
            }
        };
        return decorateButton(b,value,peach,ink);
    }
    private Button decorateButton(Button b,String value,int color,int foreground) {
        b.setText(value);b.setTextSize(16);b.setAllCaps(false);b.setTextColor(foreground);b.setMinHeight(dp(54));
        b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x224c3c37),background(color,12),null));
        b.setTypeface(inkTypeface);b.setPadding(dp(12),0,dp(12),0);b.setStateListAnimator(null);return b;
    }
    private void add(LinearLayout parent,View child,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(top); parent.addView(child,p); }
}
