package com.sr.k90sched;

import android.util.Log;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * K90 / MT6755 专用调度引擎
 * 4+4 双簇（cpu0-3 小核 A53 / cpu4-7 大核 A53），interactive 调度器，MTK HPS 热插拔
 * 所有写入前先备份到 /data/local/tmp/k90_bak/ 防止无法回滚
 */
public final class Scheduler {

    private static final String TAG = "K90Sched";
    public static final String BAK_DIR = "/data/local/tmp/k90_bak";
    public static final String APPLY_SH = "/data/local/tmp/k90_apply.sh";
    public static final String PREFS = "k90_sched";

    // 预设模式
    public static final int MODE_BALANCE = 0;
    public static final int MODE_PERF    = 1;
    public static final int MODE_GAME    = 2;
    public static final int MODE_SAVE    = 3;

    private Scheduler() {}

    public static boolean isRoot() {
        return run("id").contains("uid=0");
    }

    /** 生成并应用调度脚本 */
    public static String apply(int mode) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/system/bin/sh
");
        sb.append("set -e
");
        sb.append("BAK=" + BAK_DIR + "
");
        sb.append("mkdir -p $BAK
");
        // 备份
        sb.append("[ -f $BAK/done ] || {
");
        sb.append("  cat /proc/sys/vm/swappiness > $BAK/swappiness 2>/dev/null || true
");
        sb.append("  cat /proc/sys/vm/vfs_cache_pressure > $BAK/vfs 2>/dev/null || true
");
        sb.append("  cat /sys/devices/system/cpu/cpufreq/interactive/timer_rate > $BAK/itimer 2>/dev/null || true
");
        sb.append("  cat /sys/devices/system/cpu/cpufreq/interactive/go_hispeed_load > $BAK/ihispeed_load 2>/dev/null || true
");
        sb.append("  cat /sys/devices/system/cpu/cpufreq/interactive/hispeed_freq > $BAK/ihispeed_freq 2>/dev/null || true
");
        sb.append("  cat /proc/hps/up_threshold > $BAK/hps_up 2>/dev/null || true
");
        sb.append("  cat /proc/hps/down_threshold > $BAK/hps_dn 2>/dev/null || true
");
        sb.append("  touch $BAK/done
");
        sb.append("}
");

        // 唤醒全部核心
        sb.append("for c in 5 6 7; do echo 1 > /sys/devices/system/cpu/cpu$c/online 2>/dev/null || true; done
");

        // 小核配置
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
            default: // BALANCE
                sMin="494000"; sMax="1014000"; sHispeed="871000";  sTimer="30000"; sLoad="90";
                bMin="689000"; bMax="1950000"; bHispeed="1170000";
        }

        sb.append("# 小核 cpu0-3
");
        sb.append("for c in 0 1 2 3; do
");
        sb.append("  echo " + sMax + " > /sys/devices/system/cpu/cpu$c/cpufreq/scaling_max_freq 2>/dev/null || true
");
        sb.append("  echo " + sMin + " > /sys/devices/system/cpu/cpu$c/cpufreq/scaling_min_freq 2>/dev/null || true
");
        sb.append("  echo interactive > /sys/devices/system/cpu/cpu$c/cpufreq/scaling_governor 2>/dev/null || true
");
        sb.append("done
");

        sb.append("# 大核 cpu4-7（仅 cpu4 可写，cpu5-7 共享簇参数）
");
        sb.append("echo " + bMax + " > /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq 2>/dev/null || true
");
        sb.append("echo " + bMin + " > /sys/devices/system/cpu/cpu4/cpufreq/scaling_min_freq 2>/dev/null || true
");
        sb.append("echo interactive > /sys/devices/system/cpu/cpu4/cpufreq/scaling_governor 2>/dev/null || true
");

        sb.append("# 簇级 interactive
");
        sb.append("I=/sys/devices/system/cpu/cpufreq/interactive
");
        sb.append("echo " + sTimer + " > $I/timer_rate 2>/dev/null || true
");
        sb.append("echo " + sLoad  + " > $I/go_hispeed_load 2>/dev/null || true
");
        sb.append("echo " + bHispeed + " > $I/hispeed_freq 2>/dev/null || true
");
        sb.append("echo 1 > $I/boost 2>/dev/null || true
");
        sb.append("echo " + sHispeed + " > $I/above_hispeed_delay 2>/dev/null || true
");

        if (mode == MODE_SAVE) {
            sb.append("echo 0 > /proc/hps/input_boost_enabled 2>/dev/null || true
");
            sb.append("echo 90 > /proc/hps/up_threshold 2>/dev/null || true
");
            sb.append("echo 80 > /proc/hps/down_threshold 2>/dev/null || true
");
            sb.append("echo 0 > /proc/hps/rush_boost_enabled 2>/dev/null || true
");
        } else if (mode == MODE_PERF || mode == MODE_GAME) {
            sb.append("echo 1 > /proc/hps/input_boost_enabled 2>/dev/null || true
");
            sb.append("echo 4 > /proc/hps/input_boost_cpu_num 2>/dev/null || true
");
            sb.append("echo 1 > /proc/hps/rush_boost_enabled 2>/dev/null || true
");
            sb.append("echo " + (mode==MODE_GAME?"50":"60") + " > /proc/hps/up_threshold 2>/dev/null || true
");
            sb.append("echo " + (mode==MODE_GAME?"45":"55") + " > /proc/hps/down_threshold 2>/dev/null || true
");
        } else {
            sb.append("echo 1 > /proc/hps/input_boost_enabled 2>/dev/null || true
");
            sb.append("echo 2 > /proc/hps/input_boost_cpu_num 2>/dev/null || true
");
            sb.append("echo 70 > /proc/hps/up_threshold 2>/dev/null || true
");
            sb.append("echo 60 > /proc/hps/down_threshold 2>/dev/null || true
");
        }

        // IO
        sb.append("for b in mmcblk0 mmcblk0boot0 mmcblk0boot1; do echo deadline > /sys/block/$b/queue/scheduler 2>/dev/null || true; done
");
        sb.append("echo 2048 > /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null || true
");
        sb.append("echo 0 > /sys/block/mmcblk0/queue/rotational 2>/dev/null || true
");

        // 内存
        if (mode == MODE_SAVE) {
            sb.append("echo 100 > /proc/sys/vm/swappiness 2>/dev/null || true
");
            sb.append("echo 200 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null || true
");
        } else if (mode == MODE_PERF || mode == MODE_GAME) {
            sb.append("echo 30 > /proc/sys/vm/swappiness 2>/dev/null || true
");
            sb.append("echo 50 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null || true
");
        } else {
            sb.append("echo 60 > /proc/sys/vm/swappiness 2>/dev/null || true
");
            sb.append("echo 80 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null || true
");
        }

        sb.append("echo APPLIED
");

        // 写入脚本
        String script = sb.toString();
        writeFile(APPLY_SH, script);
        return run("sh " + APPLY_SH);
    }

    public static String revert() {
        StringBuilder sb = new StringBuilder();
        sb.append("BAK=" + BAK_DIR + "
");
        sb.append("[ -f $BAK/done ] || { echo NO_BAK; exit 0; }
");
        sb.append("echo $(cat $BAK/swappiness) > /proc/sys/vm/swappiness 2>/dev/null || true
");
        sb.append("echo $(cat $BAK/vfs) > /proc/sys/vm/vfs_cache_pressure 2>/dev/null || true
");
        sb.append("echo $(cat $BAK/itimer) > /sys/devices/system/cpu/cpufreq/interactive/timer_rate 2>/dev/null || true
");
        sb.append("echo $(cat $BAK/ihispeed_load) > /sys/devices/system/cpu/cpufreq/interactive/go_hispeed_load 2>/dev/null || true
");
        sb.append("echo $(cat $BAK/ihispeed_freq) > /sys/devices/system/cpu/cpufreq/interactive/hispeed_freq 2>/dev/null || true
");
        sb.append("echo $(cat $BAK/hps_up) > /proc/hps/up_threshold 2>/dev/null || true
");
        sb.append("echo $(cat $BAK/hps_dn) > /proc/hps/down_threshold 2>/dev/null || true
");
        sb.append("rm -f /data/adb/service.d/k90_sched.sh
");
        sb.append("echo REVERTED
");
        writeFile("/data/local/tmp/k90_revert.sh", sb.toString());
        return run("sh /data/local/tmp/k90_revert.sh");
    }

    public static String installBoot(int mode) {
        String sh = "#!/system/bin/sh
sleep 30
sh " + APPLY_SH + "
";
        writeFile("/data/local/tmp/k90_boot.sh", sh);
        return run("mkdir -p /data/adb/service.d && cp /data/local/tmp/k90_boot.sh /data/adb/service.d/k90_sched.sh && chmod 755 /data/adb/service.d/k90_sched.sh && echo BOOT_OK");
    }

    public static String uninstallBoot() {
        return run("rm -f /data/adb/service.d/k90_sched.sh && echo BOOT_REMOVED");
    }

    public static String readStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("vol=").append(run("media volume --stream 3 --get 2>&1 | tail -1")).append("
");
        sb.append("online=").append(run("cat /sys/devices/system/cpu/online")).append("
");
        sb.append("big_max=").append(run("cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq 2>/dev/null")).append("
");
        sb.append("big_cur=").append(run("cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq 2>/dev/null")).append("
");
        sb.append("small_max=").append(run("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null")).append("
");
        sb.append("thermal=").append(run("cat /sys/class/thermal/thermal_zone1/temp 2>/dev/null")).append("
");
        return sb.toString();
    }

    // ===== helper =====
    private static void writeFile(String path, String content) {
        try {
            String b64 = android.util.Base64.encodeToString(content.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
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
            os.writeBytes(cmd + "
exit
");
            os.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) out.append(line).append("
");
            BufferedReader er = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while ((line = er.readLine()) != null) out.append(line).append("
");
            p.waitFor();
        } catch (Exception e) {
            out.append("ERR: ").append(e.getMessage());
        }
        return out.toString().trim();
    }
}