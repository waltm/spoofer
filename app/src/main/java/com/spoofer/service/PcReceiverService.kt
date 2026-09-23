package com.spoofer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.spoofer.data.PreferencesDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class PcReceiverService : BroadcastReceiver() {

    @Inject lateinit var preferencesDataStore: PreferencesDataStore

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_PREF_CHANGED -> {
                val pcReceiverEnabled = runBlocking { preferencesDataStore.pcReceiverModeEnabled.first() }
                if (pcReceiverEnabled) {
                    Log.d(TAG, "PC Receiver enabled in settings — broadcasting start")
                    context.sendBroadcast(Intent(ACTION_START_PC_RECEIVER).setPackage(context.packageName))
                } else {
                    Log.d(TAG, "PC Receiver disabled in settings — broadcasting stop")
                    context.sendBroadcast(Intent(ACTION_STOP).setPackage(context.packageName))
                }
            }
            ACTION_START_PC_RECEIVER -> {
                // Broadcast to any other components that need to know
                context.sendBroadcast(Intent(ACTION_START_PC_RECEIVER).setPackage(context.packageName))
            }
            ACTION_STOP -> {
                context.sendBroadcast(Intent(ACTION_STOP).setPackage(context.packageName))
            }
        }
    }

    companion object {
        private const val TAG = "PcReceiverService"
        const val ACTION_PREF_CHANGED = "com.spoofer.action.PREF_CHANGED"
        const val ACTION_START_PC_RECEIVER = "com.spoofer.action.START_PC_RECEIVER"
        const val ACTION_STOP = "com.spoofer.action.STOP"
    }
}
