package com.mosul.adhan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * يجدول منبه واحد للأذان الجاي. لما يدك، AlarmReceiver يشغّل الأذان ويجدول اللي بعده.
 * نستخدم setAlarmClock لأنه أضمن نوع منبه بالأندرويد: يشتغل حتى بوضع السكون (Doze) والشاشة مطفية.
 */
public final class AlarmScheduler {

    public static final String EXTRA_PRAYER = "prayer";
    public static final String EXTRA_TIME = "time";

    private AlarmScheduler() {}

    /** {رقم الصلاة، الوقت بالملّي} للأذان الجاي بعد اللحظة المعطاة، أو null إذا كلها مطفية. */
    public static long[] findNext(Context ctx, long after) {
        Prefs prefs = new Prefs(ctx);
        for (int dayOffset = -1; dayOffset <= 2; dayOffset++) {
            int[] d = Prefs.iraqDate(after, dayOffset);
            int[] mins = prefs.adjustedMinutes(d[0], d[1], d[2]);
            for (int i = 0; i < PrayerTimes.COUNT; i++) {
                if (!prefs.isEnabled(i)) continue;
                long t = PrayerTimes.toEpochMillis(d[0], d[1], d[2], mins[i]);
                if (t > after) return new long[]{i, t};
            }
        }
        return null;
    }

    public static void scheduleNext(Context ctx) {
        scheduleNext(ctx, System.currentTimeMillis());
    }

    public static void scheduleNext(Context ctx, long after) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = alarmIntent(ctx, -1, 0);
        am.cancel(pi);

        long[] next = findNext(ctx, after);
        if (next == null) return;
        int prayer = (int) next[0];
        long time = next[1];
        pi = alarmIntent(ctx, prayer, time);

        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            // احتياط: إذا صلاحية المنبه الدقيق مسحوبة، نستخدم منبه غير دقيق (ممكن يتأخر شوية)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
            return;
        }
        PendingIntent show = PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(time, show), pi);
    }

    private static PendingIntent alarmIntent(Context ctx, int prayer, long time) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.putExtra(EXTRA_PRAYER, prayer);
        i.putExtra(EXTRA_TIME, time);
        return PendingIntent.getBroadcast(ctx, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
