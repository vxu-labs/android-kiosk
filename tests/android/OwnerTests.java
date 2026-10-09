package il.co.kiosk;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Dedicated device checks. Only install and run on a disposable emulator. */
public final class OwnerTests extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle(); Activity activity = null;
        DevicePolicyManager dpm = getTargetContext().getSystemService(DevicePolicyManager.class);
        ComponentName admin = new ComponentName(getTargetContext(), KioskAdminReceiver.class);
        String report;
        try {
            check(dpm.isDeviceOwnerApp("il.co.kiosk"), "Device Owner assigned");
            getTargetContext().getSharedPreferences("kiosk", 0).edit().clear()
                .putString("pin", PinCrypto.create("012345")).putBoolean("enabled", true).putString("mode", "home").commit();
            activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            check(getTargetContext().getSystemService(ActivityManager.class).getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED, "real lock task active");
            check(dpm.isLockTaskPermitted("il.co.kiosk"), "only kiosk allowlisted");
            check(dpm.getLockTaskPackages(admin).length == 1, "single allowed package");
            check(dpm.getLockTaskFeatures(admin) == DevicePolicyManager.LOCK_TASK_FEATURE_NONE, "system UI disabled");
            check(dpm.isUninstallBlocked(admin,"il.co.kiosk"), "uninstall blocked");
            UserManager users = getTargetContext().getSystemService(UserManager.class);
            for (String restriction : new String[]{UserManager.DISALLOW_DEBUGGING_FEATURES,UserManager.DISALLOW_SAFE_BOOT,UserManager.DISALLOW_FACTORY_RESET,UserManager.DISALLOW_CREATE_WINDOWS,UserManager.DISALLOW_ADD_USER}) check(users.hasUserRestriction(restriction), restriction);
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            check("il.co.kiosk".equals(getTargetContext().getPackageManager().resolveActivity(home,0).activityInfo.packageName), "persistent home points at kiosk");
            final Activity running = activity;
            final boolean[] pinChecks = new boolean[2];
            runOnMainSync(() -> {
                try {
                    java.lang.reflect.Field handle = MainActivity.class.getDeclaredField("adminHandle"); handle.setAccessible(true);
                    ((android.view.View)handle.get(running)).performLongClick();
                    java.lang.reflect.Field dialogField = MainActivity.class.getDeclaredField("dialog"); dialogField.setAccessible(true);
                    android.app.AlertDialog dialog = (android.app.AlertDialog)dialogField.get(running);
                    pinChecks[0] = dialog != null && dialog.isShowing();
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            waitForIdleSync(); // AlertDialog installs its OnShow click listener asynchronously.
            runOnMainSync(() -> {
                try {
                    java.lang.reflect.Field dialogField = MainActivity.class.getDeclaredField("dialog"); dialogField.setAccessible(true);
                    android.app.AlertDialog dialog = (android.app.AlertDialog)dialogField.get(running);
                    findInput(dialog.getWindow().getDecorView()).setText("012345");
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
                    java.lang.reflect.Field session = MainActivity.class.getDeclaredField("admin"); session.setAccessible(true); pinChecks[1] = session.getBoolean(running);
                } catch(Exception e) { throw new RuntimeException(e); }
            });
            check(pinChecks[0], "PIN dialog accessible while fully locked");
            check(pinChecks[1], "correct PIN opens admin inside lock task");
            check(getTargetContext().getSystemService(ActivityManager.class).getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED, "settings do not unlock Android");
            runOnMainSync(() -> {
                try { Class<?> type=Class.forName("il.co.kiosk.KioskPolicy"); java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(android.content.Context.class); constructor.setAccessible(true); Object policy=constructor.newInstance(getTargetContext()); java.lang.reflect.Method release=type.getDeclaredMethod("release", Activity.class, boolean.class); release.setAccessible(true); release.invoke(policy,running,true); }
                catch(Exception e) { throw new RuntimeException(e); }
            });
            check(getTargetContext().getSystemService(ActivityManager.class).getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE, "authorized release ends lock");
            check(!users.hasUserRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES), "maintenance clears restrictions");
            check(!dpm.isUninstallBlocked(admin,"il.co.kiosk"), "maintenance clears uninstall block");
            report = "PASS: " + checks + " Device Owner checks\n";
        } catch(Throwable e) { report="FAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(e); }
        finally {
            getTargetContext().getSharedPreferences("kiosk",0).edit().putBoolean("enabled",false).commit();
            // Restore connectivity only on this disposable emulator after testing that USB debugging was disabled.
            try { dpm.clearUserRestriction(admin,UserManager.DISALLOW_DEBUGGING_FEATURES); dpm.setGlobalSetting(admin,Settings.Global.ADB_ENABLED,"1"); } catch(Exception ignored) { }
        }
        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"owner-tests.txt"))) { out.write(report.getBytes(StandardCharsets.UTF_8)); } catch(Exception ignored) { }
        result.putString("stream",report); finish(report.startsWith("PASS") ? Activity.RESULT_OK : Activity.RESULT_CANCELED,result);
    }
    private void check(boolean value,String name) { if(!value) throw new AssertionError(name); checks++; }
    private android.widget.EditText findInput(android.view.View view) {
        if(view instanceof android.widget.EditText) return (android.widget.EditText)view;
        if(view instanceof android.view.ViewGroup) for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++) { android.widget.EditText input=findInput(((android.view.ViewGroup)view).getChildAt(i)); if(input!=null)return input; }
        return null;
    }
}
