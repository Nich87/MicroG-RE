// Modified by NPatch contributors for embedded billing integration (2026).
/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.vending.billing

import android.content.Context
import android.os.Binder

/** Manager-owned caller authorization. Billing stays disabled until a verifier is installed. */
object NPatchBillingBridge {
    fun interface CallerVerifier {
        fun isAllowed(context: Context, callingUid: Int): Boolean
    }

    fun interface OriginalVersionResolver {
        fun getVersionCode(context: Context, packageName: String): Int?
    }

    fun interface OriginalCertificateResolver {
        fun getCertificate(context: Context, packageName: String): ByteArray?
    }

    @Volatile
    var originalCertificateResolver: OriginalCertificateResolver? = null

    @Volatile
    var originalVersionResolver: OriginalVersionResolver? = null

    @Volatile
    var callerVerifier: CallerVerifier? = null

    @JvmStatic
    fun isBillingEnabled(context: Context): Boolean =
        callerVerifier?.isAllowed(context, Binder.getCallingUid()) == true
}
