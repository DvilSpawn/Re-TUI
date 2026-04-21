package ohi.andre.consolelauncher.commands.main.raw;

import android.app.WallpaperManager;
import android.content.Intent;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand;
import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

public class wallpaper extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {
        static_wallpaper {
            @Override
            public String exec(ExecutePack pack) {
                return openStaticWallpaperPicker(pack);
            }

            @Override
            public String label() {
                return Tuils.MINUS + "static";
            }
        },
        live {
            @Override
            public String exec(ExecutePack pack) {
                try {
                    pack.context.startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                    return Tuils.EMPTYSTRING;
                } catch (Exception e) {
                    return pack.context.getString(R.string.output_error);
                }
            }
        },
        auto {
            @Override
            public String exec(ExecutePack pack) {
                if (pack instanceof MainPack) {
                    ((MainPack) pack).redirectator.prepareRedirection(new WallpaperAutoConfirmation());
                    return "Please confirm if you have saved your preset (Yes/No)";
                }
                return enableWallpaperAuto(pack);
            }
        };

        @Override
        public int[] args() {
            return new int[0];
        }

        static Param get(String p) {
            p = p.toLowerCase();
            for (Param param : values()) {
                if (param.matches(p)) {
                    return param;
                }
            }
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

        boolean matches(String value) {
            if (value == null) {
                return false;
            }

            String label = label().toLowerCase();
            if (value.equals(label)) {
                return true;
            }

            if (label.startsWith(Tuils.MINUS)) {
                return value.equals(label.substring(1));
            }

            return false;
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int n) {
            return pack.context.getString(R.string.help_wallpaper);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return pack.context.getString(R.string.help_wallpaper);
        }
    }

    @Override
    protected ohi.andre.consolelauncher.commands.main.Param paramForString(MainPack pack, String param) {
        return Param.get(param);
    }

    @Override
    protected String doThings(ExecutePack pack) {
        if (pack.get(ohi.andre.consolelauncher.commands.main.Param.class, 0) != null) {
            return null;
        }
        return openStaticWallpaperPicker(pack);
    }

    private static String openStaticWallpaperPicker(ExecutePack pack) {
        try {
            pack.context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER), pack.context.getString(R.string.app_name)));
            return Tuils.EMPTYSTRING;
        } catch (Exception e) {
            return pack.context.getString(R.string.output_error);
        }
    }

    private static String enableWallpaperAuto(ExecutePack pack) {
        LauncherSettings.setAutoColorPick(true);

        if (pack.context instanceof Reloadable) {
            ((Reloadable) pack.context).addMessage("wallpaper", "Enabled wallpaper-derived colors");
            ((Reloadable) pack.context).reload();
            return Tuils.EMPTYSTRING;
        }

        return "Wallpaper-derived colors enabled.";
    }

    private static class WallpaperAutoConfirmation extends RedirectCommand {
        @Override
        public String onRedirect(ExecutePack pack) {
            MainPack mainPack = (MainPack) pack;
            String answer = Tuils.EMPTYSTRING;
            if (!afterObjects.isEmpty() && afterObjects.get(0) != null) {
                answer = afterObjects.get(0).toString().trim();
            }

            mainPack.redirectator.cleanup();
            if ("yes".equalsIgnoreCase(answer) || "y".equalsIgnoreCase(answer)) {
                return enableWallpaperAuto(pack);
            }
            return "Wallpaper auto cancelled.";
        }

        @Override
        public int getHint() {
            return R.string.hint_wallpaper_auto_confirm;
        }

        @Override
        public boolean isWaitingPermission() {
            return false;
        }

        @Override
        public int[] argType() {
            return new int[0];
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public int helpRes() {
            return R.string.help_wallpaper;
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int indexNotFound) {
            return null;
        }

        @Override
        public String onNotArgEnough(ExecutePack pack, int nArgs) {
            return null;
        }

        @Override
        public String exec(ExecutePack pack) {
            return null;
        }
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
        return R.string.help_wallpaper;
    }
}
