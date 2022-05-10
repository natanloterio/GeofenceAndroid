
package me.loterio.tunity

import android.app.Application
import me.loterio.tunity.data.GeofencesRepository
import timber.log.Timber

class GeoFenceApp : Application() {
    private lateinit var mRepository: GeofencesRepository

    override fun onCreate() {
        super.onCreate()
        BuildConfig.DEBUG.takeIf { it }?.run { Timber.plant(Timber.DebugTree()) }
        mRepository = GeofencesRepository(this)
    }

    fun getRepository() = mRepository
}