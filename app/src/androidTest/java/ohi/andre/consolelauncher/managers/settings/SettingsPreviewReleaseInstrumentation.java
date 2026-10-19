package ohi.andre.consolelauncher.managers.settings;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Native black-box harness; the production APK keeps its normal shrink rules and dependencies. */
public final class SettingsPreviewReleaseInstrumentation extends Instrumentation {
    private static final String HOME="ohi.andre.consolelauncher.LauncherActivity";
    private static final String EDITOR="ohi.andre.consolelauncher.commands.tuixt.TuixtActivity";
    private File folder() { return new File(getTargetContext().getExternalFilesDir(null),"Re-T-UI"); }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
    private List<View> views(View root) {
        List<View> result=new ArrayList<>();result.add(root);
        if(root instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++) result.addAll(views(group.getChildAt(i)));
        }
        return result;
    }
    private void settle() throws Exception { Thread.sleep(300);waitForIdleSync(); }
    private int resource(String name,String type) { int id=getTargetContext().getResources().getIdentifier(name,type,getTargetContext().getPackageName());require(id!=0,"Resource missing: "+name);return id; }
    private String text(int id) { return getTargetContext().getString(id); }
    private ViewGroup thumbnail(Activity activity) {
        ViewGroup panel=activity.findViewById(resource("settings_panel","id"));
        return (ViewGroup)((ViewGroup)panel.getChildAt(0)).getChildAt(0);
    }
    private void swipe(Activity activity, boolean forward) throws Exception {
        int[] bounds=new int[4];
        runOnMainSync(()-> {
            View view=thumbnail(activity);view.getLocationOnScreen(bounds);
            bounds[2]=view.getWidth();bounds[3]=view.getHeight();
        });
        float from=bounds[0]+bounds[2]*(forward?.85f:.15f);
        float to=bounds[0]+bounds[2]*(forward?.15f:.85f);
        float y=bounds[1]+bounds[3]*.5f;
        long down=android.os.SystemClock.uptimeMillis();
        for(int step=0;step<=12;step++) {
            int action=step==0?android.view.MotionEvent.ACTION_DOWN:step==12?android.view.MotionEvent.ACTION_UP:android.view.MotionEvent.ACTION_MOVE;
            android.view.MotionEvent event=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,from+(to-from)*step/12f,y,0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try { require(getUiAutomation().injectInputEvent(event,true),"Swipe injection failed"); }
            finally { event.recycle(); }
            Thread.sleep(12);
        }
        settle();
    }
    private void thirdPane(Activity activity) {
        runOnMainSync(()-> {
            View module=activity.findViewById(resource("music_module","id"));
            android.graphics.Rect visible=new android.graphics.Rect();
            require(module!=null && module.getGlobalVisibleRect(visible),"Third pane was not reached");
            View control=activity.findViewById(resource("music_play_pause","id"));
            require(control!=null && !control.hasOnClickListeners(),"Preview has active music action");
        });
    }
    private void screenshot(String name) throws Exception {
        Bitmap image=getUiAutomation().takeScreenshot();require(image!=null,"Screenshot missing");
        File output=new File(getTargetContext().getExternalFilesDir(null),"issue23/release");output.mkdirs();
        try(OutputStream stream=new FileOutputStream(new File(output,name+".png"))) { image.compress(Bitmap.CompressFormat.PNG,100,stream); }
        finally { image.recycle(); }
    }
    private void hideKeyboard(Activity activity) throws Exception {
        runOnMainSync(()-> {
            ((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(),0);
            if(activity.getCurrentFocus()!=null) activity.getCurrentFocus().clearFocus();
        });settle();
    }
    private void fixture(String name,String root,Map<String,String> values) throws Exception {
        File file=new File(folder(),name);
        javax.xml.parsers.DocumentBuilder builder=DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc=file.exists()?builder.parse(file):builder.newDocument();
        if(doc.getDocumentElement()==null) doc.appendChild(doc.createElement(root));
        for(Map.Entry<String,String> entry:values.entrySet()) {
            Element element=(Element)doc.getElementsByTagName(entry.getKey()).item(0);
            if(element==null) { element=doc.createElement(entry.getKey());doc.getDocumentElement().appendChild(element); }
            element.setAttribute("value",entry.getValue());
        }
        file.getParentFile().mkdirs();
        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(doc),new StreamResult(file));
    }
    private Map<String,byte[]> files() throws Exception {
        Map<String,byte[]> result=new HashMap<>();File[] files=folder().listFiles();
        if(files!=null) for(File file:files) if(file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) result.put(file.getName(),Files.readAllBytes(file.toPath()));
        File frames=new File(folder(),"frames/state.json");if(frames.exists()) result.put("frames/state.json",Files.readAllBytes(frames.toPath()));
        return result;
    }
    private void unchanged(Map<String,byte[]> before) throws Exception {
        Map<String,byte[]> after=files();require(before.keySet().equals(after.keySet()),"Preview changed saved file names");
        for(String name:before.keySet()) require(Arrays.equals(before.get(name),after.get(name)),"Preview wrote "+name);
    }
    private String background;
    private String mode;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);background=arguments.getString("background", "original");mode=arguments.getString("mode", "preview");start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try { if("private".equals(mode)) privateJourney(); else if("changelog".equals(mode)) changelogJourney(); else journey();result.putString("stream","\nOK: " + mode + " release journey\n");finish(Activity.RESULT_OK,result); }
        catch(Throwable failure) {
            StringWriter trace=new StringWriter();failure.printStackTrace(new PrintWriter(trace));
            result.putString("stream",trace.toString());finish(Activity.RESULT_CANCELED,result);
        }
    }
    private String shell(String command) throws Exception {
        try(InputStream stream=new android.os.ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int size;
            while((size=stream.read(bytes))!=-1) out.write(bytes,0,size);
            return out.toString("UTF-8");
        }
    }
    private boolean privateFixtureFocused() throws Exception {
        for(String line:shell("dumpsys window").split("\n"))
            if(line.contains("mCurrentFocus=") && line.contains("u11 dev.retui.privatespacetest/")) return true;
        return false;
    }
    private interface Ready { boolean get() throws Exception; }
    private void await(String message,Ready ready) throws Exception {
        long end=android.os.SystemClock.uptimeMillis()+20000;
        while(!ready.get() && android.os.SystemClock.uptimeMillis()<end) Thread.sleep(100);
        require(ready.get(),message);
    }
    private volatile Activity launcher;
    @Override public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        if (HOME.equals(activity.getClass().getName())) launcher=activity;
    }
    private Activity home() throws Exception {
        Intent intent=new Intent().setClassName(getTargetContext().getPackageName(),HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Home may already exist: singleTask re-entry has no new Activity for startActivitySync.
        runOnMainSync(()->getTargetContext().startActivity(intent));
        await("Home focus",()->launcher!=null && launcher.hasWindowFocus());return launcher;
    }
    private void command(Activity home,String text) throws Exception {
        runOnMainSync(()-> {
            EditText input=home.findViewById(resource("input_view","id"));
            input.requestFocus();input.setText(text);input.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        });settle();
    }
    private AccessibilityNodeInfo pin(AccessibilityNodeInfo node) {
        if(node==null) return null;
        if(node.isEditable() && node.isPassword()) return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo found=pin(node.getChild(i));if(found!=null)return found; }
        return null;
    }
    private void privateJourney() throws Exception {
        require("ranchu".equals(android.os.Build.HARDWARE),"Private fixture is emulator-only");
        Map<String,byte[]> original=files();Activity activity=null;
        android.os.UserManager users=(android.os.UserManager)getTargetContext().getSystemService(Activity.USER_SERVICE);
        android.content.pm.LauncherApps apps=(android.content.pm.LauncherApps)getTargetContext().getSystemService(Activity.LAUNCHER_APPS_SERVICE);
        android.os.UserHandle profile=null;
        for(android.os.UserHandle user:apps.getProfiles())
            if("android.os.usertype.profile.PRIVATE".equals(apps.getLauncherUserInfo(user).getUserType())) profile=user;
        require(profile!=null,"Private test profile missing");final android.os.UserHandle privateUser=profile;
        require(privateUser.equals(android.os.UserHandle.getUserHandleForUid(1100000)),"Unexpected fixture user");
        require(apps.getActivityList("dev.retui.privatespacetest",android.os.Process.myUserHandle()).isEmpty(),"Fixture must not be personal");
        fixture("behavior.xml","BEHAVIOR",Collections.singletonMap("enable_private_command","true"));
        fixture("behavior.xml","BEHAVIOR",Collections.singletonMap("auto_show_keyboard","false"));
        getTargetContext().getSharedPreferences("changelogPrefs",0).edit().putInt("last_seen_version_code",504).commit();
        try {
            activity=home();
            if(users.isQuietModeEnabled(profile) || !users.isUserUnlocked(profile)) {
                command(activity,"private -unlock");
                await("Private authentication",()->pin(getUiAutomation().getRootInActiveWindow())!=null);
                getUiAutomation().waitForIdle(500,5000);
                AccessibilityNodeInfo input=pin(getUiAutomation().getRootInActiveWindow());Bundle value=new Bundle();
                value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"2468");
                require(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,value),"Fixture PIN input");
                require(input.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId()),"Fixture PIN submission");
            }
            await("Private user unlocked",()->!users.isQuietModeEnabled(privateUser) && users.isUserUnlocked(privateUser));
            for(String text:Arrays.asList("PrivateProfileTest","private -open Private Profile Test")) {
                activity=home();command(activity,text);
                await("Private command foreground",this::privateFixtureFocused);
                screenshot(text.startsWith("private -")?"private-explicit-launch":"private-label-launch");
            }
            activity=home();command(activity,"private -lock");
            await("Private user locked",()->users.isQuietModeEnabled(privateUser) && !users.isUserUnlocked(privateUser));
            command(activity,"private -open Private Profile Test");
            require(!privateFixtureFocused() && users.isQuietModeEnabled(privateUser),"Locked launch opened private fixture");
        } finally {
            if(activity!=null) { Activity last=activity;runOnMainSync(last::finish); }
            for(Map.Entry<String,byte[]> entry:original.entrySet()) Files.write(new File(folder(),entry.getKey()).toPath(),entry.getValue());
        }
    }
    private void changelogJourney() throws Exception {
        Activity activity=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"ohi.andre.consolelauncher.help.ChangelogActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
        try {
            runOnMainSync(()-> {
                String notes=((TextView)activity.findViewById(resource("offline_help_output","id"))).getText().toString();
                require(notes.contains("Hotfix #1") && notes.contains("Termux, connected through SSH"),"Appended or previous notes missing");
                require(notes.contains("Build 504"),"Release number missing");
            });
            require(getTargetContext().getSharedPreferences("changelogPrefs",0).getInt("last_seen_version_code",0)==504,"Release not acknowledged");
            screenshot("hotfix-changelog");
        } finally { runOnMainSync(activity::finish); }
    }
    private void journey() throws Exception {
        Map<String,byte[]> originals=files();
        Activity home=null,editor=null;
        getTargetContext().getSharedPreferences("retui_startup",0).edit().putBoolean("initialized",true).putBoolean("complete",true).putBoolean("active",false).commit();
        getTargetContext().getSharedPreferences("changelogPrefs",0).edit().putInt("last_seen_version_code",504).commit();
        fixture("notifications.xml","NOTIFICATIONS",Collections.singletonMap("terminal_notifications","false"));
        fixture("notifications.xml","NOTIFICATIONS",Collections.singletonMap("show_notifications","false"));
        fixture("behavior.xml","BEHAVIOR",Collections.singletonMap("auto_show_keyboard","false"));
        try {
            {
                String color=background;
                if(!"original".equals(color)) fixture("theme.xml","THEME",Collections.singletonMap("background_color",color));
                home=home();settle();
                for(String name:Arrays.asList("theme.xml","ui.xml","behavior.xml")) {
                    for(int reopen=0;reopen<2;reopen++) {
                        editor=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),EDITOR).putExtra("path",new File(folder(),name).getAbsolutePath()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
                        hideKeyboard(editor);
                        Map<String,byte[]> before=files();
                        swipe(editor,true);swipe(editor,true);thirdPane(editor);
                        for(int repeat=0;repeat<2;repeat++) { swipe(editor,false);swipe(editor,true);thirdPane(editor); }
                        screenshot("third-"+name+"-"+color.substring(1)+"-"+reopen);
                        unchanged(before);
                        Activity current=editor;runOnMainSync(current::finish);settle();editor=null;
                    }
                }
                Activity current=home;runOnMainSync(current::finish);settle();home=null;
            }
        } finally {
            Activity finalEditor=editor,finalHome=home;
            runOnMainSync(()-> { if(finalEditor!=null) finalEditor.finish();if(finalHome!=null) finalHome.finish(); });
            for(Map.Entry<String,byte[]> entry:originals.entrySet()) Files.write(new File(folder(),entry.getKey()).toPath(),entry.getValue());
            for(String name:files().keySet()) if(!originals.containsKey(name)) new File(folder(),name).delete();
        }
    }
}
