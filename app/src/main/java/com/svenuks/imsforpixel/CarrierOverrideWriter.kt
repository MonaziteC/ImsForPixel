package com.svenuks.imsforpixel

import android.os.PersistableBundle

/** Replace only runtime overrides; persistent operations are system-only on the target OS. */
internal class CarrierOverrideWriter(
    private val override: (Int, PersistableBundle?, Boolean) -> Unit
) {
    fun replace(subId: Int, values: PersistableBundle?) {
        // The loader merges non-null bundles: explicitly clear the runtime layer.
        // Never probe or clear persistent=true, including during Restore Defaults.
        override(subId, null, false)
        if (values != null) override(subId, values, false)
    }

    companion object {
        @Suppress("DEPRECATION")
        fun matches(expected: PersistableBundle, actual: PersistableBundle?): Boolean {
            if (actual == null) return false
            return expected.keySet().all { key ->
                if (!actual.containsKey(key)) false else {
                    val value = expected.get(key)
                    val read = actual.get(key)
                    if (value is IntArray) read is IntArray && value.contentEquals(read)
                    else value == read
                }
            }
        }
    }
}
