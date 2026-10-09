package il.co.kiosk;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserManager;

final class KioskPolicy {
    final Context context;
    final DevicePolicyManager dpm;
    final ComponentName admin;
    static final String[] RESTRICTIONS = {
        UserManager.DISALLOW_SAFE_BOOT, UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_ADD_USER, UserManager.DISALLOW_CREATE_WINDOWS,
        UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS, UserManager.DISALLOW_USB_FILE_TRANSFER,
        UserManager.DISALLOW_DEBUGGING_FEATURES
    };
    KioskPolicy(Context context) {
        this.context = context;
        dpm = context.getSystemService(DevicePolicyManager.class);
        admin = new ComponentName(context, KioskAdminReceiver.class);
    }
    boolean isOwner() { return dpm.isDeviceOwnerApp(context.getPackageName()); }
    boolean isLocked() {
        return context.getSystemService(ActivityManager.class).getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED;
    }
    void lock(Activity activity) {
        if (!isOwner()) throw new IllegalStateException("Device Owner is required");
        if (context.getSystemService(KeyguardManager.class).isKeyguardLocked()) throw new IllegalStateException(context.getString(R.string.unlock_android_first));
        dpm.setLockTaskPackages(admin, new String[] {context.getPackageName()});
        dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
        IntentFilter home = new IntentFilter(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addCategory(Intent.CATEGORY_DEFAULT);
        dpm.addPersistentPreferredActivity(admin, home, new ComponentName(context, MainActivity.class));
        for (String restriction : RESTRICTIONS) dpm.addUserRestriction(admin, restriction);
        dpm.setStatusBarDisabled(admin, true);
        dpm.setKeyguardDisabled(admin, true);
        dpm.setUninstallBlocked(admin, context.getPackageName(), true);
        if (!isLocked()) activity.startLockTask();
    }
    void release(Activity activity, boolean clearHome) {
        if (!isOwner()) return;
        if (isLocked()) activity.stopLockTask();
        for (String restriction : RESTRICTIONS) dpm.clearUserRestriction(admin, restriction);
        dpm.setStatusBarDisabled(admin, false);
        dpm.setKeyguardDisabled(admin, false);
        dpm.setUninstallBlocked(admin, context.getPackageName(), false);
        dpm.setLockTaskPackages(admin, new String[0]);
        if (clearHome) dpm.clearPackagePersistentPreferredActivities(admin, context.getPackageName());
    }
}
