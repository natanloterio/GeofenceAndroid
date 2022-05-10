
package me.loterio.tunity.ui

import androidx.appcompat.app.AppCompatActivity
import me.loterio.tunity.GeoFenceApp

abstract class BaseActivity : AppCompatActivity() {
  fun getRepository() = (application as GeoFenceApp).getRepository()
}