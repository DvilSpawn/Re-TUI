package ohi.andre.consolelauncher.commands.main.raw;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import ohi.andre.consolelauncher.R;
import ohi.andre.consolelauncher.commands.CommandAbstraction;
import ohi.andre.consolelauncher.commands.ExecutePack;
import ohi.andre.consolelauncher.commands.main.MainPack;
import ohi.andre.consolelauncher.tuils.Tuils;

public class post implements CommandAbstraction {

    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType TEXT = MediaType.get("text/plain; charset=utf-8");

    @Override
    public String exec(ExecutePack pack) {
        MainPack info = (MainPack) pack;
        if (pack.args.length < 2) {
            return onNotArgEnough(pack, pack.args.length);
        }

        String url = (String) pack.args[0];
        String bodyContent = (String) pack.args[1];

        MediaType mediaType = bodyContent.trim().startsWith("{") || bodyContent.trim().startsWith("[") ? JSON : TEXT;
        RequestBody body = RequestBody.create(bodyContent, mediaType);
        
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        final Handler handler = new Handler(Looper.getMainLooper());
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                final String error = e.toString();
                handler.post(() -> Tuils.sendOutput(info.context, "POST Error: " + error));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    final String resBody = r.body() != null ? r.body().string() : "Empty Response";
                    final int code = r.code();
                    handler.post(() -> Tuils.sendOutput(info.context, "POST [" + code + "]: " + resBody));
                }
            }
        });

        return "Sending POST request...";
    }

    @Override
    public int helpRes() {
        return R.string.help_post;
    }

    @Override
    public int[] argType() {
        return new int[]{CommandAbstraction.PLAIN_TEXT, CommandAbstraction.PLAIN_TEXT};
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public String onNotArgEnough(ExecutePack pack, int nArgs) {
        MainPack info = (MainPack) pack;
        return info.res.getString(helpRes());
    }

    @Override
    public String onArgNotFound(ExecutePack pack, int index) {
        return null;
    }
}
