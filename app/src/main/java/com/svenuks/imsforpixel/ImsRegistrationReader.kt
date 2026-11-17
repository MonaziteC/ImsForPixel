package com.svenuks.imsforpixel

import android.os.IBinder

/** Read-only. Unknown/denied is null, not a false registration status. */
internal object ImsRegistrationReader {
    fun read(subId: Int): Boolean? = runCatching {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "phone") as IBinder
        val service = Class.forName("com.android.internal.telephony.ITelephony\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val api = Class.forName("com.android.internal.telephony.ITelephony")
        for (name in listOf("isImsRegisteredForSubscriber", "isImsRegistered")) {
            val value = runCatching { api.getMethod(name, Int::class.javaPrimitiveType).invoke(service, subId) as? Boolean }.getOrNull()
            if (value != null) return value
        }
        null
    }.getOrNull()
}
