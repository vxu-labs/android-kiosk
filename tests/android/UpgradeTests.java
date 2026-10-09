package il.co.kiosk;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Seed on 1.0.0, install 1.1.0 with -r, then verify. Disposable emulator only. */
public final class UpgradeTests extends Instrumentation {
    private boolean seed;
    @Override public void onCreate(Bundle args) { super.onCreate(args); seed=args!=null && "true".equals(args.getString("seed"));start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            SharedPreferences prefs=getTargetContext().getSharedPreferences("kiosk",0);
            if(seed) {
                prefs.edit().clear().putString("pin",PinCrypto.create("019283")).putString("mode","web")
                    .putString("url","https://example.com/upgrade").putString("title","Existing shop").putString("message","Existing message")
                    .putBoolean("enabled",true).putBoolean("clock",false).putBoolean("same_host",false).commit();
                result.putString("stream","PASS: seeded 1.0.0 configuration\n");
            } else {
                if(getTargetContext().getPackageManager().getPackageInfo("il.co.kiosk",0).getLongVersionCode()!=2)throw new AssertionError("wrong installed version");
                if(!PinCrypto.matches("019283",prefs.getString("pin",null)))throw new AssertionError("PIN changed");
                if(!prefs.getString("url","").equals("https://example.com/upgrade") || !prefs.getString("mode","").equals("web"))throw new AssertionError("website changed");
                if(!prefs.getString("title","").equals("Existing shop") || !prefs.getString("message","").equals("Existing message"))throw new AssertionError("content changed");
                if(!prefs.getBoolean("enabled",false) || prefs.getBoolean("clock",true) || prefs.getBoolean("same_host",true))throw new AssertionError("flags changed");
                Activity a=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
                java.lang.reflect.Field admin=MainActivity.class.getDeclaredField("admin");admin.setAccessible(true);if(admin.getBoolean(a))throw new AssertionError("upgrade granted admin");
                runOnMainSync(a::finish);
                result.putString("stream","PASS: same-key update preserves PIN, website, content and flags; admin remains locked\n");
            }
            finish(Activity.RESULT_OK,result);
        }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
    }
}
