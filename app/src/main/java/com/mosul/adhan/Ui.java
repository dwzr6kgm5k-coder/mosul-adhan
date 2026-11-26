package com.mosul.adhan;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** أدوات صغيرة لبناء الواجهة بالكود: ألوان وخطوط وبطاقات. غيّر الألوان من هنا. */
public final class Ui {

    public static final int GREEN = Color.parseColor("#1E5B3F");
    public static final int GREEN_LIGHT = Color.parseColor("#E3EFE7");
    public static final int GOLD = Color.parseColor("#C9A227");
    public static final int BG = Color.parseColor("#F5F2EA");
    public static final int TEXT = Color.parseColor("#222222");
    public static final int MUTED = Color.parseColor("#6B6B6B");
    public static final int WARN_BG = Color.parseColor("#FFF1D6");

    private static final String[] MONTHS = {"كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
            "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول"};
    private static final String[] WEEKDAYS = {"الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"};

    private Ui() {}

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** دقائق من منتصف الليل إلى نص مثل "5:25 ص". */
    public static String formatTime(int minutes) {
        minutes = ((minutes % 1440) + 1440) % 1440;
        int h = minutes / 60, m = minutes % 60;
        String suffix = h < 12 ? "ص" : "م";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(java.util.Locale.US, "%d:%02d %s", h12, m, suffix);
    }

    /** {سنة، شهر، يوم} إلى نص مثل "الخميس 26 تشرين الثاني 2026". */
    public static String formatDate(int[] ymd) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.clear();
        c.set(ymd[0], ymd[1] - 1, ymd[2]);
        return WEEKDAYS[c.get(java.util.Calendar.DAY_OF_WEEK) - 1] + " " + ymd[2] + " " + MONTHS[ymd[1] - 1] + " " + ymd[0];
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return l;
    }

    public static TextView text(Context c, String s, float sizeSp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static Button button(Context c, String s, int bg, int fg) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setBackground(rounded(bg, dp(c, 10)));
        int p = dp(c, 12);
        b.setPadding(p, 0, p, 0);
        return b;
    }

    public static GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    /** بطاقة بيضاء بحواف مدورة. */
    public static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setBackground(rounded(Color.WHITE, dp(c, 16)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    public static LinearLayout.LayoutParams matchWrap(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    public static void styleWindow(Activity a) {
        a.getWindow().setStatusBarColor(GREEN);
        a.getWindow().setNavigationBarColor(GREEN);
        a.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
    }
}
