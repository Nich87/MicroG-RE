// Modified by NPatch contributors for embedded billing integration (2026).
/*
 * SPDX-License-Identifier: Apache-2.0
 * Adapted from microG CheckinClient, Copyright 2013-2017 microG Project Team.
 */
package org.microg.vending.billing

import android.content.Context
import android.os.Build
import org.microg.gms.checkin.CheckinRequest
import org.microg.gms.checkin.CheckinResponse
import org.microg.gms.checkin.DeviceConfig
import org.microg.gms.checkin.DeviceFeature
import org.microg.gms.common.DeviceConfiguration
import org.microg.gms.settings.SettingsContract
import android.util.AtomicFile
import java.util.Properties
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Own Google-issued registration; never reads microG's protected registration. */
object NPatchBillingRegistration {
    data class Registration(val androidId: Long, val consistencyToken: String)

    private const val PREFS = "npatch_billing_registration"
    private const val REFRESH_INTERVAL_MS = 12L * 60 * 60 * 1000
    private const val MAX_RESPONSE_BYTES = 1024 * 1024

    /** Blocking network operation; call from the existing billing IO coroutine. */
    @Synchronized
    fun ensure(context: Context): Registration {
        val storage = AtomicFile(File(context.noBackupFilesDir, PREFS))
        val prefs = Properties()
        try { storage.openRead().use(prefs::load) } catch (_: java.io.FileNotFoundException) { }
        fun number(key: String) = prefs.getProperty(key)?.toLongOrNull() ?: 0L
        val androidId = number("android_id")
        val securityToken = number("security_token")
        val consistencyToken = prefs.getProperty("consistency_token", "").orEmpty()
        val lastCheckin = number("last_checkin_ms")
        val lastReceived = number("received_at_ms")
        val now = System.currentTimeMillis()
        if (androidId != 0L && securityToken != 0L && now >= lastReceived &&
            now - lastReceived < REFRESH_INTERVAL_MS
        ) return Registration(androidId, consistencyToken)

        val request = CheckinRequest.Builder()
            .androidId(androidId)
            .securityToken(securityToken.takeIf { it != 0L })
            .digest(prefs.getProperty("digest", SettingsContract.CheckIn.INITIAL_DIGEST))
            .checkin(CheckinRequest.Checkin.Builder()
                .build(CheckinRequest.Checkin.Build.Builder()
                    .fingerprint(Build.FINGERPRINT)
                    .hardware(Build.HARDWARE)
                    .brand(Build.BRAND)
                    .bootloader(Build.BOOTLOADER)
                    .clientId("android-google")
                    .time(Build.TIME / 1000)
                    .device(Build.DEVICE)
                    .sdkVersion(Build.VERSION.SDK_INT)
                    .model(Build.MODEL)
                    .manufacturer(Build.MANUFACTURER)
                    .product(Build.PRODUCT)
                    .otaInstalled(false)
                    .build())
                .lastCheckinMs(lastCheckin)
                .userNumber(0)
                .event(listOf(CheckinRequest.Checkin.Event.Builder()
                    .tag(if (androidId == 0L) "event_log_start" else "system_update")
                    .timeMs(now)
                    .build()))
                .build())
            .deviceConfiguration(deviceConfig(context))
            .accountCookie(listOf(""))
            .locale(Locale.getDefault().toString())
            .timeZone(TimeZone.getDefault().id)
            .version(3)
            .fragment(if (securityToken == 0L) 0 else 1)
            .build()

        val response = post(request)
        val newId = response.androidId ?: androidId
        val newSecurityToken = response.securityToken ?: securityToken
        if (newId == 0L || newSecurityToken == 0L || response.statsOk == false) {
            throw IOException("Google checkin did not return a valid device registration")
        }
        val newConsistencyToken = response.deviceDataVersionInfo ?: consistencyToken
        prefs.setProperty("android_id", newId.toString())
        prefs.setProperty("security_token", newSecurityToken.toString())
        prefs.setProperty("last_checkin_ms", (response.timeMs ?: now).toString())
        prefs.setProperty("received_at_ms", now.toString())
        prefs.setProperty("digest", response.digest ?: prefs.getProperty("digest", SettingsContract.CheckIn.INITIAL_DIGEST))
        prefs.setProperty("consistency_token", newConsistencyToken)
        val output = storage.startWrite()
        try {
            prefs.store(output, null)
            storage.finishWrite(output)
        } catch (e: Exception) {
            storage.failWrite(output)
            throw IOException("Cannot persist billing device registration", e)
        }
        return Registration(newId, newConsistencyToken)
    }

    /** Shared by checkin and the authenticated FDFE uploadDeviceConfig request. */
    fun deviceConfig(context: Context): DeviceConfig {
        val config = DeviceConfiguration(context)
        return DeviceConfig.Builder()
            .availableFeature(config.availableFeatures)
            .deviceFeatures(config.availableFeatures.map { DeviceFeature.Builder().name(it).value_(0).build() })
            .densityDpi(config.densityDpi)
            .glEsVersion(config.glEsVersion)
            .glExtension(config.glExtensions)
            .hasFiveWayNavigation(config.hasFiveWayNavigation)
            .hasHardKeyboard(config.hasHardKeyboard)
            .heightPixels(config.heightPixels)
            .keyboardType(config.keyboardType)
            .locale(config.locales)
            .nativePlatform(config.nativePlatforms)
            .navigation(config.navigation)
            .screenLayout(config.screenLayout)
            .sharedLibrary(config.sharedLibraries)
            .touchScreen(config.touchScreen)
            .widthPixels(config.widthPixels)
            .build()
    }

    private fun post(request: CheckinRequest): CheckinResponse {
        val connection = URL("https://android.clients.google.com/checkin").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-protobuffer")
            connection.setRequestProperty("Content-Encoding", "gzip")
            connection.setRequestProperty("Accept-Encoding", "gzip")
            connection.setRequestProperty("User-Agent", "Android-Checkin/2.0")
            GZIPOutputStream(connection.outputStream).use { it.write(request.encode()) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Google checkin HTTP ${connection.responseCode}")
            }
            val raw = connection.inputStream
            val input = if (connection.contentEncoding.equals("gzip", ignoreCase = true)) GZIPInputStream(raw) else raw
            return input.use { CheckinResponse.ADAPTER.decode(readBounded(it)) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("Google checkin response too large")
            output.write(buffer, 0, count)
        }
    }
}
