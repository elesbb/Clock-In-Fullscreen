package com.elesbb.clockinfullscreen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView

class RunnerService : Service() {

    private val localBinder = LocalBinder()

    private val HIDE_ACTION = "HIDE_ACTION"
    private val SHOW_ACTION = "SHOW_ACTION"

    private val notiId = 1

    val clockParams = WindowManager.LayoutParams()

    private lateinit var batteryText: TextView
    private lateinit var clockText: TextClock
    private lateinit var floatingLayout: LinearLayout
    private lateinit var windowManager: WindowManager
    private val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    private lateinit var batteryReceiver: BroadcastReceiver

    private var shouldShow = true

    private fun GetNotification(buttonContent: String, _action: String): Notification.Builder {
        val toggleVisibilityIntent = Intent(this@RunnerService, RunnerService::class.java).apply {
            action = _action
        }

        val togglePi = PendingIntent.getService(this@RunnerService, 1, toggleVisibilityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        val noti = Notification.Builder(this@RunnerService, "detector_service")
        noti.setSmallIcon(R.mipmap.ic_launcher)
        val notiAction = Notification.Action.Builder(0, buttonContent, togglePi)
        noti.addAction(notiAction.build())

        noti.setContentTitle("Detector Service")
        noti.setContentText("Detector service running. Used to detect full screen state")
        return noti
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (intent!!.action.equals(HIDE_ACTION)) {
            Log.d("ELESBB", "Hide")
            windowManager.removeView(floatingLayout)
            notificationManager.notify(notiId, GetNotification("SHOW", SHOW_ACTION).build())
            shouldShow = false

            return START_STICKY
        } else if (intent.action.equals(SHOW_ACTION)) {
            Log.d("ELESBB", "Show")
            windowManager.addView(floatingLayout, clockParams)
            notificationManager.notify(notiId, GetNotification("HIDE", HIDE_ACTION).build())
            shouldShow = true

            return START_STICKY
        }

        val myPrefs = getSharedPreferences("ops", MODE_PRIVATE)
        val notiChannel = NotificationChannel("detector_service", "Detector", NotificationManager.IMPORTANCE_MIN)

        notificationManager.createNotificationChannel(notiChannel)


        startForeground(notiId, GetNotification("HIDE", HIDE_ACTION).build())

        floatingLayout = LayoutInflater.from(this@RunnerService).inflate(R.layout.clock_layout, null, false) as LinearLayout
        clockText = floatingLayout.findViewById(R.id.floating_clock)
        val detectorLayout = LinearLayout(this@RunnerService)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager


        batteryText = floatingLayout.findViewById(R.id.battery_level)

        SetClockTextSiz(myPrefs.getFloat("clock_size", 20F))
        SetBatteryTextSize(myPrefs.getFloat("bat_size", 20F))
        floatingLayout.alpha = myPrefs.getFloat("alpha", 1F)

        val detectorParams = WindowManager.LayoutParams()
        detectorParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        detectorParams.format = PixelFormat.RGBA_8888
        detectorParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        detectorParams.gravity = Gravity.LEFT or Gravity.TOP
        detectorParams.alpha = 0F
        detectorParams.width = 0
        detectorParams.height = 0
        detectorParams.x = 0
        detectorParams.y = 0

        clockParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        clockParams.format = PixelFormat.RGBA_8888
        clockParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        clockParams.gravity = myPrefs.getInt("pos", (Gravity.TOP or Gravity.LEFT))
        clockParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        clockParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        clockParams.x = 0
        clockParams.y = 0

        windowManager.addView(detectorLayout, detectorParams)



        detectorLayout.post( {
            detectorLayout.setOnApplyWindowInsetsListener(object : View.OnApplyWindowInsetsListener {

                override fun onApplyWindowInsets(p0: View, p1: WindowInsets): WindowInsets {
                    val isVisible = p1.isVisible(WindowInsets.Type.statusBars())
                    Log.d("ELESBB_WINDOW", "Is Visible: $isVisible")

                    if (!isVisible) {
                        try {
                            if (!shouldShow) return p1
                            Log.d("ELESBB_WINDOW", "Should be showing clock")
                            windowManager.addView(floatingLayout, clockParams)
                            registerReceiver(batteryReceiver, intentFilter)
                        } catch (_: Exception) {}

                    } else {
                        try {
                            Log.d("ELESBB_WINDOW", "Should be hiding clock")
                            windowManager.removeView(floatingLayout)
                            unregisterReceiver(batteryReceiver)
                        } catch (_: Exception) {}
                    }
                    return p1
                }

            })
        })

        SetupBatteryLevelMonitor()

        return START_STICKY
    }

    private fun SetupBatteryLevelMonitor() {

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != null && intent.action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                    val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batLevel = (level * 100 / scale.toFloat()).toInt()
                    batteryText.setText("$batLevel%")
                }
            }
        }
//        registerReceiver(batteryReceiver, intentFilter)
    }


    fun SetClockTextSiz(size: Float) {
        clockText.textSize = size
    }

    fun SetBatteryTextSize(size: Float) {
        batteryText.textSize = size
    }

    fun SetTransparency(alpha: Float) {
        floatingLayout.alpha = alpha
//        floatingLayout.requestLayout()
//        floatingLayout.invalidate()
    }

    fun SetPosition(pos: Int) {
        val params = floatingLayout.layoutParams as WindowManager.LayoutParams
        params.gravity = pos
        windowManager.updateViewLayout(floatingLayout, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
    }


    override fun onBind(intent: Intent?): IBinder {
        return localBinder
    }

    inner class LocalBinder : Binder() {
        fun getService(): RunnerService = this@RunnerService
    }
}