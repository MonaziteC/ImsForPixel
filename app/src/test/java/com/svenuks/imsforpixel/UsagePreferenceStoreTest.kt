package com.svenuks.imsforpixel

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class UsagePreferenceStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun context(): Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getFilesDir(): File = temp.root
    }
    @Test fun originalsSurviveRecreationAndCannotBeOverwrittenByAnActiveMode() {
        val context = context(); val token = "a".repeat(64)
        UsagePreferenceStore(context).save(token, 0)
        val recreated = UsagePreferenceStore(context)
        recreated.save(token, 2)
        assertEquals(0, recreated.original(token))
        assertEquals(1, recreated.pendingCount())
        recreated.remove(token)
        assertEquals(0, UsagePreferenceStore(context).pendingCount())
    }
    @Test fun corruptReceiptsFailClosedInsteadOfBecomingEmptyOriginals() {
        val context = context(); val token = "b".repeat(64)
        File(GuardianStore.root(context), "usage-originals.json").writeText("broken")
        val store = UsagePreferenceStore(context)
        assertEquals(-1, store.pendingCount())
        assertThrows(Exception::class.java) { store.save(token, 0) }
        assertEquals("broken", File(GuardianStore.root(context), "usage-originals.json").readText())
    }
}
