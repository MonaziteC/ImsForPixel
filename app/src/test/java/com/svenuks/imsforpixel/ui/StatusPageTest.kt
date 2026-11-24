package com.svenuks.imsforpixel.ui

import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
class StatusPageTest {
    @get:Rule val compose = createComposeRule()
    private fun healthy() = JSONObject().put("state", "running").put("phase", "matching")
        .put("debug_off", true).put("elapsed", SystemClock.elapsedRealtime()).put("repairs", 4)

    @Test fun normalGuardianIsNotShownAsLoadingAndRestoreRemainsAvailable() {
        var restored = false
        compose.setContent {
            PixelImsTheme { StatusPage(Modifier, healthy(), true, false, true, {}, {}, { restored = true }) }
        }
        compose.onNodeWithText("Guardian active").assertExists()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onNodeWithText("Undo changes and restore defaults").performClick()
        assertTrue(restored)
    }

    @Test fun unknownRegistrationIsNotReportedAsUnregistered() {
        val state = healthy().put("sim_status", JSONArray().put(JSONObject().put("slot", 0)
            .put("present", true).put("name", "Test carrier").put("config_matches", true)
            .put("ims_registered", JSONObject.NULL)))
        compose.setContent { PixelImsTheme { StatusPage(Modifier, state, true, false, true, {}, {}, {}) } }
        compose.onNodeWithText("Not updated").assertExists()
        compose.onNodeWithText("Not registered").assertDoesNotExist()
    }

    @Test fun restoredStateDoesNotClaimThatGuardianIsStillActive() {
        val state = healthy().put("state", "restored").put("phase", "restored")
        compose.setContent { PixelImsTheme { StatusPage(Modifier, state, false, false, true, {}, {}, {}) } }
        compose.onNodeWithText("Carrier defaults restored").assertExists()
        compose.onNodeWithText("Guardian active").assertDoesNotExist()
    }

    @Test fun experimentReadbackIsNotMisreportedAsModemOrCallConfirmation() {
        val state = healthy().put("sim_status", JSONArray().put(JSONObject().put("slot", 0)
            .put("present", true).put("name", "Test carrier").put("config_matches", true)
            .put("ims_registered", true).put("usage_requested", true).put("usage_state", "preference_readback")))
        compose.setContent { PixelImsTheme { StatusPage(Modifier, state, true, false, true, {}, {}, {}) } }
        compose.onNodeWithText("Data-centric preference read back · modem unconfirmed").assertExists()
    }
}
