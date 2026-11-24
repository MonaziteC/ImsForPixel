package com.svenuks.imsforpixel.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SimStatusPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun statusCardShowsReportedMeasurementsAndACurrentValueSelector() = render("light")
    @Test @Config(qualifiers = "zh-rCN-w393dp-h852dp-night-xhdpi")
    fun darkStatusCardUsesTheSameCompactLayout() = render("dark")

    private fun render(theme: String) {
        val radio = JSONObject().put("sampled_elapsed", SystemClock.elapsedRealtime())
            .put("data_rat", 20).put("bands", "n41").put("signal_rat", "NR")
            .put("rsrp", -95).put("sinr", 7).put("bandwidths_khz", "60000,30000,100000")
            .put("measurement_age_ms", 5000)
        val sim = JSONObject().put("slot", 0).put("present", true).put("name", "中国移动")
            .put("config_matches", true).put("ims_registered", true).put("radio", radio)
            .put("usage_current", 0).put("usage_state", "off")
        compose.setContent { PixelImsTheme { Surface { Box(Modifier.fillMaxSize().padding(16.dp)) {
            SimStatusCard(sim, false, false) { _, _ -> }
        } } } }
        compose.onNodeWithText("5G SA").assertExists()
        compose.onNodeWithText("n41").assertExists()
        compose.onNodeWithText("-95").assertExists()
        compose.onNodeWithText("190").assertExists()
        compose.onNodeWithTag("usage-choice-0").assertIsDisplayed()
        compose.onNodeWithText("选择").assertDoesNotExist()
        val file = File("build/previews/sim-status-$theme.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
