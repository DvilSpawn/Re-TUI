package ohi.andre.consolelauncher.commands.main.raw;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.managers.WebhookManager;
import ohi.andre.consolelauncher.tuils.Tuils;

public class webhook implements CommandAbstraction {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType TEXT = MediaType.get("text/plain; charset=utf-8");

    @Override
    public String exec(ExecutePack pack) {
        MainPack info = (MainPack) pack;
        if (pack.args.length < 1) {
            return onNotArgEnough(pack, pack.args.length);
        }

        String subCommand = (String) pack.args[0];

        if (subCommand.equalsIgnoreCase("-add")) {
            if (pack.args.length < 4) return "Usage: webhook -add [name] [url] [body_template]";
            String name = (String) pack.args[1];
            String url = (String) pack.args[2];
            String body = (String) pack.args[3];
            info.webhookManager.add(name, url, body);
            return "Webhook " + name + " added.";
        } else if (subCommand.equalsIgnoreCase("-rm")) {
            if (pack.args.length < 2) return "Usage: webhook -rm [name]";
            String name = (String) pack.args[1];
            info.webhookManager.remove(name);
            return "Webhook " + name + " removed.";
        } else if (subCommand.equalsIgnoreCase("-ls")) {
            List<WebhookManager.Webhook> hooks = info.webhookManager.getWebhooks();
            if (hooks.isEmpty()) return "No webhooks configured.";
            StringBuilder sb = new StringBuilder();
            for (WebhookManager.Webhook w : hooks) {
                sb.append(w.name).append(" -> ").append(w.url).append(Tuils.NEWLINE);
            }
            return sb.toString().trim();
        }

        // Trigger webhook
        WebhookManager.Webhook w = info.webhookManager.getWebhook(subCommand);
        if (w == null) return "Webhook not found: " + subCommand;

        String[] webhookArgs;
        if (pack.args.length > 1) {
            Object[] rawArgs = Arrays.copyOfRange(pack.args, 1, pack.args.length);
            webhookArgs = new String[rawArgs.length];
            for (int i = 0; i < rawArgs.length; i++) {
                webhookArgs[i] = rawArgs[i].toString();
            }
        } else {
            webhookArgs = new String[0];
        }

        String bodyContent = w.substitute(webhookArgs);
        if (webhookArgs.length > 0) {
            info.historyManager.add(w.name, Tuils.toPlanString(webhookArgs, Tuils.SPACE));
        }
        MediaType mediaType = bodyContent.trim().startsWith("{") || bodyContent.trim().startsWith("[") ? JSON : TEXT;
        RequestBody body = RequestBody.create(bodyContent, mediaType);
        
        Request request = new Request.Builder()
                .url(w.url)
                .post(body)
                .build();

        final Handler handler = new Handler(Looper.getMainLooper());
        
        info.client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                final String error = e.toString();
                handler.post(() -> Tuils.sendOutput(info.context, "Webhook [" + w.name + "] Error: " + error));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    final String resBody = r.body() != null ? r.body().string() : "Empty Response";
                    final int code = r.code();
                    handler.post(() -> Tuils.sendOutput(info.context, "Webhook [" + w.name + "] Response [" + code + "]: " + resBody));
                }
            }
        });

        return "Triggering webhook: " + w.name;
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.TEXTLIST};
    }

    @Override
    public int priority() {
        return 3;
    }

    @Override
    public int helpRes() {
        return R.string.help_webhook;
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int indexNotFound) {
        return null;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        MainPack info = (MainPack) pack;
        return info.res.getString(helpRes());
    }
}
