package me.loterio.tunity.ui

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.material.snackbar.Snackbar
import me.loterio.tunity.BuildConfig
import me.loterio.tunity.R
import me.loterio.tunity.data.GeofencePosition
import me.loterio.tunity.databinding.ActivityMainBinding
import me.loterio.tunity.showGeofenceInMap
import timber.log.Timber

class MainActivity : BaseActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {
    private lateinit var map: GoogleMap
    private lateinit var locationManager: LocationManager
    private lateinit var viewBinding: ActivityMainBinding

    private val runningQOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)
            .apply {
                viewBinding = this
                val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
                mapFragment.getMapAsync(this@MainActivity)
                newGeofence.visibility = View.GONE
                currentLocation.visibility = View.GONE
                newGeofence.setOnClickListener {
                    this@MainActivity.map.run {
                        val intent = NewGeofenceActivity.newIntent(
                            this@MainActivity, cameraPosition.target, cameraPosition.zoom
                        )
                        startActivityForResult(intent, NEW_GEOFENCE_REQUEST_CODE)
                    }
                }

                locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            }
    }

    override fun onStart() {
        super.onStart()
        checkPermissionsAndStartGeofencing()
    }

    
    private fun checkPermissionsAndStartGeofencing() {
        if (foregroundAndBackgroundLocationPermissionApproved()) {
            checkDeviceLocationSettingsAndStartMap()
        } else {
            requestForegroundAndBackgroundLocationPermissions()
        }
    }

    
    @TargetApi(29)
    private fun foregroundAndBackgroundLocationPermissionApproved(): Boolean {
        val foregroundLocationApproved = (PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION))
        val backgroundLocationApproved = if (runningQOrLater)
        { PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
        else true

        Timber.d("Checking foreground and background permissions granted: %b", "${foregroundLocationApproved && backgroundLocationApproved}")
        return foregroundLocationApproved && backgroundLocationApproved
    }

    
    private fun checkDeviceLocationSettingsAndStartMap(resolve: Boolean = true) {
        Timber.d("Checking device location settings and start Geofence")
        val locationRequest = LocationRequest.create().apply { priority = LocationRequest.PRIORITY_LOW_POWER }
        val locationSettingsRequestBuilder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val settingsClient = LocationServices.getSettingsClient(this)
        val locationSettingsResponseTask = settingsClient.checkLocationSettings(locationSettingsRequestBuilder.build())

        locationSettingsResponseTask.addOnFailureListener { exception ->
            Timber.e("Requesting to check device location settings failed: $exception")
            if (exception is ResolvableApiException && resolve) {
                Timber.d("Error is resolvable so we'll start resolution")
                try {
                    exception.startResolutionForResult(this, REQUEST_TURN_DEVICE_LOCATION_ON)
                } catch (sendExc: IntentSender.SendIntentException) {
                    Timber.e(sendExc, "Error getting location settings resolution: %s", sendExc.message)
                }
            } else {
                Timber.d("Prompting user to try check device settings and start Geofence again")
                Snackbar.make(viewBinding.main, getString(R.string.location_required_error), Snackbar.LENGTH_INDEFINITE).setAction(R.string.try_again) {
                    checkDeviceLocationSettingsAndStartMap()
                }.show()
            }
        }
        locationSettingsResponseTask.addOnSuccessListener {
            Timber.d("Requesting to check device location settings was successful")
            onMapAndPermissionReady()
        }
    }

    
    @TargetApi(29 )
    private fun requestForegroundAndBackgroundLocationPermissions() {
        if (foregroundAndBackgroundLocationPermissionApproved())
            return
        var permissionsArray = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val requestCode = when {
            runningQOrLater -> {
                Timber.d("Requesting foreground and background location permissions")
                permissionsArray += Manifest.permission.ACCESS_BACKGROUND_LOCATION
                REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE
            }
            else -> {
                Timber.d("Requesting only foreground location permission")
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            }
        }
        ActivityCompat.requestPermissions(
            this,
            permissionsArray,
            requestCode
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == NEW_GEOFENCE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            showGeofences()

            val geofence = getRepository().getLast()
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(geofence?.latLng, 15f))

            Snackbar.make(viewBinding.main, R.string.geofence_added_success, Snackbar.LENGTH_LONG).show()
        }
    }

    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        Timber.d(
            "onRequestPermissionsResult() resulted %s for permissions %s",
            grantResults.joinToString(prefix = "[", postfix = "]") { if (it == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED" },
            permissions.joinToString(prefix = "[", postfix = "]")
        )

        if (grantResults.isEmpty() ||
            grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED ||
            (requestCode == REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE && grantResults[BACKGROUND_LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED)) {
            Snackbar.make(
                viewBinding.main,
                getString(R.string.permission_denied_explanation),
                Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.settings) {
                startActivity(Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }.show()
        } else
            checkDeviceLocationSettingsAndStartMap()
    }

    private fun onMapAndPermissionReady() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true
            viewBinding.newGeofence.visibility = View.VISIBLE
            viewBinding.currentLocation.visibility = View.VISIBLE

            viewBinding.currentLocation.setOnClickListener {
                locationManager.getBestProvider(Criteria(), false)?.let { bestProvider ->
                    val location = locationManager.getLastKnownLocation(bestProvider)
                    if (location != null) {
                        val latLng = LatLng(location.latitude, location.longitude)
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    }
                }
            }

            showGeofences()
            centerCamera()
        }
    }

    private fun centerCamera() {
        intent.extras.takeIf { it != null && it.containsKey(EXTRA_LAT_LNG) }?.let {
            val latLng = it.get(EXTRA_LAT_LNG) as LatLng
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        }
    }

    private fun showGeofences() {
        map.run {
            clear()
            for (geofence in getRepository().getAll()) {
                showGeofenceInMap(this@MainActivity, this, geofence)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.run {
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isMapToolbarEnabled = false
            setOnMarkerClickListener(this@MainActivity)
        }

        onMapAndPermissionReady()
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val geofence = getRepository().get(marker.tag as String)

        if (geofence != null) {
            showGeofenceRemoveAlert(geofence)
        }

        return true
    }

    private fun showGeofenceRemoveAlert(geofencePosition: GeofencePosition) {
        val alertDialog = AlertDialog.Builder(this).create()
        alertDialog.run {
            setMessage(getString(R.string.geofence_removal_alert))
            setButton(
                AlertDialog.BUTTON_POSITIVE,
                getString(R.string.geofence_removal_alert_positive)
            ) { dialog, _ ->
                removeGeofence(geofencePosition)
                dialog.dismiss()
            }
            setButton(
                AlertDialog.BUTTON_NEGATIVE,
                getString(R.string.geofence_removal_alert_negative)
            ) { dialog, _ ->
                dialog.dismiss()
            }
            show()
        }
    }

    private fun removeGeofence(geofencePosition: GeofencePosition) {
        getRepository().remove(
            geofencePosition,
            success = {
                showGeofences()
                Snackbar.make(viewBinding.main, R.string.geofence_removed_success, Snackbar.LENGTH_LONG).show()
            },
            failure = {
                Snackbar.make(viewBinding.main, it, Snackbar.LENGTH_LONG).show()
            })
    }

    companion object {
        private const val NEW_GEOFENCE_REQUEST_CODE = 330
        private const val EXTRA_LAT_LNG = "EXTRA_LAT_LNG"
        private const val REQUEST_FOREGROUND_AND_BACKGROUND_PERMISSION_RESULT_CODE = 33
        private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
        private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
        private const val LOCATION_PERMISSION_INDEX = 0
        private const val BACKGROUND_LOCATION_PERMISSION_INDEX = 1

        fun newIntent(context: Context, latLng: LatLng): Intent {
            val intent = Intent(context, MainActivity::class.java)
            intent.putExtra(EXTRA_LAT_LNG, latLng)
            return intent
        }
    }
}
