package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.data.remote.BeNobatApiConfig
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBusiness
import com.leon.be_nobat.domain.model.ManagementBusinessInput
import com.leon.be_nobat.domain.model.WeeklyHours
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.CancellationNoticeEditor
import com.leon.be_nobat.helpers.DeepLinkHelper
import com.leon.be_nobat.helpers.WeeklyHoursEditor
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.ByteArrayOutputStream

class AdminBusinessEditActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_business_edit
    override val applyBottomInsetsOnRoot: Boolean = false

    private val managementRepository: ManagementRepository by inject()
    private val tokenManager: TokenManager by inject()

    private val initialBusinessId: String?
        get() = intent.getStringExtra(EXTRA_BUSINESS_ID)

    private var editingBusinessId: String? = null
    private var existingBusiness: ManagementBusiness? = null
    private var firstBranch: ManagementBranch? = null
    private var managedBusinesses: List<ManagementBusiness> = emptyList()

    private var selectedAvatarBase64: String? = null
    private var selectedHeaderBase64: String? = null

    private lateinit var cardSelectBusiness: View
    private lateinit var spinnerSelectBusiness: Spinner
    private lateinit var ivLogoPreview: ImageView
    private lateinit var ivCoverPreview: ImageView
    private lateinit var btnChangeLogo: MaterialButton
    private lateinit var btnChangeCover: MaterialButton

    private lateinit var tilBusinessName: TextInputLayout
    private lateinit var etBusinessName: TextInputEditText
    private lateinit var tilBusinessSlug: TextInputLayout
    private lateinit var etBusinessSlug: TextInputEditText
    private lateinit var spinnerCategory: Spinner
    private lateinit var spinnerCity: Spinner
    private lateinit var tilBusinessDescription: TextInputLayout
    private lateinit var etBusinessDescription: TextInputEditText

    private lateinit var switchRequiresApproval: MaterialSwitch
    private lateinit var cancellationEditor: CancellationNoticeEditor

    private lateinit var tilDepositPercentage: TextInputLayout
    private lateinit var etDepositPercentage: TextInputEditText
    private lateinit var tilDepositAccountHolder: TextInputLayout
    private lateinit var etDepositAccountHolder: TextInputEditText
    private lateinit var tilDepositCardNumber: TextInputLayout
    private lateinit var etDepositCardNumber: TextInputEditText
    private lateinit var tilDepositIban: TextInputLayout
    private lateinit var etDepositIban: TextInputEditText

    private lateinit var etInstagram: TextInputEditText
    private lateinit var etTelegram: TextInputEditText
    private lateinit var etWhatsApp: TextInputEditText
    private lateinit var etEitaa: TextInputEditText
    private lateinit var etBale: TextInputEditText
    private lateinit var etRubika: TextInputEditText
    private lateinit var etLinkedIn: TextInputEditText
    private lateinit var etTwitter: TextInputEditText
    private lateinit var etAparat: TextInputEditText
    private lateinit var etWebsite: TextInputEditText

    private lateinit var tilFirstBranchName: TextInputLayout
    private lateinit var etFirstBranchName: TextInputEditText
    private lateinit var tilFirstBranchAddress: TextInputLayout
    private lateinit var etFirstBranchAddress: TextInputEditText
    private lateinit var weeklyHoursEditor: WeeklyHoursEditor

    private lateinit var btnSave: MaterialButton
    private lateinit var btnViewPublicPage: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private val categoryOptions = mutableListOf(
        "عمومی", "پزشکی و سلامت", "زیبایی و آرایش", "زیبایی و سلامت", "آموزش", "خدمات فنی", "ورزش", "مشاوره", "فروشگاهی", "رستوران و کافه", "سایر"
    )

    private val cityOptions = mutableListOf(
        "تهران", "مشهد", "اصفهان", "شیراز", "تبریز", "کرج", "قم", "اهواز", "رشت", "کرمانشاه", "یزد", "سایر"
    )

    private var currentAvatarViewerSource: String? = null
    private var currentCoverViewerSource: String? = null

    private val cropAvatarLauncher = registerForActivityResult(ImageCropContract()) { croppedUri: Uri? ->
        croppedUri ?: return@registerForActivityResult
        currentAvatarViewerSource = croppedUri.toString()
        processImage(croppedUri) { base64, bmp -> selectedAvatarBase64 = base64; ivLogoPreview.setImageBitmap(bmp) }
        /*
        // selectedAvatarBase64 handled in cropAvatarLauncher
        if (false) { // placeholder removed
        } */
    }

    private val cropCoverLauncher = registerForActivityResult(ImageCropContract()) { croppedUri: Uri? ->
        croppedUri ?: return@registerForActivityResult
        currentCoverViewerSource = croppedUri.toString()
        processImage(croppedUri) { base64, bmp -> selectedHeaderBase64 = base64; ivCoverPreview.setImageBitmap(bmp) }
        /*
        // selectedHeaderBase64 handled in cropCoverLauncher
        if (false) { // placeholder removed
        } */
    }

    private val pickAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        cropAvatarLauncher.launch(ImageCropRequest(uri, aspectRatio = 1f))
        // cropAvatarLauncher handles processing after crop
        // selectedAvatarBase64 handled in cropAvatarLauncher
        if (false) { // placeholder removed
        }
    }

    private val pickCoverLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        cropCoverLauncher.launch(ImageCropRequest(uri, aspectRatio = 16f / 9f))
        // cropAvatarLauncher handles processing after crop
        // selectedHeaderBase64 handled in cropCoverLauncher
        if (false) { // placeholder removed
        }
    }

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
            title = getString(R.string.admin_business_profile),
            currentMenuItemId = R.id.drawer_business_profile,
            onBack = { finish() }
        )

        cardSelectBusiness = findViewById(R.id.cardSelectBusiness)
        spinnerSelectBusiness = findViewById(R.id.spinnerSelectBusiness)

        ivLogoPreview = findViewById(R.id.ivLogoPreview)
        ivCoverPreview = findViewById(R.id.ivCoverPreview)
        btnChangeLogo = findViewById(R.id.btnChangeLogo)
        btnChangeCover = findViewById(R.id.btnChangeCover)

        tilBusinessName = findViewById(R.id.tilBusinessName)
        etBusinessName = findViewById(R.id.etBusinessName)
        tilBusinessSlug = findViewById(R.id.tilBusinessSlug)
        etBusinessSlug = findViewById(R.id.etBusinessSlug)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        spinnerCity = findViewById(R.id.spinnerCity)
        tilBusinessDescription = findViewById(R.id.tilBusinessDescription)
        etBusinessDescription = findViewById(R.id.etBusinessDescription)

        switchRequiresApproval = findViewById(R.id.switchRequiresApproval)
        cancellationEditor = findViewById(R.id.cancellationEditor)
        cancellationEditor.setValue(120, allowInheritance = false)

        tilDepositPercentage = findViewById(R.id.tilDepositPercentage)
        etDepositPercentage = findViewById(R.id.etDepositPercentage)
        tilDepositAccountHolder = findViewById(R.id.tilDepositAccountHolder)
        etDepositAccountHolder = findViewById(R.id.etDepositAccountHolder)
        tilDepositCardNumber = findViewById(R.id.tilDepositCardNumber)
        etDepositCardNumber = findViewById(R.id.etDepositCardNumber)
        tilDepositIban = findViewById(R.id.tilDepositIban)
        etDepositIban = findViewById(R.id.etDepositIban)

        etInstagram = findViewById(R.id.etInstagram)
        etTelegram = findViewById(R.id.etTelegram)
        etWhatsApp = findViewById(R.id.etWhatsApp)
        etEitaa = findViewById(R.id.etEitaa)
        etBale = findViewById(R.id.etBale)
        etRubika = findViewById(R.id.etRubika)
        etLinkedIn = findViewById(R.id.etLinkedIn)
        etTwitter = findViewById(R.id.etTwitter)
        etAparat = findViewById(R.id.etAparat)
        etWebsite = findViewById(R.id.etWebsite)

        tilFirstBranchName = findViewById(R.id.tilFirstBranchName)
        etFirstBranchName = findViewById(R.id.etFirstBranchName)
        tilFirstBranchAddress = findViewById(R.id.tilFirstBranchAddress)
        etFirstBranchAddress = findViewById(R.id.etFirstBranchAddress)
        weeklyHoursEditor = findViewById(R.id.weeklyHoursEditor)

        btnSave = findViewById(R.id.btnSave)
        btnViewPublicPage = findViewById(R.id.btnViewPublicPage)
        loadingProgress = findViewById(R.id.loadingProgress)

        btnChangeLogo.setOnClickListener { pickAvatarLauncher.launch("image/*") }
        btnChangeCover.setOnClickListener { pickCoverLauncher.launch("image/*") }
        ivLogoPreview.setOnClickListener { currentAvatarViewerSource?.let { ImageViewerActivity.open(this, it, "لوگوی کسب‌وکار") } }
        ivCoverPreview.setOnClickListener { currentCoverViewerSource?.let { ImageViewerActivity.open(this, it, "تصویر کاور کسب‌وکار") } }

        findViewById<View>(R.id.btnPublicView)?.setOnClickListener { openPublicView() }
        btnViewPublicPage.setOnClickListener { openPublicView() }

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

    private fun openPublicView() {
        val targetId = editingBusinessId?.takeIf { it.isNotBlank() }
            ?: etBusinessSlug.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        if (!targetId.isNullOrBlank()) {
            val intent = Intent(this, BusinessDetailActivity::class.java).apply {
                putExtra("businessId", targetId)
            }
            startActivity(intent)
        } else {
            showFeedback("شناسه یا آدرس صفحه عمومی مشخص نیست.", isError = true)
        }
    }

    private fun processImage(uri: Uri, onResult: (String, Bitmap) -> Unit) {
        lifecycleScope.launch {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream) ?: return@launch
                    val maxDim = 1024
                    val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
                        val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                        val (w, h) = if (ratio > 1) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                        Bitmap.createScaledBitmap(bmp, w, h, true)
                    } else bmp

                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                    onResult(base64, scaled)
                }
            } catch (e: Exception) {
                showFeedback("خطا در پردازش تصویر: ${e.message}", isError = true)
            }
        }
    }

    private fun loadTargetBusiness() {
        lifecycleScope.launch {
            loadingProgress.isVisible = true
            val listRes = managementRepository.listBusinesses(pageSize = 100).getOrNull()?.items.orEmpty()
            managedBusinesses = listRes

            if (managedBusinesses.size > 1) {
                cardSelectBusiness.isVisible = true
                val bizNames = managedBusinesses.map { it.name }
                val spinnerAdapter = ArrayAdapter(this@AdminBusinessEditActivity, android.R.layout.simple_spinner_item, bizNames).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerSelectBusiness.adapter = spinnerAdapter
                spinnerSelectBusiness.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        val selected = managedBusinesses.getOrNull(position) ?: return
                        if (selected.id != editingBusinessId) {
                            editingBusinessId = selected.id
                            loadBusinessDetails(selected.id)
                        }
                    }
                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
            } else {
                cardSelectBusiness.isVisible = false
            }

            val targetId = initialBusinessId ?: run {
                val mgmtContext = tokenManager.currentManagementContext()
                val firstBizId = mgmtContext?.memberships?.firstOrNull()?.businessId
                if (!firstBizId.isNullOrBlank()) {
                    firstBizId
                } else {
                    managedBusinesses.firstOrNull()?.id
                }
            }

            if (targetId.isNullOrBlank()) {
                loadingProgress.isVisible = false
                showFeedback("کسب‌وکاری برای مدیریت یافت نشد.", isError = true)
                return@launch
            }

            editingBusinessId = targetId
            val idx = managedBusinesses.indexOfFirst { it.id == targetId }
            if (idx >= 0 && managedBusinesses.size > 1) {
                spinnerSelectBusiness.setSelection(idx)
            }

            loadBusinessDetails(targetId)
        }
    }

    private fun loadBusinessDetails(businessId: String) {
        lifecycleScope.launch {
            loadingProgress.isVisible = true
            selectedAvatarBase64 = null
            selectedHeaderBase64 = null

            managementRepository.getBusiness(businessId).onSuccess { business ->
                loadingProgress.isVisible = false
                existingBusiness = business
                cancellationEditor.setValue(business.cancellationNoticeMinutes, allowInheritance = false)
                etBusinessName.setText(business.name)
                etBusinessSlug.setText(business.slug)
                etBusinessDescription.setText(business.description)
                switchRequiresApproval.isChecked = business.requiresApproval

                // Avatar preview
                val resolvedAvatar = BeNobatApiConfig.resolveMediaUrl(business.avatarUrl)
                currentAvatarViewerSource = resolvedAvatar
                if (!resolvedAvatar.isNullOrBlank()) {
                    ivLogoPreview.load(resolvedAvatar) {
                        crossfade(true)
                        error(R.drawable.ic_person)
                    }
                } else {
                    ivLogoPreview.setImageResource(R.drawable.ic_person)
                }

                // Cover preview
                val resolvedCover = BeNobatApiConfig.resolveMediaUrl(business.headerUrl)
                currentCoverViewerSource = resolvedCover
                if (!resolvedCover.isNullOrBlank()) {
                    ivCoverPreview.load(resolvedCover) {
                        crossfade(true)
                        error(R.drawable.ic_empty_box)
                    }
                } else {
                    ivCoverPreview.setImageResource(R.drawable.ic_empty_box)
                }

                // Deposit settings
                val depositPct = business.depositPercentage
                etDepositPercentage.setText(depositPct?.let { if (it > 0) it.toInt().toString() else "" }.orEmpty())
                etDepositAccountHolder.setText(business.depositAccountHolder.orEmpty())
                etDepositCardNumber.setText(business.depositCardNumber.orEmpty())
                etDepositIban.setText(business.depositIban.orEmpty())

                // Social channels
                etInstagram.setText(business.instagram.orEmpty())
                etTelegram.setText(business.telegram.orEmpty())
                etWhatsApp.setText(business.whatsApp.orEmpty())
                etEitaa.setText(business.eitaa.orEmpty())
                etBale.setText(business.bale.orEmpty())
                etRubika.setText(business.rubika.orEmpty())
                etLinkedIn.setText(business.linkedIn.orEmpty())
                etTwitter.setText(business.twitter.orEmpty())
                etAparat.setText(business.aparat.orEmpty())
                etWebsite.setText(business.website.orEmpty())

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
                loadingProgress.isVisible = false
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

        val depositPct = etDepositPercentage.text?.toString()?.trim()?.toDoubleOrNull()
        val depositCard = etDepositCardNumber.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val depositIban = etDepositIban.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val depositHolder = etDepositAccountHolder.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

        val instagram = etInstagram.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val telegram = etTelegram.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val whatsApp = etWhatsApp.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val eitaa = etEitaa.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val bale = etBale.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val rubika = etRubika.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val linkedIn = etLinkedIn.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val twitter = etTwitter.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val aparat = etAparat.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val website = etWebsite.text?.toString()?.trim()?.takeIf { it.isNotBlank() }

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
            depositPercentage = depositPct,
            depositCardNumber = depositCard,
            depositIban = depositIban,
            depositAccountHolder = depositHolder,
            avatarBase64 = selectedAvatarBase64,
            headerBase64 = selectedHeaderBase64,
            telegram = telegram,
            instagram = instagram,
            whatsApp = whatsApp,
            eitaa = eitaa,
            bale = bale,
            rubika = rubika,
            linkedIn = linkedIn,
            twitter = twitter,
            aparat = aparat,
            website = website,
        )

        btnSave.isEnabled = false
        loadingProgress.isVisible = true

        lifecycleScope.launch {
            managementRepository.updateBusiness(bizId, input).onSuccess {
                btnSave.isEnabled = true
                loadingProgress.isVisible = false
                selectedAvatarBase64 = null
                selectedHeaderBase64 = null
                Toast.makeText(this@AdminBusinessEditActivity, "مشخصات کسب‌وکار با موفقیت ذخیره شد.", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                loadBusinessDetails(bizId)
            }.onFailure { err ->
                btnSave.isEnabled = true
                loadingProgress.isVisible = false
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
