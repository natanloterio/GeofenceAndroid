package me.loterio.tunity.utils

import android.content.Context
import me.loterio.tunity.services.GeofenceErrorMessages

fun Exception.getHumanReadableErrorMessage(context: Context): String =
    GeofenceErrorMessages.getErrorString(context, this)