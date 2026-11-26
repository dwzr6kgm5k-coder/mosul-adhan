package com.mosul.adhan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** يستلم منبه وقت الأذان: يشغّل الصوت ويجدول الأذان الجاي. */
public class AlarmReceiver extends BroadcastReceiver {

    /** إذا المنبه تأخر أكثر من هالمدة (مثلاً الجهاز كان مطفي) ما نشغّل الأذان. */
    private static final long MAX_LATE_MS = 15 * 60_000L;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        int prayer = intent.getIntExtra(AlarmScheduler.EXTRA_PRAYER, -1);
        long time = intent.getLongExtra(AlarmScheduler.EXTRA_TIME, 0);
        long now = System.currentTimeMillis();

        if (prayer >= 0 && now - time < MAX_LATE_MS) {
            AdhanService.play(ctx, prayer);
        }
        AlarmScheduler.scheduleNext(ctx, Math.max(now, time) + 1000);
    }
}
