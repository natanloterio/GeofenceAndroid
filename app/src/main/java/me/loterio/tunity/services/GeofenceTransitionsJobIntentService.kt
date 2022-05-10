package me.loterio.tunity.services

import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import me.loterio.tunity.data.GeofencePosition
import me.loterio.tunity.GeoFenceApp
import me.loterio.tunity.R
import me.loterio.tunity.sendNotification
import timber.log.Timber

class GeofenceTransitionsJobIntentService : JobIntentService() {
    override fun onHandleWork(intent: Intent) {
        Timber.d("GeofenceTransitionsJobIntentService received work to handle")
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceErrorMessages.getErrorString(
                this,
                geofencingEvent.errorCode
            )
            Timber.e(errorMessage)
            return
        }
        handleEvent(geofencingEvent)
    }

    private fun handleEvent(event: GeofencingEvent) {
        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Timber.d("GeofenceTransitionsJobIntentService is handling a GEOFENCE_TRANSITION_EXIT")
            val geofence = getFirstGeofence(event.triggeringGeofences)
            val message = getString(R.string.msg_you_did_exit_the_geofence)
            val latLng = geofence?.latLng

            if (latLng != null) {
                Timber.d("GeofenceTransitionsJobIntentService is dispatching the exiting notification")
                sendNotification(this, message, latLng)
            } else { Timber.d("Notification not sent: latLng is null") }
        }
    }

    private fun getFirstGeofence(triggeringGeofences: List<Geofence>): GeofencePosition? {
        val firstGeofence = triggeringGeofences[0]
        return (application as GeoFenceApp).getRepository().get(firstGeofence.requestId)
    }

    companion object {
        private const val JOB_IB = 573

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(
                context,
                GeofenceTransitionsJobIntentService::class.java,
                JOB_IB,
                intent
            )
        }
    }
}