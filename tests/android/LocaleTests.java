package il.co.kiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Disposable-emulator checks for persisted language and public/private status separation. */
public final class LocaleTests extends Instrumentation {
    private Activity activity;
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            getTargetContext().getSharedPreferences("kiosk",0).edit().clear().commit();
            launch();
            check(has("הטאבלט שלך. התוכן שלך."), "existing default remains Hebrew");
            recreateAfter(() -> click("English"));
            check(has("Your tablet. Your content."), "initial English selection translates setup");
            check(contentRoot().getLayoutDirection() == View.LAYOUT_DIRECTION_LTR, "English layout is LTR");
            main(() -> { inputs().get(0).setText("012345"); inputs().get(1).setText("012345"); click("Save PIN and continue"); });
            check(has("Set up. Lock. Ready."), "English admin screen");
            check(has("○ Full Android lock is not active · Device Owner setup required"), "truthful status is available inside admin");
            screenshot("settings-en.png");
            String hash = getTargetContext().getSharedPreferences("kiosk",0).getString("pin", "");
            main(() -> { inputs().get(0).setText("https://example.com"); inputs().get(1).setText("My shop / העסק שלי"); inputs().get(2).setText("Hello / שלום"); click("עברית"); });
            recreateAfter(() -> click("Save and show kiosk"));
            check(has("ברוכים הבאים"), "saved language updates home");
            check(contentRoot().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL, "Hebrew layout is RTL");
            check(has("My shop / העסק שלי") && has("Hello / שלום"), "language change preserves custom content");
            check(hash.equals(getTargetContext().getSharedPreferences("kiosk",0).getString("pin", "")), "language change preserves PIN");
            check(!has("מצב תצוגה • נעילת Android אינה פעילה. לחץ ממושכות על ⋮ להגדרה."), "public footer removed");
            check(((ViewGroup)contentRoot()).getChildCount() == 2, "home contains content and admin handle only");
            main(() -> activity.finish()); launch();
            check(has("ברוכים הבאים"), "language persists after restart");
            openAdmin();
            main(() -> { click("English"); click("אתר אינטרנט"); });
            recreateAfter(() -> click("שמירה והצגת הקיוסק"));
            check("en".equals(getTargetContext().getSharedPreferences("kiosk",0).getString("language", "")), "English preference persisted");
            check(contentRoot().getLayoutDirection() == View.LAYOUT_DIRECTION_LTR, "website shell uses selected direction");
            check(((ViewGroup)contentRoot()).getChildCount() == 3, "website contains web view, progress and PIN handle without footer");
            check(!has("○ Full Android lock is not active · Device Owner setup required"), "admin status does not leak over website");
            openAdmin();
            check(has("Set up. Lock. Ready."), "existing PIN authenticates after both language changes");
            main(() -> { click("Built-in home"); click("Save and show kiosk"); });
            check(has("Welcome"), "English home label");
            screenshot("home-en.png");
            main(() -> activity.finish());
            result.putString("stream", "PASS: " + checks + " language and footer checks\n"); finish(Activity.RESULT_OK,result);
        } catch(Throwable error) { result.putString("stream","FAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED,result); }
    }
    private void main(Runnable action) { runOnMainSync(action); waitForIdleSync(); }
    private void launch() { activity = startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync(); }
    private void recreateAfter(Runnable action) {
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        main(action);
        Activity next=waitForMonitorWithTimeout(monitor,5000); removeMonitor(monitor);
        if(next==null)throw new AssertionError("No activity after language change"); activity=next; waitForIdleSync();
    }
    private Object field(String name) { try { java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); } catch(Exception e) { throw new RuntimeException(e); } }
    private View contentRoot() { return (View)field("root"); }
    private View root() { return activity.getWindow().getDecorView(); }
    private boolean has(String text) { return find(root(),text)!=null; }
    private void click(String text) { View v=find(root(),text); if(v==null)throw new AssertionError("Missing: "+text); v.performClick(); }
    private View find(View v,String text) { if(v instanceof TextView && text.equals(((TextView)v).getText().toString()))return v; if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View r=find(((ViewGroup)v).getChildAt(i),text);if(r!=null)return r;}return null; }
    private List<EditText> inputs() { List<EditText> list=new ArrayList<>(); collect(root(),list);return list; }
    private void collect(View v,List<EditText> list) { if(v instanceof EditText)list.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),list); }
    private void openAdmin() {
        main(() -> find(root(), "⋮").performClick());
        main(() -> { AlertDialog menu=(AlertDialog)field("dialog"); find(menu.getWindow().getDecorView(), activity.getString(R.string.kiosk_settings)).performClick(); });
        main(() -> { AlertDialog d=(AlertDialog)field("dialog"); List<EditText> list=new ArrayList<>();collect(d.getWindow().getDecorView(),list);list.get(0).setText("012345");d.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); });
    }
    private void screenshot(String name) throws Exception {
        main(() -> activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)); Thread.sleep(400);
        android.graphics.Bitmap b=getUiAutomation().takeScreenshot();
        if(b!=null)try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name))){b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        main(() -> activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE));
    }
    private void check(boolean value,String name) { if(!value)throw new AssertionError(name);checks++; }
}
