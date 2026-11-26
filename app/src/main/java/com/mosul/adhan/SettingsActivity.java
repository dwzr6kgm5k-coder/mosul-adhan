package com.mosul.adhan;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** الإعدادات: تعديل دقائق كل وقت، تفعيل كل أذان، واختيار ملف الصوت. */
public class SettingsActivity extends Activity {

    private static final int PICK_ALL = 10, PICK_FAJR = 11;
    private static final int MAX_OFFSET = 60;

    private Prefs prefs;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.styleWindow(this);
        prefs = new Prefs(this);
        build();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        content = Ui.column(this);
        int p = Ui.dp(this, 16);
        content.setPadding(p, p, p, p);
        scroll.addView(content);
        setContentView(scroll);
        render();
    }

    private void render() {
        content.removeAllViews();
        int[] today = Prefs.iraqDate(System.currentTimeMillis(), 0);
        int[] base = PrayerTimes.computeMinutes(today[0], today[1], today[2]);

        content.addView(Ui.text(this, "الإعدادات", 24, Ui.GREEN, true));

        // ===== تعديل الدقائق =====
        section("تعديل الأوقات بالدقائق",
                "اضغط + أو − حتى يطابق الوقت التقويم اللي تعتمده. التعديل يطبّق على كل الأيام.");
        LinearLayout card = Ui.card(this);
        for (int i = 0; i < PrayerTimes.COUNT; i++) {
            card.addView(prayerRow(i, base[i]), Ui.matchWrap(this, i == 0 ? 0 : 8));
        }
        content.addView(card, Ui.matchWrap(this, 8));

        // ===== الأصوات =====
        section("صوت الأذان", "اختار أي ملف صوت من التلفون (mp3 أو غيره). إذا ما تختار، يشتغل الأذان المدمج بالتطبيق.");
        LinearLayout sounds = Ui.card(this);
        sounds.addView(soundBlock(false));
        View div = new View(this);
        div.setBackgroundColor(Color.parseColor("#E5E5E5"));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
        dl.topMargin = dl.bottomMargin = Ui.dp(this, 14);
        sounds.addView(div, dl);
        sounds.addView(soundBlock(true));
        content.addView(sounds, Ui.matchWrap(this, 8));

        Button stop = Ui.button(this, "إيقاف الصوت", Ui.GREEN_LIGHT, Ui.GREEN);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AdhanService.stop(SettingsActivity.this); }
        });
        content.addView(stop, Ui.matchWrap(this, 12));
    }

    private void section(String title, String hint) {
        content.addView(Ui.text(this, title, 18, Ui.TEXT, true), Ui.matchWrap(this, 22));
        content.addView(Ui.text(this, hint, 13, Ui.MUTED, false), Ui.matchWrap(this, 2));
    }

    private View prayerRow(final int prayer, final int baseMinutes) {
        LinearLayout r = Ui.row(this);

        LinearLayout labels = Ui.column(this);
        labels.addView(Ui.text(this, PrayerTimes.NAMES[prayer], 17, Ui.TEXT, true));
        int off = prefs.getOffset(prayer);
        String sub = "اليوم: " + Ui.formatTime(baseMinutes + off);
        final TextView subView = Ui.text(this, sub, 13, Ui.MUTED, false);
        labels.addView(subView);
        r.addView(labels, Ui.weight(1));

        if (PrayerTimes.hasAdhan(prayer)) {
            Switch sw = new Switch(this);
            sw.setChecked(prefs.isEnabled(prayer));
            sw.setText("أذان");
            sw.setTextColor(Ui.MUTED);
            sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                    prefs.setEnabled(prayer, checked);
                    AlarmScheduler.scheduleNext(SettingsActivity.this);
                }
            });
            LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            swl.setMarginEnd(Ui.dp(this, 8));
            r.addView(sw, swl);
        }

        final TextView value = Ui.text(this, offsetLabel(off), 15, Ui.GREEN, true);
        value.setGravity(Gravity.CENTER);
        value.setTextDirection(View.TEXT_DIRECTION_LTR);
        Button minus = smallButton("−");
        Button plus = smallButton("+");
        View.OnClickListener l = new View.OnClickListener() {
            @Override public void onClick(View v) {
                int o = prefs.getOffset(prayer) + ("+".equals(v.getTag()) ? 1 : -1);
                o = Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, o));
                prefs.setOffset(prayer, o);
                value.setText(offsetLabel(o));
                subView.setText("اليوم: " + Ui.formatTime(baseMinutes + o));
                AlarmScheduler.scheduleNext(SettingsActivity.this);
            }
        };
        plus.setTag("+");
        minus.setTag("-");
        plus.setOnClickListener(l);
        minus.setOnClickListener(l);

        int s = Ui.dp(this, 40);
        r.addView(plus, new LinearLayout.LayoutParams(s, s));
        r.addView(value, new LinearLayout.LayoutParams(Ui.dp(this, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
        r.addView(minus, new LinearLayout.LayoutParams(s, s));
        return r;
    }

    private Button smallButton(String s) {
        Button b = Ui.button(this, s, Ui.GREEN_LIGHT, Ui.GREEN);
        b.setTextSize(20);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private String offsetLabel(int o) {
        if (o == 0) return "0 د";
        return (o > 0 ? "+" : "-") + Math.abs(o) + " د";
    }

    private View soundBlock(final boolean fajr) {
        LinearLayout block = Ui.column(this);
        block.addView(Ui.text(this, fajr ? "صوت أذان الفجر (اختياري)" : "صوت الأذان لكل الصلوات", 16, Ui.TEXT, true));

        String name = prefs.getSoundName(fajr);
        String current;
        if (prefs.getSound(fajr) == null) {
            current = fajr ? "نفس صوت باقي الصلوات" : "الأذان المدمج بالتطبيق";
        } else {
            current = name != null ? name : "ملف مختار";
        }
        block.addView(Ui.text(this, "الحالي: " + current, 13, Ui.MUTED, false), Ui.matchWrap(this, 2));

        LinearLayout buttons = Ui.row(this);
        Button pick = Ui.button(this, "اختيار ملف", Ui.GREEN, Color.WHITE);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("audio/*");
                startActivityForResult(i, fajr ? PICK_FAJR : PICK_ALL);
            }
        });
        Button test = Ui.button(this, "تجربة", Ui.GREEN_LIGHT, Ui.GREEN);
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                AdhanService.play(SettingsActivity.this, fajr ? PrayerTimes.FAJR : PrayerTimes.DHUHR);
            }
        });
        Button reset = Ui.button(this, "الافتراضي", Ui.GREEN_LIGHT, Ui.GREEN);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setSound(fajr, null);
                prefs.setSoundName(fajr, null);
                render();
            }
        });
        LinearLayout.LayoutParams a = Ui.weight(1.4f), b = Ui.weight(1), c = Ui.weight(1);
        b.setMarginStart(Ui.dp(this, 8));
        c.setMarginStart(Ui.dp(this, 8));
        buttons.addView(pick, a);
        buttons.addView(test, b);
        buttons.addView(reset, c);
        block.addView(buttons, Ui.matchWrap(this, 10));
        return block;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode != PICK_ALL && requestCode != PICK_FAJR) return;
        boolean fajr = requestCode == PICK_FAJR;
        Uri uri = data.getData();
        try {
            // نحتفظ بصلاحية قراءة الملف حتى بعد إعادة تشغيل الجهاز
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) { }
        prefs.setSound(fajr, uri);
        prefs.setSoundName(fajr, displayName(uri));
        render();
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return uri.getLastPathSegment();
    }
}
