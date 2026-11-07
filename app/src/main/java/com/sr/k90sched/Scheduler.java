package com.sr.k90sched;

import android.util.Base64;
import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/** K90 / MT6755 专用调度引擎 */
public final class Scheduler {

    private static final String TAG = "K90Sched";
    public static final String BAK_DIR = "/data/local/tmp/k90_bak";
    public static final String APPLY_SH = "/data/local/tmp/k90_apply.sh";
    public static final String PREFS = "k90_sched";

    public static final int MODE_BALANCE = 0;
    public static final int MODE_PERF    = 1;
    public static final int MODE_GAME    = 2;
    public static final int MODE_SAVE    = 3;

    private static final char LF = (char) 10;

    private Scheduler() {}

    public static boolean isRoot() {
        return run("id").contains("uid=0");
    }

    private static void a(StringBuilder sb, String s) { sb.append(s).append(LF); }

    public static String apply(int mode) {
        StringBuilder sb = new StringBuilder();
        a(sb, "#!/system/bin/sh");
        a(sb, "BAK=" + BAK_DIR);
        a(sb, "mkdir -p $BAK");
        a(sb, "[ -f $BAK/done ] || {");
        a(sb, "  cat /proc/sys/vm/swappiness > $BAK/swappiness 2>/dev/null || true");
        a(sb, "  cat /proc/sys/vm/vfs_cache_pressure > $BAK/vfs 2>/dev/null || true");
        a(sb, "  cat /sys/devices/system/cpu/cpufreq/interactive/timer_rate > $BAK/itimer 2>/dev/null || true");
        a(sb, "  cat /sys/devices/system/cpu/cpufreq/interactive/go_hispeed_load > $BAK/ihispeed_load 2>/dev/null || true");
        a(sb, "  cat /sys/devices/system/cpu/cpufreq/interactive/hispeed_freq > $BAK/ihispeed_freq 2>/dev/null || true");
        a(sb, "  cat /proc/hps/up_threshold > $BAK/hps_up 2>/dev/null || true");
        a(sb, "  cat /proc/hps/down_threshold > $BAK/hps_dn 2>/dev/null || true");
        a(sb, "  touch $BAK/done");
        a(sb, "}");
        a(sb, "for c in 5 6 7; do echo 1 > /sys/devices/system/cpu/cpu$c/online 2>/dev/null || true; done");

        String sMin, sMax, sHispeed, sTimer, sLoad;
        String bMin, bMax, bHispeed;

        switch (mode) {
            case MODE_PERF:
                sMin="1144000"; sMax="1144000"; sHispeed="1014000"; sTimer="15000"; sLoad="75";
                bMin="1170000"; bMax="1950000"; bHispeed="1500000";
                break;
            case MODE_GAME:
                sMin="871000"; sMax="1144000"; sHispeed="1014000"; sTimer="15000"; sLoad="80";
                bMin="1170000"; bMax="1950000"; bHispeed="1500000";
                break;
            case MODE_SAVE:
                sMin="338000"; sMax="871000";  sHispeed="689000";  sTimer="60000"; sLoad="95";
                bMin="286000"; bMax="1170000"; bHispeed="871000";
                break;
            default:
                sMin="494000"; sMax="1014000"; sHispeed="871000";  sTimer="30000"; sLoad="90";
                bMin="689000"; bMax="1950000"; bHispeed="1170000";
        }

        a(sb, "for c in 0 1 2 3; do");
        a(sb, "  echo " + sMax + " > /sys/devices/system/cpu/cpu$c/cpufreq/scaling_max_freq 2>/dev/null || true");
        a(sb, "  echo " + sMin + " > /sys/devices/system/cpu/cpu$c/cpufreq/scaling_min_freq 2>/dev/null || true");
        a(sb, "  echo interactive > /sys/devices/system/cpu/cpu$c/cpufreq/scaling_governor 2>/dev/null || true");
        a(sb, "done");

        a(sb, "echo " + bMax + " > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq 2>/dev/null || true");
        a(sb, "echo " + bMin + " > /sys/devices/system/cpu/cpu4/cpufreq/scaling_min_freq 2>/dev/null || true");
        a(sb, "echo interactive > /sys/devices/system/cpu/cpu4/cpufreq/scaling_governor 2>/dev/null || true");

        a(sb, "I=/sys/devices/system/cpu/cpufreq/interactive");
        a(sb, "echo " + sTimer + " > $I/timer_rate 2>/dev/null || true");
        a(sb, "echo " + sLoad  + " > $I/go_hispeed_load 2>/dev/null || true");
        a(sb, "echo " + bHispeed + " > $I/hispeed_freq 2>/dev/null || true");
        a(sb, "echo 1 > $I/boost 2>/dev/null || true");

        if (mode == MODE_SAVE) {
            a(sb, "echo 0 > /proc/hps/input_boost_enabled 2>/dev/null || true");
            a(sb, "echo 90 > /proc/hps/up_threshold 2>/dev/null || true");
            a(sb, "echo 80 > /proc/hps/down_threshold 2>/dev/null || true");
            a(sb, "echo 0 > /proc/hps/rush_boost_enabled 2>/dev/null || true");
        } else if (mode == MODE_PERF || mode == MODE_GAME) {
            a(sb, "echo 1 > /proc/hps/input_boost_enabled 2>/dev/null || true");
            a(sb, "echo 4 > /proc/hps/input_boost_cpu_num 2>/dev/null || true");
            a(sb, "echo 1 > /proc/hps/rush_boost_enabled 2>/dev/null || true");
            a(sb, "echo " + (mode==MODE_GAME?"50":"60") + " > /proc/hps/up_threshold 2>/dev/null || true");
            a(sb, "echo " + (mode==MODE_GAME?"45":"55") + " > /proc/hps/down_threshold 2>/dev/null || true");
        } else {
            a(sb, "echo 1 > /proc/hps/input_boost_enabled 2>/dev/null || true");
            a(sb, "echo 2 > /proc/hps/input_boost_cpu_num 2>/dev/null || true");
            a(sb, "echo 70 > /proc/hps/up_threshold 2>/dev/null || true");
            a(sb, "echo 60 > /proc/hps/down_threshold 2>/dev/null || true");
        }

        a(sb, "for b in mmcblk0 mmcblk0boot0 mmcblk0boot1; do echo deadline > /sys/block/$b/queue/scheduler 2>/dev/null || true; done");
        a(sb, "echo 2048 > /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null || true");
        a(sb, "echo 0 > /sys/block/mmcblk0/queue/rotational 2>/dev/null || true");

        if (mode == MODE_SAVE) {
            a(sb, "echo 100 > /proc/sys/vm/swappiness 2>/dev/null || true");
            a(sb, "echo 200 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null || true");
        } else if (mode == MODE_PERF || mode == MODE_GAME) {
            a(sb, "echo 30 > /proc/sys/vm/swappiness 2>/dev/null || true");
            a(sb, "echo 50 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null || true");
        } else {
            a(sb, "echo 60 > /proc/sys/vm/swappiness 2>/dev/null || true");
            a(sb, "echo 80 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null || true");
        }

        a(sb, "echo APPLIED");

        String script = sb.toString();
        writeFile(APPLY_SH, script);
        return run("sh " + APPLY_SH);
    }

