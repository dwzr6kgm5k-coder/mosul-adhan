package com.mosul.adhan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** بعد تشغيل الجهاز أو تغيير الوقت/التاريخ يرجّع يجدول الأذان. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        AlarmScheduler.scheduleNext(ctx);
    }
}
