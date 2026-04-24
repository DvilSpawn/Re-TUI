package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;

import java.io.File;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.APICommand;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.notifications.NotificationManager;
import ohi.andre.consolelauncher.managers.notifications.NotificationService;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager;
import ohi.andre.consolelauncher.managers.xml.options.Notifications;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

import static android.os.Build.VERSION_CODES.JELLY_BEAN_MR2;

/**
 * Created by francescoandreuzzi on 29/04/2017.
 */

public class notifications extends ParamCommand implements APICommand {

    @Override
    public boolean willWorkOn(int api) {
        return api >= JELLY_BEAN_MR2;
    }

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        inc {
            @Override
            public String exec(ExecutePack pack) {
                String output = NotificationManager.setState(pack.getLaunchInfo().componentName.getPackageName(), true);
                NotificationService.requestReload(pack.context);
                if(output == null || output.length() == 0) return null;
                return output;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.VISIBLE_PACKAGE};
            }

            @Override
            public String onArgNotFound(ExecutePack pack, int index) {
                return pack.context.getString(R.string.output_appnotfound);
            }
        },
        exc {
            @Override
            public String exec(ExecutePack pack) {
                String output = NotificationManager.setState(pack.getLaunchInfo().componentName.getPackageName(), false);
                NotificationService.requestReload(pack.context);
                if(output == null || output.length() == 0) return null;
                return output;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.VISIBLE_PACKAGE};
            }

            @Override
            public String onArgNotFound(ExecutePack pack, int index) {
                return pack.context.getString(R.string.output_appnotfound);
            }
        },
        color {
            @Override
            public String exec(ExecutePack pack) {
                String color = pack.getString();
                String output = NotificationManager.setColor(pack.getLaunchInfo().componentName.getPackageName(), color);
                NotificationService.requestReload(pack.context);
                if(output == null || output.length() == 0) return null;
                return output;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.COLOR, CommandAbstraction.VISIBLE_PACKAGE};
            }

            @Override
            public String onArgNotFound(ExecutePack pack, int index) {
                int res;
                if(index == 1) res = R.string.output_invalidcolor;
                else res = R.string.output_appnotfound;

                return pack.context.getString(res);
            }
        },
        format {
            @Override
            public String exec(ExecutePack pack) {
                String s = pack.getString();
                String output = NotificationManager.setFormat(pack.getLaunchInfo().componentName.getPackageName(), s);
                NotificationService.requestReload(pack.context);
                if(output == null || output.length() == 0) return null;
                return output;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.NO_SPACE_STRING, CommandAbstraction.VISIBLE_PACKAGE};
            }

            @Override
            public String onArgNotFound(ExecutePack pack, int index) {
                return pack.context.getString(R.string.invalid_integer);
            }
        },
        add_filter {
            @Override
            public String exec(ExecutePack pack) {
                int id = pack.getInt();
                String output = NotificationManager.addFilter(pack.getString(), id);
                NotificationService.requestReload(pack.context);
                if(output == null || output.length() == 0) return null;
                return output;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String onArgNotFound(ExecutePack pack, int index) {
                return pack.context.getString(R.string.invalid_integer);
            }
        },
        add_format {
            @Override
            public String exec(ExecutePack pack) {
                int id = pack.getInt();
                String output = NotificationManager.addFormat(pack.getString(), id);
                NotificationService.requestReload(pack.context);
                if(output == null || output.length() == 0) return null;
                return output;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.INT, CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String onArgNotFound(ExecutePack pack, int index) {
                return pack.context.getString(R.string.invalid_integer);
            }
        },
        rm_filter {
            @Override
            public String exec(ExecutePack pack) {
                String output = NotificationManager.rmFilter(pack.getInt());
                NotificationService.requestReload(pack.context);
                if(output == null || output.length() == 0) return null;
                return output;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.INT};
            }

            @Override
            public String onArgNotFound(ExecutePack pack, int index) {
                return pack.context.getString(R.string.invalid_integer);
            }
        },
        rm_format {
            @Override
            public String exec(ExecutePack pack) {
                String output = NotificationManager.rmFormat(pack.getInt());
                NotificationService.requestReload(pack.context);
                if(output == null || output.length() == 0) return null;
                return output;
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.INT};
            }

            @Override
            public String onArgNotFound(ExecutePack pack, int index) {
                return pack.context.getString(R.string.invalid_integer);
            }
        },
        file {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                pack.context.startActivity(Tuils.openFile(pack.context, new File(Tuils.getFolder(), NotificationManager.PATH)));
                return null;
            }
        },
        access {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                try {
                    pack.context.startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                } catch (Exception e) {
                    return pack.context.getString(R.string.activity_not_found);
                }
                return null;
            }
        },
        tutorial {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                pack.context.startActivity(Tuils.webPage("https://github.com/DvilSpawn/Re-TUI/wiki/Notifications"));
                return null;
            }
        },
        on {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                return setNotificationTerminal(pack, true);
            }
        },
        off {
            @Override
            public int[] args() {
                return new int[0];
            }

            @Override
            public String exec(ExecutePack pack) {
                return setNotificationTerminal(pack, false);
            }
        };

        static Param get(String p) {
            p = p.toLowerCase();
            Param[] ps = values();
            for (Param p1 : ps)
                if (p.endsWith(p1.label()))
                    return p1;
            return null;
        }

        static String[] labels() {
            Param[] ps = values();
            String[] ss = new String[ps.length];

            for (int count = 0; count < ps.length; count++) {
                ss[count] = ps[count].label();
            }

            return ss;
        }

        @Override
        public String label() {
            return Tuils.MINUS + name();
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return null;
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_notifications);
        }
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }

    private static String setNotificationTerminal(ExecutePack pack, boolean enabled) {
        LauncherSettings.set(pack.context, Notifications.show_notifications, String.valueOf(enabled));

        if (pack.context instanceof Reloadable) {
            ((Reloadable) pack.context).addMessage("notifications", enabled ? "Notification terminal enabled" : "Notification terminal disabled");
            ((Reloadable) pack.context).reload();
            return Tuils.EMPTYSTRING;
        }

        return enabled ? "Notification terminal enabled." : "Notification terminal disabled.";
    }

    @Override
    public String[] params() {
        return Param.labels();
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_notifications;
    }
}
