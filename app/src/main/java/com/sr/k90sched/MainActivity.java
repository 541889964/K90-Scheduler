package com.sr.k90sched;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * K90 调度器 - 液态玻璃 UI / 横屏平板&手机双布局 / 动态&静态背景
 * 横屏触发“平板模式”（左右分栏）；竖屏为“手机模式”（上下堆叠）。
 */
public class MainActivity extends Activity {

    private static final int REQ_WALLPAPER = 1001;

    private FrameLayout root;
    private DynamicBgView bgView;
    private ImageView staticWallpaper;
    private TextView statusText;
    private TextView titleView;
    private LinearLayout panelLeft, panelRight;
    private LinearLayout glassContainer;

    private boolean isTabletMode = false;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Runnable ticker;

    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sp = getSharedPreferences(Scheduler.PREFS, MODE_PRIVATE);

        root = new FrameLayout(this);
        setContentView(root);

        // 1) 背景层
        staticWallpaper = new ImageView(this);
        staticWallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        staticWallpaper.setVisibility(View.GONE);
        root.addView(staticWallpaper, new FrameLayout.LayoutParams(-1, -1));

        bgView = new DynamicBgView();
        root.addView(bgView, new FrameLayout.LayoutParams(-1, -1));

        // 2) 内容层
        buildUi();

        // 状态刷新
        ticker = new Runnable() {
            @Override public void run() {
                refreshStatus();
                h.postDelayed(this, 1500);
            }
        };
        h.post(ticker);

        // 恢复背景设置
        String mode = sp.getString("bg_mode", "dynamic");
        if ("static".equals(mode)) {
            String uri = sp.getString("bg_static_uri", null);
            if (uri != null) {
                try {
                    staticWallpaper.setImageURI(Uri.parse(uri));
                    staticWallpaper.setVisibility(View.VISIBLE);
                    bgView.setVisibility(View.GONE);
                } catch (Exception ignored) {}
            }
        }

