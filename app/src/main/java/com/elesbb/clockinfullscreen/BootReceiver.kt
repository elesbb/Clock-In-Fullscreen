package com.elesbb.clockinfullscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null && intent.action != null && intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            val runnerIntent = Intent(context, RunnerService::class.java)
            context!!.startForegroundService(runnerIntent)
        }
    }
}