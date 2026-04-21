package ohi.andre.consolelauncher;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ohi.andre.consolelauncher.tuils.CustomExceptionHandler;
import ohi.andre.consolelauncher.MainManager;
import ohi.andre.consolelauncher.managers.RegexManager;
import ohi.andre.consolelauncher.managers.TerminalManager;
import ohi.andre.consolelauncher.managers.TimeManager;
import ohi.andre.consolelauncher.UIManager;
import ohi.andre.consolelauncher.managers.notifications.KeeperService;
import ohi.andre.consolelauncher.managers.notifications.NotificationManager;
import ohi.andre.consolelauncher.managers.notifications.NotificationMonitorService;
import ohi.andre.consolelauncher.managers.notifications.NotificationService;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;
import ohi.andre.consolelauncher.managers.xml.options.Notifications;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.managers.xml.options.Ui;
import ohi.andre.consolelauncher.tuils.LongClickableSpan;
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver;
import ohi.andre.consolelauncher.tuils.PublicIOReceiver;
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Inputable;
import ohi.andre.consolelauncher.tuils.interfaces.Outputable;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;
import ohi.andre.consolelauncher.commands.main.MainPack;

public class LauncherActivity extends AppCompatActivity implements Reloadable {

    public static LauncherActivity instance;

    public static final int COMMAND_REQUEST_PERMISSION = 10;
    public static final int STARTING_PERMISSION = 11;
    public static final int COMMAND_SUGGESTION_REQUEST_PERMISSION = 12;
    public static final int LOCATION_REQUEST_PERMISSION = 13;

    public static final int TUIXT_REQUEST = 110;
    public static final int MANAGE_STORAGE_REQUEST = 100;

    private UIManager ui;
    private MainManager main;

    private PrivateIOReceiver privateIOReceiver;
    private PublicIOReceiver publicIOReceiver;

    private boolean openKeyboardOnStart;
    private boolean canApplyTheme;
    private boolean backButtonEnabled;

    private Set<ReloadMessageCategory> categories;

