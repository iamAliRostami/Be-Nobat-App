package com.leon.be_nobat.ui.activities
import android.content.Intent

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.BusinessRequestInput
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.StatusBadgeHelper
import com.leon.be_nobat.ui.view_models.ProfileUiState
import com.leon.be_nobat.ui.view_models.ProfileViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class BusinessUpgradeActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_business_upgrade
    override val applyBottomInsetsOnRoot: Boolean = true

    private val viewModel: ProfileViewModel by viewModel()

    private val fallbackCategories = listOf(
        "زیبایی و آرایش", "پیرایش آقایان", "پزشکی", "دندانپزشکی", "فیزیوتراپی و توان‌بخشی",
        "ماساژ و تندرستی", "مشاوره و روان‌شناسی", "ورزش و تناسب اندام", "دامپزشکی",
        "آموزش", "خدمات خودرو", "خدمات منزل", "عمومی"
    )
    private var currentCategories: List<String> = fallbackCategories
    private var initializedFromState = false

    override fun setupViews() {
        setToolbarTitle(true) // hide default toolbar

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
        findViewById<View>(R.id.btnThemeToggle)?.setOnClickListener { setupThemeToggle() }

        val spCategory = findViewById<Spinner>(R.id.spReqCategory)
        val initialCategories = viewModel.state.value.categories.ifEmpty { fallbackCategories }
        currentCategories = initialCategories
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currentCategories).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spCategory.adapter = catAdapter

        findViewById<MaterialButton>(R.id.btnSubmitReq).setOnClickListener {
            submitRequest()
        }

        if (viewModel.state.value.categories.isEmpty()) {
            viewModel.loadCategories()
        }
    }

    private fun submitRequest() {
        val etName = findViewById<TextInputEditText>(R.id.etReqBusinessName)
        val tilName = findViewById<TextInputLayout>(R.id.tilReqBusinessName)
        val spCategory = findViewById<Spinner>(R.id.spReqCategory)
        val etCity = findViewById<TextInputEditText>(R.id.etReqCity)
        val tilCity = findViewById<TextInputLayout>(R.id.tilReqCity)
        val etPhone = findViewById<TextInputEditText>(R.id.etReqContactPhone)
        val tilPhone = findViewById<TextInputLayout>(R.id.tilReqContactPhone)
        val etBranch = findViewById<TextInputEditText>(R.id.etReqFirstBranchName)
        val tilBranch = findViewById<TextInputLayout>(R.id.tilReqFirstBranchName)
        val etAddress = findViewById<TextInputEditText>(R.id.etReqFirstBranchAddress)
        val tilAddress = findViewById<TextInputLayout>(R.id.tilReqFirstBranchAddress)
        val etDesc = findViewById<TextInputEditText>(R.id.etReqDescription)

        tilName.error = null
        tilCity.error = null
        tilPhone.error = null
        tilBranch.error = null
        tilAddress.error = null

        val name = etName.text?.toString()?.trim().orEmpty()
        val cat = currentCategories.getOrNull(spCategory.selectedItemPosition)
            ?: spCategory.selectedItem?.toString()
            ?: currentCategories.firstOrNull()
            ?: "عمومی"
        val city = etCity.text?.toString()?.trim().orEmpty()
        val phone = etPhone.text?.toString()?.trim().orEmpty()
        val branch = etBranch.text?.toString()?.trim().orEmpty()
        val address = etAddress.text?.toString()?.trim().orEmpty()
        val desc = etDesc.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (name.isBlank()) {
            tilName.error = getString(R.string.platform_biz_name_required)
            hasError = true
        }
        if (city.isBlank()) {
            tilCity.error = getString(R.string.error_city_required)
            hasError = true
        }
        if (phone.isBlank()) {
            tilPhone.error = getString(R.string.error_phone_required)
            hasError = true
        }
        if (branch.isBlank()) {
            tilBranch.error = getString(R.string.platform_biz_branch_required)
            hasError = true
        }
        if (address.isBlank()) {
            tilAddress.error = getString(R.string.error_address_required)
            hasError = true
        }
        if (hasError) return

        val input = BusinessRequestInput(
            businessName = name,
            category = cat,
            city = city,
            contactPhone = phone,
            firstBranchName = branch,
            firstBranchAddress = address,
            description = desc,
        )
        viewModel.submitBusinessRequest(input)
    }

    override fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ProfileUiState) {
        val btnSubmit = findViewById<MaterialButton>(R.id.btnSubmitReq)
        btnSubmit.isEnabled = !state.submittingBusinessRequest
        btnSubmit.text = if (state.submittingBusinessRequest) getString(R.string.saving) else getString(R.string.business_request_submit_btn)

        val spCategory = findViewById<Spinner>(R.id.spReqCategory)
        val newCategories = state.categories.ifEmpty { fallbackCategories }
        val req = state.businessRequest

        if (newCategories != currentCategories) {
            val selectedItem = spCategory?.selectedItem?.toString()
            currentCategories = newCategories
            val newAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currentCategories).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spCategory?.adapter = newAdapter

            val targetCategory = if (initializedFromState) selectedItem else req?.category ?: selectedItem
            if (!targetCategory.isNullOrBlank()) {
                val idx = currentCategories.indexOf(targetCategory).takeIf { it >= 0 }
                    ?: run {
                        currentCategories = listOf(targetCategory) + currentCategories
                        val updatedAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currentCategories).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }
                        spCategory?.adapter = updatedAdapter
                        0
                    }
                spCategory?.setSelection(idx)
            }
        }

        val cardIntro = findViewById<View>(R.id.cardIntroInfo)
        val cardStatus = findViewById<View>(R.id.cardExistingStatus)
        val formContainer = findViewById<View>(R.id.formFieldsContainer)
        val tvStatusTitle = findViewById<TextView>(R.id.tvStatusTitle)
        val tvStatusBadge = findViewById<TextView>(R.id.tvStatusBadge)
        val tvStatusDesc = findViewById<TextView>(R.id.tvStatusDesc)

        val isStaff = state.user?.isStaff == true
        val isApproved = isStaff || req?.status.equals("Approved", ignoreCase = true)

        if (isApproved) {
            cardIntro?.isVisible = false
            formContainer?.isVisible = false
            cardStatus.isVisible = true
            val bizName = req?.businessName?.takeIf { it.isNotBlank() }
            tvStatusTitle.text = if (bizName != null) "کسب‌وکار «$bizName» فعال است" else getString(R.string.business_request_status_approved)
            tvStatusBadge.text = getString(R.string.status_active)
            StatusBadgeHelper.applyBadgeStyle(tvStatusBadge, StatusBadgeHelper.BadgeStyle.COMPLETED)
            tvStatusDesc.text = getString(R.string.profile_manager_active_desc)
            btnSubmit.text = getString(R.string.nav_admin)
            btnSubmit.setOnClickListener {
                startActivity(Intent(this, AdminHomeActivity::class.java))
                finish()
            }
        } else {
            cardIntro?.isVisible = true
            formContainer?.isVisible = true
            btnSubmit.setOnClickListener { submitRequest() }

            if (req != null) {
                cardStatus.isVisible = true
                when {
                    req.status.equals("Pending", ignoreCase = true) -> {
                        tvStatusTitle.text = getString(R.string.business_request_status_pending, req.businessName)
                        tvStatusBadge.text = getString(R.string.platform_tab_pending)
                        StatusBadgeHelper.applyBadgeStyle(tvStatusBadge, StatusBadgeHelper.BadgeStyle.PENDING)
                        tvStatusDesc.text = "درخواست شما ثبت شده و در صف بررسی مدیران سامانه قرار دارد."
                        btnSubmit.text = getString(R.string.business_request_resubmit_btn)
                    }
                    req.status.equals("Rejected", ignoreCase = true) -> {
                        tvStatusTitle.text = getString(R.string.business_request_status_rejected)
                        tvStatusBadge.text = getString(R.string.platform_tab_rejected)
                        StatusBadgeHelper.applyBadgeStyle(tvStatusBadge, StatusBadgeHelper.BadgeStyle.CANCELLED)
                        tvStatusDesc.text = if (!req.reviewNote.isNullOrBlank()) {
                            "درخواست تأیید نشد: ${req.reviewNote}"
                        } else {
                            "درخواست شما برای «${req.businessName}» رد شد. می‌توانید با ویرایش مشخصات مجدداً ارسال کنید."
                        }
                        btnSubmit.text = getString(R.string.business_request_resubmit_btn)
                    }
                    else -> {
                        cardStatus.isVisible = false
                        btnSubmit.text = getString(R.string.business_request_submit_btn)
                    }
                }

                if (!initializedFromState) {
                    initializedFromState = true
                    findViewById<TextInputEditText>(R.id.etReqBusinessName)?.setText(req.businessName)
                    val catIdx = currentCategories.indexOf(req.category).takeIf { it >= 0 }
                        ?: run {
                            if (req.category.isNotBlank()) {
                                currentCategories = listOf(req.category) + currentCategories
                                val updatedAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currentCategories).apply {
                                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                }
                                spCategory?.adapter = updatedAdapter
                                0
                            } else 0
                        }
                    spCategory?.setSelection(catIdx)
                    findViewById<TextInputEditText>(R.id.etReqCity)?.setText(req.city)
                    findViewById<TextInputEditText>(R.id.etReqContactPhone)?.setText(req.contactPhone)
                    findViewById<TextInputEditText>(R.id.etReqFirstBranchName)?.setText(req.firstBranchName)
                    findViewById<TextInputEditText>(R.id.etReqFirstBranchAddress)?.setText(req.firstBranchAddress)
                    findViewById<TextInputEditText>(R.id.etReqDescription)?.setText(req.description.orEmpty())
                }
            } else {
                cardStatus.isVisible = false
                btnSubmit.text = getString(R.string.business_request_submit_btn)
                if (!initializedFromState && !state.phoneNumber.isNullOrBlank()) {
                    initializedFromState = true
                    findViewById<TextInputEditText>(R.id.etReqContactPhone)?.setText(state.phoneNumber)
                }
            }
        }

        state.message?.let { msg ->
            if (msg == "business_request_submitted") {
                showToast(getString(R.string.business_request_submitted_success), isLong = true)
                viewModel.clearMessages()
                setResult(RESULT_OK)
                finish()
            } else {
                Snackbar.make(findViewById(R.id.scrollView), msg, Snackbar.LENGTH_LONG).show()
                viewModel.clearMessages()
            }
        }

        state.error?.let { err ->
            Snackbar.make(findViewById(R.id.scrollView), err, Snackbar.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }
}
