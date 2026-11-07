package com.sr.k90sched;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

/** 开机自动应用上次使用的调度 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(Scheduler.PREFS, Context.MODE_PRIVATE);
            int mode = sp.getInt("last_mode", Scheduler.MODE_BALANCE);
            Scheduler.apply(mode);
            Log.i("K90Sched", "boot apply mode=" + mode);
        } catch (Exception e) {
            Log.e("K90Sched", "boot", e);
        }
    }
}