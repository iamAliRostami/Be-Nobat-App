package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementBusinessInput
import com.leon.be_nobat.domain.model.WeeklyHours
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.CancellationNoticeEditor
import com.leon.be_nobat.helpers.WeeklyHoursEditor
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AdminBusinessEditActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_platform_business_edit
    override val applyBottomInsetsOnRoot: Boolean = false

    private val managementRepository: ManagementRepository by inject()
    private val tokenManager: TokenManager by inject()

    private val initialBusinessId: String?
        get() = intent.getStringExtra(EXTRA_BUSINESS_ID)

    private var editingBusinessId: String? = null
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
    private lateinit var cancellationEditor: CancellationNoticeEditor
    private lateinit var cardBusinessStatus: View
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
            return Intent(context, AdminBusinessEditActivity::class.java).apply {
                businessId?.let { putExtra(EXTRA_BUSINESS_ID, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireBusinessManagementPermission()
    }

    override fun setupViews() {
        setupAdminHeader(
            title = getString(R.string.platform_biz_edit_title),
            currentMenuItemId = R.id.drawer_business_profile,
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
        cardBusinessStatus.isVisible = false // Managed exclusively by platform admin

        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        btnSave.text = getString(R.string.platform_biz_save_btn)
        btnCancel.setOnClickListener { finish() }

        setupSpinners()
        btnSave.setOnClickListener { saveBusiness() }

        loadTargetBusiness()
    }

    override fun observeViewModel() {
        // Direct Coroutine/Repository invocation
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

    private fun loadTargetBusiness() {
        lifecycleScope.launch {
            val targetId = initialBusinessId ?: run {
                val mgmtContext = tokenManager.currentManagementContext()
                val firstBizId = mgmtContext?.memberships?.firstOrNull()?.businessId
                if (!firstBizId.isNullOrBlank()) {
                    firstBizId
                } else {
                    managementRepository.listBusinesses(pageSize = 1).getOrNull()?.items?.firstOrNull()?.id
                }
            }

            if (targetId.isNullOrBlank()) {
                showFeedback("کسب‌وکاری برای مدیریت یافت نشد.", isError = true)
                return@launch
            }

            editingBusinessId = targetId
            loadBusinessDetails(targetId)
        }
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

                // Load first branch for address and weekly hours
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

    private fun saveBusiness() {
        val bizId = editingBusinessId
        if (bizId.isNullOrBlank()) {
            showFeedback("شناسه کسب‌وکار مشخص نیست.", isError = true)
            return
        }

        if (cancellationEditor.validate() != null) {
            return
        }

        val name = etBusinessName.text?.toString()?.trim().orEmpty()
        val slug = etBusinessSlug.text?.toString()?.trim().orEmpty()
        val description = etBusinessDescription.text?.toString()?.trim().orEmpty()
        val catIdx = spinnerCategory.selectedItemPosition
        val category = if (catIdx in categoryOptions.indices) categoryOptions[catIdx] else "عمومی"
        val cityIdx = spinnerCity.selectedItemPosition
        val city = if (cityIdx in cityOptions.indices) cityOptions[cityIdx] else "تهران"
        val requiresApproval = switchRequiresApproval.isChecked
        val branchName = etFirstBranchName.text?.toString()?.trim().orEmpty()
        val branchAddress = etFirstBranchAddress.text?.toString()?.trim().orEmpty()

        if (name.isBlank()) {
            tilBusinessName.error = getString(R.string.platform_biz_name_required)
            etBusinessName.requestFocus()
            return
        } else {
            tilBusinessName.error = null
        }

        if (branchName.isBlank()) {
            tilFirstBranchName.error = getString(R.string.platform_biz_branch_required)
            etFirstBranchName.requestFocus()
            return
        } else {
            tilFirstBranchName.error = null
        }

        if (slug.isNotBlank() && !slug.matches(Regex("^[a-z0-9]+(-[a-z0-9]+)*$"))) {
            tilBusinessSlug.error = "شناسه اینترنتی فقط می‌تواند شامل حروف کوچک انگلیسی، اعداد و خط تیره باشد."
            etBusinessSlug.requestFocus()
            return
        } else {
            tilBusinessSlug.error = null
        }

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
            managementRepository.updateBusiness(bizId, input).onSuccess {
                Toast.makeText(this@AdminBusinessEditActivity, getString(R.string.platform_biz_saved_success), Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }.onFailure { err ->
                btnSave.isEnabled = true
                showFeedback(mapErrorMessage(err.message), isError = true)
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
