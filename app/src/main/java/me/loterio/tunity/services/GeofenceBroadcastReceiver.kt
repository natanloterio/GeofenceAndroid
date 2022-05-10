package me.loterio.tunity.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("GeofenceBroadcastReceiver received intent")
        GeofenceTransitionsJobIntentService.enqueueWork(context, intent)
    }
}