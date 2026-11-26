package com.mosul.adhan;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * التحديث التلقائي من موقع التطبيق.
 *
 * مرة باليوم (وكل ما ينفتح التطبيق) يقرا version.json من الموقع. إذا اكو نسخة أحدث،
 * ينزّلها بالخلفية ويبدي التثبيت. أندرويد يطلب من المستخدم ضغطة "تحديث" للتأكيد،
 * إلا إذا التطبيق نفسه هو اللي نصّب النسخة الحالية (أندرويد 12 وأحدث)، وقتها يتحدث بدون سؤال.
 *
 * حتى تطلع نسخة جديدة: زيد versionCode، ابنِ الـ APK، حطه بـ docs/MosulAdhan.apk،
 * وحدّث docs/version.json بنفس الرقم.
 */
public final class Updater {

    public static final String VERSION_URL = "https://dwzr6kgm5k-coder.github.io/mosul-adhan/version.json";
    static final String ACTION_INSTALL_RESULT = "com.mosul.adhan.INSTALL_RESULT";
    static final String EXTRA_FROM_UI = "from_ui";
    private static final String CHANNEL_ID = "updates";
    private static final int NOTIF_ID = 200;
    private static final String PREFS = "updater";

    private Updater() {}

    /** نسخة جاهزة نزلت وتنتظر تثبيت، أو null. */
    public static String pendingVersionName(Context ctx) {
        SharedPreferences sp = sp(ctx);
        int code = sp.getInt("code", 0);
        if (code <= installedCode(ctx) || !apkFile(ctx, code).exists()) return null;
        return sp.getString("name", "");
    }

    /** يفحص بخيط منفصل. onReady ينادى على الخيط الرئيسي إذا صارت نسخة جاهزة. */
    public static void checkAsync(final Context context, final boolean fromUi, final Runnable onReady) {
        final Context ctx = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ready = checkNow(ctx);
                if (ready) {
                    if (fromUi && onReady != null) {
                        new Handler(Looper.getMainLooper()).post(onReady);
                    } else if (!fromUi) {
                        install(ctx, false);
                    }
                }
            }
        }, "update-check").start();
    }

    /** يرجّع true إذا اكو نسخة أحدث ونزلت كاملة. */
    static boolean checkNow(Context ctx) {
        try {
            cleanOld(ctx);
            JSONObject json = new JSONObject(new String(fetch(VERSION_URL), "UTF-8"));
            int code = json.getInt("versionCode");
            String name = json.optString("versionName", "");
            String apkUrl = json.getString("apk");
            if (code <= installedCode(ctx)) return false;

            File f = apkFile(ctx, code);
            if (!f.exists() || !validApk(ctx, f, code)) {
                File tmp = new File(f.getPath() + ".part");
                download(apkUrl, tmp);
                if (!validApk(ctx, tmp, code)) { tmp.delete(); return false; }
                if (!tmp.renameTo(f)) return false;
            }
            sp(ctx).edit().putInt("code", code).putString("name", name).apply();
            return true;
        } catch (Exception e) {
            return false; // ماكو إنترنت أو الموقع واكف: نحاول باجر
        }
    }

    /** يبدي التثبيت. fromUi = المستخدم ضغط زر التحديث. */
    public static void install(Context context, boolean fromUi) {
        Context ctx = context.getApplicationContext();
        int code = sp(ctx).getInt("code", 0);
        File f = apkFile(ctx, code);
        if (code <= installedCode(ctx) || !f.exists()) return;

        if (!ctx.getPackageManager().canRequestPackageInstalls()) {
            // لازم المستخدم يسمح للتطبيق يثبّت تحديثاته مرة وحدة
            Intent allow = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:" + ctx.getPackageName()));
            if (fromUi) {
                allow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(allow);
            } else {
                notifyUpdate(ctx, PendingIntent.getActivity(ctx, 3, openApp(ctx), PendingIntent.FLAG_IMMUTABLE));
            }
            return;
        }

        PackageInstaller.Session session = null;
        try {
            PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(ctx.getPackageName());
            if (Build.VERSION.SDK_INT >= 31) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            }
            int id = pi.createSession(params);
            session = pi.openSession(id);
            InputStream in = new FileInputStream(f);
            OutputStream out = session.openWrite("base.apk", 0, f.length());
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            session.fsync(out);
            in.close();
            out.close();

            Intent result = new Intent(ctx, InstallResultReceiver.class)
                    .setAction(ACTION_INSTALL_RESULT)
                    .putExtra(EXTRA_FROM_UI, fromUi);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            session.commit(PendingIntent.getBroadcast(ctx, 4, result, flags).getIntentSender());
            session.close();
        } catch (Exception e) {
            if (session != null) session.abandon();
        }
    }

    /** إشعار "اكو تحديث"، الضغط عليه يفتح شاشة التأكيد أو التطبيق. */
    static void notifyUpdate(Context ctx, PendingIntent tap) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "التحديثات", NotificationManager.IMPORTANCE_DEFAULT));
        String name = sp(ctx).getString("name", "");
        Notification n = new Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("نسخة جديدة من أذان الموصل " + name)
                .setContentText("اضغط هنا حتى تتحدث")
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build();
        nm.notify(NOTIF_ID, n);
    }

    static void cancelNotification(Context ctx) {
        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(NOTIF_ID);
    }

    static Intent openApp(Context ctx) {
        return new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /** فحص يومي بالخلفية. */
    public static void scheduleDaily(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 5, new Intent(ctx, UpdateReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HOUR * 3,
                AlarmManager.INTERVAL_DAY, pi);
    }

    // ===== مساعدات =====

    static int installedCode(Context ctx) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static boolean validApk(Context ctx, File f, int code) {
        PackageInfo info = ctx.getPackageManager().getPackageArchiveInfo(f.getPath(), 0);
        return info != null && ctx.getPackageName().equals(info.packageName) && info.versionCode == code;
    }

    private static File apkFile(Context ctx, int code) {
        File dir = new File(ctx.getFilesDir(), "update");
        dir.mkdirs();
        return new File(dir, "MosulAdhan-" + code + ".apk");
    }

    private static void cleanOld(Context ctx) {
        File[] files = new File(ctx.getFilesDir(), "update").listFiles();
        if (files == null) return;
        int installed = installedCode(ctx);
        for (File f : files) {
            String n = f.getName();
            try {
                int c = Integer.parseInt(n.replaceAll("[^0-9]", ""));
                if (c <= installed) f.delete();
            } catch (NumberFormatException e) {
                f.delete();
            }
        }
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
        return c;
    }

    private static byte[] fetch(String url) throws Exception {
        HttpURLConnection c = open(url + "?t=" + System.currentTimeMillis());
        InputStream in = c.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        c.disconnect();
        return out.toByteArray();
    }

    private static void download(String url, File to) throws Exception {
        HttpURLConnection c = open(url);
        InputStream in = c.getInputStream();
        OutputStream out = new FileOutputStream(to);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
        c.disconnect();
    }
}