    private final Runnable stopActivity = () -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    };

    private final Inputable in = new Inputable() {
        @Override
        public void in(String s) {
            if (ui != null) {
                ui.setInput(s);
            }
        }

        @Override
        public void changeHint(String s) {
            if (ui != null) {
                ui.setHint(s);
            }
        }

        @Override
        public void resetHint() {
            if (ui != null) {
                ui.setHint(Tuils.getHint(main != null ? main.getMainPack().currentDirectory.getAbsolutePath() : Tuils.getFolder().getAbsolutePath()));
            }
        }

        public void onBack() {
            if (backButtonEnabled) {
                onBackPressed();
            }
        }
    };

    private final Outputable out = new Outputable() {
        @Override
        public void onOutput(CharSequence s, int category) {
            if (ui != null) {
                ui.setOutput(s, category);
            }
        }

        @Override
        public void onOutput(int color, CharSequence s) {
            if (ui != null) {
                ui.setOutput(color, s);
            }
        }

        @Override
        public void onOutput(CharSequence s) {
            if (ui != null) {
                ui.setOutput(s, TerminalManager.CATEGORY_OUTPUT);
            }
        }

        @Override
        public void dispose() {}
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        instance = this;

        // Special check for MANAGE_EXTERNAL_STORAGE (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(android.net.Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, MANAGE_STORAGE_REQUEST);
                }
                Toast.makeText(this, "Please grant storage permissions to Re:T-UI", Toast.LENGTH_LONG).show();
                super.onCreate(savedInstanceState);
                return;
            }
        }

        XMLPrefsManager.loadCommons(this);

        boolean useSystemWP = XMLPrefsManager.getBoolean(Ui.system_wallpaper);
        if (useSystemWP) {
            setTheme(R.style.Custom_SystemWP);
        } else {
            setTheme(R.style.Custom_Solid);
        }

        if (XMLPrefsManager.getBoolean(Ui.fullscreen)) {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
        }

        super.onCreate(savedInstanceState);

        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCrashlyticsCollectionEnabled(true);
        crashlytics.log("LauncherActivity onCreate - Crashlytics initialized");

        overridePendingTransition(0, 0);

        if (isFinishing()) {
            return;
        }

        List<String> permissionsToRequest = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), LauncherActivity.STARTING_PERMISSION);
        } else {
            canApplyTheme = true;
            finishOnCreate();
        }
    }

    private void finishOnCreate() {

        Thread.currentThread().setUncaughtExceptionHandler(new CustomExceptionHandler());

        new RegexManager(LauncherActivity.this);
        new TimeManager(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(PrivateIOReceiver.ACTION_INPUT);
        filter.addAction(PrivateIOReceiver.ACTION_OUTPUT);
        filter.addAction(PrivateIOReceiver.ACTION_REPLY);

        privateIOReceiver = new PrivateIOReceiver(this, out, in);
        LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(privateIOReceiver, filter);

        IntentFilter filter1 = new IntentFilter();
        filter1.addAction(PublicIOReceiver.ACTION_CMD);
        filter1.addAction(PublicIOReceiver.ACTION_OUTPUT);

        publicIOReceiver = new PublicIOReceiver();
        ContextCompat.registerReceiver(getApplicationContext(), publicIOReceiver, filter1, "ohi.andre.consolelauncher.permission.RECEIVE_CMD", null, ContextCompat.RECEIVER_EXPORTED);

        backButtonEnabled = XMLPrefsManager.getBoolean(Behavior.back_button_enabled);

        fixOrientation();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !XMLPrefsManager.getBoolean(Ui.ignore_bar_color)) {
            Window window = getWindow();

            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(XMLPrefsManager.getColor(Theme.statusbar_color));
            window.setNavigationBarColor(XMLPrefsManager.getColor(Theme.navigationbar_color));
        }

        boolean showNotification = XMLPrefsManager.getBoolean(Behavior.tui_notification);
        Intent keeperIntent = new Intent(this, KeeperService.class);
        if (showNotification) {
            String homePath;
            try {
                java.io.File homeDir = XMLPrefsManager.get(java.io.File.class, Behavior.home_path);
                homePath = homeDir != null ? homeDir.getAbsolutePath() : Tuils.getFolder().getAbsolutePath();
            } catch (Exception e) {
                homePath = Tuils.getFolder().getAbsolutePath();
            }
            keeperIntent.putExtra(KeeperService.PATH_KEY, homePath);
            startService(keeperIntent);
        } else {
            try {
                stopService(keeperIntent);
            } catch (Exception e) {}
        }

        boolean fullscreen = XMLPrefsManager.getBoolean(Ui.fullscreen);
        if (fullscreen) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                View decorView = getWindow().getDecorView();
                decorView.post(() -> {
                    WindowInsetsController controller = getWindow().getInsetsController();
                    if (controller != null) {
                        controller.hide(WindowInsets.Type.statusBars());
                        controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                    }
                });
            } else {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }

        try {
            NotificationManager.create(this);
        } catch (Exception e) {
            Tuils.toFile(e);
        }

        boolean notifications = XMLPrefsManager.getBoolean(Notifications.show_notifications) || XMLPrefsManager.get(Notifications.show_notifications).equalsIgnoreCase("enabled");
        if (notifications) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                try {
                    ComponentName notificationComponent = new ComponentName(this, NotificationService.class);
                    PackageManager pm = getPackageManager();
                    pm.setComponentEnabledSetting(notificationComponent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

                    if (!Tuils.hasNotificationAccess(this)) {
                        Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        if (i.resolveActivity(getPackageManager()) == null) {
                            Toast.makeText(this, R.string.no_notification_access, Toast.LENGTH_LONG).show();
                        } else {
                            startActivity(i);
                        }
                    }

                    Intent monitor = new Intent(this, NotificationMonitorService.class);
                    startService(monitor);

                    Intent notificationIntent = new Intent(this, NotificationService.class);
                    startService(notificationIntent);
                    NotificationService.requestListenerRebind(this);
                } catch (NoClassDefFoundError er) {
                    Intent intent = new Intent(PrivateIOReceiver.ACTION_OUTPUT);
                    intent.putExtra(PrivateIOReceiver.TEXT, getString(R.string.output_notification_error) + Tuils.SPACE + er.toString());
                }
            } else {
                Tuils.sendOutput(Color.RED, this, R.string.notification_low_api);
            }
        }

        LongClickableSpan.longPressVibrateDuration = XMLPrefsManager.getInt(Behavior.long_click_vibration_duration);

        openKeyboardOnStart = XMLPrefsManager.getBoolean(Behavior.auto_show_keyboard);
        if (!openKeyboardOnStart) {
            this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        setContentView(R.layout.base_view);

        if (XMLPrefsManager.getBoolean(Ui.show_restart_message)) {
            CharSequence s = getIntent().getCharSequenceExtra(Reloadable.MESSAGE);
            if (s != null)
                out.onOutput(Tuils.span(s, XMLPrefsManager.getColor(Theme.restart_message_color)));
        }

        categories = new HashSet<>();

        main = new MainManager(this);

        ViewGroup mainView = (ViewGroup) findViewById(R.id.mainview);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !XMLPrefsManager.getBoolean(Ui.ignore_bar_color) && !XMLPrefsManager.getBoolean(Ui.statusbar_light_icons)) {
            mainView.setSystemUiVisibility(mainView.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        ui = new UIManager(this, mainView, main.getMainPack(), canApplyTheme, main.executer());
        ui.scheduleTypefaceRefreshes();

        main.setRedirectionListener(ui.buildRedirectionListener());
        ui.pack = main.getMainPack();

        in.in(Tuils.EMPTYSTRING);
        ui.focusTerminal();

        System.gc();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ui != null) ui.resume();
    }

    private void fixOrientation() {
        int orientation = XMLPrefsManager.getInt(Behavior.orientation);
        if (orientation == 1) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (orientation == 0) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private boolean disposed = false;

    private void dispose() {
        if (disposed) return;
        disposed = true;

        if (main != null) main.dispose();
        if (ui != null) ui.dispose();
        if (privateIOReceiver != null)
            LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(privateIOReceiver);
        if (publicIOReceiver != null) try {
            unregisterReceiver(publicIOReceiver);
        } catch (Exception e) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dispose();
    }

    public UIManager getUIManager() {
        return ui;
    }

    @Override
    public void onBackPressed() {
        if (ui != null) {
            ui.onBackPressed();
        } else if (main == null) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    public void reload() {
        XMLPrefsManager.dispose();
        Tuils.cancelFont();
        Intent intent = getIntent();
        finish();
        startActivity(intent);
    }

    public void addMessage(String title, String message) {
        if (ui != null) {
            ui.setOutput(title + ": " + message, TerminalManager.CATEGORY_OUTPUT);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && ui != null) {
            ui.focusTerminal();
            ui.scheduleTypefaceRefreshes();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == TUIXT_REQUEST) {
            if (resultCode == TuixtActivity.SAVE_PRESSED) {
                reload();
            }
        } else if (requestCode == MANAGE_STORAGE_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (android.os.Environment.isExternalStorageManager()) {
                    reload();
                } else {
                    Toast.makeText(this, "Storage permission is required for Re:T-UI to function.", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STARTING_PERMISSION) {
            canApplyTheme = true;
            finishOnCreate();
        } else if (requestCode == COMMAND_REQUEST_PERMISSION || requestCode == COMMAND_SUGGESTION_REQUEST_PERMISSION || requestCode == LOCATION_REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                reload();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasExtra(Reloadable.MESSAGE)) {
            reload();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public enum ReloadMessageCategory {
        THEME,
        BEHAVIOR,
        UI,
        NOTIFICATIONS,
        APPS,
        RSS
    }
}
