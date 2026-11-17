package com.svenuks.imsforpixel

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class GuardianStoreTest {
    private val context: Context get() = org.robolectric.RuntimeEnvironment.getApplication()

    @Test fun activationFinishesBeforeGuardianAndIdsCannotInjectCommands() {
        val command = GuardianStore.launcher("test-123")
        assertTrue(command.startsWith("nohup setsid sh -c "))
        assertTrue(command.contains(GuardianStore.COMPONENT))
        assertTrue(command.indexOf("BrokerInstrumentation;") < command.indexOf("-e guardian_id test-123"))
        for (id in listOf("", "../bad", "x;reboot", "$(id)", "x y")) {
            assertThrows(IllegalArgumentException::class.java) { GuardianStore.launcher(id) }
        }
    }

    @Test fun guardianIsEnabledByDefaultAndAnExplicitOptOutIsRespected() {
        val prefs = context.getSharedPreferences("volte_settings", Context.MODE_PRIVATE)
        prefs.edit().remove("guardian_enabled").commit()
        assertTrue(GuardianController.enabled(context))
        prefs.edit().putBoolean("guardian_enabled", false).commit()
        assertFalse(GuardianController.enabled(context))
    }

    @Test fun staleOrRebootedEvidenceCannotAuthorizeANewSession() {
        val state = JSONObject().put("guardian_id", "test-123").put("state", "running")
            .put("pid", 987654).put("boot", GuardianStore.boot(context))
        GuardianStore.write(context, state)
        assertFalse(GuardianStore.active(context))
        state.put("state", "starting").put("elapsed", android.os.SystemClock.elapsedRealtime())
        assertTrue(GuardianStore.active(context, state))
        state.put("boot", GuardianStore.boot(context) + 1)
        assertFalse(GuardianStore.active(context, state))
        state.put("boot", GuardianStore.boot(context)).put("state", "needs_authorization")
        assertFalse(GuardianStore.active(context, state))
    }

    @Test fun restoreAndEditAreExplicitValidatedCommands() {
        GuardianStore.stop(context, "test-commands", "restore")
        assertEquals("restore", GuardianStore.command(context, "test-commands"))
        GuardianStore.stop(context, "test-commands", "edit")
        assertEquals("edit", GuardianStore.command(context, "test-commands"))
        assertThrows(IllegalArgumentException::class.java) { GuardianStore.stop(context, "test-commands", "anything") }
        val cmd = GuardianStore.brokerLauncher("restore-123", true)
        assertTrue(cmd.contains("-e clear true -e ui_request_id restore-123"))
        assertTrue(cmd.contains("ui_action=status"))
        val inner = cmd.substringAfter("sh -c '").substringBeforeLast("' </dev/null")
        assertEquals(0, ProcessBuilder("sh", "-n", "-c", inner).start().waitFor())
    }

    @Test fun statusAndStopAreAvailableWithoutAdbAndHaveNoExportHistory() {
        val id = "test-local-stop"
        val stop = java.io.File(GuardianStore.root(context), "stop-$id")
        stop.delete()
        GuardianStore.write(context, JSONObject().put("state", "running").put("repairs", 3))
        assertEquals(3, GuardianStore.latest(context)!!.getInt("repairs"))
        assertFalse(GuardianStore.stopRequested(context, id))
        GuardianStore.stop(context, id)
        assertTrue(GuardianStore.stopRequested(context, id))
        assertThrows(IllegalArgumentException::class.java) { GuardianStore.stop(context, "../bad") }
        assertFalse(GuardianStore.root(context).listFiles().orEmpty().any { it.extension == "jsonl" })
    }
}
