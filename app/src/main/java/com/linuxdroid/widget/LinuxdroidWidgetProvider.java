package com.linuxdroid.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.File;

/**
 * Widget providing a list to launch scripts in $HOME/.linuxdroid/shortcuts/.
 * <p>
 * See https://developer.android.com/guide/topics/appwidgets/index.html
 */
public final class LinuxdroidWidgetProvider extends AppWidgetProvider {

    private static final String LIST_ITEM_CLICKED_ACTION = "com.linuxdroid.widgets.LIST_ITEM_CLICKED_ACTION";
    private static final String REFRESH_WIDGET_ACTION = "com.linuxdroid.widgets.REFRESH_WIDGET_ACTION";
    public static final String EXTRA_CLICKED_FILE = "com.linuxdroid.widgets.EXTRA_CLICKED_FILE";

    public static final String LINUXDROID_SERVICE = "com.linuxdroid.app.LinuxdroidService";
    public static final String ACTION_EXECUTE = "com.linuxdroid.service_execute";


    /**
     * "This is called to update the App Widget at intervals defined by the updatePeriodMillis attribute in the
     * AppWidgetProviderInfo (see Adding the AppWidgetProviderInfo Metadata above). This method is also called when the
     * user adds the App Widget, so it should perform the essential setup, such as define event handlers for Views and
     * start a temporary Service, if necessary. However, if you have declared a configuration Activity, this method is
     * not called when the user adds the App Widget, but is called for the subsequent updates. It is the responsibility
     * of the configuration Activity to perform the first update when configuration is done. (See Creating an App Widget
     * Configuration Activity below.)"
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            // The empty view is displayed when the collection has no items. It should be a sibling
            // of the collection view:
            rv.setEmptyView(R.id.widget_list, R.id.empty_view);

            // Setup intent which points to the LinuxdroidWidgetService which will provide the views for this collection.
            Intent intent = new Intent(context, LinuxdroidWidgetService.class);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            // When intents are compared, the extras are ignored, so we need to embed the extras
            // into the data so that the extras will not be ignored.
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            rv.setRemoteAdapter(R.id.widget_list, intent);

            // Setup refresh button:
            Intent refreshIntent = new Intent(context, LinuxdroidWidgetProvider.class);
            refreshIntent.setAction(LinuxdroidWidgetProvider.REFRESH_WIDGET_ACTION);
            refreshIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            refreshIntent.setData(Uri.parse(refreshIntent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(context, 0, refreshIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setOnClickPendingIntent(R.id.refresh_button, refreshPendingIntent);

            // Here we setup the a pending intent template. Individuals items of a collection
            // cannot setup their own pending intents, instead, the collection as a whole can
            // setup a pending intent template, and the individual items can set a fillInIntent
            // to create unique before on an item to item basis.
            Intent toastIntent = new Intent(context, LinuxdroidWidgetProvider.class);
            toastIntent.setAction(LinuxdroidWidgetProvider.LIST_ITEM_CLICKED_ACTION);
            toastIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));
            PendingIntent toastPendingIntent = PendingIntent.getBroadcast(context, 0, toastIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            rv.setPendingIntentTemplate(R.id.widget_list, toastPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, rv);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        switch (intent.getAction()) {
            case LIST_ITEM_CLICKED_ACTION:
                String clickedFilePath = intent.getStringExtra(EXTRA_CLICKED_FILE);
                File clickedFile = new File(clickedFilePath);
                if (clickedFile.isDirectory()) return;
                ensureFileReadableAndExecutable(clickedFile);
                Uri scriptUri = new Uri.Builder().scheme("com.linuxdroid.file").path(clickedFilePath).build();

                // Note: Must match LinuxdroidService#ACTION_EXECUTE constant:
                Intent executeIntent = new Intent(ACTION_EXECUTE, scriptUri);
                executeIntent.setClassName("com.linuxdroid", LINUXDROID_SERVICE);
                if (clickedFile.getParentFile().getName().equals("tasks")) {
                    executeIntent.putExtra("com.linuxdroid.execute.background", true);
                    // Show feedback for executed background task.
                    String message = "Task executed: " + clickedFile.getName();
                    Toast toast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                }
                startLinuxdroidService(context, executeIntent);
                break;
            case REFRESH_WIDGET_ACTION:
                int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
                AppWidgetManager.getInstance(context).notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list);

                Toast toast = Toast.makeText(context, R.string.scripts_reloaded, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
                break;
        }
    }

    static void startLinuxdroidService(Context context, Intent executeIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // https://developer.android.com/about/versions/oreo/background.html
            context.startForegroundService(executeIntent);
        } else {
            context.startService(executeIntent);
        }
    }

    /** Ensure readable and executable file if user forgot to do so. */
    static void ensureFileReadableAndExecutable(File file) {
        if (!file.canRead()) file.setReadable(true);
        if (!file.canExecute()) file.setExecutable(true);
    }
}
