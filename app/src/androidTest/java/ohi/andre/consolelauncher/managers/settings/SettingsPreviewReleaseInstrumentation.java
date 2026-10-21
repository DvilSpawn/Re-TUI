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
        return (ViewGroup)((ViewGroup)panel.getChildAt(0)).getChildAt(1);
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
    @Override public void runOnMainSync(Runnable action) {
        final java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        super.runOnMainSync(()-> { try { action.run(); } catch(Throwable error) { failure.set(error); } });
        if(failure.get()!=null) throw new AssertionError("UI assertion failed",failure.get());
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments);background=arguments.getString("background", "original");mode=arguments.getString("mode", "preview");start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try { if("hotfix5".equals(mode)) hotfixFiveJourney(); else if("hotfix4".equals(mode)) hotfixFourJourney(); else if("hotfix2".equals(mode)) hotfixTwoJourney(); else if("private".equals(mode)) privateJourney(); else if("changelog".equals(mode)) changelogJourney(); else journey();result.putString("stream","\nOK: " + mode + " release journey\n");finish(Activity.RESULT_OK,result); }
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
        getTargetContext().getSharedPreferences("changelogPrefs",0).edit().putInt("last_seen_version_code",releaseVersion()).commit();
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
    @SuppressWarnings("deprecation")
    private int releaseVersion() {
        try { return getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionCode; }
        catch(android.content.pm.PackageManager.NameNotFoundException error) { throw new AssertionError(error); }
    }
    private String setting(String file,String key) throws Exception {
        Document doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(folder(),file));
        Element node=(Element)doc.getElementsByTagName(key).item(0);
        return node==null?null:node.getAttribute("value");
    }
    private void writeFixture(File file,String xml) throws Exception {
        file.getParentFile().mkdirs();Files.write(file.toPath(),xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private void hotfixFourJourney() throws Exception {
        Map<String,byte[]> original=files(), backups=new HashMap<>();
        File preset=new File(folder(),"presets/compatibility-hotfix4-fixture");
        require(!preset.exists(),"Fixture already exists");
        String clock="[size=30]HH:mm[/size]@EEE, dd MMM";
        String behavior="<BEHAVIOR><time_format value=\""+clock+"\"/><auto_show_keyboard value=\"false\"/></BEHAVIOR>";
        Activity activity=null;
        String[] names={"behavior.xml","theme.xml","ui.xml"};
        for(String name:names) {
            File backup=new File(folder(),name+".pre-migration");
            if(backup.isFile()) { backups.put(name,Files.readAllBytes(backup.toPath()));Files.delete(backup.toPath()); }
        }
        try {
            writeFixture(new File(folder(),"behavior.xml"),behavior);
            writeFixture(new File(folder(),"theme.xml"),"<THEME><input_bg value=\"#ff000000\"/><status_lines_bg value=\"#ff000000,#FF123456\"/></THEME>");
            writeFixture(new File(folder(),"ui.xml"),"<UI><display_margin_mm value=\"1,2,3,4\"/><status_lines_alignment value=\"1,0,-1\"/></UI>");
            getTargetContext().getSharedPreferences("retui_startup",0).edit().putBoolean("initialized",true).putBoolean("complete",true).putBoolean("active",false).commit();
            getTargetContext().getSharedPreferences("changelogPrefs",0).edit().putInt("last_seen_version_code",releaseVersion()).commit();
            activity=home();settle();
            require(clock.equals(setting("behavior.xml","status_time_format")),"Status clock lost");
            require(clock.equals(setting("behavior.xml","output_time_format")),"Output clock lost");
            require("#ff000000".equals(setting("theme.xml","input_background_color")),"Opaque black changed");
            require("#FF123456".equals(setting("theme.xml","device_status_background_color")),"Status list lost");
            require("1,2,3,4".equals(setting("ui.xml","display_margin_top_section")),"Margin lost");
            require(Arrays.equals(behavior.getBytes(java.nio.charset.StandardCharsets.UTF_8),Files.readAllBytes(new File(folder(),"behavior.xml.pre-migration").toPath())),"Recovery copy differs");
            screenshot("hotfix4-upgraded-home");
            writeFixture(new File(preset,"theme.xml"),"<THEME><input_color value=\"#112233\"/><input_text_color value=\"#445566\"/><status_lines_bg value=\"#ff000000\"/></THEME>");
            writeFixture(new File(preset,"suggestions.xml"),"<SUGGESTIONS/>");
            writeFixture(new File(preset,"ui.xml"),"<UI><display_margin_mm value=\"2,3,4,5\"/></UI>");
            writeFixture(new File(preset,"behavior.xml"),"<BEHAVIOR><time_format value=\"HH:mm:ss@EEE\"/></BEHAVIOR>");
            command(activity,"preset -apply compatibility-hotfix4-fixture");settle();
            require("#445566".equals(setting("theme.xml","input_text_color")),"Preset alias precedence failed");
            require("2,3,4,5".equals(setting("ui.xml","display_margin_top_section")),"Saved preset margin lost");
            require("HH:mm:ss@EEE".equals(setting("behavior.xml","output_time_format")),"Saved preset output clock lost");
            Map<String,byte[]> beforeConflict=files();
            writeFixture(new File(preset,"theme.xml"),"<THEME><input_text_color value=\"#AA0000\"/></THEME>");
            writeFixture(new File(preset,"behavior.xml"),"<BEHAVIOR><status_time_format value=\"HH:mm\"/><status_time_format value=\"ss\"/></BEHAVIOR>");
            activity=home();command(activity,"preset -apply compatibility-hotfix4-fixture");settle();
            for(String name:names) require(Arrays.equals(beforeConflict.get(name),Files.readAllBytes(new File(folder(),name).toPath())),"Conflict partially applied "+name);
            changelogJourney();
        } finally {
            if(launcher!=null) { Activity last=launcher;runOnMainSync(last::finish); }
            for(Map.Entry<String,byte[]> entry:original.entrySet()) Files.write(new File(folder(),entry.getKey()).toPath(),entry.getValue());
            for(String name:names) {
                File backup=new File(folder(),name+".pre-migration");
                if(backups.containsKey(name)) Files.write(backup.toPath(),backups.get(name));else backup.delete();
            }
            File[] children=preset.listFiles();if(children!=null) for(File file:children) file.delete();preset.delete();
        }
    }
    private void hotfixTwoJourney() throws Exception {
        changelogJourney();
        Activity help=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),
            "ohi.andre.consolelauncher.help.OfflineHelpActivity").putExtra("topic","termux-workspace").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            settle();
            runOnMainSync(()-> {
                TextView output=help.findViewById(resource("offline_help_output","id"));
                require(output.getText().toString().contains("10 " + text(resource("offline_help_root","string"))),"Article has no Help home");
                ((EditText)help.findViewById(resource("offline_help_input","id"))).setText("10");
                help.findViewById(resource("offline_help_enter","id")).performClick();
                require(output.getText().toString().startsWith("1 " + text(resource("offline_help_getting_started_title","string"))),"Help home did not reset directory");
            });
            settle();screenshot("hotfix2-help-home");
        } finally { runOnMainSync(help::finish); }
        Activity notes=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),
            "ohi.andre.consolelauncher.notes.RememberActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            settle();
            runOnMainSync(()-> {
                View body=notes.findViewById(resource("offline_help_panel","id"));
                View toolbar=notes.getWindow().getDecorView().findViewWithTag("notes-bottom-dock");
                require(body!=null && toolbar!=null && toolbar.getParent()==body,"Notes does not use shared pane");
                require(toolbar.getBackground()==null,"Notes toolbar is boxed");
                require((notes.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)!=0,"Notes backdrop missing");
            });
            screenshot("hotfix2-notes");
        } finally { runOnMainSync(notes::finishAndRemoveTask); }
    }
    private void changelogJourney() throws Exception {
        Activity activity=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"ohi.andre.consolelauncher.help.ChangelogActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));settle();
        try {
            runOnMainSync(()-> {
                String notes=((TextView)activity.findViewById(resource("offline_help_output","id"))).getText().toString();
                require(notes.contains("Hotfix 2") && notes.contains("Hotfix #1") && notes.contains("Termux, connected through SSH"),"Appended or previous notes missing");
                require(notes.contains("Build " + releaseVersion()),"Release number missing");
            });
            require(getTargetContext().getSharedPreferences("changelogPrefs",0).getInt("last_seen_version_code",0)==releaseVersion(),"Release not acknowledged");
            screenshot("hotfix-changelog");
        } finally { runOnMainSync(activity::finish); }
    }

    private TextView label(Activity activity, String value) {
        for(View view:views(activity.getWindow().getDecorView()))
            if(view instanceof TextView && ((TextView)view).getText().toString().equals(value)) return (TextView)view;
        throw new AssertionError("Missing control: " + value);
    }
    private TextView frameSelector(Activity activity) {
        for(View view:views(activity.getWindow().getDecorView()))
            if(view instanceof TextView && ((TextView)view).getText().toString().startsWith("FRAME:")) return (TextView)view;
        throw new AssertionError("Missing inline frame selector");
    }
    private void hotfixFiveJourney() throws Exception {
        changelogJourney();
        Map<String,byte[]> original=files();Activity editor=null,frames=null;
        getTargetContext().getSharedPreferences("retui_startup",0).edit().putBoolean("initialized",true).putBoolean("complete",true).putBoolean("active",false).commit();
        fixture("behavior.xml","BEHAVIOR",Collections.singletonMap("auto_show_keyboard","false"));
        fixture("behavior.xml","BEHAVIOR",Collections.singletonMap("battery_progress_bar","true"));
        fixture("behavior.xml","BEHAVIOR",Collections.singletonMap("battery_progress_bar_length","10"));
        fixture("behavior.xml","BEHAVIOR",Collections.singletonMap("battery_progress_bar_symbol","#"));
        fixture("ui.xml","UI",Collections.singletonMap("show_battery","true"));
        try {
            home();settle();
            editor=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),EDITOR)
                .putExtra("path",new File(folder(),"ui.xml").getAbsolutePath()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();hideKeyboard(editor);final Activity currentEditor=editor;
            Map<String,byte[]> before=files();
            runOnMainSync(()-> {
                boolean battery=false;
                for(View view:views(thumbnail(currentEditor)))
                    if(view instanceof TextView && ((TextView)view).getText().toString().contains("#")) battery=true;
                require(battery,"Live battery bar missing from preview");
            });
            for(String page:Arrays.asList("Terminal","Modules","Help","Calculator","ANSI")) {
                swipe(editor,true);
                runOnMainSync(()-> {
                    boolean found=false;
                    for(View view:views(currentEditor.getWindow().getDecorView()))
                        if(view instanceof TextView && ((TextView)view).getText().toString().contains(page+" ·")) found=true;
                    require(found,"Preview page missing: "+page);
                });
            }
            screenshot("hotfix5-ansi");unchanged(before);
            runOnMainSync(currentEditor::finish);editor=null;
            frames=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),
                "ohi.andre.consolelauncher.commands.tuixt.ThemerActivity").putExtra("section","frames").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            settle();final Activity currentFrames=frames;before=files();
            runOnMainSync(()->frameSelector(currentFrames).performClick());settle();
            runOnMainSync(()-> {
                require(frameSelector(currentFrames).getText().toString().endsWith("▴"),"Selector did not expand");
                label(currentFrames,"Sprout Lands icon button").performClick();
            });settle();
            runOnMainSync(()-> {
                String selected=frameSelector(currentFrames).getText().toString();
                require(selected.contains("Sprout Lands icon button") && selected.endsWith("▾"),"Selection did not update inline");
                frameSelector(currentFrames).performClick();
            });settle();
            runOnMainSync(()->label(currentFrames,text(resource("frame_selector_default","string"))).performClick());settle();
            unchanged(before);screenshot("hotfix5-frame-selector");
        } finally {
            final Activity finalEditor=editor,finalFrames=frames;
            runOnMainSync(()-> { if(finalEditor!=null)finalEditor.finish();if(finalFrames!=null)finalFrames.finish(); });
            for(Map.Entry<String,byte[]> entry:original.entrySet()) Files.write(new File(folder(),entry.getKey()).toPath(),entry.getValue());
        }
    }
    private void journey() throws Exception {
        Map<String,byte[]> originals=files();
        Activity home=null,editor=null;
        getTargetContext().getSharedPreferences("retui_startup",0).edit().putBoolean("initialized",true).putBoolean("complete",true).putBoolean("active",false).commit();
        getTargetContext().getSharedPreferences("changelogPrefs",0).edit().putInt("last_seen_version_code",releaseVersion()).commit();
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
