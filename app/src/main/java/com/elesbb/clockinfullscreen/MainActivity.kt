package com.elesbb.clockinfullscreen

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowInsets
import android.widget.CompoundButton
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButton.OnCheckedChangeListener

class MainActivity : AppCompatActivity() {

    private var runnerService: RunnerService? = null

    private val serviceConnect = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            runnerService = (p1 as RunnerService.LocalBinder).getService()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            runnerService = null
        }

    }

    override fun onStop() {
        super.onStop()
        if (runnerService != null) {
            unbindService(serviceConnect)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        val myPrefs = getSharedPreferences("ops", MODE_PRIVATE)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        RequestPermissions()
        val intent = Intent(this@MainActivity, RunnerService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnect, Context.BIND_AUTO_CREATE)

        Handler(mainLooper).postDelayed({
            window.insetsController?.hide(WindowInsets.Type.statusBars())
        }, 500)

        val clockSizer = findViewById<SeekBar>(R.id.clock_sizer)
        val batSizer = findViewById<SeekBar>(R.id.battery_sizer)
        val transSlider = findViewById<SeekBar>(R.id.transparency_slider)
        val topLeft = findViewById<RadioButton>(R.id.pos_top_left)
        topLeft.tag = (Gravity.TOP or Gravity.LEFT)
        val topRight = findViewById<RadioButton>(R.id.pos_top_right)
        topRight.tag = (Gravity.TOP or Gravity.RIGHT)
        val bottomLeft = findViewById<RadioButton>(R.id.pos_bottom_left)
        bottomLeft.tag = (Gravity.BOTTOM or Gravity.LEFT)
        val bottomRight = findViewById<RadioButton>(R.id.pos_bottom_right)
        bottomRight.tag = (Gravity.BOTTOM or Gravity.RIGHT)

        val pos = myPrefs.getInt("pos", Gravity.TOP or Gravity.LEFT)

        when (pos) {
            topLeft.tag -> topLeft.isChecked = true
            topRight.tag -> topRight.isChecked = true
            bottomLeft.tag -> bottomLeft.isChecked = true
            bottomRight.tag -> bottomRight.isChecked = true
        }

        val checkedChanged = object : CompoundButton.OnCheckedChangeListener {

            var ignore = false

            override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
                Log.e("ELESBB_RADIO", "Ignore: $ignore")
                if (ignore) {
                    ignore = false
                    return
                }

                val value = buttonView?.tag as Int

                ignore = true
                when (buttonView?.id) {
                    topLeft.id -> {
                        topRight.isChecked = !isChecked
                        bottomLeft.isChecked = !isChecked
                        bottomRight.isChecked = !isChecked
                    }

                    topRight.id -> {
                        topLeft.isChecked = !isChecked
                        bottomLeft.isChecked = !isChecked
                        bottomRight.isChecked = !isChecked
                    }

                    bottomLeft.id -> {
                        topRight.isChecked = !isChecked
                        topLeft.isChecked = !isChecked
                        bottomRight.isChecked = !isChecked
                    }

                    bottomRight.id -> {
                        topRight.isChecked = !isChecked
                        topLeft.isChecked = !isChecked
                        bottomLeft.isChecked = !isChecked
                    }
                }

                myPrefs.edit().putInt("pos", value).apply()
                runnerService?.SetPosition(value)
            }

        }

        topLeft.setOnCheckedChangeListener(checkedChanged)
        topRight.setOnCheckedChangeListener(checkedChanged)
        bottomLeft.setOnCheckedChangeListener(checkedChanged)
        bottomRight.setOnCheckedChangeListener(checkedChanged)

        var savedSize = myPrefs.getFloat("clock_size", 20F)
        savedSize = (savedSize / 50F) * 100F
        clockSizer.progress = savedSize.toInt()

        savedSize = myPrefs.getFloat("bat_size", 20F)
        savedSize = (savedSize / 50F) * 100F
        batSizer.progress = savedSize.toInt()

        transSlider.progress = (myPrefs.getFloat("alpha", 1F) * 100F).toInt()

        val sizer = object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (runnerService == null) return
                val size = (progress.toFloat() / 100F) * 50F
                when (seekBar?.id) {
                    R.id.battery_sizer -> {
                        runnerService?.SetBatteryTextSize(size)
                        myPrefs.edit().putFloat("bat_size", size).apply()
                    }

                    R.id.clock_sizer -> {
                        runnerService?.SetClockTextSiz(size)
                        myPrefs.edit().putFloat("clock_size", size).apply()
                    }

                    R.id.transparency_slider -> {
                        val trans = progress / 100F
                        runnerService?.SetTransparency(trans)
                        myPrefs.edit().putFloat("alpha", trans).apply()
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        clockSizer.setOnSeekBarChangeListener(sizer)
        batSizer.setOnSeekBarChangeListener(sizer)
        transSlider.setOnSeekBarChangeListener(sizer)
    }


    private fun RequestPermissions() {
        if (!Settings.canDrawOverlays(this@MainActivity)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}