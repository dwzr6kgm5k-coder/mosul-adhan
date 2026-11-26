package com.mosul.adhan;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * حساب مواقيت الصلاة فلكياً لمدينة الموصل.
 *
 * الطريقة: زاوية الفجر 18° والعشاء 17° (رابطة العالم الإسلامي)، العصر على المذهب الشافعي (ظل المثل)،
 * والشروق والغروب مع تصحيح ارتفاع المدينة عن سطح البحر.
 * غيّر الثوابت بالأسفل إذا تريد مدينة ثانية أو طريقة حساب ثانية.
 * فروقات الدقائق اليدوية لكل صلاة تنضبط من شاشة الإعدادات (Prefs) وما تنحسب هنا.
 */
public final class PrayerTimes {

    // ===== إعدادات المدينة وطريقة الحساب =====
    public static final double LATITUDE = 36.34;      // خط العرض
    public static final double LONGITUDE = 43.13;     // خط الطول
    public static final double ELEVATION_M = 223;     // الارتفاع عن سطح البحر بالمتر
    public static final double TIMEZONE_HOURS = 3;    // توقيت العراق UTC+3 بدون توقيت صيفي
    public static final double FAJR_ANGLE = 18;
    public static final double ISHA_ANGLE = 17;
    public static final double ASR_SHADOW = 1;        // 1 = شافعي، 2 = حنفي

    // ===== ترتيب الأوقات =====
    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    public static final int COUNT = 6;
    public static final String[] NAMES = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    public static final String[] KEYS = {"fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"};

    /** هل هذا الوقت بيه أذان؟ (الشروق بس للعرض) */
    public static boolean hasAdhan(int i) {
        return i != SUNRISE;
    }

    private PrayerTimes() {}

    /**
     * يرجّع الأوقات الستة بالدقائق من منتصف الليل (بتوقيت العراق)، مقرّبة لأقرب دقيقة، بدون فروقات المستخدم.
     */
    public static int[] computeMinutes(int year, int month, int day) {
        double jd = julianDate(year, month, day) - LONGITUDE / (15.0 * 24.0);
        double horizon = 0.833 + 0.0347 * Math.sqrt(Math.max(0, ELEVATION_M));

        // قيم أولية تقريبية بالساعات ثم تحسين مرتين
        double[] t = {5, 6, 12, 13, 18, 18};
        for (int iter = 0; iter < 3; iter++) {
            double[] n = new double[COUNT];
            n[FAJR] = sunAngleTime(jd, FAJR_ANGLE, t[FAJR], true);
            n[SUNRISE] = sunAngleTime(jd, horizon, t[SUNRISE], true);
            n[DHUHR] = midDay(jd, t[DHUHR]);
            n[ASR] = asrTime(jd, ASR_SHADOW, t[ASR]);
            n[MAGHRIB] = sunAngleTime(jd, horizon, t[MAGHRIB], false);
            n[ISHA] = sunAngleTime(jd, ISHA_ANGLE, t[ISHA], false);
            t = n;
        }
        int[] out = new int[COUNT];
        for (int i = 0; i < COUNT; i++) {
            double local = t[i] + TIMEZONE_HOURS - LONGITUDE / 15.0;
            out[i] = (int) Math.round(local * 60.0);
        }
        return out;
    }

    /** يحوّل دقائق يوم معيّن (بتوقيت العراق) إلى وقت مطلق بالملّي ثانية. */
    public static long toEpochMillis(int year, int month, int day, int minutes) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(year, month - 1, day, 0, 0, 0);
        return c.getTimeInMillis() + (minutes - (long) (TIMEZONE_HOURS * 60)) * 60_000L;
    }

    // ===== الفلك =====

    private static double julianDate(int year, int month, int day) {
        if (month <= 2) { year -= 1; month += 12; }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** يرجّع {الميل، معادلة الزمن} */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = fixHour(darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0);
        double eqt = q / 15.0 - ra;
        eqt = eqt - 24.0 * Math.floor((eqt + 12.0) / 24.0);
        double decl = darcsin(dsin(e) * dsin(l));
        return new double[]{decl, eqt};
    }

    private static double midDay(double jd, double t) {
        double eqt = sunPosition(jd + t / 24.0)[1];
        return fixHour(12 - eqt);
    }

    private static double sunAngleTime(double jd, double angle, double t, boolean beforeNoon) {
        double decl = sunPosition(jd + t / 24.0)[0];
        double noon = midDay(jd, t);
        double cos = (-dsin(angle) - dsin(decl) * dsin(LATITUDE)) / (dcos(decl) * dcos(LATITUDE));
        cos = Math.max(-1, Math.min(1, cos));
        double v = darccos(cos) / 15.0;
        return noon + (beforeNoon ? -v : v);
    }

    private static double asrTime(double jd, double factor, double t) {
        double decl = sunPosition(jd + t / 24.0)[0];
        double angle = -darccot(factor + dtan(Math.abs(LATITUDE - decl)));
        return sunAngleTime(jd, angle, t, false);
    }

    private static double dsin(double d) { return Math.sin(Math.toRadians(d)); }
    private static double dcos(double d) { return Math.cos(Math.toRadians(d)); }
    private static double dtan(double d) { return Math.tan(Math.toRadians(d)); }
    private static double darcsin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double darccos(double x) { return Math.toDegrees(Math.acos(x)); }
    private static double darctan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double darccot(double x) { return Math.toDegrees(Math.atan(1 / x)); }
    private static double fixAngle(double a) { a = a - 360 * Math.floor(a / 360); return a; }
    private static double fixHour(double a) { a = a - 24 * Math.floor(a / 24); return a; }
}
