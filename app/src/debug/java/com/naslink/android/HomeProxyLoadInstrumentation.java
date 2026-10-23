package com.naslink.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Debug-only runner inside ART, so JNI_OnLoad is exercised unlike dlopen.
 * No VPN consent, network change, or user configuration is required. */
public final class HomeProxyLoadInstrumentation extends Instrumentation {
    private boolean connect;
    private int soakSeconds;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        connect=arguments!=null && "true".equals(arguments.getString("connect"));
        if(arguments!=null) soakSeconds=Math.min(120,Math.max(0,Integer.parseInt(arguments.getString("soakSeconds","0"))));
        start();
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        int resultCode=Activity.RESULT_OK;
        try {
            Class<?> engine=Class.forName("com.naslink.android.HomeProxyEngine",true,getTargetContext().getClassLoader());
            engine.getDeclaredMethod("run",String.class,int.class,long.class,android.net.VpnService.class);
            engine.getDeclaredMethod("quit");
            result.putString("stream","PASS ART loads NestLink home proxy without upstream example JNI registration\n");
            if(connect) {
                android.content.Context context=getTargetContext();
                if(android.net.VpnService.prepare(context)!=null) throw new IllegalStateException("VPN consent required for connection test");
                context.startActivity(new android.content.Intent(context,MainActivity.class).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                context.startForegroundService(new android.content.Intent(context,TunnelService.class));
                android.content.SharedPreferences status=context.getSharedPreferences("status",0);
                long until=android.os.SystemClock.elapsedRealtime()+25000;
                do {
                    android.os.SystemClock.sleep(500);
                    if(status.getLong("checkedAt",0)>0 || !status.getString("homeError","").isEmpty()) break;
                } while(android.os.SystemClock.elapsedRealtime()<until);
                if(!TunnelService.alive) throw new IllegalStateException("Connection stopped: "+status.getString("message",""));
                result.putString("stream",result.getString("stream")+"PASS connection service stayed alive; phase="+status.getString("phase","")
                    +"; homeMode="+status.getBoolean("homeMode",false)+"; healthChecked="+(status.getLong("checkedAt",0)>0)+"\n");
                if(soakSeconds>0) {
                    int switches=status.getInt("routeSwitches",0),home=status.getInt("homeStarts",0),nodes=status.getInt("nodeStarts",0);
                    long end=android.os.SystemClock.elapsedRealtime()+soakSeconds*1000L;
                    int samples=0,connectedSamples=0;
                    while(android.os.SystemClock.elapsedRealtime()<end) {
                        android.os.SystemClock.sleep(1000);samples++;
                        if(!TunnelService.alive) throw new IllegalStateException("Service stopped during soak");
                        if("connected".equals(status.getString("phase",""))) connectedSamples++;
                        if(samples%10==0) context.startService(new android.content.Intent(context,TunnelService.class).setAction(TunnelService.RECHECK));
                    }
                    if(switches!=status.getInt("routeSwitches",0) || home!=status.getInt("homeStarts",0) || nodes!=status.getInt("nodeStarts",0))
                        throw new IllegalStateException("Route or engine restarted during stationary network soak");
                    result.putString("stream",result.getString("stream")+"PASS "+soakSeconds+"s stationary soak: no route/engine restart; connectedSamples="+connectedSamples+"/"+samples+"; homeMode="+status.getBoolean("homeMode",false)+"\n");
                }
            }
        } catch(Throwable e) {
            result.putString("stream","FAIL home proxy loading: "+e+"\n");
            resultCode=Activity.RESULT_CANCELED;
        } finally {
            if(connect && TunnelService.alive) {
                getTargetContext().startService(new android.content.Intent(getTargetContext(),TunnelService.class).setAction(TunnelService.STOP));
                android.os.SystemClock.sleep(1000);
            }
        }
        finish(resultCode,result);
    }
}
