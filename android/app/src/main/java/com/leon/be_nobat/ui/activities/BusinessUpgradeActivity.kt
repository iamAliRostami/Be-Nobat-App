package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import com.leon.be_nobat.domain.model.BusinessRequestInput
import com.leon.be_nobat.domain.model.LoginIdentifier
import com.leon.be_nobat.domain.model.ReceiptUploadInput
import com.leon.be_nobat.domain.model.RequestedBranch
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.DeepLinkHelper
import com.leon.be_nobat.helpers.PersianUtils
import com.leon.be_nobat.helpers.StatusBadgeHelper
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.Locale
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
    private var isEditingResubmit = false
    private var selectedPlanType: String = "Trial7Days"

    private val extraBranchCards = mutableListOf<Pair<TextInputEditText, TextInputEditText>>()

    private val cropReceiptLauncher = registerForActivityResult(ImageCropContract()) { croppedUri: Uri? -> if (croppedUri != null) uploadReceiptFromUri(croppedUri) }
    private val pickReceiptLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            cropReceiptLauncher.launch(ImageCropRequest(uri, aspectRatio = 0f))
        }
    }

    override fun setupViews() {
        setToolbarTitle(true) // hide default toolbar

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
        findViewById<View>(R.id.btnThemeToggle)?.setOnClickListener { setupThemeToggle() }

        val actvCategory = findViewById<MaterialAutoCompleteTextView>(R.id.actvReqCategory)
        val initialCategories = viewModel.state.value.categories.ifEmpty { fallbackCategories }
        currentCategories = initialCategories
        val catAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currentCategories)
        actvCategory?.setAdapter(catAdapter)

        findViewById<MaterialButton>(R.id.btnSubmitReq).setOnClickListener {
            submitRequest()
        }

        findViewById<MaterialButton>(R.id.btnGoToAdmin).setOnClickListener {
            startActivity(Intent(this, AdminHomeActivity::class.java))
            finish()
        }

        findViewById<MaterialButton>(R.id.btnStartResubmit).setOnClickListener {
            isEditingResubmit = true
            prefillFormFromRequest()
            render(viewModel.state.value)
        }

        findViewById<MaterialButton>(R.id.btnCancelResubmit).setOnClickListener {
            isEditingResubmit = false
            render(viewModel.state.value)
        }

        findViewById<MaterialButton>(R.id.btnAddExtraBranch)?.setOnClickListener {
            addExtraBranchField()
        }

        findViewById<MaterialButton>(R.id.btnUploadReceipt)?.setOnClickListener {
            pickReceiptLauncher.launch("image/*")
        }

        findViewById<View>(R.id.cardPlanTrial)?.setOnClickListener { selectPlan("Trial7Days") }
        findViewById<View>(R.id.cardPlan1Month)?.setOnClickListener { selectPlan("Monthly") }
        findViewById<View>(R.id.cardPlan3Months)?.setOnClickListener { selectPlan("Quarterly") }
        findViewById<View>(R.id.cardPlan6Months)?.setOnClickListener { selectPlan("SemiAnnual") }
        findViewById<View>(R.id.cardPlan1Year)?.setOnClickListener { selectPlan("Annual") }

        viewModel.load()
        selectPlan(selectedPlanType)
    }

    private fun prefillFormFromRequest() {
        val req = viewModel.state.value.businessRequest ?: return
        findViewById<TextInputEditText>(R.id.etReqBusinessName)?.setText(req.businessName)
        findViewById<MaterialAutoCompleteTextView>(R.id.actvReqCategory)?.setText(req.category, false)
        findViewById<TextInputEditText>(R.id.etReqCity)?.setText(req.city)
        val phone = req.contactPhone.ifBlank {
            viewModel.state.value.phoneNumber.ifBlank { viewModel.state.value.user?.phoneNumber.orEmpty() }
        }
        findViewById<TextInputEditText>(R.id.etReqContactPhone)?.setText(phone)
        findViewById<TextInputEditText>(R.id.etReqFirstBranchName)?.setText(req.firstBranchName)
        findViewById<TextInputEditText>(R.id.etReqFirstBranchAddress)?.setText(req.firstBranchAddress)
        findViewById<TextInputEditText>(R.id.etReqDescription)?.setText(req.description.orEmpty())

        if (req.planType.isNotBlank()) {
            selectPlan(req.planType)
        }

        val container = findViewById<LinearLayout>(R.id.layoutExtraBranchesContainer)
        container?.removeAllViews()
        extraBranchCards.clear()
        req.additionalBranches.forEach { br ->
            addExtraBranchField(br.name, br.address)
        }
        updateLivePricing()
    }

    private fun submitRequest() {
        val etName = findViewById<TextInputEditText>(R.id.etReqBusinessName)
        val tilName = findViewById<TextInputLayout>(R.id.tilReqBusinessName)
        val actvCategory = findViewById<MaterialAutoCompleteTextView>(R.id.actvReqCategory)
        val tilCategory = findViewById<TextInputLayout>(R.id.tilReqCategory)
        val etCity = findViewById<TextInputEditText>(R.id.etReqCity)
        val tilCity = findViewById<TextInputLayout>(R.id.tilReqCity)
        val etPhone = findViewById<TextInputEditText>(R.id.etReqContactPhone)
        val tilPhone = findViewById<TextInputLayout>(R.id.tilReqContactPhone)
        val etBranch = findViewById<TextInputEditText>(R.id.etReqFirstBranchName)
        val tilBranch = findViewById<TextInputLayout>(R.id.tilReqFirstBranchName)
        val etAddress = findViewById<TextInputEditText>(R.id.etReqFirstBranchAddress)
        val tilAddress = findViewById<TextInputLayout>(R.id.tilReqFirstBranchAddress)
        val etDesc = findViewById<TextInputEditText>(R.id.etReqDescription)

        tilName?.error = null
        tilCategory?.error = null
        tilCity?.error = null
        tilPhone?.error = null
        tilBranch?.error = null
        tilAddress?.error = null

        val name = etName?.text?.toString()?.trim().orEmpty()
        val cat = actvCategory?.text?.toString()?.trim().orEmpty()
        val city = etCity?.text?.toString()?.trim().orEmpty()
        val phone = etPhone?.text?.toString()?.trim().orEmpty()
        val branch = etBranch?.text?.toString()?.trim().orEmpty()
        val address = etAddress?.text?.toString()?.trim().orEmpty()
        val desc = etDesc?.text?.toString()?.trim().orEmpty()

        var hasError = false
        if (name.isBlank()) {
            tilName?.error = getString(R.string.platform_biz_name_required)
            hasError = true
        }
        if (cat.isBlank()) {
            tilCategory?.error = getString(R.string.error_category_required)
            hasError = true
        }
        if (city.isBlank()) {
            tilCity?.error = getString(R.string.error_city_required)
            hasError = true
        }
        if (phone.isBlank()) {
            tilPhone?.error = getString(R.string.error_phone_required)
            hasError = true
        }
        if (branch.isBlank()) {
            tilBranch?.error = getString(R.string.platform_biz_branch_required)
            hasError = true
        }
        if (address.isBlank()) {
            tilAddress?.error = getString(R.string.error_address_required)
            hasError = true
        }
        if (hasError) return

        val validExtraBranches = collectExtraBranches()
        val planMonths = when (selectedPlanType) {
            "Monthly" -> 1
            "Quarterly" -> 3
            "SemiAnnual" -> 6
            "Annual" -> 12
            else -> 0
        }
        val discount = when (selectedPlanType) {
            "Quarterly" -> viewModel.state.value.pricing.discountPercent3Months
            "SemiAnnual" -> viewModel.state.value.pricing.discountPercent6Months
            "Annual" -> viewModel.state.value.pricing.discountPercent12Months
            else -> 0
        }
        val input = BusinessRequestInput(
            businessName = name,
            category = cat,
            city = city,
            contactPhone = phone,
            firstBranchName = branch,
            firstBranchAddress = address,
            description = desc,
            additionalBranches = validExtraBranches,
            planType = selectedPlanType,
            planDurationMonths = planMonths,
            discountPercent = discount,
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
        btnSubmit?.isEnabled = !state.submittingBusinessRequest
        btnSubmit?.text = if (state.submittingBusinessRequest) {
            getString(R.string.saving)
        } else {
            getString(R.string.business_request_submit_to_admin_btn)
        }

        val actvCategory = findViewById<MaterialAutoCompleteTextView>(R.id.actvReqCategory)
        val newCategories = state.categories.ifEmpty { fallbackCategories }

        if (newCategories != currentCategories) {
            currentCategories = newCategories
            val newAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currentCategories)
            actvCategory?.setAdapter(newAdapter)
        }

        val layoutManagerActive = findViewById<View>(R.id.layoutManagerActive)
        val layoutPendingStatus = findViewById<View>(R.id.layoutPendingStatus)
        val layoutRejectedStatus = findViewById<View>(R.id.layoutRejectedStatus)
        val cardIntroInfo = findViewById<View>(R.id.cardIntroInfo)
        val formFieldsContainer = findViewById<View>(R.id.formFieldsContainer)
        val btnCancelResubmit = findViewById<View>(R.id.btnCancelResubmit)

        val isStaff = state.user?.isStaff == true
        val req = state.businessRequest
        val isApproved = isStaff || req?.status.equals("Approved", ignoreCase = true)
        val isPending = !isApproved && req?.status.equals("Pending", ignoreCase = true)
        val isRejected = !isApproved && req?.status.equals("Rejected", ignoreCase = true)

        when {
            isApproved -> {
                layoutManagerActive?.isVisible = true
                layoutPendingStatus?.isVisible = false
                layoutRejectedStatus?.isVisible = false
                cardIntroInfo?.isVisible = false
                formFieldsContainer?.isVisible = false
            }
            isPending -> {
                layoutManagerActive?.isVisible = false
                layoutPendingStatus?.isVisible = true
                layoutRejectedStatus?.isVisible = false
                cardIntroInfo?.isVisible = false
                formFieldsContainer?.isVisible = false

                val isTrial = req?.planType == "Trial7Days"
                val tvPendingHeader = findViewById<TextView>(R.id.tvPendingHeader)
                val tvPendingDescMsg = findViewById<TextView>(R.id.tvPendingDescMsg)
                val badgePending = findViewById<TextView>(R.id.tvPendingStatusBadge)

                if (isTrial) {
                    tvPendingHeader?.text = getString(R.string.business_request_pending_trial_header)
                    tvPendingDescMsg?.text =
                        getString(R.string.business_request_pending_trial_msg, req?.businessName.orEmpty())
                    badgePending?.text = getString(R.string.business_request_pending_trial_badge)
                } else if (req?.hasReceipt == true) {
                    tvPendingHeader?.text = getString(R.string.business_request_pending_header)
                    tvPendingDescMsg?.text =
                        getString(R.string.business_request_pending_detail_msg, req?.businessName.orEmpty())
                    badgePending?.text = getString(R.string.platform_tab_pending)
                } else {
                    tvPendingHeader?.text = getString(R.string.business_request_pending_payment_header)
                    tvPendingDescMsg?.text =
                        getString(R.string.business_request_pending_payment_msg, req?.businessName.orEmpty())
                    badgePending?.text = getString(R.string.platform_tab_pending)
                }
                findViewById<TextView>(R.id.tvPendingBizName)?.text = req?.businessName.orEmpty()
                findViewById<TextView>(R.id.tvPendingCategory)?.text = req?.category.orEmpty()
                findViewById<TextView>(R.id.tvPendingPlan)?.text = formatPlanTitle(req?.planType, req?.discountPercent ?: 0)
                findViewById<TextView>(R.id.tvPendingCity)?.text = req?.city.orEmpty()
                findViewById<TextView>(R.id.tvPendingPhone)?.text = req?.contactPhone.orEmpty()
                findViewById<TextView>(R.id.tvPendingBranch)?.text = req?.firstBranchName.orEmpty()
                findViewById<TextView>(R.id.tvPendingAddress)?.text = req?.firstBranchAddress.orEmpty()

                val desc = req?.description?.trim().orEmpty()
                val rowDesc = findViewById<View>(R.id.rowPendingDesc)
                val tvDesc = findViewById<TextView>(R.id.tvPendingDesc)
                if (desc.isNotEmpty()) {
                    rowDesc?.isVisible = true
                    tvDesc?.text = desc
                } else {
                    rowDesc?.isVisible = false
                }

                // Total branches and fee
                val totalBranches = req?.totalBranchesCount ?: 1
                val totalFee = req?.totalFee ?: 0.0
                findViewById<TextView>(R.id.tvPendingTotalBranches)?.text = getString(
                    R.string.platform_request_total_branches,
                    PersianUtils.toPersianDigits(totalBranches.toString())
                )
                findViewById<TextView>(R.id.tvPendingTotalFee)?.text = if (isTrial || totalFee == 0.0) {
                    getString(R.string.platform_request_total_fee, getString(R.string.business_request_free))
                } else {
                    getString(R.string.platform_request_total_fee, getString(R.string.business_request_toman_format, formatPrice(totalFee)))
                }

                // Extra branches list
                val layoutExtra = findViewById<View>(R.id.layoutPendingExtraBranches)
                val tvExtraTitle = findViewById<TextView>(R.id.tvPendingExtraBranchesTitle)
                val tvExtraList = findViewById<TextView>(R.id.tvPendingExtraBranchesList)
                if (!req?.additionalBranches.isNullOrEmpty()) {
                    layoutExtra?.isVisible = true
                    tvExtraTitle?.text = getString(
                        R.string.platform_request_other_branches,
                        PersianUtils.toPersianDigits(req!!.additionalBranches.size.toString())
                    )
                    tvExtraList?.text = req.additionalBranches.joinToString("\n") { br ->
                        "• ${br.name}" + if (br.address.isNotBlank()) " (${br.address})" else ""
                    }
                } else {
                    layoutExtra?.isVisible = false
                }

                // Hide bank info and receipt upload card if Trial
                findViewById<View>(R.id.cardPendingBankInfo)?.isVisible = !isTrial
                findViewById<View>(R.id.cardPendingReceipt)?.isVisible = !isTrial

                if (!isTrial) {
                    // Bank info in pending state
                    val pendingHolder = findViewById<TextView>(R.id.tvPendingAccountHolder)
                    val pBankParts = mutableListOf<String>()
                    if (state.pricing.bankName.isNotBlank()) pBankParts.add(state.pricing.bankName)
                    if (state.pricing.accountHolder.isNotBlank()) pBankParts.add("به نام ${state.pricing.accountHolder}")
                    pendingHolder?.text = if (pBankParts.isNotEmpty()) pBankParts.joinToString(" - ") else ""
                    pendingHolder?.isVisible = pBankParts.isNotEmpty()

                    // Pending Card Number
                    val layoutPCard = findViewById<View>(R.id.layoutPendingCardNumber)
                    val tvPCard = findViewById<TextView>(R.id.tvPendingCardNumber)
                    val btnPCopyCard = findViewById<View>(R.id.btnCopyPendingCard)
                    val pCardNum = state.pricing.cardNumber.trim()
                    if (pCardNum.isNotBlank()) {
                        layoutPCard?.isVisible = true
                        tvPCard?.text = getString(R.string.business_request_bank_card_label, PersianUtils.toPersianDigits(pCardNum))
                        val copyPCardAction = View.OnClickListener {
                            DeepLinkHelper.copyToClipboard(this, pCardNum, "Card Number")
                            Toast.makeText(this, R.string.card_number_copied, Toast.LENGTH_SHORT).show()
                        }
                        btnPCopyCard?.setOnClickListener(copyPCardAction)
                        layoutPCard?.setOnClickListener(copyPCardAction)
                    } else {
                        layoutPCard?.isVisible = false
                    }

                    // Pending IBAN
                    val layoutPIban = findViewById<View>(R.id.layoutPendingIban)
                    val tvPIban = findViewById<TextView>(R.id.tvPendingIban)
                    val btnPCopyIban = findViewById<View>(R.id.btnCopyPendingIban)
                    val pIbanNum = state.pricing.iban.trim()
                    if (pIbanNum.isNotBlank()) {
                        layoutPIban?.isVisible = true
                        tvPIban?.text = getString(R.string.business_request_bank_iban_label, pIbanNum)
                        val copyPIbanAction = View.OnClickListener {
                            DeepLinkHelper.copyToClipboard(this, pIbanNum, "IBAN")
                            Toast.makeText(this, R.string.iban_copied, Toast.LENGTH_SHORT).show()
                        }
                        btnPCopyIban?.setOnClickListener(copyPIbanAction)
                        layoutPIban?.setOnClickListener(copyPIbanAction)
                    } else {
                        layoutPIban?.isVisible = false
                    }

                    // Pending Account Number
                    val layoutPAcc = findViewById<View>(R.id.layoutPendingAccountNumber)
                    val tvPAcc = findViewById<TextView>(R.id.tvPendingAccountNumber)
                    val btnPCopyAcc = findViewById<View>(R.id.btnCopyPendingAccountNumber)
                    val pAccNum = state.pricing.accountNumber.trim()
                    if (pAccNum.isNotBlank()) {
                        layoutPAcc?.isVisible = true
                        tvPAcc?.text = getString(R.string.business_request_bank_account_number_label, PersianUtils.toPersianDigits(pAccNum))
                        val copyPAccAction = View.OnClickListener {
                            DeepLinkHelper.copyToClipboard(this, pAccNum, "Account Number")
                            Toast.makeText(this, R.string.account_number_copied, Toast.LENGTH_SHORT).show()
                        }
                        btnPCopyAcc?.setOnClickListener(copyPAccAction)
                        layoutPAcc?.setOnClickListener(copyPAccAction)
                    } else {
                        layoutPAcc?.isVisible = false
                    }

                    findViewById<TextView>(R.id.tvPendingInstructions)?.text = state.pricing.paymentInstructions

                    // Receipt info & status
                    val badgeReceipt = findViewById<TextView>(R.id.tvPendingReceiptBadge)
                    val btnUpload = findViewById<MaterialButton>(R.id.btnUploadReceipt)
                    val ivPreview = findViewById<ImageView>(R.id.ivPendingReceiptPreview)
                    val pbReceipt = findViewById<ProgressBar>(R.id.pbReceiptLoading)
                    pbReceipt?.isVisible = state.uploadingReceipt
                    btnUpload?.isEnabled = !state.uploadingReceipt

                    val etTracking = findViewById<TextInputEditText>(R.id.etPendingTrackingCode)
                    if (etTracking?.text.isNullOrBlank() && !req?.receiptTrackingCode.isNullOrBlank()) {
                        etTracking?.setText(req.receiptTrackingCode)
                    }

                    if (req?.hasReceipt == true) {
                        badgeReceipt?.text = getString(R.string.business_request_receipt_uploaded_badge)
                        StatusBadgeHelper.applyBadgeStyle(badgeReceipt, StatusBadgeHelper.BadgeStyle.CONFIRMED)
                        btnUpload?.text = getString(R.string.business_request_receipt_change_btn)

                        if (!req.receiptImageBase64.isNullOrBlank()) {
                            try {
                                val decoded = Base64.decode(req.receiptImageBase64, Base64.DEFAULT)
                                val bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                                if (bmp != null) {
                                    ivPreview?.isVisible = true
                                    ivPreview?.setImageBitmap(bmp)
                                }
                            } catch (_: Exception) {
                                ivPreview?.isVisible = false
                            }
                        } else {
                            ivPreview?.isVisible = false
                        }
                    } else {
                        badgeReceipt?.text = getString(R.string.business_request_receipt_pending_badge)
                        StatusBadgeHelper.applyBadgeStyle(badgeReceipt, StatusBadgeHelper.BadgeStyle.PENDING)
                        btnUpload?.text = getString(R.string.business_request_receipt_upload_btn)
                        ivPreview?.isVisible = false
                    }
                }
            }
            isRejected && !isEditingResubmit -> {
                layoutManagerActive?.isVisible = false
                layoutPendingStatus?.isVisible = false
                layoutRejectedStatus?.isVisible = true
                cardIntroInfo?.isVisible = false
                formFieldsContainer?.isVisible = false

                val tvRejectedReason = findViewById<TextView>(R.id.tvRejectedReason)
                tvRejectedReason?.text = if (!req?.reviewNote.isNullOrBlank()) {
                    getString(R.string.business_request_review_note, req.reviewNote)
                } else {
                    getString(R.string.business_request_status_rejected)
                }
            }
            else -> {
                // New request or (isRejected && isEditingResubmit)
                layoutManagerActive?.isVisible = false
                layoutPendingStatus?.isVisible = false
                layoutRejectedStatus?.isVisible = false
                cardIntroInfo?.isVisible = true
                formFieldsContainer?.isVisible = true
                btnCancelResubmit?.isVisible = isRejected && isEditingResubmit

                val resolvedPhone = state.phoneNumber.ifBlank { state.user?.phoneNumber.orEmpty() }.ifBlank {
                    val email = state.user?.email.orEmpty().trim()
                    if (LoginIdentifier.isIranianMobile(email)) LoginIdentifier.normalizeIranianMobileDigits(email) else ""
                }

                if (!initializedFromState) {
                    if (req != null) {
                        initializedFromState = true
                        prefillFormFromRequest()
                    } else if (resolvedPhone.isNotBlank()) {
                        initializedFromState = true
                        findViewById<TextInputEditText>(R.id.etReqContactPhone)?.setText(resolvedPhone)
                    }
                }
                val etPhone = findViewById<TextInputEditText>(R.id.etReqContactPhone)
                if (etPhone?.text.isNullOrBlank() && resolvedPhone.isNotBlank()) {
                    etPhone?.setText(resolvedPhone)
                }

                updateLivePricing()
            }
        }

        state.message?.let { msg ->
            if (msg == "receipt_uploaded") {
                showToast(getString(R.string.business_request_receipt_success), isLong = true)
                viewModel.clearMessages()
            } else if (msg == "business_request_submitted") {
                showToast(getString(R.string.business_request_submitted_success), isLong = true)
                isEditingResubmit = false
                viewModel.clearMessages()
                setResult(RESULT_OK)
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
    private fun formatPrice(price: Number): String {
        return NumberFormat.getNumberInstance(Locale("fa", "IR")).format(price.toLong())
    }

    private fun collectExtraBranches(): List<RequestedBranch> {
        return extraBranchCards.mapNotNull { (etName, etAddr) ->
            val name = etName.text?.toString()?.trim().orEmpty()
            val addr = etAddr.text?.toString()?.trim().orEmpty()
            if (name.isNotBlank()) RequestedBranch(name, addr) else null
        }
    }

    private fun selectPlan(planType: String) {
        selectedPlanType = planType
        val selectedStroke = ContextCompat.getColor(this, R.color.plan_card_selected_stroke)
        val defaultStroke = ContextCompat.getColor(this, R.color.plan_card_default_stroke)
        val selectedBg = ContextCompat.getColorStateList(this, R.color.plan_card_selected_bg)
        val defaultBg = ContextCompat.getColorStateList(this, R.color.plan_card_default_bg)
        val density = resources.displayMetrics.density
        val strokeSelected = (2 * density).toInt()
        val strokeDefault = (1 * density).toInt()

        val cards = listOf(
            Pair("Trial7Days", findViewById<MaterialCardView>(R.id.cardPlanTrial)),
            Pair("Monthly", findViewById<MaterialCardView>(R.id.cardPlan1Month)),
            Pair("Quarterly", findViewById<MaterialCardView>(R.id.cardPlan3Months)),
            Pair("SemiAnnual", findViewById<MaterialCardView>(R.id.cardPlan6Months)),
            Pair("Annual", findViewById<MaterialCardView>(R.id.cardPlan1Year))
        )

        cards.forEach { (type, card) ->
            if (card != null) {
                if (type == selectedPlanType) {
                    card.strokeWidth = strokeSelected
                    card.strokeColor = selectedStroke
                    card.setCardBackgroundColor(selectedBg)
                } else {
                    card.strokeWidth = strokeDefault
                    card.strokeColor = defaultStroke
                    card.setCardBackgroundColor(defaultBg)
                }
            }
        }
        updateLivePricing()
    }

    private fun formatPlanTitle(planType: String?, discount: Int): String = when (planType) {
        "Trial7Days" -> getString(R.string.business_request_plan_trial_title) + " (۷ روز)"
        "Monthly" -> getString(R.string.business_request_plan_1m_title)
        "Quarterly" -> if (discount > 0) "۳ ماهه ($discount٪ تخفیف)" else "۳ ماهه"
        "SemiAnnual" -> if (discount > 0) "۶ ماهه ($discount٪ تخفیف)" else "۶ ماهه"
        "Annual" -> if (discount > 0) "۱ ساله ($discount٪ تخفیف ویژه)" else "۱ ساله"
        else -> "تعرفه پایه"
    }

    private fun updateLivePricing() {
        val pricing = viewModel.state.value.pricing
        val extraCount = collectExtraBranches().size
        val totalBranches = 1 + extraCount
        val monthly = pricing.baseBusinessPrice + (extraCount * pricing.extraBranchPrice)

        val cost3m = (monthly * 3.0) * (100 - pricing.discountPercent3Months) / 100.0
        val cost6m = (monthly * 6.0) * (100 - pricing.discountPercent6Months) / 100.0
        val cost1y = (monthly * 12.0) * (100 - pricing.discountPercent12Months) / 100.0

        // Update discount tags on cards
        findViewById<TextView>(R.id.tvPlan3MonthsDiscount)?.apply {
            if (pricing.discountPercent3Months > 0) {
                isVisible = true
                text = getString(R.string.business_request_plan_discount_format, pricing.discountPercent3Months)
            } else {
                isVisible = false
            }
        }
        findViewById<TextView>(R.id.tvPlan6MonthsDiscount)?.apply {
            if (pricing.discountPercent6Months > 0) {
                isVisible = true
                text = getString(R.string.business_request_plan_discount_format, pricing.discountPercent6Months)
            } else {
                isVisible = false
            }
        }
        findViewById<TextView>(R.id.tvPlan1YearDiscount)?.apply {
            if (pricing.discountPercent12Months > 0) {
                isVisible = true
                text = getString(R.string.business_request_plan_discount_format, pricing.discountPercent12Months)
            } else {
                isVisible = false
            }
        }

        // Update card prices
        findViewById<TextView>(R.id.tvPlan1MonthPrice)?.text =
            getString(R.string.business_request_toman_format, formatPrice(monthly))
        findViewById<TextView>(R.id.tvPlan3MonthsPrice)?.text =
            getString(R.string.business_request_toman_format, formatPrice(cost3m))
        findViewById<TextView>(R.id.tvPlan6MonthsPrice)?.text =
            getString(R.string.business_request_toman_format, formatPrice(cost6m))
        findViewById<TextView>(R.id.tvPlan1YearPrice)?.text =
            getString(R.string.business_request_toman_format, formatPrice(cost1y))

        // Update calculation card
        val planDiscount = when (selectedPlanType) {
            "Quarterly" -> pricing.discountPercent3Months
            "SemiAnnual" -> pricing.discountPercent6Months
            "Annual" -> pricing.discountPercent12Months
            else -> 0
        }
        val planTitle = formatPlanTitle(selectedPlanType, planDiscount)
        findViewById<TextView>(R.id.tvPlanCalcTitle)?.text =
            getString(R.string.business_request_calc_title_format, planTitle)

        val tvSubtitle = findViewById<TextView>(R.id.tvPlanCalcSubtitle)
        if (selectedPlanType == "Trial7Days") {
            tvSubtitle?.text = getString(R.string.business_request_calc_trial_desc)
        } else {
            val sb = StringBuilder(getString(R.string.business_request_calc_paid_desc_base, formatPrice(pricing.baseBusinessPrice)))
            if (extraCount > 0) {
                sb.append(getString(R.string.business_request_calc_paid_desc_extra, extraCount, formatPrice(pricing.extraBranchPrice)))
            }
            if (planDiscount > 0) {
                sb.append(getString(R.string.business_request_calc_paid_desc_discount, planDiscount))
            }
            tvSubtitle?.text = sb.toString()
        }

        val liveFee = when (selectedPlanType) {
            "Trial7Days" -> 0.0
            "Monthly" -> monthly
            "Quarterly" -> cost3m
            "SemiAnnual" -> cost6m
            "Annual" -> cost1y
            else -> monthly
        }
        findViewById<TextView>(R.id.tvPlanCalcTotalFee)?.text = if (selectedPlanType == "Trial7Days" || liveFee == 0.0) {
            getString(R.string.business_request_free)
        } else {
            getString(R.string.business_request_toman_format, formatPrice(liveFee))
        }

        findViewById<TextView>(R.id.tvPlanCalcFooter)?.text = if (selectedPlanType == "Trial7Days") {
            getString(R.string.business_request_calc_trial_footer, totalBranches)
        } else {
            getString(R.string.business_request_calc_paid_footer, totalBranches)
        }

        // Button text
        findViewById<MaterialButton>(R.id.btnSubmitReq)?.let { btn ->
            if (btn.isEnabled) {
                btn.text = if (selectedPlanType == "Trial7Days") {
                    getString(R.string.business_request_submit_trial_btn)
                } else {
                    getString(R.string.business_request_submit_paid_btn)
                }
            }
        }

        findViewById<View>(R.id.tvNoExtraBranchesHint)?.isVisible = extraBranchCards.isEmpty()
    }

    private fun addExtraBranchField(initialName: String = "", initialAddress: String = "") {
        val container = findViewById<LinearLayout>(R.id.layoutExtraBranchesContainer) ?: return
        val branchIndex = extraBranchCards.size + 2

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (10 * resources.displayMetrics.density).toInt()
            }
            radius = 12 * resources.displayMetrics.density
            cardElevation = 0f
            strokeWidth = (1 * resources.displayMetrics.density).toInt()
            setStrokeColor(getColor(R.color.colorOutline))
        }

        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
            }
        }

        val tvTitle = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = getString(R.string.business_request_extra_branch_title, branchIndex)
            setTextAppearance(R.style.TextAppearance_BeNobat_Title)
            textSize = 12.5f
        }

        val btnDelete = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.business_request_remove_branch)
            textSize = 11f
            setTextColor(getColor(R.color.colorError))
            strokeColor = android.content.res.ColorStateList.valueOf(getColor(R.color.colorError))
            val padH = (8 * resources.displayMetrics.density).toInt()
            setPadding(padH, 0, padH, 0)
        }

        headerRow.addView(tvTitle)
        headerRow.addView(btnDelete)
        cardContent.addView(headerRow)

        val tilName = com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            hint = getString(R.string.business_request_extra_branch_name)
        }
        val etName = TextInputEditText(tilName.context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setText(initialName)
            maxLines = 1
        }
        tilName.addView(etName)
        cardContent.addView(tilName)

        val tilAddress = com.google.android.material.textfield.TextInputLayout(this, null, com.google.android.material.R.attr.textInputStyle).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * resources.displayMetrics.density).toInt()
            }
            hint = getString(R.string.business_request_extra_branch_address)
        }
        val etAddress = TextInputEditText(tilAddress.context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setText(initialAddress)
            maxLines = 2
        }
        tilAddress.addView(etAddress)
        cardContent.addView(tilAddress)

        card.addView(cardContent)
        container.addView(card)

        val pair = Pair(etName, etAddress)
        extraBranchCards.add(pair)

        etName.doAfterTextChanged { updateLivePricing() }

        btnDelete.setOnClickListener {
            container.removeView(card)
            extraBranchCards.remove(pair)
            // Renumber remaining cards
            extraBranchCards.forEachIndexed { i, p ->
                val parentCard = p.first.parent?.parent?.parent as? LinearLayout
                val titleView = parentCard?.getChildAt(0) as? LinearLayout
                val tv = titleView?.getChildAt(0) as? TextView
                tv?.text = getString(R.string.business_request_extra_branch_title, i + 2)
            }
            updateLivePricing()
        }

        updateLivePricing()
    }

    private fun uploadReceiptFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bmp = BitmapFactory.decodeStream(stream) ?: return
                // Scale bitmap if too large
                val maxDim = 1200
                val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
                    val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                    val targetW = if (ratio >= 1) maxDim else (maxDim * ratio).toInt()
                    val targetH = if (ratio >= 1) (maxDim / ratio).toInt() else maxDim
                    Bitmap.createScaledBitmap(bmp, targetW, targetH, true)
                } else bmp

                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val bytes = baos.toByteArray()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val trackingCode = findViewById<TextInputEditText>(R.id.etPendingTrackingCode)?.text?.toString()?.trim()

                viewModel.uploadReceipt(
                    ReceiptUploadInput(
                        trackingCode = trackingCode?.ifBlank { null },
                        contentType = "image/jpeg",
                        dataBase64 = base64
                    )
                )
            }
        } catch (e: Exception) {
            showToast("خطا در خواندن تصویر فیش: ${e.message}")
        }
    }
}