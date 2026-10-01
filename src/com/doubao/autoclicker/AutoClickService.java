package com.doubao.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍连点服务：通过 dispatchGesture 在指定坐标循环模拟点击。
 */
public class AutoClickService extends AccessibilityService {

    private static final String CHANNEL_ID = "click_status";
    private static final int NOTIF_ID = 1001;

    private static AutoClickService sInstance;

    private static volatile boolean sRunning = false;
    private static volatile int sX = -1;
    private static volatile int sY = -1;
    private static volatile long sIntervalMs = 100;
    private static volatile int sRemain = -1; // -1 表示无限

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (!sRunning) {
                return;
            }
            if (sRemain == 0) {
                stopClick();
                return;
            }
            performTap(sX, sY);
            if (sRemain > 0) {
                sRemain--;
            }
            mHandler.postDelayed(this, Math.max(sIntervalMs, 10));
        }
    };

    public static boolean isRunning() {
        return sRunning;
    }

    public static boolean isReady() {
        return sInstance != null;
    }

    public static void startClick(int x, int y, long intervalMs, int count) {
        sX = x;
        sY = y;
        sIntervalMs = Math.max(intervalMs, 10);
        sRemain = count <= 0 ? -1 : count;
        sRunning = true;
        AutoClickService svc = sInstance;
        if (svc != null) {
            svc.schedule();
        }
    }

    public static void stopClick() {
        sRunning = false;
        sRemain = -1;
        AutoClickService svc = sInstance;
        if (svc != null) {
            svc.mHandler.removeCallbacks(svc.mTick);
            svc.stopForegroundInternal();
        }
    }

    public static void singleTap(int x, int y) {
        AutoClickService svc = sInstance;
        if (svc != null) {
            svc.performTap(x, y);
        }
    }

    private void schedule() {
        mHandler.removeCallbacks(mTick);
        mHandler.post(mTick);
        startForegroundInternal();
    }

    private void performTap(int x, int y) {
        if (x < 0 || y < 0) {
            return;
        }
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 10);
        GestureDescription gesture =
                new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, null, null);
    }

    private void startForegroundInternal() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "连点运行状态", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("显示连点器正在后台运行");
            nm.createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("自动连点器运行中")
                    .setContentText("点击回到设置界面")
                    .setSmallIcon(android.R.drawable.ic_menu_agenda)
                    .setContentIntent(pi)
                    .build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } else {
            Notification n = new Notification.Builder(this)
                    .setContentTitle("自动连点器运行中")
                    .setContentText("点击回到设置界面")
                    .setSmallIcon(android.R.drawable.ic_menu_agenda)
                    .setContentIntent(pi)
                    .build();
            startForeground(NOTIF_ID, n);
        }
    }

    private void stopForegroundInternal() {
        if (Build.VERSION.SDK_INT >= 33) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 连点功能无需处理界面事件
    }

    @Override
    public void onInterrupt() {
        // 忽略
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sRunning = false;
        sInstance = null;
        mHandler.removeCallbacks(mTick);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        sRunning = false;
        sInstance = null;
        mHandler.removeCallbacks(mTick);
        super.onDestroy();
    }
}
