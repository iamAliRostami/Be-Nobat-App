package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBranchService
import com.leon.be_nobat.domain.model.ManagementBranchServiceInput
import com.leon.be_nobat.domain.model.ManagementService
import com.leon.be_nobat.domain.model.ManagementServiceInput
import com.leon.be_nobat.domain.model.ServiceCatalogItem
import com.leon.be_nobat.domain.repository.BusinessRepository
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.domain.usecase.AdminCreateServiceUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateServiceUseCase
import com.leon.be_nobat.helpers.AdminFormDialog
import com.leon.be_nobat.helpers.AppToast
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.CancellationNoticeEditor
import com.leon.be_nobat.helpers.PersianUtils
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AdminServiceEditActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_service_edit
    override val applyBottomInsetsOnRoot: Boolean = false

    private val managementRepository: ManagementRepository by inject()
    private val businessRepository: BusinessRepository by inject()
    private val createServiceUseCase: AdminCreateServiceUseCase by inject()
    private val updateServiceUseCase: AdminUpdateServiceUseCase by inject()

    private val editingServiceId: String?
        get() = intent.getStringExtra(EXTRA_SERVICE_ID)

    private var existingService: ManagementService? = null
    private var branchesList: List<ManagementBranch> = emptyList()
    private var catalogItems: List<ServiceCatalogItem> = emptyList()
    private var catalogCategories: List<String> = emptyList()
    private var currentFilteredCatalog: List<ServiceCatalogItem> = emptyList()
    private var selectedCatalogItem: ServiceCatalogItem? = null

    private lateinit var spBranch: Spinner
    private lateinit var tvSourceLabel: TextView
    private lateinit var rgSource: RadioGroup
    private lateinit var rbCatalog: RadioButton
    private lateinit var rbCustom: RadioButton
    private lateinit var layoutCatalogSection: View
    private lateinit var spCatalogCategory: Spinner
    private lateinit var tilCatalogSearch: TextInputLayout
    private lateinit var etCatalogSearch: TextInputEditText
    private lateinit var spCatalogItem: Spinner
    private lateinit var tilName: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var tilDescription: TextInputLayout
    private lateinit var etDescription: TextInputEditText
    private lateinit var tilDuration: TextInputLayout
    private lateinit var etDuration: TextInputEditText
    private lateinit var tilPrice: TextInputLayout
    private lateinit var etPrice: TextInputEditText
    private lateinit var layoutCancellation: FrameLayout
    private lateinit var cancellationEditor: CancellationNoticeEditor
    private lateinit var cardServiceDetails: View
    private lateinit var tvServiceId: TextView
    private lateinit var btnBranchCancellation: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnSave: MaterialButton

    companion object {
        const val EXTRA_SERVICE_ID = "extra_service_id"

        fun createIntent(context: Context, serviceId: String? = null): Intent {
            return Intent(context, AdminServiceEditActivity::class.java).apply {
                serviceId?.let { putExtra(EXTRA_SERVICE_ID, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireBusinessManagementPermission()
    }

    override fun setupViews() {
        val isEditing = !editingServiceId.isNullOrBlank()
        setupAdminHeader(
            title = if (isEditing) getString(R.string.form_edit_service_title) else getString(R.string.form_create_service_title),
            onBack = { finish() }
        )

        spBranch = findViewById(R.id.spBranch)
        tvSourceLabel = findViewById(R.id.tvSourceLabel)
        rgSource = findViewById(R.id.rgSource)
        rbCatalog = findViewById(R.id.rbCatalog)
        rbCustom = findViewById(R.id.rbCustom)
        layoutCatalogSection = findViewById(R.id.layoutCatalogSection)
        spCatalogCategory = findViewById(R.id.spCatalogCategory)
        tilCatalogSearch = findViewById(R.id.tilCatalogSearch)
        etCatalogSearch = findViewById(R.id.etCatalogSearch)
        spCatalogItem = findViewById(R.id.spCatalogItem)
        tilName = findViewById(R.id.tilName)
        etName = findViewById(R.id.etName)
        tilDescription = findViewById(R.id.tilDescription)
        etDescription = findViewById(R.id.etDescription)
        tilDuration = findViewById(R.id.tilDuration)
        etDuration = findViewById(R.id.etDuration)
        tilPrice = findViewById(R.id.tilPrice)
        etPrice = findViewById(R.id.etPrice)
        layoutCancellation = findViewById(R.id.layoutCancellation)
        cardServiceDetails = findViewById(R.id.cardServiceDetails)
        tvServiceId = findViewById(R.id.tvServiceId)
        btnBranchCancellation = findViewById(R.id.btnBranchCancellation)
        btnCancel = findViewById(R.id.btnCancel)
        btnSave = findViewById(R.id.btnSave)

        cancellationEditor = CancellationNoticeEditor(this)
        cancellationEditor.setValue(null)
        layoutCancellation.addView(cancellationEditor)

        btnSave.text = if (isEditing) getString(R.string.platform_biz_save_btn) else "ایجاد خدمت"
        btnCancel.setOnClickListener { finish() }

        if (isEditing) {
            tvSourceLabel.visibility = View.GONE
            rgSource.visibility = View.GONE
            layoutCatalogSection.visibility = View.GONE
        } else {
            setupSourceRadioGroup()
        }

        loadData()
    }

    override fun observeViewModel() {
        // Direct Coroutine/UseCase invocation
    }

    private fun setupSourceRadioGroup() {
        rgSource.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbCatalog) {
                layoutCatalogSection.visibility = View.VISIBLE
                updateCatalogItemsDropdown()
            } else {
                layoutCatalogSection.visibility = View.GONE
                selectedCatalogItem = null
                if (existingService == null) {
                    etName.setText("")
                    etDescription.setText("")
                }
            }
        }

        spCatalogCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateCatalogItemsDropdown()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        etCatalogSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCatalogItemsDropdown()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        spCatalogItem.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val item = currentFilteredCatalog.getOrNull(position) ?: return
                selectedCatalogItem = item
                if (existingService == null) {
                    etName.setText(item.name)
                    etDescription.setText(item.description)
                    if (item.suggestedDurationMinutes > 0) {
                        etDuration.setText(item.suggestedDurationMinutes.toString())
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun updateCatalogItemsDropdown() {
        val selectedCatIdx = spCatalogCategory.selectedItemPosition
        val selectedCat = if (selectedCatIdx > 0 && selectedCatIdx < catalogCategories.size) catalogCategories[selectedCatIdx] else ""
        val query = etCatalogSearch.text?.toString()?.trim().orEmpty()

        currentFilteredCatalog = catalogItems.filter { item ->
            (selectedCat.isBlank() || item.category == selectedCat) &&
            (query.isBlank() || item.name.contains(query, ignoreCase = true) || item.category.contains(query, ignoreCase = true))
        }

        val itemLabels = if (currentFilteredCatalog.isEmpty()) {
            listOf("— موردی یافت نشد —")
        } else {
            currentFilteredCatalog.map { "${it.category} · ${it.name}" }
        }

        val itemAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, itemLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spCatalogItem.adapter = itemAdapter

        if (currentFilteredCatalog.isNotEmpty()) {
            val first = currentFilteredCatalog.first()
            selectedCatalogItem = first
            if (existingService == null) {
                etName.setText(first.name)
                etDescription.setText(first.description)
                if (first.suggestedDurationMinutes > 0) {
                    etDuration.setText(first.suggestedDurationMinutes.toString())
                }
            }
        } else {
            selectedCatalogItem = null
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            // 1. Load active branches
            val branchesResult = loadCancellationBranches()
            branchesList = branchesResult.getOrNull()?.filter { it.active }.orEmpty()
            if (branchesList.isEmpty() && editingServiceId == null) {
                AppToast.show(this@AdminServiceEditActivity, "ابتدا یک شعبهٔ فعال ایجاد کنید.")
                finish()
                return@launch
            }

            val branchLabels = branchesList.map { "${it.businessName.orEmpty()} · ${it.name}" }
            val branchAdapter = ArrayAdapter(this@AdminServiceEditActivity, android.R.layout.simple_spinner_item, branchLabels).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spBranch.adapter = branchAdapter

            // 2. If editing, load service details directly
            if (!editingServiceId.isNullOrBlank()) {
                loadServiceDetails(editingServiceId!!)
            } else {
                // Load catalog items only when creating new service
                catalogItems = businessRepository.getCatalog(page = 1, pageSize = 200).getOrNull()?.items.orEmpty()
                catalogCategories = listOf("همهٔ دسته‌ها") + catalogItems.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

                val catAdapter = ArrayAdapter(this@AdminServiceEditActivity, android.R.layout.simple_spinner_item, catalogCategories).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spCatalogCategory.adapter = catAdapter

                updateCatalogItemsDropdown()
            }
        }
    }

    private suspend fun loadServiceDetails(serviceId: String) {
        managementRepository.getService(serviceId).onSuccess { service ->
            existingService = service
            cancellationEditor.setValue(service.cancellationNoticeMinutes)
            etName.setText(service.name)
            etDescription.setText(service.description)

            // Price in Toman
            val priceToman = service.price.toLong().toString()
            etPrice.setText(priceToman)

            // Duration
            etDuration.setText(service.durationMinutes.toString())

            // Select branch
            val branchIdx = branchesList.indexOfFirst { it.id in service.branchIds }
            if (branchIdx >= 0) {
                spBranch.setSelection(branchIdx)
            }
            spBranch.isEnabled = false // Service branch assignment is already bound

            // Hide catalog creation tools in edit mode
            tvSourceLabel.visibility = View.GONE
            rgSource.visibility = View.GONE
            layoutCatalogSection.visibility = View.GONE

            // Show additional details card
            cardServiceDetails.isVisible = true
            tvServiceId.text = getString(R.string.service_system_id, service.id)
            btnBranchCancellation.setOnClickListener {
                showBranchCancellationDialog(service)
            }

            btnSave.text = getString(R.string.platform_biz_save_btn)
            btnSave.setOnClickListener { saveService() }
        }.onFailure { err ->
            showFeedback(err.message ?: "خطا در بارگذاری مشخصات خدمت", isError = true)
        }
    }

    override fun onResume() {
        super.onResume()
        btnSave.setOnClickListener { saveService() }
    }

    private fun saveService() {
        tilName.error = null
        tilPrice.error = null
        tilDuration.error = null

        val name = etName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            tilName.error = getString(R.string.msg_invalid_input)
            etName.requestFocus()
            return
        }

        val durText = PersianUtils.toEnglishDigits(etDuration.text?.toString()?.trim().orEmpty())
        val duration = durText.toIntOrNull() ?: 0
        if (duration < 5 || duration > 1440) {
            tilDuration.error = "مدت خدمت باید بین ۵ تا ۱۴۴۰ دقیقه باشد"
            etDuration.requestFocus()
            return
        }

        val cancelError = cancellationEditor.validate()
        if (cancelError != null) {
            return
        }

        val priceToman = etPrice.text?.toString()?.toDoubleOrNull() ?: 0.0

        val selectedBranch = branchesList.getOrNull(spBranch.selectedItemPosition)
        if (selectedBranch == null && existingService == null) {
            showFeedback("لطفاً ابتدا یک شعبه انتخاب کنید.", isError = true)
            return
        }

        btnSave.isEnabled = false
        lifecycleScope.launch {
            if (editingServiceId.isNullOrBlank()) {
                val branch = selectedBranch!!
                val catalogId = if (rbCatalog.isChecked) selectedCatalogItem?.id else null

                val input = ManagementServiceInput(
                    businessId = branch.businessId,
                    branchId = branch.id,
                    catalogItemId = catalogId,
                    name = name,
                    description = etDescription.text?.toString()?.trim().orEmpty(),
                    durationMinutes = duration,
                    price = priceToman,
                    cancellationNoticeMinutes = cancellationEditor.value,
                )

                createServiceUseCase(input).onSuccess {
                    Toast.makeText(this@AdminServiceEditActivity, "خدمت با موفقیت ایجاد شد.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { err ->
                    btnSave.isEnabled = true
                    showFeedback(err.message ?: "خطا در ثبت خدمت", isError = true)
                }
            } else {
                val service = existingService!!
                val input = ManagementServiceInput(
                    businessId = service.businessId,
                    catalogItemId = service.catalogItemId,
                    cancellationNoticeMinutes = cancellationEditor.value,
                    name = name,
                    description = etDescription.text?.toString()?.trim().orEmpty(),
                    durationMinutes = duration,
                    price = priceToman,
                )

                updateServiceUseCase(editingServiceId!!, input).onSuccess {
                    Toast.makeText(this@AdminServiceEditActivity, "خدمت با موفقیت ویرایش شد.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { err ->
                    btnSave.isEnabled = true
                    showFeedback(err.message ?: "خطا در ذخیره تغییرات خدمت", isError = true)
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

    private suspend fun loadCancellationBranches(businessId: String? = null): Result<List<ManagementBranch>> = runCatching {
        val items = mutableListOf<ManagementBranch>()
        var page = 1
        do {
            val result = managementRepository.listBranches(businessId = businessId, page = page++, pageSize = 100).getOrThrow()
            items += result.items
            val more = result.items.isNotEmpty() && items.size < result.total
        } while (more)
        items
    }

    private suspend fun findCancellationBranchService(branchId: String, serviceId: String): Result<ManagementBranchService> = runCatching {
        var page = 1
        var count = 0
        var found: ManagementBranchService? = null
        do {
            val result = managementRepository.listBranchServices(branchId, page = page++, pageSize = 100).getOrThrow()
            found = result.items.firstOrNull { it.serviceId == serviceId }
            count += result.items.size
            val more = found == null && result.items.isNotEmpty() && count < result.total
        } while (more)
        found ?: error("اتصال خدمت به شعبه تغییر کرده است؛ فهرست را تازه کنید.")
    }

    private fun showBranchCancellationDialog(service: ManagementService) = lifecycleScope.launch {
        loadCancellationBranches(service.businessId).onSuccess { available ->
            val branches = available.filter { it.id in service.branchIds }
            if (branches.isEmpty()) {
                AppToast.show(this@AdminServiceEditActivity, "این خدمت به شعبه‌ای متصل نیست.")
                return@onSuccess
            }
            MaterialAlertDialogBuilder(this@AdminServiceEditActivity)
                .setTitle("انتخاب شعبه برای ${service.name}")
                .setItems(branches.map { it.name }.toTypedArray()) { _, index ->
                    val branch = branches[index]
                    lifecycleScope.launch {
                        findCancellationBranchService(branch.id, service.id).onSuccess { link ->
                            val editor = CancellationNoticeEditor(this@AdminServiceEditActivity).apply {
                                setValue(link.cancellationNoticeMinutes)
                            }
                            AdminFormDialog.Builder(this@AdminServiceEditActivity)
                                .setTitle("مهلت لغو ${service.name} · ${branch.name}")
                                .customView(editor).validate { editor.validate() }
                                .onSubmit {
                                    lifecycleScope.launch {
                                        managementRepository.upsertBranchService(
                                            branch.id, service.id,
                                            ManagementBranchServiceInput(price = link.price, cancellationNoticeMinutes = editor.value)
                                        ).onSuccess { AppToast.show(this@AdminServiceEditActivity, "مهلت لغو خدمتِ شعبه ذخیره شد.") }
                                         .onFailure { AppToast.show(this@AdminServiceEditActivity, it.message.orEmpty()) }
                                    }
                                }.show()
                        }.onFailure { AppToast.show(this@AdminServiceEditActivity, it.message.orEmpty()) }
                    }
                }.show()
        }.onFailure { AppToast.show(this@AdminServiceEditActivity, it.message.orEmpty()) }
    }
}
