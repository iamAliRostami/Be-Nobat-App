package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
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
import com.leon.be_nobat.domain.model.ReceiptUploadInput
import com.leon.be_nobat.domain.model.RequestedBranch
import com.leon.be_nobat.helpers.StatusBadgeHelper
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.Locale
import com.leon.be_nobat.helpers.BaseActivity
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

    private val extraBranchCards = mutableListOf<Pair<TextInputEditText, TextInputEditText>>()

    private val pickReceiptLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            uploadReceiptFromUri(uri)
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

        if (viewModel.state.value.categories.isEmpty()) {
            viewModel.loadCategories()
        }
        viewModel.loadPricing()
    }

    private fun prefillFormFromRequest() {
        val req = viewModel.state.value.businessRequest ?: return
        findViewById<TextInputEditText>(R.id.etReqBusinessName)?.setText(req.businessName)
        findViewById<MaterialAutoCompleteTextView>(R.id.actvReqCategory)?.setText(req.category, false)
        findViewById<TextInputEditText>(R.id.etReqCity)?.setText(req.city)
        findViewById<TextInputEditText>(R.id.etReqContactPhone)?.setText(req.contactPhone)
        findViewById<TextInputEditText>(R.id.etReqFirstBranchName)?.setText(req.firstBranchName)
        findViewById<TextInputEditText>(R.id.etReqFirstBranchAddress)?.setText(req.firstBranchAddress)
        findViewById<TextInputEditText>(R.id.etReqDescription)?.setText(req.description.orEmpty())

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
        val input = BusinessRequestInput(
            businessName = name,
            category = cat,
            city = city,
            contactPhone = phone,
            firstBranchName = branch,
            firstBranchAddress = address,
            description = desc,
            additionalBranches = validExtraBranches,
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

                findViewById<TextView>(R.id.tvPendingDescMsg)?.text =
                    getString(R.string.business_request_pending_detail_msg, req?.businessName.orEmpty())
                findViewById<TextView>(R.id.tvPendingBizName)?.text = req?.businessName.orEmpty()
                findViewById<TextView>(R.id.tvPendingCategory)?.text = req?.category.orEmpty()
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
                val totalFee = req?.totalFee ?: state.pricing.baseBusinessPrice
                findViewById<TextView>(R.id.tvPendingTotalBranches)?.text = getString(R.string.platform_request_total_branches, totalBranches)
                findViewById<TextView>(R.id.tvPendingTotalFee)?.text = getString(R.string.platform_request_total_fee, formatPrice(totalFee))

                // Extra branches list
                val layoutExtra = findViewById<View>(R.id.layoutPendingExtraBranches)
                val tvExtraTitle = findViewById<TextView>(R.id.tvPendingExtraBranchesTitle)
                val tvExtraList = findViewById<TextView>(R.id.tvPendingExtraBranchesList)
                if (!req?.additionalBranches.isNullOrEmpty()) {
                    layoutExtra?.isVisible = true
                    tvExtraTitle?.text = getString(R.string.platform_request_other_branches, req!!.additionalBranches.size)
                    tvExtraList?.text = req.additionalBranches.joinToString("\n") { br ->
                        "• " + if (br.address.isNotBlank()) " ()" else ""
                    }
                } else {
                    layoutExtra?.isVisible = false
                }

                // Bank info
                findViewById<TextView>(R.id.tvPendingCardNumber)?.text = getString(R.string.business_request_bank_card_label, state.pricing.cardNumber)
                findViewById<TextView>(R.id.tvPendingIban)?.text = getString(R.string.business_request_bank_iban_label, state.pricing.iban, state.pricing.accountHolder)
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

                if (!initializedFromState) {
                    initializedFromState = true
                    if (req != null) {
                        prefillFormFromRequest()
                    } else if (!state.phoneNumber.isNullOrBlank()) {
                        findViewById<TextInputEditText>(R.id.etReqContactPhone)?.setText(state.phoneNumber)
                    }
                }
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
    private fun formatPrice(price: Long): String {
        return NumberFormat.getNumberInstance(Locale("fa", "IR")).format(price)
    }

    private fun collectExtraBranches(): List<RequestedBranch> {
        return extraBranchCards.mapNotNull { (etName, etAddr) ->
            val name = etName.text?.toString()?.trim().orEmpty()
            val addr = etAddr.text?.toString()?.trim().orEmpty()
            if (name.isNotBlank()) RequestedBranch(name, addr) else null
        }
    }

    private fun updateLivePricing() {
        val pricing = viewModel.state.value.pricing
        val extraCount = collectExtraBranches().size
        val basePrice = pricing.baseBusinessPrice
        val extraCost = extraCount * pricing.extraBranchPrice
        val totalFee = basePrice + extraCost

        findViewById<TextView>(R.id.tvLiveBasePrice)?.text =
            getString(R.string.business_request_pricing_base, formatPrice(basePrice))

        val tvExtra = findViewById<TextView>(R.id.tvLiveExtraCost)
        if (extraCount > 0) {
            tvExtra?.isVisible = true
            tvExtra?.text = getString(R.string.business_request_pricing_extra, extraCount, formatPrice(extraCost))
        } else {
            tvExtra?.isVisible = false
        }

        findViewById<TextView>(R.id.tvLiveTotalFee)?.text =
            getString(R.string.business_request_pricing_total, formatPrice(totalFee))

        findViewById<TextView>(R.id.tvLiveBankInfo)?.text =
            getString(R.string.business_request_bank_card_label, pricing.cardNumber) + " (" + pricing.accountHolder + ")"
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
            showToast("خطا در خواندن تصویر فیش: ")
        }
    }
}