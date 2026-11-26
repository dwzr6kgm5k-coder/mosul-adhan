package com.mosul.adhan;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import java.util.Calendar;
import java.util.TimeZone;

/** كل إعدادات المستخدم: فروقات الدقائق، تفعيل كل أذان، وملفات الصوت. */
public final class Prefs {

    private static final String FILE = "adhan_prefs";
    private static final String SOUND_ALL = "sound_all";
    private static final String SOUND_FAJR = "sound_fajr";

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ===== فرق الدقائق لكل وقت =====
    public int getOffset(int prayer) {
        return sp.getInt("offset_" + PrayerTimes.KEYS[prayer], 0);
    }

    public void setOffset(int prayer, int minutes) {
        sp.edit().putInt("offset_" + PrayerTimes.KEYS[prayer], minutes).apply();
    }

    // ===== تفعيل/إيقاف أذان كل صلاة =====
    public boolean isEnabled(int prayer) {
        return PrayerTimes.hasAdhan(prayer) && sp.getBoolean("enabled_" + PrayerTimes.KEYS[prayer], true);
    }

    public void setEnabled(int prayer, boolean enabled) {
        sp.edit().putBoolean("enabled_" + PrayerTimes.KEYS[prayer], enabled).apply();
    }

    // ===== الأصوات (null = النغمة الافتراضية) =====
    public Uri getSound(boolean fajr) {
        String s = sp.getString(fajr ? SOUND_FAJR : SOUND_ALL, null);
        return s == null ? null : Uri.parse(s);
    }

    public void setSound(boolean fajr, Uri uri) {
        sp.edit().putString(fajr ? SOUND_FAJR : SOUND_ALL, uri == null ? null : uri.toString()).apply();
    }

    public String getSoundName(boolean fajr) {
        return sp.getString((fajr ? SOUND_FAJR : SOUND_ALL) + "_name", null);
    }

    public void setSoundName(boolean fajr, String name) {
        sp.edit().putString((fajr ? SOUND_FAJR : SOUND_ALL) + "_name", name).apply();
    }

    /** الصوت اللي ينلعب لهالصلاة: صوت الفجر الخاص إذا موجود، وإلا الصوت العام. */
    public Uri soundFor(int prayer) {
        if (prayer == PrayerTimes.FAJR) {
            Uri f = getSound(true);
            if (f != null) return f;
        }
        return getSound(false);
    }

    // ===== الأوقات النهائية بعد الفروقات =====

    /** دقائق اليوم لكل وقت بعد إضافة فروقات المستخدم. */
    public int[] adjustedMinutes(int year, int month, int day) {
        int[] m = PrayerTimes.computeMinutes(year, month, day);
        for (int i = 0; i < m.length; i++) m[i] += getOffset(i);
        return m;
    }

    /** اليوم الحالي بتوقيت العراق {سنة، شهر، يوم} مع إزاحة أيام. */
    public static int[] iraqDate(long nowMillis, int addDays) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(nowMillis + (long) (PrayerTimes.TIMEZONE_HOURS * 3600_000L));
        c.add(Calendar.DAY_OF_MONTH, addDays);
        return new int[]{c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)};
    }
}
