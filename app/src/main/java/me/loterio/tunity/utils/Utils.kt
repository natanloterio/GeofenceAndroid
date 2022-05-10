

package me.loterio.tunity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import me.loterio.tunity.data.GeofencePosition
import me.loterio.tunity.ui.MainActivity
import timber.log.Timber


fun hideKeyboard(context: Context, view: View) {
    val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    keyboard.hideSoftInputFromWindow(view.windowToken, 0)
}

fun vectorToBitmap(resources: Resources, @DrawableRes id: Int): BitmapDescriptor {
    val vectorDrawable = ResourcesCompat.getDrawable(resources, id, null)
    val bitmap = Bitmap.createBitmap(
        vectorDrawable!!.intrinsicWidth,
        vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
    vectorDrawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

fun showGeofenceInMap(
    context: Context,
    map: GoogleMap,
    geofencePosition: GeofencePosition
) {
    if (geofencePosition.latLng != null) {
        val latLng = geofencePosition.latLng as LatLng
        val vectorToBitmap =
            vectorToBitmap(context.resources, R.drawable.ic_twotone_location_on_48px)
        val marker = map.addMarker(MarkerOptions().position(latLng).icon(vectorToBitmap))
        marker.tag = geofencePosition.id
        if (geofencePosition.radius != null) {
            val radius = geofencePosition.radius as Double
            map.addCircle(
                CircleOptions()
                    .center(geofencePosition.latLng)
                    .radius(radius)
                    .strokeColor(ContextCompat.getColor(context, R.color.colorAccent))
                    .fillColor(ContextCompat.getColor(context, R.color.colorGeofenceFill))
            )
        }
    }
}

private const val NOTIFICATION_CHANNEL_ID = BuildConfig.APPLICATION_ID + ".channel"

fun sendNotification(context: Context, message: String, latLng: LatLng) {
    val notificationManager = context
        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        && notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null
    ) {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.app_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableLights(true)
            lightColor = Color.BLUE
            enableVibration(true)
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        Timber.d("Targeting Android O or higher: Creating channel ${channel.name}")
        notificationManager.createNotificationChannel(channel)
    }

    val intent = MainActivity.newIntent(context.applicationContext, latLng)

    val stackBuilder = TaskStackBuilder.create(context)
        .addParentStack(MainActivity::class.java)
        .addNextIntent(intent)
    val notificationPendingIntent = stackBuilder
        .getPendingIntent(getUniqueId(), PendingIntent.FLAG_UPDATE_CURRENT)

    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(message)
        .setContentIntent(notificationPendingIntent)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setVibrate(longArrayOf(100, 200, 100, 200))
        .build()

    notificationManager.notify(getUniqueId(), notification)
        .also { Timber.d("Notification sent: $message") }
}

private fun getUniqueId() = ((System.currentTimeMillis() % 10000).toInt())