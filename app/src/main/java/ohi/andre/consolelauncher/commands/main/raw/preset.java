package ohi.andre.consolelauncher.commands.main.raw;

import java.util.List;

import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand;
import ohi.andre.consolelauncher.managers.PresetManager;
import ohi.andre.consolelauncher.tuils.Tuils;
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable;

public class preset extends ParamCommand {

    private enum Param implements ohi.andre.consolelauncher.commands.main.Param {
        save {
            @Override
            public String exec(ExecutePack pack) {
                String name = pack.getString();
                try {
                    PresetManager.save(name);
                    if (pack.context instanceof Reloadable) {
                        ((Reloadable) pack.context).addMessage("preset", "Saved preset: " + name.trim());
                        ((Reloadable) pack.context).reload();
                    }
                    return "Preset '" + name.trim() + "' saved.";
                } catch (IllegalArgumentException e) {
                    return e.getMessage();
                } catch (Exception e) {
                    return pack.context.getString(R.string.output_error);
                }
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PRESET_NAME};
            }
        },
        apply {
            @Override
            public String exec(ExecutePack pack) {
                String name = pack.getString();
                try {
                    PresetManager.apply(name);

                    if (pack.context instanceof Reloadable) {
                        ((Reloadable) pack.context).addMessage("preset", "Applied preset: " + name.trim());
                        ((Reloadable) pack.context).reload();
                    }

                    return "Preset '" + name.trim() + "' applied.";
                } catch (IllegalArgumentException e) {
                    return e.getMessage();
                } catch (Exception e) {
                    return pack.context.getString(R.string.output_error);
                }
            }

            @Override
            public int[] args() {
                return new int[] {CommandAbstraction.PRESET_NAME};
            }
        },
        ls {
            @Override
            public String exec(ExecutePack pack) {
                List<String> list = PresetManager.listAllPresetNames();
                if (list.isEmpty()) return "No presets found.";
                return Tuils.toPlanString(list, "\n");
            }

            @Override
            public int[] args() {
                return new int[0];
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
            return pack.context.getString(R.string.help_preset);
        }

        @Override
        public String onArgNotFound(ExecutePack pack, int index) {
            return pack.context.getString(R.string.help_preset);
        }
    }

    @Override
    public String[] params() {
        return Param.labels();
    }

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
        return R.string.help_preset;
    }

    @Override
    protected String doThings(ExecutePack pack) {
        return null;
    }
}
