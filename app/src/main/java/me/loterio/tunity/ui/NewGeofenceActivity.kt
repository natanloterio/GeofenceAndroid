

package me.loterio.tunity.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.databinding.DataBindingUtil
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import me.loterio.tunity.R
import me.loterio.tunity.data.GeofencePosition
import me.loterio.tunity.databinding.ActivityNewGeofenceBinding
import me.loterio.tunity.hideKeyboard
import me.loterio.tunity.showGeofenceInMap

class NewGeofenceActivity : BaseActivity(), OnMapReadyCallback {
    private lateinit var viewBinding: ActivityNewGeofenceBinding
    private lateinit var map: GoogleMap
    private var geofencePosition = GeofencePosition(latLng = null, radius = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityNewGeofenceBinding>(this, R.layout.activity_new_geofence)
            .apply {
                viewBinding = this
                val mapFragment = supportFragmentManager
                    .findFragmentById(R.id.map) as SupportMapFragment
                mapFragment.getMapAsync(this@NewGeofenceActivity)

                instructionTitle.visibility = View.GONE
                instructionSubtitle.visibility = View.GONE
                radiusDescription.visibility = View.GONE

                supportActionBar?.setDisplayHomeAsUpEnabled(true)
            }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isMapToolbarEnabled = false

        centerCamera()
        showConfigureLocationStep()
    }

    private fun centerCamera() {
        intent.extras.takeIf {
            it != null &&
                    it.containsKey(EXTRA_LAT_LNG) &&
                    it.containsKey(EXTRA_ZOOM)
        }?.let {
            val latLng = it.get(EXTRA_LAT_LNG) as LatLng
            val zoom = it.get(EXTRA_ZOOM) as Float
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
        }
    }

    private fun showConfigureLocationStep() {
        with(viewBinding) {
            marker.visibility = View.VISIBLE
            instructionTitle.visibility = View.VISIBLE
            instructionSubtitle.visibility = View.VISIBLE
            radiusDescription.visibility = View.GONE
            instructionTitle.text = getString(R.string.instruction_where_description)
            next.setOnClickListener {
                geofencePosition.latLng = this@NewGeofenceActivity.map.cameraPosition.target
                geofencePosition.radius = DEFAULT_RADIUS
                hideKeyboard(this@NewGeofenceActivity, next)
                addGeofence(geofencePosition)
            }
        }

        showGeofenceUpdate()
    }


    private fun addGeofence(geofencePosition: GeofencePosition) {
        getRepository().add(geofencePosition,
                            success = {
                setResult(Activity.RESULT_OK)
                finish()
            },
                            failure = {
                Snackbar.make(viewBinding.main, it, Snackbar.LENGTH_LONG).show()
            })
    }

    private fun showGeofenceUpdate() {
        map.clear()
        showGeofenceInMap(this, map, geofencePosition)
    }

    companion object {
        private const val EXTRA_LAT_LNG = "EXTRA_LAT_LNG"
        private const val EXTRA_ZOOM = "EXTRA_ZOOM"

        fun newIntent(context: Context, latLng: LatLng, zoom: Float): Intent {
            val intent = Intent(context, NewGeofenceActivity::class.java)
            intent
                .putExtra(EXTRA_LAT_LNG, latLng)
                .putExtra(EXTRA_ZOOM, zoom)
            return intent
        }

        private val DEFAULT_RADIUS = 50.0
    }
}
