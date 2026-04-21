package ohi.andre.consolelauncher.commands.main.raw;

import android.content.Intent;
import android.graphics.Color;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.PresetManager;
import ohi.andre.consolelauncher.managers.ThemeManager;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.options.Theme;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

public class theme extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {

        apply {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                Intent intent = new Intent(ThemeManager.ACTION_APPLY);
                intent.putExtra(ThemeManager.NAME, pack.getString());
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(intent);
                return null;
            }
        },
        set {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.CONFIG_ENTRY, CommandAbstraction.PLAIN_TEXT};
            }

            @Override
            public String exec(ExecutePack pack) {
                Object o = pack.get();
                if (!(o instanceof Theme)) {
                    return "Invalid theme element.";
                }
                Theme element = (Theme) o;
                String color = pack.getString();

                try {
                    Color.parseColor(color);
                } catch (Exception e) {
                    return "Invalid color format. Use #RRGGBB or #AARRGGBB";
                }

                LauncherSettings.setTheme(element, color);

                try {
                    if (pack.context instanceof Reloadable) {
                        ((Reloadable) pack.context).reload();
                    }
                } catch (Exception e) {}

                return element.label() + " updated to " + color;
            }
        },
        preset {
            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.THEME_PRESET};
            }

            @Override
            public String exec(ExecutePack pack) {
                String name = pack.getString().toLowerCase();
                if (!PresetManager.applyBuiltIn(name)) {
                    return "Unknown preset. Available: " + Tuils.toPlanString(PresetManager.listBuiltInPresets(), ", ");
                }

                try {
                    if (pack.context instanceof Reloadable) {
                        ((Reloadable) pack.context).reload();
                    }
                } catch (Exception e) {}

                return "Applied " + name + " preset!";
            }
        },
        standard {
            @Override
            public int[] args() {
                return new int[] {};
            }

            @Override
            public String exec(ExecutePack pack) {
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(ThemeManager.ACTION_STANDARD));
                return null;
            }
        },
        old {
            @Override
            public String exec(ExecutePack pack) {
                LocalBroadcastManager.getInstance(pack.context.getApplicationContext()).sendBroadcast(new Intent(ThemeManager.ACTION_REVERT));
                return null;
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
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_theme);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return null;
        }

        @Override
        public int[] args() {
            return new int[0];
        }
    }

    @Override
    public String[] params() {
        return Param.labels();
    };

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    public int priority() {
        return 4;
    }

    @Override
    public int helpRes() {
        return R.string.help_theme;
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }
}
