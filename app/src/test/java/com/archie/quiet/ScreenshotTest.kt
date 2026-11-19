package com.archie.quiet

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 產生畫面預覽圖（app/build/shots），交付前用來檢查對齊。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w384dp-h1150dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent { QuietTheme { content() } }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun quietOn() = shot("1_quiet_on") {
        MainScreen(Samples.state(on = true, calls = Samples.calls), ScreenActions())
    }

    @Test
    fun quietOnPowerSave() = shot("2_quiet_on_power") {
        MainScreen(Samples.state(on = true, powerSave = true), ScreenActions())
    }

    @Test
    fun firstRun() = shot("3_first_run") {
        MainScreen(
            Samples.state(on = false, perms = Quiet.Perms(dnd = true, brightness = false, contacts = false, screening = false)),
            ScreenActions(),
        )
    }

    @Test
    fun afterQuiet() = shot("4_after_quiet") {
        MainScreen(Samples.state(on = false, calls = Samples.calls, powerLeftOn = true), ScreenActions())
    }
}
