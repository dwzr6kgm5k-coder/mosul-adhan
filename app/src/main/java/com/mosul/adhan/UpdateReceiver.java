package com.mosul.adhan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** الفحص اليومي للتحديث (من AlarmManager). */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Updater.checkAsync(ctx, false, null);
    }
}