    public static String revert() {
        StringBuilder sb = new StringBuilder();
        a(sb, "BAK=" + BAK_DIR);
        a(sb, "[ -f $BAK/done ] || { echo NO_BAK; exit 0; }");
        a(sb, "echo $(cat $BAK/swappiness) > /proc/sys/vm/swappiness 2>/dev/null || true");
        a(sb, "echo $(cat $BAK/vfs) > /proc/sys/vm/vfs_cache_pressure 2>/dev/null || true");
        a(sb, "echo $(cat $BAK/itimer) > /sys/devices/system/cpu/cpufreq/interactive/timer_rate 2>/dev/null || true");
        a(sb, "echo $(cat $BAK/ihispeed_load) > /sys/devices/system/cpu/cpufreq/interactive/go_hispeed_load 2>/dev/null || true");
        a(sb, "echo $(cat $BAK/ihispeed_freq) > /sys/devices/system/cpu/cpufreq/interactive/hispeed_freq 2>/dev/null || true");
        a(sb, "echo $(cat $BAK/hps_up) > /proc/hps/up_threshold 2>/dev/null || true");
        a(sb, "echo $(cat $BAK/hps_dn) > /proc/hps/down_threshold 2>/dev/null || true");
        a(sb, "rm -f /data/adb/service.d/k90_sched.sh");
        a(sb, "echo REVERTED");
        writeFile("/data/local/tmp/k90_revert.sh", sb.toString());
        return run("sh /data/local/tmp/k90_revert.sh");
    }

    public static String installBoot(int mode) {
        String sh = "#!/system/bin/sh" + LF + "sleep 30" + LF + "sh " + APPLY_SH + LF;
        writeFile("/data/local/tmp/k90_boot.sh", sh);
        return run("mkdir -p /data/adb/service.d && cp /data/local/tmp/k90_boot.sh /data/adb/service.d/k90_sched.sh && chmod 755 /data/adb/service.d/k90_sched.sh && echo BOOT_OK");
    }

    public static String uninstallBoot() {
        return run("rm -f /data/adb/service.d/k90_sched.sh && echo BOOT_REMOVED");
    }

    private static void writeFile(String path, String content) {
        try {
            String b64 = Base64.encodeToString(content.getBytes("UTF-8"), Base64.NO_WRAP);
            run("echo '" + b64 + "' | base64 -d > " + path);
        } catch (Exception e) {
            Log.e(TAG, "writeFile", e);
        }
    }

    public static String run(String cmd) {
        StringBuilder out = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su"});
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + LF + "exit" + LF);
            os.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) out.append(line).append(LF);
            BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = er.readLine()) != null) out.append(line).append(LF);
            p.waitFor();
        } catch (Exception e) {
            out.append("ERR: ").append(e.getMessage());
        }
        return out.toString().trim();
    }
}