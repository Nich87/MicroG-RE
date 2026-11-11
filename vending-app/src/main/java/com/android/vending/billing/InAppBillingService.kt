// Modified by NPatch contributors for embedded billing integration (2026).
package com.android.vending.billing

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.annotation.RequiresApi
import org.microg.gms.profile.ProfileManager
import org.microg.vending.billing.ContextProvider
import org.microg.vending.billing.InAppBillingServiceImpl

@RequiresApi(21)
open class InAppBillingService: Service() {
    override fun onCreate() {
        super.onCreate()
        val profileData = mutableMapOf<String, String>()
        for ((prefix, type) in listOf("Build" to android.os.Build::class.java,
                                     "Build.VERSION" to android.os.Build.VERSION::class.java)) {
            for (field in type.fields) {
                val value = runCatching { field.get(null) }.getOrNull() ?: continue
                profileData["$prefix.${field.name}"] =
                    if (value is Array<*>) value.joinToString(",") else value.toString()
            }
        }
        ProfileManager.applyProfileData(profileData)
        ContextProvider.init(application)
    }

    override fun onBind(intent: Intent): IBinder {
        return InAppBillingServiceImpl(this)
    }


}
