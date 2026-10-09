package il.co.kiosk;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.webkit.WebView;
import java.util.ArrayList;
import java.util.List;

/** Runs on a disposable emulator only. Clears this app's test data at the start. */
public final class SmokeTests extends Instrumentation {
    private Activity activity;
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            getTargetContext().getSharedPreferences("kiosk", 0).edit().clear().commit();
            launch();
            check(hasText("הטאבלט שלך. התוכן שלך."), "first-run setup");
            main(() -> { List<EditText> fields = fields(); fields.get(0).setText("012345"); fields.get(1).setText("012345"); click("שמירת PIN והמשך"); });
            check(hasText("מגדירים. נועלים. מוכנים."), "PIN creation opens settings");
            screenshot("settings.png");
            main(() -> click("חזרה למסך הקיוסק"));
            check(hasText("הקיוסק שלי"), "native home");
            screenshot("home.png");
            check(!hasText("מגדירים. נועלים. מוכנים."), "settings hidden by default");
            main(() -> activity.onBackPressed());
            check(!activity.isFinishing(), "Back does not exit kiosk");
            openPin();
            main(() -> { EditText input = (EditText)dialogField(); input.setText("111111"); pinButton().performClick(); });
            check(!hasText("מגדירים. נועלים. מוכנים."), "wrong PIN denied");
            main(() -> { ((EditText)dialogField()).setText("012345"); pinButton().performClick(); });
            check(hasText("מגדירים. נועלים. מוכנים."), "correct PIN granted");
            main(() -> { callActivityOnPause(activity); callActivityOnResume(activity); });
            check(!hasText("מגדירים. נועלים. מוכנים."), "pause revokes admin session");
            openPin();
            main(() -> { ((EditText)dialogField()).setText("012345"); pinButton().performClick(); click("אתר אינטרנט"); fields().get(0).setText("https://example.com"); click("שמירה והצגת הקיוסק"); });
            check(findWeb(root()) != null, "website mode creates WebView");
            check("web".equals(getTargetContext().getSharedPreferences("kiosk", 0).getString("mode", "")), "mode persisted");
            main(() -> { WebView w = findWeb(root()); check(!w.getSettings().getAllowFileAccess(), "file access off"); check(!w.getSettings().getAllowContentAccess(), "content access off"); check(w.getSettings().getMixedContentMode() == 1, "mixed content off"); });
            main(() -> activity.finish()); launch();
            check(findWeb(root()) != null, "web mode survives activity restart");
            check(!hasText("מגדירים. נועלים. מוכנים."), "restart does not restore admin");
            openPin();
            for (int i = 0; i < 5; i++) main(() -> { ((EditText)dialogField()).setText("111111"); pinButton().performClick(); });
            main(() -> { ((EditText)dialogField()).setText("012345"); pinButton().performClick(); });
            check(!hasText("מגדירים. נועלים. מוכנים."), "cooldown blocks even correct PIN");
            check(getTargetContext().getSharedPreferences("kiosk", 0).getInt("failures", 0) == 5, "failure count persisted");
            main(() -> activity.finish()); launch();
            openPin();
            main(() -> { ((EditText)dialogField()).setText("012345"); pinButton().performClick(); });
            check(!hasText("מגדירים. נועלים. מוכנים."), "cooldown survives restart");
            String saved = getTargetContext().getSharedPreferences("kiosk", 0).getString("pin", "");
            check(saved.startsWith("120000:") && !saved.contains("012345"), "only salted PIN hash persisted");
            main(() -> activity.finish());
            result.putString("stream", "PASS: " + checks + " Android smoke checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(error)); finish(Activity.RESULT_CANCELED, result);
        }
    }
    private void launch() { activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); waitForIdleSync(); }
    private void screenshot(String name) throws Exception {
        main(() -> activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE));
        Thread.sleep(400);
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap != null) try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null), name))) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); }
        main(() -> activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE));
    }
    private void main(Runnable action) { runOnMainSync(action); waitForIdleSync(); }
    private void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); checks++; }
    private View root() { return activity.getWindow().getDecorView(); }
    private boolean hasText(String text) { return find(root(), text) != null; }
    private View find(View view, String text) {
        if (view instanceof TextView && text.equals(((TextView)view).getText().toString())) return view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) { View found = find(((ViewGroup)view).getChildAt(i), text); if (found != null) return found; }
        return null;
    }
    private void click(String text) { View v = find(root(), text); if (v == null) throw new AssertionError("Missing: " + text); v.performClick(); }
    private List<EditText> fields() { List<EditText> values = new ArrayList<>(); collect(root(),values); return values; }
    private void collect(View v, List<EditText> list) { if (v instanceof EditText) list.add((EditText)v); if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) collect(((ViewGroup)v).getChildAt(i),list); }
    private WebView findWeb(View v) { if (v instanceof WebView) return (WebView)v; if (v instanceof ViewGroup) for (int i=0;i<((ViewGroup)v).getChildCount();i++) { WebView w=findWeb(((ViewGroup)v).getChildAt(i)); if(w!=null)return w; } return null; }
    private void openPin() { main(() -> find(root(), "⋮").performClick()); main(() -> find(dialog().getWindow().getDecorView(), activity.getString(R.string.kiosk_settings)).performClick()); }
    private android.app.AlertDialog dialog() {
        try { java.lang.reflect.Field field=MainActivity.class.getDeclaredField("dialog"); field.setAccessible(true); return (android.app.AlertDialog)field.get(activity); } catch(Exception e) { throw new RuntimeException(e); }
    }
    private View dialogField() { List<EditText> list=new ArrayList<>(); collect(dialog().getWindow().getDecorView(),list); return list.get(0); }
    private View pinButton() { return dialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE); }
}
