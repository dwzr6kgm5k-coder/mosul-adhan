package com.mosul.adhan;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** نتيجة تثبيت التحديث من PackageInstaller. */
public class InstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        boolean fromUi = intent.getBooleanExtra(Updater.EXTRA_FROM_UI, false);

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) return;
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (fromUi) {
                try {
                    ctx.startActivity(confirm);
                    return;
                } catch (Exception ignored) { }
            }
            // بالخلفية ما نگدر نفتح شاشة، فنطلّع إشعار يفتح شاشة التأكيد
            Updater.notifyUpdate(ctx, PendingIntent.getActivity(ctx, 6, confirm,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Updater.cancelNotification(ctx);
        } else if (fromUi && status != PackageInstaller.STATUS_FAILURE_ABORTED) {
            Toast.makeText(ctx, "ما گدرنا نحدّث، حاول مرة ثانية أو نزّله من الموقع", Toast.LENGTH_LONG).show();
        }
    }
}
