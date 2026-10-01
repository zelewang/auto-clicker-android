package com.doubao.autoclicker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private EditText etX;
    private EditText etY;
    private EditText etInterval;
    private EditText etCount;
    private Button btnToggle;
    private TextView tvStatus;
    private int screenW;
    private int screenH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void buildUi() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels;
        screenH = dm.heightPixels;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Color.parseColor("#111827"));
        root.addView(title);

        TextView info = new TextView(this);
        info.setText("屏幕分辨率：" + screenW + " × " + screenH + "，坐标单位为像素");
        info.setTextSize(13);
        info.setGravity(Gravity.CENTER);
        info.setTextColor(Color.parseColor("#6B7280"));
        root.addView(info);

        etX = addInput(root, "点击 X 坐标（像素）", String.valueOf(screenW / 2));
        etY = addInput(root, "点击 Y 坐标（像素）", String.valueOf(screenH / 2));
        etInterval = addInput(root, "连点间隔（毫秒，建议 50 以上）", "100");
        etCount = addInput(root, "连点次数（0 = 无限，直到手动停止）", "0");

        Button btnTest = new Button(this);
        btnTest.setText("测点：先点一次，验证坐标位置");
        root.addView(btnTest);

        btnToggle = new Button(this);
        root.addView(btnToggle);

        tvStatus = new TextView(this);
        tvStatus.setTextSize(14);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, dp(8), 0, dp(8));
        root.addView(tvStatus);

        Button btnEnable = new Button(this);
        btnEnable.setText("去开启无障碍服务（首次使用必须）");
        root.addView(btnEnable);

        TextView tip = new TextView(this);
        tip.setText("使用步骤：\n" +
                "1. 点上方按钮，在系统设置中找到「自动连点器」并打开开关\n" +
                "2. 回到本界面，填好 X/Y 坐标、间隔和次数\n" +
                "3. 先点「测点」验证位置，再点「开始连点」\n" +
                "提示：连点期间可以切到任意应用，点击会作用在当前屏幕的对应坐标");
        tip.setTextSize(13);
        tip.setTextColor(Color.parseColor("#6B7280"));
        root.addView(tip);

        setContentView(root);

        btnTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ensureService()) {
                    return;
                }
                int x = getX();
                int y = getY();
                if (x < 0 || y < 0) {
                    return;
                }
                AutoClickService.singleTap(x, y);
            }
        });

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (AutoClickService.isRunning()) {
                    AutoClickService.stopClick();
                } else {
                    if (!ensureService()) {
                        return;
                    }
                    int x = getX();
                    int y = getY();
                    if (x < 0 || y < 0) {
                        return;
                    }
                    AutoClickService.startClick(x, y, getInterval(), getCount());
                }
                refresh();
            }
        });

        btnEnable.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
    }

    private EditText addInput(LinearLayout root, String label, String def) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setTextColor(Color.parseColor("#374151"));
        tv.setPadding(0, dp(10), 0, dp(2));
        root.addView(tv);

        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(def);
        et.setTextSize(16);
        et.setSingleLine(true);
        root.addView(et);
        return et;
    }

    private int parse(EditText et) {
        try {
            return Integer.parseInt(et.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
            return -1;
        }
    }

    private int getX() {
        int v = parse(etX);
        if (v < 0) {
            return v;
        }
        return Math.min(v, screenW - 1);
    }

    private int getY() {
        int v = parse(etY);
        if (v < 0) {
            return v;
        }
        return Math.min(v, screenH - 1);
    }

    private int getInterval() {
        return parse(etInterval);
    }

    private int getCount() {
        return parse(etCount);
    }

    private boolean ensureService() {
        if (isAccessibilityEnabled()) {
            return true;
        }
        Toast.makeText(this, "请先在系统设置中开启本应用的无障碍服务", Toast.LENGTH_LONG).show();
        return false;
    }

    private boolean isAccessibilityEnabled() {
        // 通过系统设置中的已开启无障碍服务列表判断（跨版本稳定，不依赖 AccessibilityServiceInfo 的 API 变动）
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) {
            return false;
        }
        String flat = new ComponentName(this, AutoClickService.class).flattenToString();
        for (String s : enabled.split(":")) {
            if (s.equalsIgnoreCase(flat)) {
                return true;
            }
        }
        return false;
    }

    private void refresh() {
        boolean enabled = isAccessibilityEnabled();
        boolean running = AutoClickService.isRunning();
        if (!enabled) {
            tvStatus.setText("无障碍服务未开启，无法连点");
            tvStatus.setTextColor(Color.parseColor("#DC2626"));
            btnToggle.setText("开始连点");
            btnToggle.setEnabled(false);
        } else if (running) {
            tvStatus.setText("连点中，可切换到目标应用使用");
            tvStatus.setTextColor(Color.parseColor("#16A34A"));
            btnToggle.setText("停止连点");
            btnToggle.setEnabled(true);
        } else {
            tvStatus.setText("就绪，可开始连点");
            tvStatus.setTextColor(Color.parseColor("#16A34A"));
            btnToggle.setText("开始连点");
            btnToggle.setEnabled(true);
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