        if (!Scheduler.isRoot()) {
            Toast.makeText(this, "⚠ 需要 Root 权限才能应用调度", Toast.LENGTH_LONG).show();
        }
    }

    private void buildUi() {
        // 外层：滚动容器
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);
        FrameLayout.LayoutParams scLp = new FrameLayout.LayoutParams(-1, -1);
        root.addView(sc, scLp);

        glassContainer = new LinearLayout(this);
        glassContainer.setOrientation(LinearLayout.VERTICAL);
        glassContainer.setPadding(dp(20), dp(40), dp(20), dp(24));
        sc.addView(glassContainer, new LinearLayout.LayoutParams(-1, -2));

        // 标题
        titleView = new TextView(this);
        titleView.setText("K90 调度器");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setShadowLayer(16, 0, 2, 0x99000000);
        glassContainer.addView(titleView);

        TextView sub = new TextView(this);
        sub.setText("MT6755 · 4+4 双簇 · Liquid Glass");
        sub.setTextColor(0xCCFFFFFF);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setShadowLayer(8, 0, 1, 0x99000000);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.topMargin = dp(4);
        subLp.bottomMargin = dp(16);
        glassContainer.addView(sub, subLp);

        // 状态卡片（玻璃）
        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        statusText.setLineSpacing(dp(4), 1f);
        statusText.setText("读取中...");
        statusText.setPadding(dp(18), dp(16), dp(18), dp(16));
        statusText.setShadowLayer(8, 0, 1, 0x99000000);
        statusText.setBackground(makeGlassBg(0x33FFFFFF, 24));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(-1, -2);
        stLp.bottomMargin = dp(20);
        glassContainer.addView(statusText, stLp);

        // 双栏容器（用于响应式布局）
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        glassContainer.addView(columns, new LinearLayout.LayoutParams(-1, -2));

        panelLeft = new LinearLayout(this);
        panelLeft.setOrientation(LinearLayout.VERTICAL);
        panelRight = new LinearLayout(this);
        panelRight.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(0, -2, 1f);
        columns.addView(panelLeft, colLp);
        colLp = new LinearLayout.LayoutParams(0, -2, 1f);
        colLp.leftMargin = dp(16);
        columns.addView(panelRight, colLp);

        // 左栏：四个模式按钮
        addPanelTitle(panelLeft, "调度模式");
        addModeButton(panelLeft, "均衡", Scheduler.MODE_BALANCE, 0xFF4FC3F7);
        addModeButton(panelLeft, "性能", Scheduler.MODE_PERF,    0xFFFFA726);
        addModeButton(panelLeft, "游戏", Scheduler.MODE_GAME,    0xFFEF5350);
        addModeButton(panelLeft, "省电", Scheduler.MODE_SAVE,    0xFF66BB6A);

        // 右栏：快捷工具
        addPanelTitle(panelRight, "快捷工具");
        addGlassButton(panelRight, "🔊 音量拉满", v -> quick("media volume --stream 3 --set 15; settings put system volume_music 15; echo VOL_OK"));
        addGlassButton(panelRight, "📶 刷新网络", v -> quick("echo 1 > /proc/sys/net/ipv4/tcp_fastopen 2>/dev/null; echo NET_OK"));
        addGlassButton(panelRight, "🧹 清理缓存", v -> quick("sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null; echo CACHE_OK"));
        addGlassButton(panelRight, "🔁 还原原厂", v -> {
            String r = Scheduler.revert();
            toast("已还原：" + r);
            refreshStatus();
        });
        addGlassButton(panelRight, "⏱ 开机自启", v -> {
            String r = Scheduler.installBoot(currentMode);
            toast("开机自启：" + r);
        });
        addGlassButton(panelRight, "🚫 关闭自启", v -> {
            String r = Scheduler.uninstallBoot();
            toast(r);
        });

        // 背景设置
        addPanelTitle(panelRight, "背景设置");
        addGlassButton(panelRight, "🎨 动态玻璃背景", v -> {
            sp.edit().putString("bg_mode", "dynamic").apply();
            staticWallpaper.setVisibility(View.GONE);
            bgView.setVisibility(View.VISIBLE);
            toast("动态背景已启用");
        });
        addGlassButton(panelRight, "🖼 选择静态图片", v -> pickWallpaper());

        // 底部开关
        Switch sw = new Switch(this);
        sw.setText("性能动画（粒子流光）");
        sw.setTextColor(Color.WHITE);
        sw.setShadowLayer(6, 0, 1, 0x99000000);
        sw.setChecked(sp.getBoolean("bg_anim", true));
        sw.setOnCheckedChangeListener((view, isChecked) -> {
            sp.edit().putBoolean("bg_anim", isChecked).apply();
            bgView.setParticlesEnabled(isChecked);
        });
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(-1, -2);
        swLp.topMargin = dp(24);
        glassContainer.addView(sw, swLp);
    }

    // ===== 动态玻璃背景 =====
    private class DynamicBgView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = {0xFF0F1B2D, 0xFF2A2F6B, 0xFF1E4C7A, 0xFF3A2D6B};
        private float phase = 0f;
        private final int N = 60;
        private final float[] dx = new float[N], dy = new float[N], dv = new float[N];
        private boolean particlesEnabled = true;

        DynamicBgView() {
            super(MainActivity.this);
            setLayerType(LAYER_TYPE_HARDWARE, null);
            dot.setColor(0x66FFFFFF);
            for (int i = 0; i < N; i++) {
                dx[i] = (float) Math.random();
                dy[i] = (float) Math.random();
                dv[i] = 0.0007f + (float) Math.random() * 0.0022f;
            }
        }

        void setParticlesEnabled(boolean b) { particlesEnabled = b; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), hh = getHeight();
            // 旋转渐变
            float angle = phase * 0.25f;
            float cx = w * 0.5f + (float)Math.cos(angle) * w * 0.35f;
            float cy = hh * 0.5f + (float)Math.sin(angle) * hh * 0.35f;
            Shader sh = new LinearGradient(0, 0, cx, cy, colors, null, Shader.TileMode.CLAMP);
            p.setShader(sh);
            c.drawRect(0, 0, w, hh, p);

            // 光斑
            for (int k = 0; k < 3; k++) {
                float ox = w * (0.25f + 0.25f * k) + (float)Math.cos(phase + k * 1.7f) * w * 0.25f;
                float oy = hh * (0.4f + 0.15f * k) + (float)Math.sin(phase * 0.7f + k) * hh * 0.25f;
                float r = Math.min(w, hh) * (0.35f + 0.05f * (float)Math.sin(phase + k));
                int a = 40 + (int)(25 * (0.5f + 0.5f * (float)Math.sin(phase + k)));
                p.setShader(new android.graphics.RadialGradient(ox, oy, r,
                        new int[]{Color.argb(a, 255, 255, 255), Color.TRANSPARENT},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(ox, oy, r, p);
            }
            p.setShader(null);

            if (particlesEnabled) {
                for (int i = 0; i < N; i++) {
                    dx[i] += dv[i];
                    if (dx[i] > 1.05f) { dx[i] = -0.05f; dy[i] = (float)Math.random(); }
                    c.drawCircle(dx[i] * w, dy[i] * hh, 1.6f, dot);
                }
            }
            phase += 0.012f;
            postInvalidateDelayed(20);
        }
    }

    // ===== 玻璃视觉素材 =====
    private GradientDrawable makeGlassBg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), 0x55FFFFFF);
        return g;
    }

    private Button makeBtn(String text, int color, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x33FFFFFF, color & 0x66FFFFFF});
        g.setCornerRadius(dp(18));
        g.setStroke(dp(1), 0x66FFFFFF);
        b.setBackground(g);
        b.setPadding(dp(18), dp(14), dp(18), dp(14));
        b.setOnClickListener(l);
        return b;
    }

    private void addPanelTitle(LinearLayout parent, String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(0xEEFFFFFF);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setShadowLayer(8, 0, 1, 0x99000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        parent.addView(v, lp);
    }

    private int currentMode = Scheduler.MODE_BALANCE;

    private void addModeButton(LinearLayout parent, String name, int mode, int accent) {
        Button b = makeBtn(name, accent, v -> {
            currentMode = mode;
            String out = Scheduler.apply(mode);
            toast(name + " 已应用");
            refreshStatus();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        parent.addView(b, lp);
    }

    private void addGlassButton(LinearLayout parent, String text, android.view.View.OnClickListener l) {
        Button b = makeBtn(text, 0x33FFFFFF, l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        parent.addView(b, lp);
    }

    private void quick(String cmd) {
        String r = Scheduler.run(cmd);
        toast(r.length() > 60 ? r.substring(0, 60) + "…" : r);
        refreshStatus();
    }

    private void pickWallpaper() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(i, REQ_WALLPAPER);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_WALLPAPER && res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            staticWallpaper.setImageURI(u);
            staticWallpaper.setVisibility(View.VISIBLE);
            bgView.setVisibility(View.GONE);
            sp.edit().putString("bg_mode", "static").putString("bg_static_uri", u.toString()).apply();
            toast("静态背景已设置");
        }
    }

    private void refreshStatus() {
        Scheduler.run("true"); // ensure shell ready
        h.post(() -> {
            try {
                String vol = Scheduler.run("media volume --stream 3 --get 2>&1 | tail -1");
                String online = Scheduler.run("cat /sys/devices/system/cpu/online");
                String bmax = Scheduler.run("cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_max_freq 2>/dev/null");
                String bcur = Scheduler.run("cat /sys/devices/system/cpu/cpu4/cpufreq/scaling_cur_freq 2>/dev/null");
                String smax = Scheduler.run("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq 2>/dev/null");
                String therm = Scheduler.run("cat /sys/class/thermal/thermal_zone1/temp 2>/dev/null");
                String bat = Scheduler.run("dumpsys battery | grep -E 'level|temperature' | tr '\
' ' '");

                StringBuilder sb = new StringBuilder();
                sb.append("▪ 音量：").append(vol.replace("[v] ", "")).append("
");
                sb.append("▪ 在线核心：").append(online).append("
");
                sb.append("▪ 大核 频率：").append(khz(bcur)).append(" / 上限 ").append(khz(bmax)).append("
");
                sb.append("▪ 小核 上限：").append(khz(smax)).append("
");
                sb.append("▪ CPU 温度：").append(temp(therm)).append("
");
                sb.append("▪ 电池：").append(bat.replace(" ", "  "));
                statusText.setText(sb.toString());
            } catch (Exception e) {
                statusText.setText("读取失败：" + e.getMessage());
            }
        });
    }

    private String khz(String s) {
        try { long v = Long.parseLong(s.trim()); return String.format("%.2fGHz", v / 1000000.0); }
        catch (Exception e) { return "-"; }
    }

    private String temp(String s) {
        try { float v = Float.parseFloat(s.trim()); return String.format("%.1f°C", v / 1000f); }
        catch (Exception e) { return "-"; }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        boolean tablet = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (tablet != isTabletMode) {
            isTabletMode = tablet;
            updateLayoutMode();
        }
    }

    private void updateLayoutMode() {
        if (isTabletMode) {
            titleView.setText("K90 调度器 · 平板模式");
        } else {
            titleView.setText("K90 调度器");
            // 手机模式：左右栏改成上下堆叠
            LinearLayout colParent = (LinearLayout) panelLeft.getParent();
            if (colParent != null && colParent.getOrientation() == LinearLayout.HORIZONTAL) {
                colParent.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) panelRight.getLayoutParams();
                lp.width = -1; lp.weight = 0; lp.leftMargin = 0; lp.topMargin = dp(16);
                panelRight.setLayoutParams(lp);
                LinearLayout.LayoutParams lp1 = (LinearLayout.LayoutParams) panelLeft.getLayoutParams();
                lp1.width = -1; lp1.weight = 0;
                panelLeft.setLayoutParams(lp1);
            }
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 横屏初始检测
        boolean landscape = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (landscape != isTabletMode) {
            isTabletMode = landscape;
            updateLayoutMode();
        }
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}