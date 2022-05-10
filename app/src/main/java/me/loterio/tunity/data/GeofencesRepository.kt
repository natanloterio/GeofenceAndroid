

package me.loterio.tunity.data

import android.Manifest.permission
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.content.ContextCompat.checkSelfPermission
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import me.loterio.tunity.services.GeofenceBroadcastReceiver
import me.loterio.tunity.utils.getHumanReadableErrorMessage

class GeofencesRepository(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "GeofenceRepository"
        private const val GEOFENCES = "GEOFENCES"
    }

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val geofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun add(geofencePosition: GeofencePosition, success: () -> Unit, failure: (error: String) -> Unit) {
        val geofence = buildGeofence(geofencePosition)
        if (geofence != null && checkSelfPermission(context, permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED) {
            geofencingClient.addGeofences(buildGeofencingRequest(geofence), geofencePendingIntent)
                .addOnSuccessListener {
                    saveAll(getAll() + geofencePosition)
                    success()
                }
                .addOnFailureListener { failure(it.getHumanReadableErrorMessage(context)) }
        }
    }

    private fun buildGeofence(geofencePosition: GeofencePosition): Geofence? {
        val latitude = geofencePosition.latLng?.latitude
        val longitude = geofencePosition.latLng?.longitude
        val radius = geofencePosition.radius
        if (latitude != null && longitude != null && radius != null) {
            return Geofence.Builder()
                .setRequestId(geofencePosition.id)
                .setCircularRegion(
                    latitude,
                    longitude,
                    radius.toFloat()
                )
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }
        return null
    }

    private fun buildGeofencingRequest(geofence: Geofence): GeofencingRequest {
        return GeofencingRequest.Builder()
            .addGeofence(geofence)
            .setInitialTrigger(0)
            .build()
    }

    fun remove(
        geofencePosition: GeofencePosition,
        success: () -> Unit,
        failure: (error: String) -> Unit
    ) {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                saveAll(getAll() - geofencePosition)
                success()
            }
            .addOnFailureListener { failure(it.getHumanReadableErrorMessage(context)) }
    }

    private fun saveAll(list: List<GeofencePosition>) {
        preferences
            .edit()
            .putString(GEOFENCES, gson.toJson(list))
            .apply()
    }

    fun getAll(): List<GeofencePosition> {
        if (preferences.contains(GEOFENCES)) {
            val geofencesString = preferences.getString(GEOFENCES, null)
            val arrayOfGeofences = gson.fromJson(
                geofencesString,
                Array<GeofencePosition>::class.java
            )
            if (arrayOfGeofences != null) {
                return arrayOfGeofences.toList()
            }
        }
        return listOf()
    }

    fun get(requestId: String?) = getAll().firstOrNull { it.id == requestId }

    fun getLast() = getAll().lastOrNull()
}