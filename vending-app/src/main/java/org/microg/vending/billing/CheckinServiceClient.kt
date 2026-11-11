// Modified by NPatch contributors for embedded billing integration (2026).
/*
 * SPDX-FileCopyrightText: 2024 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.vending.billing

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.google.android.gms.checkin.internal.ICheckinService
import com.android.vending.BuildConfig
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

// TODO: Connect to check-in settings provider instead
object CheckinServiceClient {
    private val serviceQueue = LinkedBlockingQueue<ICheckinService>()

    fun getConsistencyToken(context: Context): String {
        try {
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    service?.let { serviceQueue.add(ICheckinService.Stub.asInterface(it)) }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    serviceQueue.clear()
                }
            }
            val intent = Intent("${BuildConfig.BASE_PACKAGE_NAME}.android.gms.checkin.BIND_TO_SERVICE")
            intent.setPackage("${BuildConfig.BASE_PACKAGE_NAME}.android.gms")
            val res = context.bindService(intent, conn, BIND_AUTO_CREATE)
            if (!res) return ""
            try {
                val service = serviceQueue.poll(10, TimeUnit.SECONDS) ?: return ""
                return service.deviceDataVersionInfo ?: ""
            } finally {
                context.unbindService(conn)
            }
        } catch (e: Exception) {
            Log.e(TAG, "getConsistencyToken", e)
            return ""
        }
    }
}
