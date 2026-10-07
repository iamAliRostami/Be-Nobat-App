package com.leon.be_nobat

import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.DatePicker
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.leon.be_nobat.data.objects
import com.leon.be_nobat.data.text
import com.leon.be_nobat.ui.MainActivity
import com.leon.be_nobat.ui.Screen
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** Optional live tests take disposable fixture credentials from instrumentation arguments. */
@RunWith(AndroidJUnit4::class)
class LiveApiFlowTest {
    private val args get() = InstrumentationRegistry.getArguments()
    private val server get() = args.getString("serverUrl").orEmpty()
    private fun ready(activity: MainActivity, route: String) = activity.screen.name == route && !activity.vm.state.value.loading && activity.vm.state.value.error == null
    private fun waitFor(scenario: ActivityScenario<MainActivity>, description: String, predicate: (MainActivity) -> Boolean) {
        val deadline = System.currentTimeMillis() + 90_000
        var passed = false
        var error: String? = null
        while (System.currentTimeMillis() < deadline) {
            scenario.onActivity { passed = predicate(it); error = it.vm.state.value.error }
            if (passed) return
            if (error != null) fail("$description failed with localized API code $error")
            Thread.sleep(100)
        }
        fail("Timed out waiting for $description")
    }
    private fun configure(activity: MainActivity) {
        activity.vm.clearSession(); activity.vm.drafts.clear()
        activity.vm.preferences.edit().putString("server_url", server).commit()
        activity.open(Screen(), false)
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun field(activity: MainActivity, label: Int, value: String) {
        val input = views(activity.window.decorView).filterIsInstance<TextInputEditText>().single { it.hint?.toString() == activity.getString(label) }
        input.setText(value)
    }
    private fun button(activity: MainActivity, label: Int) = views(activity.window.decorView).filterIsInstance<MaterialButton>().single { it.text.toString() == activity.getString(label) && it.visibility == View.VISIBLE }
    private fun login(scenario: ActivityScenario<MainActivity>, email: String, password: String) {
        scenario.onActivity { it.open(Screen("login"), false) }
        waitFor(scenario, "native sign-in form") { ready(it, "login") && views(it.window.decorView).filterIsInstance<TextInputEditText>().size == 2 }
        scenario.onActivity { activity -> field(activity, R.string.email, email); field(activity, R.string.password, password); button(activity, R.string.login).performClick() }
        waitFor(scenario, "email sign in") { ready(it, "home") && it.vm.signedIn && it.vm.user.text("email").equals(email, true) }
    }
    @Test fun liveCustomerBooksAndCancelsThroughNativeControls() {
        val email = args.getString("customerEmail").orEmpty(); val password = args.getString("customerPassword").orEmpty()
        val businessId = args.getString("businessId").orEmpty()
        assumeTrue(server.isNotBlank() && email.isNotBlank() && password.isNotBlank() && businessId.isNotBlank())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity(::configure)
            waitFor(scenario, "business discovery") { ready(it, "home") && it.vm.state.value.data.objects().isNotEmpty() }
            login(scenario, email, password)
            scenario.onActivity { it.open(Screen("business", businessId)) }
            waitFor(scenario, "business detail") { ready(it, "business") && it.vm.state.value.data.text("id") == businessId }
            scenario.onActivity { button(it, R.string.book).performClick() }
            waitFor(scenario, "booking services") { ready(it, "booking") && it.vm.state.value.data.objects("bookServices").isNotEmpty() }
            val branchId = args.getString("branchId").orEmpty()
            if (branchId.isNotBlank()) {
                var branchName = ""
                scenario.onActivity { activity ->
                    branchName = activity.vm.state.value.data.objects("branches").single { it.text("id") == branchId }.text("name")
                    views(activity.window.decorView).filterIsInstance<MaterialButton>().first { it.text.startsWith(activity.getString(R.string.branch) + ":") }.performClick()
                }
                onView(withText(branchName)).inRoot(isDialog()).perform(click())
                waitFor(scenario, "selected native branch") { ready(it, "booking") && it.vm.drafts["book_branch"] == branchId }
            }
            scenario.onActivity { activity ->
                val services = activity.vm.state.value.data.objects("bookServices")
                val selected = services.first { args.getString("serviceId").isNullOrBlank() || it.text("id") == args.getString("serviceId") }
                views(activity.window.decorView).filterIsInstance<CheckBox>().first { it.text.startsWith(selected.text("name")) }.isChecked = true
            }
            waitFor(scenario, "provider eligibility") { ready(it, "booking") && it.vm.state.value.data.objects("providers").isNotEmpty() }
            scenario.onActivity { activity ->
                views(activity.window.decorView).filterIsInstance<MaterialButton>().first { it.text.startsWith(activity.getString(R.string.choose_date) + ":") }.performClick()
            }
            val day = LocalDate.now().plusDays(7)
            onView(isAssignableFrom(DatePicker::class.java)).perform(object : ViewAction {
                override fun getConstraints() = isAssignableFrom(DatePicker::class.java)
                override fun getDescription() = "Select a future native appointment date"
                override fun perform(controller: UiController, view: View) { (view as DatePicker).updateDate(day.year, day.monthValue - 1, day.dayOfMonth); controller.loopMainThreadUntilIdle() }
            })
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click())
            waitFor(scenario, "future native appointment day") { ready(it, "booking") && it.vm.drafts["book_date"] == day.toString() }
            scenario.onActivity { button(it, R.string.find_slots).performClick() }
            waitFor(scenario, "live appointment times") { ready(it, "booking") && it.vm.state.value.data.optJSONObject("availability")?.objects("slots")?.isNotEmpty() == true }
            val marker = "native-smoke-" + UUID.randomUUID().toString().take(8)
            scenario.onActivity { activity ->
                val provider = activity.vm.state.value.data.optJSONObject("availability")!!.objects("slots").first().text("resourceName")
                views(activity.window.decorView).filterIsInstance<MaterialButton>().first { it.text.contains(" – ") && it.text.contains(provider) }.performClick()
                field(activity, R.string.customer_note, marker)
                views(activity.window.decorView).filterIsInstance<CheckBox>().single { it.text.toString() == activity.getString(R.string.terms) }.isChecked = true
                button(activity, R.string.confirm_booking).performClick()
            }
            var appointmentId = ""
            waitFor(scenario, "booking confirmation and persisted appointment") { activity ->
                if (!ready(activity, "appointments")) false else activity.vm.state.value.data.objects().firstOrNull { it.text("customerNote") == marker }?.also { appointmentId = it.text("id"); assertTrue(it.text("trackingCode").isNotBlank()); assertTrue(it.optBoolean("canCancel")) } != null
            }
            onView(withText(R.string.close)).inRoot(isDialog()).perform(click())
            scenario.onActivity { activity ->
                val note = views(activity.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == marker }
                views(note.parent as View).filterIsInstance<MaterialButton>().single { it.text.toString() == activity.getString(R.string.cancel_appointment) }.performClick()
            }
            onView(withText(R.string.confirm)).inRoot(isDialog()).perform(click())
            waitFor(scenario, "customer cancellation") { activity -> ready(activity, "appointments") && activity.vm.state.value.data.objects().any { it.text("id") == appointmentId && it.text("status") == "Cancelled" && !it.optBoolean("canCancel") } }
        }
    }
    @Test fun liveRegistrationAndProfilePersistOnServer() {
        assumeTrue(server.isNotBlank())
        val suffix = UUID.randomUUID().toString().take(12)
        val email = "android-$suffix@demo.benobat.example"
        val password = "native$suffix" + "1"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { configure(it); it.open(Screen("register"), false) }
            waitFor(scenario, "native registration form") { ready(it, "register") && views(it.window.decorView).filterIsInstance<TextInputEditText>().size == 4 }
            scenario.onActivity { activity ->
                field(activity, R.string.display_name, "Android Instrumentation")
                field(activity, R.string.phone_number, "09123456789")
                field(activity, R.string.email, email); field(activity, R.string.password, password)
                button(activity, R.string.register).performClick()
            }
            waitFor(scenario, "new email account") { ready(it, "home") && it.vm.signedIn && it.vm.user.text("email") == email }
            scenario.onActivity { it.open(Screen("profile"), false) }
            waitFor(scenario, "native profile") { ready(it, "profile") && it.vm.state.value.data.text("email") == email }
            scenario.onActivity { activity -> field(activity, R.string.display_name, "Native Updated"); button(activity, R.string.save).performClick() }
            waitFor(scenario, "profile server update") { ready(it, "profile") && it.vm.state.value.data.text("displayName") == "Native Updated" }
            scenario.onActivity { button(it, R.string.logout).performClick() }
            waitFor(scenario, "revoked native session") { ready(it, "home") && !it.vm.signedIn }
        }
    }
    @Test fun livePlatformMenusAndNativeManagementFormsLoad() {
        val email = args.getString("adminEmail").orEmpty(); val password = args.getString("adminPassword").orEmpty()
        assumeTrue(server.isNotBlank() && email.isNotBlank() && password.isNotBlank())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity(::configure)
            waitFor(scenario, "management discovery") { ready(it, "home") }
            login(scenario, email, password)
            scenario.onActivity { it.open(Screen("manage")) }
            waitFor(scenario, "authorized management dashboard") { ready(it, "manage") && it.vm.state.value.data.optJSONObject("context")?.optBoolean("isPlatformAdmin") == true }
            listOf("admin/businesses", "admin/branches", "admin/services", "admin/resources", "admin/memberships", "admin/availability", "admin/reviews", "admin/customer-reviews", "platform/categories", "platform/catalog", "platform/users").forEach { route ->
                scenario.onActivity { it.open(Screen("manage_list", route)) }
                waitFor(scenario, "native list $route") { ready(it, "manage_list") && it.screen.id == route && it.vm.state.value.data.has("items") }
            }
            scenario.onActivity { it.open(Screen("manage_list", "admin/branches")) }
            waitFor(scenario, "branch editor list") { ready(it, "manage_list") && it.vm.state.value.data.objects().isNotEmpty() }
            scenario.onActivity { activity ->
                val row = activity.vm.state.value.data.objects().first()
                activity.vm.drafts.keys.filter { it.startsWith("edit_") }.toList().forEach(activity.vm.drafts::remove)
                activity.open(Screen("manage_form", "admin/branches", row.toString()))
            }
            waitFor(scenario, "native branch editor") { ready(it, "manage_form") && views(it.window.decorView).filterIsInstance<TextInputEditText>().size >= 4 }
        }
    }
}
