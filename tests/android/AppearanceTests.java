package il.co.kiosk;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Menu, authentication boundary and appearance checks on a disposable emulator. */
public final class AppearanceTests extends Instrumentation {
    private Activity activity;
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            SharedPreferences prefs=getTargetContext().getSharedPreferences("kiosk",0);
            prefs.edit().clear().putString("pin",PinCrypto.create("012345")).putString("language","en")
                .putString("title","Welcome to your space").putString("message","Make yourself at home.").commit();
            String savedPin=prefs.getString("pin","");
            launch();
            check((Boolean)field("dark"),"dark is the default without a saved preference");
            check((Integer)field("BG")==Color.parseColor("#09090B"),"dark home palette");
            screenshot("home-dark.png");
            openMenu();
            check(dialog().isShowing(),"single tap opens actions");
            check(buttonCount(dialogRoot())==2,"menu has exactly two actions");
            check(!(Boolean)field("admin"),"menu grants no admin session");
            screenshot("menu-dark.png");
            main(() -> clickDialog(R.string.refresh_home));
            check(!(Boolean)field("admin") && field("dialog")==null,"refresh remains public and closes menu");
            check(find(root(),"Welcome to your space")!=null,"native refresh restores saved home");
            openPin();
            check(inputs(dialogRoot()).size()==1,"settings action shows PIN input");
            EditText pin=inputs(dialogRoot()).get(0);
            check(pin.getHint()!=null && pin.getHint().length()>0,"PIN has visible input hint");
            check(pin.getBackground() instanceof StateListDrawable,"PIN has bounded normal and focus states");
            main(pin::requestFocus);
            check(pin.hasFocus() && pin.isFocusable() && pin.isEnabled(),"PIN is editable and shows focus");
            check((dialog().getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE)!=0,"PIN dialog protects screenshots");
            screenshot("pin-dark.png");
            main(() -> { pin.setText("999999"); dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); });
            check(!(Boolean)field("admin") && dialog().isShowing(),"wrong PIN cannot open settings");
            main(() -> dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
            check(!(Boolean)field("admin"),"cancel remains locked");
            openPin(); unlock();
            check((Boolean)field("admin"),"correct PIN opens settings");
            screenshot("settings-dark.png");
            main(() -> click(R.string.theme_light));
            recreateAfter(() -> click(R.string.save_preview));
            check("light".equals(prefs.getString("appearance","")) && !(Boolean)field("dark"),"saved Light choice applied");
            check(!(Boolean)field("admin"),"appearance recreation revokes admin");
            check(savedPin.equals(prefs.getString("pin","")),"appearance change retains PIN");
            check(find(root(),"Welcome to your space")!=null,"appearance change retains custom content");
            screenshot("home-light.png");
            main(() -> activity.finish()); launch();
            check(!(Boolean)field("dark"),"Light persists after restart");
            openMenu(); screenshot("menu-light.png");
            main(() -> clickDialog(R.string.kiosk_settings));
            check(inputs(dialogRoot()).get(0).getCurrentTextColor()==Color.parseColor("#1D1D1F"),"light PIN has contrasting text");
            screenshot("pin-light.png"); unlock(); screenshot("settings-light.png");
            main(() -> { click(R.string.theme_dark); click(R.string.mode_web); inputs(root()).get(0).setText("https://example.com/home"); });
            recreateAfter(() -> click(R.string.save_preview));
            check((Boolean)field("dark") && "dark".equals(prefs.getString("appearance","")),"saved Dark choice applied");
            WebView before=(WebView)field("web");
            main(() -> before.loadUrl("https://example.com/other"));
            openMenu(); main(() -> clickDialog(R.string.refresh_home));
            WebView after=(WebView)field("web");
            check(after!=null && after!=before,"website refresh replaces prior navigation session");
            String[] loadedUrl={null};
            for(int i=0;i<30 && loadedUrl[0]==null;i++){main(() -> loadedUrl[0]=after.getUrl());if(loadedUrl[0]==null)Thread.sleep(100);}
            check("https://example.com/home".equals(loadedUrl[0]),"refresh loads configured homepage");
            check(!(Boolean)field("admin"),"website refresh grants no admin");
            main(() -> activity.finish());
            prefs.edit().putString("mode","home").putString("language","he").commit(); launch();
            openMenu();
            check(dialogRoot().getLayoutDirection()==View.LAYOUT_DIRECTION_RTL,"Hebrew menu follows RTL");
            check(find(dialogRoot(),"ריענון עמוד הבית")!=null && find(dialogRoot(),"הגדרות הקיוסק")!=null,"Hebrew action labels");
            screenshot("menu-he-dark.png");
            main(() -> clickDialog(R.string.kiosk_settings)); screenshot("pin-he-dark.png"); unlock(); screenshot("settings-he-dark.png");
            main(() -> activity.finish());
            result.putString("stream","PASS: "+checks+" appearance and menu checks\n");finish(Activity.RESULT_OK,result);
        } catch(Throwable error) { result.putString("stream","FAIL after "+checks+" checks: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result); }
    }
    private void main(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private void launch(){activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();}
    private void recreateAfter(Runnable action){ActivityMonitor m=addMonitor(MainActivity.class.getName(),null,false);main(action);Activity next=waitForMonitorWithTimeout(m,5000);removeMonitor(m);if(next==null)throw new AssertionError("Activity did not recreate");activity=next;waitForIdleSync();}
    private Object field(String name){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}catch(Exception e){throw new RuntimeException(e);}}
    private View root(){return activity.getWindow().getDecorView();}
    private AlertDialog dialog(){return (AlertDialog)field("dialog");}
    private View dialogRoot(){return dialog().getWindow().getDecorView();}
    private View find(View v,String text){if(v instanceof TextView && text.equals(((TextView)v).getText().toString()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
    private void click(int id){find(root(),activity.getString(id)).performClick();}
    private void clickDialog(int id){find(dialogRoot(),activity.getString(id)).performClick();}
    private void openMenu(){main(() -> find(root(),"⋮").performClick());}
    private void openPin(){openMenu();main(() -> clickDialog(R.string.kiosk_settings));}
    private void unlock(){main(() -> {inputs(dialogRoot()).get(0).setText("012345");dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();});}
    private List<EditText> inputs(View root){List<EditText> list=new ArrayList<>();collect(root,list);return list;}
    private void collect(View v,List<EditText> list){if(v instanceof EditText)list.add((EditText)v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)collect(((ViewGroup)v).getChildAt(i),list);}
    private int buttonCount(View v){int n=v instanceof Button && v.getVisibility()==View.VISIBLE?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=buttonCount(((ViewGroup)v).getChildAt(i));return n;}
    private void screenshot(String name)throws Exception{
        AlertDialog d=dialog();
        main(() -> {activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);if(d!=null && d.isShowing())d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);});Thread.sleep(350);
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap!=null)try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
        main(() -> {activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);if(d!=null && d.isShowing())d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);});
    }
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);checks++;}
}
