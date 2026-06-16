package com.dolphinforward.grayscale

import android.Manifest
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the grayscale toggle against the real display-daltonizer secure
 * settings on the device/emulator.
 *
 * The instrumentation runs in the app-under-test's UID, so adopting the shell
 * permission identity grants this process WRITE_SECURE_SETTINGS for the
 * duration of the test - the same capability the app gets via ADB/Shizuku/root
 * in production. We snapshot and restore the original settings so the test is
 * non-destructive.
 */
@RunWith(AndroidJUnit4::class)
class GrayscaleControllerTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private var originalEnabled = 0
    private var originalMode = -1

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)

        // Sanity: the adopted identity really does grant the permission.
        assertTrue(
            "Expected WRITE_SECURE_SETTINGS via adopted shell identity",
            PermissionManager.hasWriteSecureSettings(context)
        )

        val cr = context.contentResolver
        originalEnabled = Settings.Secure.getInt(cr, KEY_ENABLED, 0)
        originalMode = Settings.Secure.getInt(cr, KEY_MODE, -1)
    }

    @After
    fun tearDown() {
        val cr = context.contentResolver
        Settings.Secure.putInt(cr, KEY_MODE, if (originalMode >= 0) originalMode else 0)
        Settings.Secure.putInt(cr, KEY_ENABLED, originalEnabled)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .dropShellPermissionIdentity()
    }

    @Test
    fun enableGrayscale_turnsScreenGrayscale() {
        assertTrue(GrayscaleController.enableGrayscale(context))

        val cr = context.contentResolver
        assertEquals(1, Settings.Secure.getInt(cr, KEY_ENABLED, 0))
        assertEquals(0, Settings.Secure.getInt(cr, KEY_MODE, -1))
        assertTrue(GrayscaleController.isGrayscaleOn(context))
    }

    @Test
    fun disableGrayscale_restoresColor() {
        GrayscaleController.enableGrayscale(context)
        assertTrue(GrayscaleController.isGrayscaleOn(context))

        assertTrue(GrayscaleController.disableGrayscale(context))
        assertEquals(0, Settings.Secure.getInt(context.contentResolver, KEY_ENABLED, -1))
        assertFalse(GrayscaleController.isGrayscaleOn(context))
    }

    @Test
    fun canWriteSecureSettings_trueWhenPermissionHeld() {
        // The functional probe should agree with the held permission - this is
        // what makes the UI report "granted" right after a fresh grant.
        assertTrue(GrayscaleController.canWriteSecureSettings(context))
        assertTrue(PermissionManager.hasWriteSecureSettings(context))
    }

    @Test
    fun toggle_roundTrip_onOffOn() {
        GrayscaleController.enableGrayscale(context)
        assertTrue(GrayscaleController.isGrayscaleOn(context))

        GrayscaleController.disableGrayscale(context)
        assertFalse(GrayscaleController.isGrayscaleOn(context))

        GrayscaleController.enableGrayscale(context)
        assertTrue(GrayscaleController.isGrayscaleOn(context))
    }

    companion object {
        // Mirror the keys GrayscaleController writes (hidden Settings.Secure constants).
        private const val KEY_ENABLED = "accessibility_display_daltonizer_enabled"
        private const val KEY_MODE = "accessibility_display_daltonizer"
    }
}
