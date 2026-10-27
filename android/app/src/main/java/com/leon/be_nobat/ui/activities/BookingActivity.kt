package com.leon.be_nobat.ui.activities

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.databinding.ItemServiceSelectableBinding
import com.leon.be_nobat.domain.model.Service
import com.leon.be_nobat.domain.model.Slot
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.JalaliDateHelper
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.helpers.toPersianDigits
import com.leon.be_nobat.ui.adapters.simpleDiff
import com.leon.be_nobat.ui.view_models.BookingUiState
import com.leon.be_nobat.ui.view_models.BookingViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class BookingDayItem(
    val isoDate: String,
    val weekday: String,
    val dayNumber: String,
    val monthName: String,
    val isToday: Boolean,
)

class BookingActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_booking

    private val viewModel: BookingViewModel by viewModel()
    private val tokenManager: TokenManager by inject()
    private var hasAutoShownTermsDialog = false

    private var servicesAdapter: ServicesAdapter? = null
    private var dateStripAdapter: DateStripAdapter? = null
    private var slotsAdapter: SlotsAdapter? = null
    private var windowOffsetDays: Int = 0
    private var hasNavigatedToSuccess: Boolean = false

    override fun setupViews() {
        setupAdminHeader(getString(R.string.book_now)) {
            handleBackPress()
        }

        onBackPressedDispatcher.addCallback(this) {
            handleBackPress()
        }

        val bizId = intent.getStringExtra("businessId") ?: ""
        val branchIdExtra = intent.getStringExtra("branchId")
        val serviceIdExtra = intent.getStringExtra("serviceId")
        val dateExtra = intent.getStringExtra("selectedDate")
        val slotStartsAtExtra = intent.getStringExtra("slotStartsAt")
        val slotResourceIdExtra = intent.getStringExtra("slotResourceId")
        viewModel.setBusinessId(bizId, branchIdExtra, serviceIdExtra, dateExtra, slotStartsAtExtra, slotResourceIdExtra)

        // Services List setup (Step 1)
        servicesAdapter = ServicesAdapter(
            selectedIds = { viewModel.state.value.selectedServiceIds },
            onToggle = { viewModel.toggleService(it) },
        )
        findViewById<RecyclerView>(R.id.servicesList).apply {
            layoutManager = LinearLayoutManager(this@BookingActivity)
            adapter = servicesAdapter
        }

        // Step 1 Continue button
        findViewById<View>(R.id.btnStep1Next).setOnClickListener {
            if (viewModel.state.value.selectedServices.isEmpty()) {
                showToast(getString(R.string.booking_select_service_first))
            } else {
                viewModel.setStep(2)
                findViewById<NestedScrollView>(R.id.scrollBooking).smoothScrollTo(0, 0)
            }
        }

        // Horizontal Date Strip setup (Step 2)
        dateStripAdapter = DateStripAdapter(
            getSelectedDate = { viewModel.state.value.date },
            onSelect = { iso ->
                viewModel.setDate(iso)
                dateStripAdapter?.notifyDataSetChanged()
            }
        )
        findViewById<RecyclerView>(R.id.rvDateStrip).apply {
            layoutManager = LinearLayoutManager(this@BookingActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = dateStripAdapter
        }
        refreshDateStrip(windowOffsetDays)

        findViewById<View>(R.id.btnPrevWindow).setOnClickListener {
            if (windowOffsetDays > 0) {
                windowOffsetDays = maxOf(0, windowOffsetDays - 7)
                refreshDateStrip(windowOffsetDays)
            }
        }
        findViewById<View>(R.id.btnNextWindow).setOnClickListener {
            windowOffsetDays += 7
            refreshDateStrip(windowOffsetDays)
        }
        findViewById<View>(R.id.btnPickCalendar).setOnClickListener {
            showStandardCalendar()
        }

        // Time Slots Grid setup (Step 2 - 3 columns)
        slotsAdapter = SlotsAdapter(
            getSelectedSlot = { viewModel.state.value.selectedSlot },
            onSelect = { slot ->
                viewModel.selectSlot(slot)
                slotsAdapter?.notifyDataSetChanged()
            }
        )
        findViewById<RecyclerView>(R.id.rvSlots).apply {
            layoutManager = GridLayoutManager(this@BookingActivity, 3)
            adapter = slotsAdapter
        }

        // Step 2 Navigation buttons
        findViewById<View>(R.id.btnStep2Back).setOnClickListener {
            viewModel.setStep(1)
            findViewById<NestedScrollView>(R.id.scrollBooking).smoothScrollTo(0, 0)
        }
        findViewById<View>(R.id.btnStep2Next).setOnClickListener {
            if (viewModel.state.value.selectedSlot == null) {
                showToast(getString(R.string.booking_select_slot))
            } else {
                viewModel.setStep(3)
                findViewById<NestedScrollView>(R.id.scrollBooking).smoothScrollTo(0, 0)
            }
        }

        // Step 3 Navigation buttons
        findViewById<View>(R.id.btnStep3Back).setOnClickListener {
            viewModel.setStep(2)
            findViewById<NestedScrollView>(R.id.scrollBooking).smoothScrollTo(0, 0)
        }

        // Pre-fill user phone if logged in
        lifecycleScope.launch {
            val phone = tokenManager.currentSession()?.user?.phoneNumber
            if (!phone.isNullOrBlank()) {
                findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPhone).setText(phone)
            }
        }

        // Terms agreement checkbox & rules text listeners
        val cbTerms = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbTerms)
        cbTerms.setOnCheckedChangeListener { _, isChecked ->
            if (viewModel.state.value.termsAccepted != isChecked) {
                viewModel.setTerms(isChecked)
            }
        }

        findViewById<View>(R.id.tvTermsText)?.let { tv ->
            val termsHtml = getString(R.string.accept_terms_with_link)
            (tv as? TextView)?.text = android.text.Html.fromHtml(termsHtml, android.text.Html.FROM_HTML_MODE_LEGACY)
            tv.setOnClickListener {
                showBookingRulesDialog()
            }
        }

        // Confirm button
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm).setOnClickListener {
            lifecycleScope.launch {
                if (tokenManager.currentSession() == null) {
                    MaterialAlertDialogBuilder(this@BookingActivity)
                        .setTitle(R.string.login_required_title)
                        .setMessage(R.string.login_required_desc)
                        .setPositiveButton(R.string.login) { _, _ ->
                            val intent = Intent(this@BookingActivity, LoginActivity::class.java).apply {
                                putExtra(LoginActivity.EXTRA_RETURN_TARGET, BookingActivity::class.java.name)
                                putExtra(LoginActivity.EXTRA_BUSINESS_ID, viewModel.state.value.businessId)
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                    return@launch
                }

                viewModel.setPhone(findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPhone).text.toString())
                viewModel.setNote(findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNote).text.toString())
                viewModel.setTerms(findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbTerms).isChecked)
                viewModel.book()
            }
        }
    }

    private fun handleBackPress() {
        val state = viewModel.state.value
        if (state.bookedTrackingCode != null) {
            finish()
            return
        }
        when (state.currentStep) {
            3 -> {
                viewModel.setStep(2)
                findViewById<NestedScrollView>(R.id.scrollBooking)?.smoothScrollTo(0, 0)
            }
            2 -> {
                viewModel.setStep(1)
                findViewById<NestedScrollView>(R.id.scrollBooking)?.smoothScrollTo(0, 0)
            }
            else -> finish()
        }
    }

    private fun refreshDateStrip(offset: Int) {
        val days = buildDayItems(offset)
        dateStripAdapter?.submitList(days)
        findViewById<View>(R.id.btnPrevWindow)?.isEnabled = offset > 0
    }

    private fun buildDayItems(startOffset: Int): List<BookingDayItem> {
        val items = mutableListOf<BookingDayItem>()
        val cal = Calendar.getInstance()
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        for (i in startOffset until (startOffset + 14)) {
            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
            val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
            val parts = iso.split("-").mapNotNull { it.toIntOrNull() }
            if (parts.size == 3) {
                val j = JalaliDateHelper.gregorianToJalali(parts[0], parts[1], parts[2])
                val weekdayIdx = when (c.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.SATURDAY -> 0
                    Calendar.SUNDAY -> 1
                    Calendar.MONDAY -> 2
                    Calendar.TUESDAY -> 3
                    Calendar.WEDNESDAY -> 4
                    Calendar.THURSDAY -> 5
                    Calendar.FRIDAY -> 6
                    else -> 0
                }
                items.add(
                    BookingDayItem(
                        isoDate = iso,
                        weekday = JalaliDateHelper.getWeekdayName(weekdayIdx),
                        dayNumber = PersianUtils.toPersianDigits(j.day),
                        monthName = j.monthName,
                        isToday = (iso == todayIso)
                    )
                )
            }
        }
        return items
    }

    private fun showStandardCalendar() {
        val cal = Calendar.getInstance()
        val currentIso = viewModel.state.value.date
        if (currentIso.isNotBlank()) {
            val p = currentIso.split("-").mapNotNull { it.toIntOrNull() }
            if (p.size == 3) {
                cal.set(p[0], p[1] - 1, p[2])
            }
        }
        DatePickerDialog(
            this,
            R.style.ThemeOverlay_BeNobat_DatePickerDialog,
            { _, y, m, d ->
                val chosen = Calendar.getInstance().apply { set(y, m, d) }
                val today = Calendar.getInstance()
                val diffMs = chosen.timeInMillis - today.timeInMillis
                val daysDiff = maxOf(0, (diffMs / (1000 * 60 * 60 * 24)).toInt())
                windowOffsetDays = (daysDiff / 7) * 7
                val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.time)
                viewModel.setDate(iso)
                refreshDateStrip(windowOffsetDays)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    private fun render(state: BookingUiState) {
        val isSuccess = state.bookedTrackingCode != null

        // Display error toast if present and clear it
        if (!state.error.isNullOrBlank()) {
            val errorText = com.leon.be_nobat.helpers.ErrorFormatter.format(this, state.error)
            showToast(errorText, isLong = true)
            viewModel.clearError()
        }

        // Step visibility
        findViewById<View>(R.id.layoutStepProgress).isVisible = !isSuccess
        findViewById<View>(R.id.layoutSuccess).isVisible = isSuccess
        findViewById<View>(R.id.layoutStep1).isVisible = !isSuccess && state.currentStep == 1
        findViewById<View>(R.id.layoutStep2).isVisible = !isSuccess && state.currentStep == 2
        findViewById<View>(R.id.layoutStep3).isVisible = !isSuccess && state.currentStep == 3

        if (!isSuccess) {
            renderStepProgress(state.currentStep)
            if (state.currentStep == 3 && !hasAutoShownTermsDialog) {
                hasAutoShownTermsDialog = true
                showBookingRulesDialog()
            }
        }

        // Branch dropdown
        val branchTv = findViewById<MaterialAutoCompleteTextView>(R.id.dropdownBranch)
        if (state.branches.isNotEmpty()) {
            val branchNames = state.branches.map { it.name }
            branchTv.setSimpleItems(branchNames.toTypedArray())
            branchTv.setOnItemClickListener { _, _, pos, _ ->
                val chosen = state.branches.getOrNull(pos)
                chosen?.let { viewModel.selectBranch(it.id) }
            }
            val currentBranch = state.branches.firstOrNull { it.id == state.branchId }
            if (currentBranch != null && branchTv.text.toString() != currentBranch.name) {
                branchTv.setText(currentBranch.name, false)
            }
        }

        // Services (Step 1)
        servicesAdapter?.submitList(state.services)

        // Step 1 Summary Card
        val hasServices = state.selectedServices.isNotEmpty()
        val btnStep1Next = findViewById<View>(R.id.btnStep1Next)
        btnStep1Next.isEnabled = hasServices
        btnStep1Next.alpha = if (hasServices) 1.0f else 0.5f

        val tvStep1Duration = findViewById<TextView>(R.id.tvStep1Duration)
        val tvStep1Price = findViewById<TextView>(R.id.tvStep1Price)
        tvStep1Duration.text = getString(
            R.string.selected_services_badge,
            PersianUtils.toPersianDigits(state.selectedServices.size)
        ) + " • " + PersianUtils.formatDuration(state.totalDuration)
        tvStep1Price.text = PersianUtils.formatPrice(state.totalPrice.toLong())

        // Provider dropdown (Step 2)
        val providerTv = findViewById<MaterialAutoCompleteTextView>(R.id.dropdownProvider)
        val tilProvider = findViewById<TextInputLayout>(R.id.tilProvider)
        if (state.selectedServices.isEmpty()) {
            tilProvider.helperText = getString(R.string.booking_select_service_first)
            providerTv.setSimpleItems(emptyArray())
            providerTv.setText("", false)
            providerTv.isEnabled = false
        } else {
            tilProvider.helperText = null
            providerTv.isEnabled = true
            val providerOptions = listOf(getString(R.string.any_provider)) + state.providers.map { it.name }
            providerTv.setSimpleItems(providerOptions.toTypedArray())
            providerTv.setOnItemClickListener { _, _, pos, _ ->
                val id = if (pos == 0) null else state.providers.getOrNull(pos - 1)?.id
                viewModel.selectProvider(id)
            }
            val currentProvider = state.providers.firstOrNull { it.id == state.selectedProviderId }
            val expectedProviderText = currentProvider?.name ?: getString(R.string.any_provider)
            if (providerTv.text.toString() != expectedProviderText) {
                providerTv.setText(expectedProviderText, false)
            }
        }

        // Date selection display text & adapter refresh (Step 2)
        findViewById<TextView>(R.id.tvSelectedDateDisplay).text =
            JalaliDateHelper.formatIsoToJalaliDisplay(state.date)
        dateStripAdapter?.notifyDataSetChanged()

        // Time slots grid (Step 2)
        val rawSlots = state.slots?.slots ?: emptyList()
        val slots = rawSlots.distinctBy { it.startsAt }
        slotsAdapter?.submitList(slots)
        findViewById<View>(R.id.progressSlots).isVisible = state.loadingSlots
        findViewById<View>(R.id.tvNoSlots).isVisible = (state.slots != null && slots.isEmpty() && !state.loadingSlots)

        val badge = findViewById<TextView>(R.id.tvSlotsCountBadge)
        if (!state.loadingSlots && slots.isNotEmpty()) {
            badge.isVisible = true
            badge.text = getString(R.string.booking_available_slots_count, PersianUtils.toPersianDigits(slots.size))
        } else {
            badge.isVisible = false
        }

        // Step 2 Continue Button
        val hasSlot = state.selectedSlot != null
        val btnStep2Next = findViewById<View>(R.id.btnStep2Next)
        btnStep2Next.isEnabled = hasSlot
        btnStep2Next.alpha = if (hasSlot) 1.0f else 0.5f

        // Step 3 Confirmation Card
        val currentBranch = state.branches.firstOrNull { it.id == state.branchId }
        val branchName = currentBranch?.name.orEmpty()
        val branchAddress = currentBranch?.address.orEmpty()
        findViewById<TextView>(R.id.tvConfirmBusiness).text = intent.getStringExtra("businessName") ?: branchName
        findViewById<TextView>(R.id.tvConfirmBranch).text = if (branchAddress.isNotBlank()) "$branchName — $branchAddress" else branchName
        val commaSep = if (PersianUtils.isEnglishLocale()) ", " else "، "
        findViewById<TextView>(R.id.tvConfirmServices).text = state.selectedServices.joinToString(commaSep) { it.name }
        val currentProvider = state.providers.firstOrNull { it.id == state.selectedProviderId }
        findViewById<TextView>(R.id.tvConfirmSpecialist).text = currentProvider?.name ?: getString(R.string.any_provider)
        val selectedDateDisplay = JalaliDateHelper.formatIsoToJalaliDisplay(state.date)
        val selectedTimeDisplay = state.selectedSlot?.let { JalaliDateHelper.formatIsoTime(it.startsAt) } ?: "—"
        val atSeparator = if (PersianUtils.isEnglishLocale()) "at" else "ساعت"
        findViewById<TextView>(R.id.tvConfirmTime).text = "$selectedDateDisplay • $atSeparator $selectedTimeDisplay"
        findViewById<TextView>(R.id.tvConfirmPrice).text = PersianUtils.formatPrice(state.totalPrice.toLong())

        // Terms checkbox sync
        val cbTerms = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbTerms)
        if (cbTerms.isChecked != state.termsAccepted) {
            cbTerms.isChecked = state.termsAccepted
        }

        // Confirm button state (Step 3)
        val btnConfirm = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)
        btnConfirm.text = if (state.booking) getString(R.string.booking_in_progress) else getString(R.string.confirm_booking)
        val canConfirm = !state.booking &&
            state.termsAccepted &&
            state.selectedSlot != null &&
            state.selectedServices.isNotEmpty() &&
            state.date.isNotBlank()
        btnConfirm.isEnabled = canConfirm
        btnConfirm.alpha = if (canConfirm) 1.0f else 0.5f

        findViewById<View>(R.id.loadingProgress).isVisible = state.booking

        // Success Activity navigation
        if (!hasNavigatedToSuccess && state.bookedTrackingCode != null) {
            hasNavigatedToSuccess = true
            val successIntent = Intent(this, BookingSuccessActivity::class.java).apply {
                putExtra(BookingSuccessActivity.EXTRA_TRACKING_CODE, state.bookedTrackingCode)
                val servicesText = state.bookedAppointment?.serviceNames?.takeIf { it.isNotEmpty() }?.joinToString(commaSep)
                    ?: state.selectedServices.joinToString(commaSep) { it.name }
                putExtra(BookingSuccessActivity.EXTRA_SERVICES, servicesText)
                val providerText = state.bookedAppointment?.resourceName?.takeIf { !it.isNullOrBlank() }
                    ?: state.providers.firstOrNull { it.id == state.selectedProviderId }?.name
                    ?: getString(R.string.any_provider)
                putExtra(BookingSuccessActivity.EXTRA_PROVIDER, providerText)
                val timeIso = state.bookedAppointment?.startsAt?.takeIf { it.isNotBlank() }
                    ?: state.selectedSlot?.startsAt
                    ?: ""
                val selectedDateDisplay = JalaliDateHelper.formatIsoToJalaliDisplay(state.date.ifBlank { timeIso.take(10) })
                val selectedTimeDisplay = if (timeIso.isNotBlank()) JalaliDateHelper.formatIsoTime(timeIso) else ""
                val formattedTime = if (selectedTimeDisplay.isNotBlank()) {
                    "$selectedDateDisplay • ساعت $selectedTimeDisplay"
                } else {
                    selectedDateDisplay
                }
                putExtra(BookingSuccessActivity.EXTRA_TIME, formattedTime)
                val businessName = state.bookedAppointment?.businessName?.takeIf { it.isNotBlank() }
                    ?: intent.getStringExtra("businessName")
                    ?: state.branches.firstOrNull { it.id == state.branchId }?.name
                    ?: ""
                putExtra(BookingSuccessActivity.EXTRA_BUSINESS_NAME, businessName)
                putExtra(BookingSuccessActivity.EXTRA_PRICE, state.bookedAppointment?.finalPrice ?: state.totalPrice)
                putExtra(BookingSuccessActivity.EXTRA_CURRENCY, state.bookedAppointment?.currency ?: "IRR")
            }
            startActivity(successIntent)
            finish()
            return
        }
    }

    private fun renderStepProgress(step: Int) {
        val colorPrimary = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, 0)
        val colorOnPrimary = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary, 0)
        val colorOnSurfaceVariant = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
        val colorOutline = androidx.core.content.ContextCompat.getColor(this, R.color.colorOutline)

        val circle1 = findViewById<TextView>(R.id.tvStepCircle1)
        val label1 = findViewById<TextView>(R.id.tvStepLabel1)
        val div1 = findViewById<View>(R.id.stepDivider1)

        val circle2 = findViewById<TextView>(R.id.tvStepCircle2)
        val label2 = findViewById<TextView>(R.id.tvStepLabel2)
        val div2 = findViewById<View>(R.id.stepDivider2)

        val circle3 = findViewById<TextView>(R.id.tvStepCircle3)
        val label3 = findViewById<TextView>(R.id.tvStepLabel3)

        // Step 1
        if (step > 1) {
            circle1.setBackgroundResource(R.drawable.bg_step_circle_done)
            circle1.text = "✓"
            circle1.setTextColor(colorPrimary)
            label1.setTextColor(colorPrimary)
            div1.setBackgroundColor(colorPrimary)
        } else {
            circle1.setBackgroundResource(R.drawable.bg_step_circle_active)
            circle1.text = PersianUtils.toPersianDigits(1)
            circle1.setTextColor(colorOnPrimary)
            label1.setTextColor(colorPrimary)
            div1.setBackgroundColor(colorOutline)
        }

        // Step 2
        if (step > 2) {
            circle2.setBackgroundResource(R.drawable.bg_step_circle_done)
            circle2.text = "✓"
            circle2.setTextColor(colorPrimary)
            label2.setTextColor(colorPrimary)
            div2.setBackgroundColor(colorPrimary)
        } else if (step == 2) {
            circle2.setBackgroundResource(R.drawable.bg_step_circle_active)
            circle2.text = PersianUtils.toPersianDigits(2)
            circle2.setTextColor(colorOnPrimary)
            label2.setTextColor(colorPrimary)
            div2.setBackgroundColor(colorOutline)
        } else {
            circle2.setBackgroundResource(R.drawable.bg_step_circle_inactive)
            circle2.text = PersianUtils.toPersianDigits(2)
            circle2.setTextColor(colorOnSurfaceVariant)
            label2.setTextColor(colorOnSurfaceVariant)
            div2.setBackgroundColor(colorOutline)
        }

        // Step 3
        if (step >= 3) {
            circle3.setBackgroundResource(R.drawable.bg_step_circle_active)
            circle3.text = PersianUtils.toPersianDigits(3)
            circle3.setTextColor(colorOnPrimary)
            label3.setTextColor(colorPrimary)
        } else {
            circle3.setBackgroundResource(R.drawable.bg_step_circle_inactive)
            circle3.text = PersianUtils.toPersianDigits(3)
            circle3.setTextColor(colorOnSurfaceVariant)
            label3.setTextColor(colorOnSurfaceVariant)
        }
    }

    private class ServicesAdapter(
        private val selectedIds: () -> List<String>,
        private val onToggle: (Service) -> Unit,
    ) : androidx.recyclerview.widget.ListAdapter<Service, ServicesAdapter.VH>(
        simpleDiff({ a, b -> a.id == b.id }, { a, b -> a == b })
    ) {
        inner class VH(val binding: ItemServiceSelectableBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Service) {
                binding.apply {
                    tvName.text = item.name
                    tvDescription.text = item.description
                    tvDurationPrice.text = "${PersianUtils.formatDuration(item.durationMinutes)} — ${PersianUtils.formatPrice(item.price, item.currency)}"
                    cbSelected.setOnCheckedChangeListener(null)
                    cbSelected.isChecked = selectedIds().contains(item.id)
                    cbSelected.setOnCheckedChangeListener { _, _ -> onToggle(item) }
                    root.setOnClickListener {
                        cbSelected.isChecked = !cbSelected.isChecked
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val inflater = LayoutInflater.from(parent.context)
            return VH(ItemServiceSelectableBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class DateStripAdapter(
        private val getSelectedDate: () -> String,
        private val onSelect: (String) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<BookingDayItem, DateStripAdapter.VH>(
        simpleDiff({ a, b -> a.isoDate == b.isoDate }, { a, b -> a == b })
    ) {
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.cardDate)
            val tvWeekday: TextView = itemView.findViewById(R.id.tvWeekday)
            val tvDayNumber: TextView = itemView.findViewById(R.id.tvDayNumber)
            val tvMonth: TextView = itemView.findViewById(R.id.tvMonth)

            fun bind(item: BookingDayItem) {
                val isSelected = item.isoDate == getSelectedDate()
                tvWeekday.text = item.weekday
                tvDayNumber.text = item.dayNumber
                tvMonth.text = item.monthName

                val ctx = itemView.context
                val colorPrimary = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary)
                val colorOnPrimary = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnPrimary)
                val colorSurface = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface)
                val colorOnSurface = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface)
                val colorMuted = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurfaceVariant)

                if (isSelected) {
                    card.setCardBackgroundColor(colorPrimary)
                    card.strokeWidth = 0
                    tvWeekday.setTextColor(colorOnPrimary)
                    tvDayNumber.setTextColor(colorOnPrimary)
                    tvMonth.setTextColor(colorOnPrimary)
                } else {
                    card.setCardBackgroundColor(colorSurface)
                    card.strokeWidth = (1 * ctx.resources.displayMetrics.density).toInt()
                    tvWeekday.setTextColor(colorMuted)
                    tvDayNumber.setTextColor(colorOnSurface)
                    tvMonth.setTextColor(colorMuted)
                }

                card.setOnClickListener { onSelect(item.isoDate) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_booking_date, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }
    }

    private class SlotsAdapter(
        private val getSelectedSlot: () -> Slot?,
        private val onSelect: (Slot) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<Slot, SlotsAdapter.VH>(
        simpleDiff({ a, b -> a.startsAt == b.startsAt }, { a, b -> a == b })
    ) {
        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val card: MaterialCardView = itemView.findViewById(R.id.cardSlot)
            val tvTime: TextView = itemView.findViewById(R.id.tvTime)

            fun bind(slot: Slot) {
                val isSelected = slot.startsAt == getSelectedSlot()?.startsAt
                tvTime.text = JalaliDateHelper.formatIsoTime(slot.startsAt)

                val ctx = itemView.context
                val colorPrimary = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary)
                val colorOnPrimary = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnPrimary)
                val colorSurface = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurface)
                val colorOnSurface = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface)

                if (isSelected) {
                    card.setCardBackgroundColor(colorPrimary)
                    card.strokeWidth = 0
                    tvTime.setTextColor(colorOnPrimary)
                } else {
                    card.setCardBackgroundColor(colorSurface)
                    card.strokeWidth = (1 * ctx.resources.displayMetrics.density).toInt()
                    tvTime.setTextColor(colorOnSurface)
                }

                card.setOnClickListener { onSelect(slot) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_booking_slot, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(getItem(position))
        }
    }
    private fun showBookingRulesDialog() {
        val message = buildString {
            append(getString(R.string.booking_rule_1))
            append("\n\n")
            append(getString(R.string.booking_rule_2))
            append("\n\n")
            append(getString(R.string.booking_rule_3))
            append("\n\n")
            append(getString(R.string.booking_rule_4))
            append("\n\n")
            append(getString(R.string.booking_rule_5))
        }

        val cbTerms = findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.cbTerms)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.booking_rules_title)
            .setIcon(R.drawable.ic_help)
            .setMessage(message)
            .setPositiveButton(R.string.accept_rules) { _, _ ->
                cbTerms?.isChecked = true
                viewModel.setTerms(true)
            }
            .setNegativeButton(R.string.action_close, null)
            .show()
    }
}
