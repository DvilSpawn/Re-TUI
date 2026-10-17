package ohi.andre.consolelauncher.managers.widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** Runs in the standalone test APK process without dependencies on the target app. */
public final class WidgetPaneFixtureProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id, "Widget fixture — tap me");
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if ("retui.widget.fixture.TAP".equals(intent.getAction())) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
            update(context, AppWidgetManager.getInstance(context), id, "Widget tapped");
        }
    }

    private void update(Context context, AppWidgetManager manager, int id, String label) {
        RemoteViews views = new RemoteViews(context.getPackageName(), android.R.layout.simple_list_item_1);
        views.setTextViewText(android.R.id.text1, label);
        views.setTextColor(android.R.id.text1, android.graphics.Color.WHITE);
        views.setInt(android.R.id.text1, "setBackgroundColor", 0xff263238);
        Intent tap = new Intent(context, WidgetPaneFixtureProvider.class)
            .setAction("retui.widget.fixture.TAP").putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        views.setOnClickPendingIntent(android.R.id.text1, PendingIntent.getBroadcast(context, id, tap,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(id, views);
    }
}
