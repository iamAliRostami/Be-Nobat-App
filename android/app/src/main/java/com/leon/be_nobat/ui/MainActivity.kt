package com.leon.be_nobat.ui

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.BuildConfig
import com.leon.be_nobat.R
import com.leon.be_nobat.data.objects
import com.leon.be_nobat.data.ApiFailure
import com.leon.be_nobat.data.strings
import com.leon.be_nobat.data.text
import com.leon.be_nobat.domain.BookingRules
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Native Material UI. Views contain labels from Android resources; remote names are user data. */
class MainActivity : AppCompatActivity() {
    lateinit var vm: NativeViewModel; private set
    private lateinit var toolbar: Toolbar
    private lateinit var content: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var errors: LinearLayout
    private lateinit var errorLabel: TextView
    private lateinit var signInAgain: MaterialButton
    private lateinit var navigation: LinearLayout
    private var rendered = -1
    private val management by lazy { ManagementScreens(this) }
    private val photoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.action {
            val type = contentResolver.getType(uri)
            if (type !in listOf("image/png", "image/jpeg", "image/webp")) throw ApiFailure("invalid_avatar")
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer); if (read < 0) break
                        if (output.size() + read > 2 * 1024 * 1024) throw ApiFailure("invalid_avatar")
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                } ?: throw ApiFailure("invalid_avatar")
            }
            vm.updateUser(vm.api.request("me/avatar", "POST", JSONObject().put("contentType", type).put("dataBase64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))))
            load(screen)
        }
    }
    val screen get() = vm.state.value.screen

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("settings", 0)
        AppCompatDelegate.setDefaultNightMode(prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[NativeViewModel::class.java]
        val root = column().apply { setBackgroundColor(getColor(R.color.windowBackground)) }
        toolbar = Toolbar(this).apply { title = getString(R.string.app_name); setTitleTextColor(getColor(R.color.colorOnSurface)) }
        root.addView(toolbar)
        toolbar.menu.add(getString(R.string.settings)).setOnMenuItemClickListener { open(Screen("settings")); true }
        toolbar.setNavigationOnClickListener { goBack() }
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true; contentDescription = getString(R.string.loading) }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
        errors = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        errorLabel = text("", 14).apply { setTextColor(getColor(R.color.error_red)); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        errors.addView(errorLabel)
        errors.addView(button(R.string.retry, outlined = true) { load(screen) })
        signInAgain = button(R.string.login, outlined = true) { open(Screen("login")) }
        errors.addView(signInAgain)
        root.addView(errors)
        content = column().apply { setPadding(dp(20), dp(16), dp(20), dp(24)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(4), dp(4), dp(4), dp(4)) }
        listOf(R.string.discover to "home", R.string.appointments to "appointments", R.string.favorites to "favorites", R.string.profile to "profile").forEach { (label, route) ->
            navigation.addView(button(label, outlined = true) { open(Screen(route), false) }.apply { textSize = 11f; minWidth = 0; setPadding(dp(2), 0, dp(2), 0) }, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        root.addView(navigation)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() { goBack() } })
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect { state ->
                    progress.visibility = if (state.loading) View.VISIBLE else View.INVISIBLE
                    errors.visibility = if (state.error != null) View.VISIBLE else View.GONE
                    errorLabel.setText(errorResource(state.error))
                    signInAgain.visibility = if (state.error == "unauthorized") View.VISIBLE else View.GONE
                    if (rendered != state.revision) {
                        rendered = state.revision
                        render(state)
                    }
                }
            }
        }
        if (savedInstanceState == null) load(screen)
    }

    fun open(target: Screen, push: Boolean = true) {
        val protected = target.name in listOf("appointments", "favorites", "profile", "booking", "manage", "manage_list", "manage_form", "calendar", "platform")
        if (protected && !vm.signedIn) { vm.drafts["after_login"] = target.name; vm.drafts["after_login_id"] = target.id; vm.move(Screen("login"), push); return }
        if (!push) vm.stack.clear()
        vm.move(target, push)
        load(target)
    }
    private fun goBack() {
        val previous = vm.back()
        if (previous != null) { vm.move(previous, false); load(previous) }
        else if (screen.name != "home") open(Screen(), false)
        else finish()
    }

    fun load(target: Screen) {
        when (target.name) {
            "home" -> vm.load {
                val page = vm.drafts["page"] ?: "1"
                val data = vm.api.request("businesses", query = mapOf("q" to draft("q"), "city" to draft("city"), "category" to draft("category"), "page" to page, "pageSize" to "20"))
                data.put("categories", vm.api.request("categories").optJSONArray("items") ?: JSONArray())
            }
            "business" -> vm.load {
                vm.api.request("businesses/${target.id}").apply {
                    if (vm.signedIn) put("favorite", JSONObject().put("items", vm.api.all("favorites")).objects().any { it.text("id") == target.id })
                }
            }
            "favorites", "appointments" -> vm.load { vm.api.request(target.name, query = mapOf("pageSize" to "100", "status" to if (target.name == "appointments") draft("appointment_status") else "")) }
            "profile" -> if (vm.signedIn) vm.load { vm.api.request("me").also(vm::updateUser) }
            "booking" -> loadBooking(target)
            "manage", "manage_list", "manage_form", "calendar", "platform" -> management.load(target)
        }
    }

    private fun render(state: NativeState) {
        content.removeAllViews()
        toolbar.navigationIcon = if (state.screen.name != "home") getDrawable(R.drawable.ic_back) else null
        toolbar.navigationContentDescription = getString(R.string.back)
        val title = when (state.screen.name) {
            "home" -> R.string.app_name; "business" -> R.string.business_details; "booking" -> R.string.book
            "appointments" -> R.string.appointments; "favorites" -> R.string.favorites; "profile" -> R.string.profile
            "login" -> R.string.login; "register" -> R.string.register; "settings" -> R.string.settings
            "manage" -> R.string.management; "calendar" -> R.string.calendar; "platform" -> R.string.platform
            "manage_list", "manage_form" -> management.label(state.screen.id)
            else -> R.string.app_name
        }
        toolbar.setTitle(title)
        navigation.visibility = if (state.screen.name in listOf("booking", "manage_form", "login", "register")) View.GONE else View.VISIBLE
        when (state.screen.name) {
            "home" -> renderHome(state.data)
            "business" -> renderBusiness(state.data)
            "booking" -> renderBooking(state.data)
            "appointments" -> renderAppointments(state.data)
            "favorites" -> renderBusinesses(state.data, R.string.empty)
            "profile" -> renderProfile(state.data)
            "login", "register" -> renderAuth(state.screen.name == "register")
            "settings" -> renderSettings()
            "manage", "manage_list", "manage_form", "calendar", "platform" -> management.render(state)
        }
    }

    private fun renderHome(data: JSONObject) {
        add(text(getString(R.string.welcome), 24, true))
        field(content, R.string.search_hint, "q")
        field(content, R.string.city, "city")
        val categories = data.objects("categories").filter { it.text("kind").equals("Business", true) }
        choice(content, R.string.category, listOf("" to getString(R.string.all_categories)) + categories.map { it.text("name") to it.text("name") }, draft("category")) { vm.drafts["category"] = it }
        choice(content, R.string.sort, listOf("name" to getString(R.string.sort_name), "rating" to getString(R.string.sort_rating), "city" to getString(R.string.sort_city)), draft("sort", "name")) { vm.drafts["sort"] = it; renderBusinessesOnly(data) }
        add(button(R.string.apply_filters) { vm.drafts["page"] = "1"; load(screen) })
        add(button(R.string.clear_filters, outlined = true) { listOf("q", "city", "category", "page").forEach(vm.drafts::remove); load(screen) })
        val list = column().apply { tag = "business_list" }
        add(list)
        businessCards(list, data, R.string.no_businesses)
        if (data.optInt("total") > data.optInt("page", 1) * data.optInt("pageSize", 20)) add(button(R.string.load_more, outlined = true) {
            val next = data.optInt("page", 1) + 1
            vm.load {
                val response = vm.api.request("businesses", query = mapOf("q" to draft("q"), "city" to draft("city"), "category" to draft("category"), "page" to next.toString(), "pageSize" to "20"))
                val combined = JSONArray(); (data.objects() + response.objects()).forEach(combined::put)
                response.put("items", combined).put("categories", data.optJSONArray("categories"))
            }
        })
        if (vm.signedIn && management.canManage()) add(button(R.string.management, outlined = true) { open(Screen("manage")) })
        if (!vm.signedIn) add(button(R.string.login, outlined = true) { open(Screen("login")) })
    }
    private fun renderBusinessesOnly(data: JSONObject) {
        content.findViewWithTag<LinearLayout>("business_list")?.let { it.removeAllViews(); businessCards(it, data, R.string.no_businesses) }
    }
    private fun renderBusinesses(data: JSONObject, empty: Int) { businessCards(content, data, empty); paginated(data, screen.name) }
    private fun businessCards(parent: LinearLayout, data: JSONObject, empty: Int) {
        var businesses = data.objects()
        businesses = when (draft("sort", "name")) {
            "rating" -> businesses.sortedByDescending { it.optDouble("rating") }; "city" -> businesses.sortedBy { it.text("city") }; else -> businesses.sortedBy { it.text("name") }
        }
        if (businesses.isEmpty() && !vm.state.value.loading) parent.addView(text(getString(empty)))
        businesses.forEach { business ->
            card(parent) {
                addView(text(business.text("name"), 20, true))
                addView(text(listOf(business.text("category"), business.text("city")).filter(String::isNotBlank).joinToString(" · ")))
                addView(text(getString(R.string.rating_format, NumberFormat.getNumberInstance().format(business.optDouble("rating")), business.optInt("reviewCount")), 14))
                addView(text(business.text("description"), 14))
                addView(button(R.string.more) { open(Screen("business", business.text("id"))) })
            }
        }
    }
    private fun renderBusiness(data: JSONObject) {
        if (data.text("id").isBlank()) return
        add(text(data.text("name"), 26, true))
        add(text(listOf(data.text("city"), data.text("category")).joinToString(" · ")))
        add(text(data.text("description")))
        add(text(getString(R.string.rating_format, NumberFormat.getNumberInstance().format(data.optDouble("rating")), data.optInt("reviewCount"))))
        add(button(R.string.book) { open(Screen("booking", screen.id)) })
        add(button(if (data.optBoolean("favorite")) R.string.remove_favorite else R.string.add_favorite, outlined = true) {
            if (!vm.signedIn) open(Screen("login")) else vm.action {
                vm.api.request("favorites/${screen.id}", if (data.optBoolean("favorite")) "DELETE" else "PUT")
                load(screen)
            }
        })
        add(text(getString(R.string.branches), 20, true))
        data.objects("branches").forEach { branch -> card(content) {
            addView(text(branch.text("name"), 18, true)); addView(text(branch.text("address")))
            addView(text(getString(R.string.branch_timezone, branch.text("timeZoneId")), 13))
        } }
        add(text(getString(R.string.services), 20, true))
        data.objects("services").forEach { service -> card(content) {
            addView(text(service.text("name"), 18, true)); addView(text(service.text("description")))
            addView(text(getString(R.string.price_duration, money(service.opt("price"), service.text("currency")), service.optInt("durationMinutes"))))
        } }
        add(text(getString(R.string.reviews), 20, true))
        data.objects("reviews").forEach { review -> card(content) {
            addView(text(review.text("customerName"), 16, true)); addView(text(stars(review.optInt("rating"))))
            addView(text(review.text("comment")))
            if (review.text("managerReply").isNotBlank()) addView(text(getString(R.string.manager_reply) + ": " + review.text("managerReply")))
        } }
    }

    private fun loadBooking(target: Screen) {
        if (draft("book_business") != target.id) {
            listOf("book_branch", "book_services", "book_provider", "book_slot", "find_slots", "book_terms").forEach(vm.drafts::remove)
            vm.drafts["book_business"] = target.id
        }
        vm.load {
            val business = vm.api.request("businesses/${target.id}")
            val branches = business.objects("branches")
            var branchId = draft("book_branch")
            if (branches.none { it.text("id") == branchId }) { branchId = branches.firstOrNull()?.text("id").orEmpty(); vm.drafts["book_branch"] = branchId; vm.drafts.remove("book_services") }
            val localToday = runCatching { LocalDate.now(ZoneId.of(branches.firstOrNull { it.text("id") == branchId }?.text("timeZoneId") ?: "Asia/Tehran")) }.getOrDefault(LocalDate.now())
            vm.drafts.putIfAbsent("book_date", localToday.toString())
            val services = if (branchId.isNotBlank()) vm.api.request("businesses/${target.id}/branches/$branchId/services").objects() else emptyList()
            val selected = draft("book_services").split(',').filter { id -> services.any { it.text("id") == id } }
            vm.drafts["book_services"] = selected.joinToString(",")
            val providers = if (selected.isNotEmpty()) vm.api.request("businesses/${target.id}/branches/$branchId/providers", query = mapOf("serviceIds" to selected.joinToString(","))).objects() else emptyList()
            if (providers.none { it.text("id") == draft("book_provider") }) vm.drafts.remove("book_provider")
            business.put("bookServices", JSONArray().apply { services.forEach(::put) }).put("providers", JSONArray().apply { providers.forEach(::put) })
            if (selected.isNotEmpty() && draft("find_slots") == "true") {
                val slots = vm.api.request("slots", query = mapOf("businessId" to target.id, "branchId" to branchId, "serviceIds" to selected.joinToString(","), "resourceId" to draft("book_provider"), "date" to draft("book_date", LocalDate.now().toString())))
                business.put("availability", slots)
                if (slots.objects("slots").none { it.toString() == draft("book_slot") }) vm.drafts.remove("book_slot")
            }
            business
        }
    }
    private fun renderBooking(data: JSONObject) {
        if (data.text("id").isBlank()) return
        add(text(data.text("name"), 24, true))
        val branches = data.objects("branches")
        choice(content, R.string.branch, branches.map { it.text("id") to it.text("name") }, draft("book_branch")) {
            vm.drafts["book_branch"] = it; listOf("book_services", "book_provider", "book_slot", "find_slots").forEach(vm.drafts::remove); load(screen)
        }
        val branch = branches.firstOrNull { it.text("id") == draft("book_branch") }
        add(text(branch?.text("address").orEmpty()))
        add(text(getString(R.string.branch_timezone, branch?.text("timeZoneId").orEmpty()), 13))
        add(text(getString(R.string.select_services), 20, true))
        val services = data.objects("bookServices")
        services.forEach { service ->
            check(content, service.text("name") + " · " + getString(R.string.price_duration, money(service.opt("price"), service.text("currency")), service.optInt("durationMinutes")), draft("book_services").split(',').contains(service.text("id"))) { selected ->
                val ids = draft("book_services").split(',').filter(String::isNotBlank).toMutableSet()
                if (selected) ids.add(service.text("id")) else ids.remove(service.text("id"))
                vm.drafts["book_services"] = ids.joinToString(","); listOf("book_slot", "find_slots", "book_provider").forEach(vm.drafts::remove); load(screen)
            }
        }
        if (services.isEmpty()) add(text(getString(R.string.empty)))
        val selected = services.filter { it.text("id") in draft("book_services").split(',') }
        val total = BookingRules.total(selected.map { it.text("price").toBigDecimalOrNull() ?: BigDecimal.ZERO })
        add(text(getString(R.string.total_price, money(total, selected.firstOrNull()?.text("currency").orEmpty())), 18, true))
        add(text(getString(R.string.duration_format, BookingRules.duration(selected.map { it.optInt("durationMinutes") }))))
        choice(content, R.string.provider, listOf("" to getString(R.string.any_provider)) + data.objects("providers").map { it.text("id") to it.text("name") }, draft("book_provider")) {
            vm.drafts["book_provider"] = it; vm.drafts.remove("book_slot"); vm.drafts.remove("find_slots"); load(screen)
        }
        dateChoice(content, "book_date", R.string.choose_date) { vm.drafts.remove("book_slot"); vm.drafts.remove("find_slots"); load(screen) }
        add(button(R.string.find_slots) {
            if (selected.isEmpty() || !BookingRules.validDate(draft("book_date", LocalDate.now().toString()))) vm.showError("booking")
            else { vm.drafts["find_slots"] = "true"; load(screen) }
        })
        val availability = data.optJSONObject("availability")
        if (availability != null) {
            add(text(getString(R.string.available_times), 20, true))
            val slots = availability.objects("slots")
            if (slots.isEmpty()) add(text(getString(R.string.no_slots)))
            slots.forEach { slot ->
                val selectedSlot = draft("book_slot") == slot.toString()
                add(buttonText((if (selectedSlot) "✓ " else "") + time(slot.text("startsAt"), availability.text("timeZoneId")) + " – " + time(slot.text("endsAt"), availability.text("timeZoneId")) + " · " + slot.text("resourceName"), outlined = !selectedSlot) {
                    vm.drafts["book_slot"] = slot.toString()
                    renderBookingAgain(data)
                })
            }
        }
        field(content, R.string.phone_number, "book_phone", vm.user.text("phoneNumber"), InputType.TYPE_CLASS_PHONE)
        field(content, R.string.customer_note, "book_note", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        add(text(getString(R.string.terms_detail), 13))
        check(content, getString(R.string.terms), draft("book_terms") == "true") { vm.drafts["book_terms"] = it.toString() }
        add(button(R.string.confirm_booking) {
            val slot = runCatching { JSONObject(draft("book_slot")) }.getOrNull()
            if (slot == null || selected.isEmpty() || !BookingRules.validPhone(draft("book_phone", vm.user.text("phoneNumber"))) || draft("book_terms") != "true") { vm.showError("booking"); return@button }
            val body = JSONObject().put("businessId", screen.id).put("branchId", draft("book_branch")).put("serviceIds", JSONArray(selected.map { it.text("id") }))
                .put("resourceId", slot.text("resourceId").takeIf(String::isNotBlank) ?: JSONObject.NULL).put("startsAt", slot.text("startsAt"))
                .put("phoneNumber", draft("book_phone", vm.user.text("phoneNumber"))).put("customerNote", draft("book_note")).put("termsAccepted", true)
                .put("expectedPrice", availability?.opt("totalPrice") ?: total)
                .put("expectedDurationMinutes", availability?.optInt("totalDurationMinutes") ?: BookingRules.duration(selected.map { it.optInt("durationMinutes") }))
            vm.action {
                val booked = vm.api.request("appointments", "POST", body)
                listOf("book_services", "book_slot", "book_terms", "book_note", "find_slots").forEach(vm.drafts::remove)
                open(Screen("appointments"), false)
                MaterialAlertDialogBuilder(this).setTitle(R.string.booking_success).setMessage(getString(R.string.tracking_code, booked.text("trackingCode"))).setPositiveButton(R.string.close, null).show()
            }
        })
    }
    private fun renderBookingAgain(data: JSONObject) { content.removeAllViews(); renderBooking(data) }

    private fun renderAppointments(data: JSONObject) {
        choice(content, R.string.status, listOf("" to getString(R.string.all_statuses)) + statusOptions(), draft("appointment_status")) { vm.drafts["appointment_status"] = it; load(screen) }
        if (data.objects().isEmpty() && !vm.state.value.loading) add(text(getString(R.string.empty)))
        data.objects().forEach { appointment -> appointmentCard(content, appointment, false) }
        paginated(data, "appointments", mapOf("status" to draft("appointment_status")))
    }
    fun appointmentCard(parent: LinearLayout, appointment: JSONObject, admin: Boolean) {
        card(parent) {
            addView(text(appointment.text("businessName"), 20, true))
            addView(text(appointment.text("branchName") + " · " + appointment.text("resourceName")))
            addView(text(appointment.strings("serviceNames").joinToString("، ").ifBlank { appointment.text("serviceName") }))
            addView(text(dateTime(appointment.text("startsAt"), appointment.text("timeZoneId")), 18, true))
            addView(text(getString(R.string.branch_timezone, appointment.text("timeZoneId")), 13))
            addView(text(statusLabel(appointment.text("status")), 16, true))
            addView(text(getString(R.string.total_price, money(appointment.opt("finalPrice"), appointment.text("currency")))))
            if (appointment.text("trackingCode").isNotBlank()) addView(text(getString(R.string.tracking_code, appointment.text("trackingCode"))))
            if (appointment.text("customerNote").isNotBlank()) addView(text(appointment.text("customerNote")))
            if (admin) {
                addView(text(appointment.text("customerName") + " · " + appointment.text("customerPhoneNumber")))
                if (BookingRules.statusActions(appointment.text("status"), appointment.text("startsAt")).isNotEmpty()) addView(button(R.string.status, outlined = true) { management.changeAppointmentStatus(appointment) })
                if (management.canRate(appointment) && appointment.text("status").equals("Completed", true)) addView(button(R.string.customer_rating, outlined = true) { management.rateCustomer(appointment) })
            } else {
                if (appointment.optBoolean("canCancel")) addView(button(R.string.cancel_appointment, outlined = true) {
                    MaterialAlertDialogBuilder(this@MainActivity).setMessage(R.string.cancel_confirm).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm) { _, _ ->
                        vm.action { vm.api.request("appointments/${appointment.text("id")}/cancel", "POST"); load(screen) }
                    }.show()
                })
                if (appointment.optBoolean("canReview")) addView(button(R.string.leave_review, outlined = true) { reviewDialog { rating, comment ->
                    vm.action { vm.api.request("appointments/${appointment.text("id")}/review", "POST", JSONObject().put("rating", rating).put("comment", comment)); toast(R.string.review_pending); load(screen) }
                } })
            }
        }
    }
    fun reviewDialog(initial: JSONObject = JSONObject(), submit: (Int, String) -> Unit) {
        val form = column().apply { setPadding(dp(24), dp(8), dp(24), 0) }
        var rating = initial.optInt("rating", 5)
        choice(form, R.string.rating, (1..5).map { it.toString() to stars(it) }, rating.toString()) { rating = it.toInt() }
        val comment = field(form, R.string.comment, "review_comment", initial.text("comment"), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        MaterialAlertDialogBuilder(this).setTitle(R.string.leave_review).setView(form).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save) { _, _ -> submit(rating, comment.text.toString()) }.show()
    }

    private fun renderAuth(register: Boolean) {
        add(text(getString(if (register) R.string.register else R.string.login), 26, true))
        if (register) { field(content, R.string.display_name, "auth_name"); field(content, R.string.phone_number, "auth_phone", type = InputType.TYPE_CLASS_PHONE) }
        val email = field(content, R.string.email, "auth_email", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        val password = field(content, R.string.password, "auth_password", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, persist = false)
        add(button(if (register) R.string.register else R.string.login) {
            val valid = BookingRules.validEmail(email.text.toString()) && (if (register) BookingRules.validPassword(password.text.toString()) && draft("auth_name").isNotBlank() && BookingRules.validPhone(draft("auth_phone")) else password.text.toString().isNotEmpty())
            if (!valid) { vm.showError("invalid_input"); return@button }
            val request = JSONObject().put("email", email.text.toString().trim()).put("password", password.text.toString())
            if (register) request.put("displayName", draft("auth_name")).put("phoneNumber", draft("auth_phone"))
            vm.action {
                vm.saveSession(vm.api.request("auth/${if (register) "register" else "login"}", "POST", request))
                password.text?.clear()
                val next = Screen(draft("after_login", "home"), draft("after_login_id"))
                vm.drafts.remove("after_login"); vm.drafts.remove("after_login_id")
                open(next, false)
            }
        })
        add(button(if (register) R.string.login else R.string.register, outlined = true) { open(Screen(if (register) "login" else "register"), false) })
    }
    private fun renderProfile(data: JSONObject) {
        if (data.text("id").isBlank()) return
        val photo = ImageView(this).apply { contentDescription = getString(R.string.profile); scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(R.drawable.logo_be_nobat) }
        content.addView(photo, LinearLayout.LayoutParams(dp(88), dp(88)).apply { bottomMargin = dp(16); gravity = Gravity.CENTER_HORIZONTAL })
        if (data.text("avatarUrl").isNotBlank()) lifecycleScope.launch {
            val image = runCatching { vm.api.avatar(data.text("avatarUrl")) }.getOrNull()
            if (image != null && screen.name == "profile") photo.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(image, 0, image.size))
        }
        add(text(data.text("displayName").ifBlank { vm.user.text("displayName") }, 24, true))
        add(text(data.text("email").ifBlank { vm.user.text("email") }))
        add(button(R.string.change_avatar, outlined = true) { photoPicker.launch("image/*") })
        if (data.text("avatarUrl").isNotBlank()) add(button(R.string.remove_avatar, outlined = true) { vm.action { vm.updateUser(vm.api.request("me/avatar", "DELETE")); load(screen) } })
        field(content, R.string.display_name, "profile_name", data.text("displayName"))
        field(content, R.string.phone_number, "profile_phone", data.text("phoneNumber"), InputType.TYPE_CLASS_PHONE)
        add(button(R.string.save) { vm.action { vm.updateUser(vm.api.request("me", "PATCH", JSONObject().put("displayName", draft("profile_name")).put("phoneNumber", draft("profile_phone")))); toast(R.string.saved); load(screen) } })
        add(text(getString(R.string.account_security), 20, true))
        add(button(R.string.change_password, outlined = true) { securityDialog(false) })
        add(button(R.string.change_email, outlined = true) { securityDialog(true) })
        if (management.canManage()) add(button(R.string.management) { open(Screen("manage")) })
        if (management.isPlatform()) add(button(R.string.platform) { open(Screen("platform")) })
        add(button(R.string.logout, outlined = true) {
            vm.action {
                try { vm.api.request("auth/logout", "POST") } finally { vm.clearSession(); vm.drafts.clear(); open(Screen(), false) }
            }
        })
    }
    private fun securityDialog(email: Boolean) {
        val form = column().apply { setPadding(dp(24), dp(8), dp(24), 0) }
        val current = field(form, R.string.current_password, "current_secret", type = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, persist = false)
        val next = field(form, if (email) R.string.email else R.string.new_password, "new_secret", type = InputType.TYPE_CLASS_TEXT or if (email) InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS else InputType.TYPE_TEXT_VARIATION_PASSWORD, persist = false)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(if (email) R.string.change_email else R.string.change_password).setView(form).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            if (current.text.isNullOrBlank() || if (email) !BookingRules.validEmail(next.text.toString()) else !BookingRules.validPassword(next.text.toString())) { vm.showError("invalid_input"); return@setOnClickListener }
            vm.action {
                val result = vm.api.request("me/${if (email) "email" else "password"}", "POST", JSONObject().put("currentPassword", current.text.toString()).put(if (email) "email" else "newPassword", next.text.toString()))
                current.text?.clear(); next.text?.clear(); dialog.dismiss(); toast(R.string.saved)
                if (result.has("accessToken")) vm.saveSession(result)
                load(screen)
            }
        } }
        dialog.show()
    }
    private fun renderSettings() {
        choice(content, R.string.language, listOf("fa" to getString(R.string.language_persian), "en" to getString(R.string.language_english), "ar" to getString(R.string.language_arabic)), AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "fa" }) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it))
        }
        choice(content, R.string.theme, listOf("-1" to getString(R.string.theme_system), "1" to getString(R.string.theme_light), "2" to getString(R.string.theme_dark)), vm.preferences.getInt("theme", -1).toString()) {
            vm.preferences.edit().putInt("theme", it.toInt()).apply(); AppCompatDelegate.setDefaultNightMode(it.toInt())
        }
        add(text(getString(R.string.server_hint), 14))
        val url = field(content, R.string.server_url, "server_url", vm.serverUrl, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        add(button(R.string.save) {
            val validated = runCatching { BookingRules.serverUrl(url.text.toString(), BuildConfig.DEBUG) }.getOrNull()
            if (validated == null) vm.showError("url") else {
                vm.preferences.edit().putString("server_url", validated).apply(); vm.clearSession(); vm.drafts.clear(); toast(R.string.server_changed); open(Screen(), false)
            }
        })
    }

    fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun add(view: View) { content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }) }
    fun body() = content
    fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    fun text(value: String, size: Int = 16, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.colorOnSurface)); setPadding(0, dp(4), 0, dp(4))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }
    fun button(label: Int, outlined: Boolean = false, action: () -> Unit) = buttonText(getString(label), outlined, action)
    fun buttonText(label: String, outlined: Boolean = false, action: () -> Unit): MaterialButton = MaterialButton(this, null,
        if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
        text = label; isAllCaps = false; minHeight = dp(48); setOnClickListener { action() }
    }
    fun card(parent: LinearLayout, build: LinearLayout.() -> Unit) {
        val inner = column().apply { setPadding(dp(16), dp(12), dp(16), dp(12)); build() }
        val card = MaterialCardView(this).apply { radius = dp(18).toFloat(); strokeWidth = dp(1); setStrokeColor(getColor(R.color.colorOutline)); addView(inner) }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
    }
    fun draft(key: String, default: String = "") = vm.drafts[key] ?: default
    fun field(parent: LinearLayout, label: Int, key: String, default: String = "", type: Int = InputType.TYPE_CLASS_TEXT, persist: Boolean = true): TextInputEditText {
        val layout = TextInputLayout(this).apply { hint = getString(label); if ((type and InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE }
        val edit = TextInputEditText(layout.context).apply {
            inputType = type; setText(if (persist) draft(key, default) else default); minHeight = dp(52); isSaveEnabled = persist
            if (persist) {
                vm.drafts.putIfAbsent(key, default)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { vm.drafts[key] = s.toString() }
                    override fun afterTextChanged(s: Editable?) {}
                })
            }
        }
        layout.addView(edit)
        parent.addView(layout, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return edit
    }
    fun choice(parent: LinearLayout, label: Int, choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
        val value = choices.firstOrNull { it.first == selected }?.second ?: getString(R.string.select)
        lateinit var button: MaterialButton
        button = buttonText(getString(R.string.label_value, getString(label), value), true) {
            MaterialAlertDialogBuilder(this).setTitle(label).setSingleChoiceItems(choices.map { it.second }.toTypedArray(), choices.indexOfFirst { it.first == selected }) { dialog, index ->
                button.text = getString(R.string.label_value, getString(label), choices[index].second)
                onSelect(choices[index].first); dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }
    fun check(parent: LinearLayout, label: String, selected: Boolean, onSelect: (Boolean) -> Unit) {
        val check = CheckBox(this).apply {
            text = label; isChecked = selected; minHeight = dp(48); setTextColor(getColor(R.color.colorOnSurface))
            buttonTintList = ColorStateList.valueOf(getColor(R.color.colorPrimary)); setOnCheckedChangeListener { _, value -> onSelect(value) }
        }
        parent.addView(check)
    }
    fun dateChoice(parent: LinearLayout, key: String, label: Int = R.string.date, onSelect: () -> Unit) {
        val date = runCatching { LocalDate.parse(draft(key, LocalDate.now().toString())) }.getOrDefault(LocalDate.now())
        vm.drafts.putIfAbsent(key, date.toString())
        parent.addView(buttonText(getString(label) + ": " + date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), true) {
            val dialog = DatePickerDialog(this, { _, year, month, day -> vm.drafts[key] = LocalDate.of(year, month + 1, day).toString(); onSelect() }, date.year, date.monthValue - 1, date.dayOfMonth)
            if (key == "book_date") {
                val today = LocalDate.now()
                dialog.datePicker.minDate = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                dialog.datePicker.maxDate = today.plusDays(90).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            dialog.show()
        }, LinearLayout.LayoutParams(-1, -2))
    }
    fun timeChoice(parent: LinearLayout, key: String, label: Int) {
        val selected = runCatching { LocalTime.parse(draft(key, "09:00:00")) }.getOrDefault(LocalTime.of(9, 0))
        lateinit var button: MaterialButton
        button = buttonText(getString(R.string.label_value, getString(label), selected.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))), true) {
            android.app.TimePickerDialog(this, { _, hour, minute ->
                val value = LocalTime.of(hour, minute)
                vm.drafts[key] = value.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT))
                button.text = getString(R.string.label_value, getString(label), value.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)))
            }, selected.hour, selected.minute, true).show()
        }
        parent.addView(button, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    fun money(value: Any?, currency: String): String = NumberFormat.getNumberInstance().format(value?.toString()?.toBigDecimalOrNull() ?: BigDecimal.ZERO) + " " + currency
    fun time(value: String, zone: String): String = runCatching { Instant.parse(value).atZone(ZoneId.of(zone.ifBlank { "Asia/Tehran" })).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())) }.getOrDefault(value)
    fun dateTime(value: String, zone: String): String = runCatching { Instant.parse(value).atZone(ZoneId.of(zone.ifBlank { "Asia/Tehran" })).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.getDefault())) }.getOrDefault(value)
    fun stars(rating: Int) = "★".repeat(rating.coerceIn(0, 5)) + "☆".repeat((5 - rating).coerceIn(0, 5))
    fun paginated(data: JSONObject, route: String, query: Map<String, String> = emptyMap()) {
        if (data.optInt("total") <= data.objects().size) return
        add(button(R.string.load_more, outlined = true) {
            vm.load {
                val response = vm.api.request(route, query = query + mapOf("page" to (data.optInt("page", 1) + 1).toString(), "pageSize" to data.optInt("pageSize", 100).toString()))
                val combined = JSONArray(); (data.objects() + response.objects()).forEach(combined::put)
                data.put("items", combined).put("page", response.optInt("page")).put("total", response.optInt("total"))
            }
        })
    }
    fun statusOptions() = listOf("Pending", "Confirmed", "Completed", "Cancelled", "NoShow").map { it to statusLabel(it) }
    fun statusLabel(value: String) = getString(when (value.lowercase()) {
        "pending" -> R.string.pending; "confirmed" -> R.string.confirmed; "completed" -> R.string.completed; "cancelled" -> R.string.cancelled; "noshow" -> R.string.no_show
        "published" -> R.string.published; "rejected" -> R.string.rejected; else -> R.string.status
    })
    fun toast(label: Int) { Toast.makeText(this, label, Toast.LENGTH_SHORT).show() }
    private fun errorResource(value: String?) = when (value) {
        "unauthorized" -> R.string.error_unauthorized; "forbidden" -> R.string.error_forbidden; "invalid_input", "validation_failed", "weak_password", "duplicate_email", "identity_error" -> R.string.error_invalid_input
        "invalid_credentials", "credentials", "password_mismatch", "account_locked" -> R.string.error_credentials; "conflict", "slot_unavailable", "terms_changed", "service_unavailable" -> R.string.error_conflict
        "not_found" -> R.string.error_not_found; "rate_limited" -> R.string.error_rate_limited; "network" -> R.string.error_network
        "booking" -> R.string.error_booking; "url" -> R.string.error_url; "required" -> R.string.error_required
        "configuration" -> R.string.error_configuration; "invalid_avatar" -> R.string.error_avatar
        else -> R.string.error_server
    }
}
