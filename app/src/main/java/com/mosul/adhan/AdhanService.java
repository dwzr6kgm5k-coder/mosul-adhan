package com.mosul.adhan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * خدمة أمامية (Foreground Service) تشغّل صوت الأذان وتعرض إشعار بيه زر إيقاف.
 * الصوت ينلعب على مستوى صوت المنبه، فيشتغل حتى إذا التلفون على الصامت.
 */
public class AdhanService extends Service {

    private static final String CHANNEL_ID = "adhan";
    private static final int NOTIF_ID = 100;
    private static final String ACTION_STOP = "com.mosul.adhan.STOP";
    private static final String EXTRA_PRAYER = "prayer";
    private static final long MAX_PLAY_MS = 10 * 60_000L;

    private MediaPlayer player;
    private boolean triedFallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable stopper = new Runnable() {
        @Override public void run() { stopSelf(); }
    };

    /** شغّل الأذان لصلاة معيّنة (يُستدعى من المنبه أو زر التجربة). */
    public static void play(Context ctx, int prayer) {
        Intent i = new Intent(ctx, AdhanService.class);
        i.putExtra(EXTRA_PRAYER, prayer);
        ctx.startForegroundService(i);
    }

    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, AdhanService.class));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int prayer = intent == null ? PrayerTimes.DHUHR : intent.getIntExtra(EXTRA_PRAYER, PrayerTimes.DHUHR);

        Notification n = buildNotification(prayer);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        releasePlayer();
        triedFallback = false;
        Uri uri = new Prefs(this).soundFor(prayer);
        if (uri == null) {
            triedFallback = true;
            uri = defaultSound();
        }
        startPlayer(uri);

        handler.removeCallbacks(stopper);
        handler.postDelayed(stopper, MAX_PLAY_MS);
        return START_NOT_STICKY;
    }

    private void startPlayer(Uri uri) {
        if (uri == null) { stopSelf(); return; }
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(this, uri);
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer mp) { stopSelf(); }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer mp, int what, int extra) {
                    playFallback();
                    return true;
                }
            });
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer mp) { mp.start(); }
            });
            player.prepareAsync();
        } catch (Exception e) {
            // الملف انحذف أو ما نكدر نقراه: نرجع للنغمة الافتراضية
            playFallback();
        }
    }

    private void playFallback() {
        releasePlayer();
        if (triedFallback) { stopSelf(); return; }
        triedFallback = true;
        startPlayer(defaultSound());
    }

    private Uri defaultSound() {
        Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return u;
    }

    private Notification buildNotification(int prayer) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "الأذان", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null); // الصوت يشتغل من الخدمة نفسها
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AdhanService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان الآن موعد أذان " + PrayerTimes.NAMES[prayer])
                .setContentText("حسب توقيت مدينة الموصل")
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(open)
                .setDeleteIntent(stop)
                .addAction(new Notification.Action.Builder(null, "إيقاف الأذان", stop).build())
                .setOngoing(true)
                .build();
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(stopper);
        releasePlayer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
