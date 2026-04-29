package ohi.andre.consolelauncher.commands;

import android.content.Context;
import android.os.Build;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import ohi.andre.consolelauncher.commands.main.specific.APICommand;
import ohi.andre.consolelauncher.tuils.Tuils;

public class CommandGroup {

    private String packageName;
    private CommandAbstraction[] commands;
    private String[] commandNames;

    public CommandGroup(Context c, String packageName) {
        this.packageName = packageName;

        List<String> cmds;
        try {
            cmds = Tuils.getClassesInPackage(packageName, c);
        } catch (IOException e) {
            cmds = new ArrayList<>();
        }

        if (cmds.isEmpty()) {
            if (packageName.equals("ohi.andre.consolelauncher.commands.main.raw")) {
                cmds.addAll(Arrays.asList("airplane", "alias", "apps", "bbman", "beep", "bluetooth", "brightness", "calc", "call", "changelog", "clear", "cntcts", "config", "ctrlc", "dashboard", "data", "debug", "devutils", "donate", "exit", "flash", "hack", "help", "htmlextract", "install", "location", "music", "notes", "notifications", "open", "pomodoro", "post", "preset", "rate", "refresh", "regex", "reply", "restart", "rss", "search", "settings", "share", "shortcut", "status", "stopwatch", "termux", "theme", "themer", "time", "timer", "tui", "tuiweather", "tuixt", "tutorial", "uninstall", "username", "vibrate", "volume", "wallpaper", "webhook", "wifi"));
            } else if (packageName.equals("ohi.andre.consolelauncher.commands.tuixt.raw")) {
                cmds.addAll(Arrays.asList("exit", "help", "save"));
            }
        }

        List<CommandAbstraction> cmdAbs = new ArrayList<>();
        Iterator<String> iterator = cmds.iterator();
        while (iterator.hasNext()) {
            String s = iterator.next();
            CommandAbstraction ca = buildCommand(s);
            if(ca != null && ( !(ca instanceof APICommand) || ((APICommand) ca).willWorkOn(Build.VERSION.SDK_INT))) {
                cmdAbs.add(ca);
            } else {
                iterator.remove();
            }
        }

        Collections.sort(cmds);
        commandNames = new String[cmds.size()];
        cmds.toArray(commandNames);

        Collections.sort(cmdAbs, (o1, o2) -> o2.priority() - o1.priority());
        commands = new CommandAbstraction[cmdAbs.size()];
        cmdAbs.toArray(commands);
    }

    public CommandAbstraction getCommandByName(String name) {
        for(CommandAbstraction c : commands) {
            if(c.getClass().getSimpleName().equalsIgnoreCase(name)) {
                return c;
            }
        }

        return null;
    }

    private CommandAbstraction buildCommand(String name) {
        String fullCmdName = packageName + Tuils.DOT + name;
        try {
            Class<CommandAbstraction> clazz = (Class<CommandAbstraction>) Class.forName(fullCmdName);
            if(CommandAbstraction.class.isAssignableFrom(clazz)) {
                Constructor<CommandAbstraction> constructor = clazz.getConstructor();
                return constructor.newInstance();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public CommandAbstraction[] getCommands() {
        return commands;
    }

    public String[] getCommandNames() {
        return commandNames;
    }

}
