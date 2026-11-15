package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementBusinessInput
import com.leon.be_nobat.domain.model.WeeklyHours
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.domain.usecase.PlatformCreateBusinessUseCase
import com.leon.be_nobat.domain.usecase.PlatformToggleBusinessActiveUseCase
import com.leon.be_nobat.domain.usecase.PlatformUpdateBusinessUseCase
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.WeeklyHoursEditor
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class PlatformBusinessEditActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_platform_business_edit
    override val applyBottomInsetsOnRoot: Boolean = false

    private val managementRepository: ManagementRepository by inject()
    private val createBusinessUseCase: PlatformCreateBusinessUseCase by inject()
    private val updateBusinessUseCase: PlatformUpdateBusinessUseCase by inject()
    private val toggleBusinessActiveUseCase: PlatformToggleBusinessActiveUseCase by inject()

    private val editingBusinessId: String?
        get() = intent.getStringExtra(EXTRA_BUSINESS_ID)

    private var existingBusiness: ManagementBusiness? = null
    private var firstBranch: ManagementBranch? = null

    private lateinit var tilBusinessName: TextInputLayout
    private lateinit var etBusinessName: TextInputEditText
    private lateinit var tilBusinessSlug: TextInputLayout
    private lateinit var etBusinessSlug: TextInputEditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerCity: Spinner
    private lateinit var tilBusinessDescription: TextInputLayout
    private lateinit var etBusinessDescription: TextInputEditText
    private lateinit var switchRequiresApproval: MaterialSwitch
    private lateinit var tilFirstBranchName: TextInputLayout
    private lateinit var etFirstBranchName: TextInputEditText
    private lateinit var tilFirstBranchAddress: TextInputLayout
    private lateinit var etFirstBranchAddress: TextInputEditText
    private lateinit var weeklyHoursEditor: WeeklyHoursEditor
    private lateinit var cancellationEditor: com.leon.be_nobat.helpers.CancellationNoticeEditor
    private lateinit var cardBusinessStatus: View
    private lateinit var switchBusinessActive: MaterialSwitch
    private lateinit var tvBusinessId: TextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private val categoryOptions = mutableListOf(
        "عمومی", "پزشکی و سلامت", "زیبایی و آرایش", "زیبایی و سلامت", "آموزش", "خدمات فنی", "ورزش", "مشاوره", "فروشگاهی", "رستوران و کافه", "سایر"
    )

    private val cityOptions = mutableListOf(
        "تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "قم", "اهواز", "رشت", "کرمانشاه", "یزد", "سایر"
    )

    companion object {
        const val EXTRA_BUSINESS_ID = "extra_business_id"

        fun createIntent(context: Context, businessId: String? = null): Intent {
            return Intent(context, PlatformBusinessEditActivity::class.java).apply {
                businessId?.let { putExtra(EXTRA_BUSINESS_ID, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requirePlatformAdminPermission()
    }

    override fun setupViews() {
        val isEditing = !editingBusinessId.isNullOrBlank()
        setupAdminHeader(
            title = if (isEditing) getString(R.string.platform_biz_edit_title) else getString(R.string.platform_biz_new_title),
            onBack = { finish() }
        )

        tilBusinessName = findViewById(R.id.tilBusinessName)
        etBusinessName = findViewById(R.id.etBusinessName)
        tilBusinessSlug = findViewById(R.id.tilBusinessSlug)
        etBusinessSlug = findViewById(R.id.etBusinessSlug)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerCity = findViewById(R.id.spinnerCity)
        tilBusinessDescription = findViewById(R.id.tilBusinessDescription)
        etBusinessDescription = findViewById(R.id.etBusinessDescription)
        switchRequiresApproval = findViewById(R.id.switchRequiresApproval)
        tilFirstBranchName = findViewById(R.id.tilFirstBranchName)
        etFirstBranchName = findViewById(R.id.etFirstBranchName)
        tilFirstBranchAddress = findViewById(R.id.tilFirstBranchAddress)
        etFirstBranchAddress = findViewById(R.id.etFirstBranchAddress)
        weeklyHoursEditor = findViewById(R.id.weeklyHoursEditor)
        cancellationEditor = findViewById(R.id.cancellationEditor)
        cancellationEditor.setValue(120, allowInheritance = false)
        cardBusinessStatus = findViewById(R.id.cardBusinessStatus)
        switchBusinessActive = findViewById(R.id.switchBusinessActive)
        tvBusinessId = findViewById(R.id.tvBusinessId)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        btnSave.text = if (isEditing) getString(R.string.platform_biz_save_btn) else getString(R.string.platform_biz_create_btn)
        btnCancel.setOnClickListener { finish() }

        setupSpinners()

        if (isEditing) {
            loadBusinessDetails(editingBusinessId!!)
        }
    }

    override fun observeViewModel() {
        // Direct Coroutine/UseCase invocation
    }

    private fun setupSpinners() {
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoryOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerCategory.adapter = catAdapter

        val cityAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cityOptions).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerCity.adapter = cityAdapter
    }

    private fun loadBusinessDetails(businessId: String) {
        lifecycleScope.launch {
            managementRepository.getBusiness(businessId).onSuccess { business ->
                existingBusiness = business
                cancellationEditor.setValue(business.cancellationNoticeMinutes, allowInheritance = false)
                etBusinessName.setText(business.name)
                etBusinessSlug.setText(business.slug)
                etBusinessDescription.setText(business.description)
                switchRequiresApproval.isChecked = business.requiresApproval

                if (business.category.isNotBlank() && !categoryOptions.contains(business.category)) {
                    categoryOptions.add(0, business.category)
                    setupSpinners()
                    spinnerCategory.setSelection(0)
                } else {
                    val catIdx = categoryOptions.indexOf(business.category)
                    if (catIdx >= 0) spinnerCategory.setSelection(catIdx)
                }

                if (business.city.isNotBlank() && !cityOptions.contains(business.city)) {
                    cityOptions.add(0, business.city)
                    setupSpinners()
                    spinnerCity.setSelection(0)
                } else {
                    val cityIdx = cityOptions.indexOf(business.city)
                    if (cityIdx >= 0) spinnerCity.setSelection(cityIdx)
                }

                cardBusinessStatus.isVisible = true
                tvBusinessId.text = getString(R.string.platform_biz_system_id, business.id)
                switchBusinessActive.isChecked = business.active

                switchBusinessActive.setOnCheckedChangeListener { _, isChecked ->
                    lifecycleScope.launch {
                        toggleBusinessActiveUseCase(business.id, isChecked).onSuccess { updated ->
                            existingBusiness = updated
                            Toast.makeText(this@PlatformBusinessEditActivity, if (isChecked) getString(R.string.platform_biz_activated) else getString(R.string.platform_biz_deactivated), Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            switchBusinessActive.isChecked = !isChecked
                            showFeedback(err.message ?: getString(R.string.platform_biz_status_error), isError = true)
                        }
                    }
                }

                // Load first branch for branch info and weekly hours
                val branch = if (!business.firstBranchId.isNullOrBlank()) {
                    managementRepository.getBranch(business.firstBranchId).getOrNull()
                } else {
                    managementRepository.listBranches(businessId = business.id, pageSize = 1).getOrNull()?.items?.firstOrNull()
                }

                if (branch != null) {
                    firstBranch = branch
                    etFirstBranchName.setText(branch.name)
                    etFirstBranchAddress.setText(branch.address)
                    val hours = branch.weeklyHours ?: WeeklyHours.legacy(branch.openHour, branch.closeHour)
                    weeklyHoursEditor.setWeeklyHours(hours)
                } else {
                    etFirstBranchName.setText("شعبه مرکزی")
                    weeklyHoursEditor.setWeeklyHours(WeeklyHours.defaults())
                }
            }.onFailure { err ->
                showFeedback(err.message ?: getString(R.string.platform_biz_load_error), isError = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        btnSave.setOnClickListener { saveBusiness() }
    }

    private fun saveBusiness() {
        if (cancellationEditor.validate() != null) return
        val name = etBusinessName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            tilBusinessName.error = getString(R.string.platform_biz_name_required)
            etBusinessName.requestFocus()
            return
        }
        tilBusinessName.error = null

        val branchName = etFirstBranchName.text?.toString()?.trim().orEmpty()
        if (branchName.isBlank()) {
            tilFirstBranchName.error = getString(R.string.platform_biz_branch_required)
            etFirstBranchName.requestFocus()
            return
        }
        tilFirstBranchName.error = null

        val slug = etBusinessSlug.text?.toString()?.trim().orEmpty()
        val catIdx = spinnerCategory.selectedItemPosition
        val category = if (catIdx in categoryOptions.indices) categoryOptions[catIdx] else "عمومی"
        val cityIdx = spinnerCity.selectedItemPosition
        val city = if (cityIdx in cityOptions.indices) cityOptions[cityIdx] else "تهران"
        val description = etBusinessDescription.text?.toString()?.trim().orEmpty()
        val requiresApproval = switchRequiresApproval.isChecked
        val branchAddress = etFirstBranchAddress.text?.toString()?.trim().orEmpty()

        val hours = weeklyHoursEditor.value
        val hoursValidation = WeeklyHours.validate(hours)
        if (hoursValidation != null) {
            showFeedback(hoursValidation, isError = true)
            return
        }

        val input = ManagementBusinessInput(
            name = name,
            slug = slug,
            category = category,
            city = city,
            description = description,
            requiresApproval = requiresApproval,
            firstBranchName = branchName,
            firstBranchAddress = branchAddress,
            firstBranchWeeklyHours = hours,
            cancellationNoticeMinutes = cancellationEditor.value,
        )

        btnSave.isEnabled = false
        lifecycleScope.launch {
            if (editingBusinessId.isNullOrBlank()) {
                createBusinessUseCase(input).onSuccess {
                    Toast.makeText(this@PlatformBusinessEditActivity, getString(R.string.platform_biz_created_success), Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { err ->
                    btnSave.isEnabled = true
                    showFeedback(mapErrorMessage(err.message), isError = true)
                }
            } else {
                updateBusinessUseCase(editingBusinessId!!, input).onSuccess {
                    Toast.makeText(this@PlatformBusinessEditActivity, getString(R.string.platform_biz_saved_success), Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { err ->
                    btnSave.isEnabled = true
                    showFeedback(mapErrorMessage(err.message), isError = true)
                }
            }
        }
    }

    private fun showFeedback(message: String, isError: Boolean = false) {
        val anchor = findViewById<View>(R.id.layoutBottomActions) ?: btnSave
        Snackbar.make(anchor, message, if (isError) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT).apply {
            setAnchorView(anchor)
            show()
        }
    }

    private fun mapErrorMessage(raw: String?): String {
        return when {
            raw.isNullOrBlank() -> getString(R.string.platform_biz_save_error)
            raw.contains("The record changed", ignoreCase = true) || raw.contains("conflict", ignoreCase = true) ->
                "اطلاعات همزمان تغییر کرده است؛ لطفاً صفحه را مجدداً باز کرده و تلاش کنید."
            raw.contains("slug is already in use", ignoreCase = true) ->
                "این شناسه اینترنتی قبلاً برای کسب‌وکار دیگری ثبت شده است."
            else -> raw
        }
    }
}
