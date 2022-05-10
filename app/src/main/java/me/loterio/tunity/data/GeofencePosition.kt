
package me.loterio.tunity.data

import com.google.android.gms.maps.model.LatLng
import java.util.*

data class GeofencePosition(val id: String = UUID.randomUUID().toString(),
                            var latLng: LatLng?,
                            var radius: Double?)