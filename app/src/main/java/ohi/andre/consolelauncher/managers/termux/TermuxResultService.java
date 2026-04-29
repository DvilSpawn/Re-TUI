package ohi.andre.consolelauncher.managers.termux;

import android.app.IntentService;
import android.content.Intent;

import androidx.annotation.Nullable;

public class TermuxResultService extends IntentService {

    public TermuxResultService() {
        super("TermuxResultService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        TermuxResultReceiver.forwardResult(getApplicationContext(), intent);
    }
}
