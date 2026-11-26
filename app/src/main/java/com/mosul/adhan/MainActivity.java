package com.mosul.adhan;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** الشاشة الرئيسية: أوقات اليوم والأذان الجاي والتنبيهات المهمة. */
public class MainActivity extends Activity {

    private LinearLayout warnings;
    private LinearLayout timesList;
    private TextView dateView, nextView, countdownView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 20_000);
        }
    };

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.styleWindow(this);
        buildUi();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmScheduler.scheduleNext(this);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        refresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);

        LinearLayout root = Ui.column(this);
        scroll.addView(root);

        // ===== الرأس الأخضر =====
        LinearLayout header = Ui.column(this);
        header.setBackgroundColor(Ui.GREEN);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Ui.dp(this, 20);
        header.setPadding(p, p, p, Ui.dp(this, 28));
        TextView title = Ui.text(this, "أذان الموصل", 26, Color.WHITE, true);
        dateView = Ui.text(this, "", 15, Color.parseColor("#D7E8DD"), false);
        nextView = Ui.text(this, "", 20, Ui.GOLD, true);
        countdownView = Ui.text(this, "", 15, Color.WHITE, false);
        for (TextView t : new TextView[]{title, dateView, nextView, countdownView}) {
            t.setGravity(Gravity.CENTER);
            header.addView(t, Ui.matchWrap(this, 4));
        }
        root.addView(header);

        LinearLayout body = Ui.column(this);
        int bp = Ui.dp(this, 16);
        body.setPadding(bp, bp, bp, bp);
        root.addView(body);

        warnings = Ui.column(this);
        body.addView(warnings);

        LinearLayout card = Ui.card(this);
        timesList = Ui.column(this);
        card.addView(timesList);
        body.addView(card, Ui.matchWrap(this, 0));

        // ===== الأزرار =====
        LinearLayout buttons = Ui.row(this);
        Button test = Ui.button(this, "تجربة الأذان", Ui.GREEN, Color.WHITE);
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AdhanService.play(MainActivity.this, PrayerTimes.DHUHR); }
        });
        Button stop = Ui.button(this, "إيقاف الصوت", Ui.GREEN_LIGHT, Ui.GREEN);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AdhanService.stop(MainActivity.this); }
        });
        LinearLayout.LayoutParams lp1 = Ui.weight(1);
        LinearLayout.LayoutParams lp2 = Ui.weight(1);
        lp2.setMarginStart(Ui.dp(this, 10));
        buttons.addView(test, lp1);
        buttons.addView(stop, lp2);
        body.addView(buttons, Ui.matchWrap(this, 16));

        Button settings = Ui.button(this, "الإعدادات  (تعديل الدقائق والأصوات)", Color.WHITE, Ui.GREEN);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        body.addView(settings, Ui.matchWrap(this, 10));

        TextView note = Ui.text(this,
                "الأوقات محسوبة فلكياً للموصل وتتغير يومياً. إذا اكو فرق ويه التقويم اللي تعتمده، عدّل الدقائق من الإعدادات.",
                13, Ui.MUTED, false);
        note.setGravity(Gravity.CENTER);
        body.addView(note, Ui.matchWrap(this, 16));

        setContentView(scroll);
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        int[] today = Prefs.iraqDate(now, 0);
        Prefs prefs = new Prefs(this);
        int[] mins = prefs.adjustedMinutes(today[0], today[1], today[2]);

        dateView.setText(Ui.formatDate(today));

        long[] next = AlarmScheduler.findNext(this, now);
        int nextPrayer = -1;
        if (next != null) {
            nextPrayer = (int) next[0];
            long diffMin = (next[1] - now + 59_999) / 60_000;
            boolean isToday = next[1] < PrayerTimes.toEpochMillis(today[0], today[1], today[2], 24 * 60);
            int[] nd = Prefs.iraqDate(next[1], 0);
            int[] nm = prefs.adjustedMinutes(nd[0], nd[1], nd[2]);
            nextView.setText("الأذان الجاي: " + PrayerTimes.NAMES[nextPrayer] + "  " + Ui.formatTime(nm[nextPrayer]));
            countdownView.setText("باقي " + formatDuration(diffMin));
            if (!isToday) nextPrayer = -1; // لا نظلّل صف اليوم إذا الأذان الجاي باجر
        } else {
            nextView.setText("كل الأذانات مطفية من الإعدادات");
            countdownView.setText("");
        }

        // ===== جدول الأوقات =====
        timesList.removeAllViews();
        for (int i = 0; i < PrayerTimes.COUNT; i++) {
            LinearLayout r = Ui.row(this);
            int vp = Ui.dp(this, 12), hp = Ui.dp(this, 12);
            r.setPadding(hp, vp, hp, vp);
            boolean highlight = i == nextPrayer;
            if (highlight) r.setBackground(Ui.rounded(Ui.GREEN_LIGHT, Ui.dp(this, 10)));

            String label = PrayerTimes.NAMES[i];
            if (PrayerTimes.hasAdhan(i) && !prefs.isEnabled(i)) label += "  (بدون أذان)";
            TextView name = Ui.text(this, label, 18, highlight ? Ui.GREEN : Ui.TEXT, highlight);
            TextView time = Ui.text(this, Ui.formatTime(mins[i]), 18, highlight ? Ui.GREEN : Ui.TEXT, true);
            time.setGravity(Gravity.END);
            r.addView(name, Ui.weight(1));
            r.addView(time, Ui.weight(1));
            timesList.addView(r);
        }

        refreshWarnings();
    }

    private String formatDuration(long minutes) {
        long h = minutes / 60, m = minutes % 60;
        if (h == 0) return m + " دقيقة";
        if (m == 0) return h + " ساعة";
        return h + " ساعة و " + m + " دقيقة";
    }

    /** بطاقات تحذير إذا شي يمنع الأذان من الاشتغال. */
    private void refreshWarnings() {
        warnings.removeAllViews();

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (!nm.areNotificationsEnabled()) {
            addWarning("الإشعارات مطفية، فعّلها حتى يطلع زر إيقاف الأذان.", "تفعيل", new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
            addWarning("حتى يدك الأذان بوقته والتلفون نايم، اسمح للتطبيق يشتغل بدون توفير طاقة.", "سماح",
                    new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        }

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) {
                addWarning("صلاحية المنبهات الدقيقة مطفية.", "تفعيل",
                        new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            }
        }
    }

    private void addWarning(String msg, String action, final Intent intent) {
        LinearLayout box = Ui.row(this);
        box.setBackground(Ui.rounded(Ui.WARN_BG, Ui.dp(this, 12)));
        int p = Ui.dp(this, 12);
        box.setPadding(p, p, p, p);
        TextView t = Ui.text(this, msg, 14, Ui.TEXT, false);
        Button b = Ui.button(this, action, Ui.GOLD, Color.WHITE);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + getPackageName())));
                }
            }
        });
        box.addView(t, Ui.weight(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(this, 40));
        lp.setMarginStart(Ui.dp(this, 10));
        box.addView(b, lp);
        LinearLayout.LayoutParams outer = Ui.matchWrap(this, 0);
        outer.bottomMargin = Ui.dp(this, 12);
        warnings.addView(box, outer);
    }
}
