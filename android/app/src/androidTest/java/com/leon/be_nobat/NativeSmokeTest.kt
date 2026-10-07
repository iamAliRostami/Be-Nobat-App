package com.leon.be_nobat

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import com.leon.be_nobat.data.SecureSession
import com.leon.be_nobat.ui.MainActivity
import com.leon.be_nobat.ui.Screen
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class NativeSmokeTest {
    @Test fun signInScreenUsesNativeInputFields() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.open(Screen("login"))
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val views = descendants(activity.window.decorView)
                assertEquals(2, views.filterIsInstance<TextInputEditText>().size)
                assertFalse(views.any { it is android.webkit.WebView })
            }
        }
    }
    @Test fun secureSessionRoundTripOnlyPersistsCiphertext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = SecureSession(context)
        val token = "instrumentation-test-session"
        try {
            store.save(JSONObject().put("accessToken", token).put("user", JSONObject().put("displayName", "Test")))
            assertEquals(token, store.read()!!.getString("accessToken"))
            assertFalse(context.getSharedPreferences("session", 0).getString("encrypted", "")!!.contains(token))
        } finally { store.clear() }
        assertNull(store.read())
    }
    @Test fun threeLocalesProvideLocalizedInterfaceLabels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val labels = listOf("fa", "en", "ar").map { language ->
            val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }
            context.createConfigurationContext(config).getString(R.string.confirm_booking)
        }
        assertEquals(3, labels.distinct().size)
        assertTrue(labels.all(String::isNotBlank))
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
