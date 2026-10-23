package com.naslink.android;

import android.content.*;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

/** App-only language preference: English on every first launch, independent of system language. */
public final class AppLanguage {
    private AppLanguage() {}
    public static Context wrap(Context base) {
        String language=base.getSharedPreferences("connection",0).getString("language","en");
        Locale locale="zh".equals(language)?Locale.SIMPLIFIED_CHINESE:Locale.ENGLISH;
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);config.setLocales(new LocaleList(locale));return base.createConfigurationContext(config);
    }
    public static String inputError(Context context,Exception error) {
        String message=error.getMessage();
        if(error instanceof NumberFormatException) return context.getString(R.string.ui_068_choose_a_port_from_1_to_65535);
        if("home_ssid_invalid".equals(message)) return context.getString(R.string.home_ssid_invalid);
        if("home_nas_invalid".equals(message)) return context.getString(R.string.home_nas_invalid);
        if("home_port_invalid".equals(message)) return context.getString(R.string.home_port_invalid);
        if(message==null) return context.getString(R.string.input_error);
        boolean chinese=context.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");
        if(chinese || !message.matches("(?s).*[\\u4e00-\\u9fff].*")) return message;
        if(message.contains("IPv4")) return context.getString(R.string.ipv4_error);
        if(message.contains("Moon")||message.contains("Seed")) return context.getString(R.string.moon_error);
        return context.getString(R.string.input_error);
    }
    public static String notification(Context context,String phase) {
        Context localized=wrap(context);
        int id;
        switch(phase) {
            case "connected": id=R.string.notice_connected;break;
            case "authorization": id=R.string.notice_authorization;break;
            case "validating": id=R.string.notice_checking;break;
            case "degraded": case "reconnecting": id=R.string.notice_retrying;break;
            case "disconnected": id=R.string.notice_offline;break;
            default:id=R.string.notice_joining;
        }
        return localized.getString(id);
    }
}
