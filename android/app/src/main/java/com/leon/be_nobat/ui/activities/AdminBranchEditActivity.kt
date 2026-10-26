package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ManagementBranchInput
import com.leon.be_nobat.domain.model.WeeklyHours
import com.leon.be_nobat.domain.repository.ManagementRepository
import com.leon.be_nobat.domain.usecase.AdminCreateBranchUseCase
import com.leon.be_nobat.domain.usecase.AdminToggleBranchActiveUseCase
import com.leon.be_nobat.domain.usecase.AdminUpdateBranchUseCase
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.WeeklyHoursEditor
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AdminBranchEditActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_admin_branch_edit
    override val applyBottomInsetsOnRoot: Boolean = false

    private val managementRepository: ManagementRepository by inject()
    private val createBranchUseCase: AdminCreateBranchUseCase by inject()
    private val updateBranchUseCase: AdminUpdateBranchUseCase by inject()
    private val toggleBranchActiveUseCase: AdminToggleBranchActiveUseCase by inject()

    private var editingBranchId: String? = null
    private var existingBranch: ManagementBranch? = null
    private var businessesList: List<Pair<String, String>> = emptyList() // ID to Name
    private var selectedBusinessId: String? = null
    private var businessFilterAdapter: BusinessFilterAdapter? = null

    private lateinit var tilBusiness: TextInputLayout
    private lateinit var autoCompleteBusiness: MaterialAutoCompleteTextView
    private lateinit var tilBranchName: TextInputLayout
    private lateinit var etBranchName: TextInputEditText
    private lateinit var spinnerTimeZone: Spinner
    private lateinit var tilBranchAddress: TextInputLayout
    private lateinit var etBranchAddress: TextInputEditText
    private lateinit var weeklyHoursEditor: WeeklyHoursEditor
    private lateinit var cancellationEditor: com.leon.be_nobat.helpers.CancellationNoticeEditor
    private lateinit var cardBranchDetails: View
    private lateinit var switchBranchActive: MaterialSwitch
    private lateinit var tvBranchId: TextView
    private lateinit var btnViewTeam: MaterialButton
    private lateinit var btnViewServices: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var btnCancel: MaterialButton

    private val timeZones = listOf(
        "Asia/Tehran" to "تهران (Asia/Tehran +03:30)",
        "Asia/Dubai" to "دبی (Asia/Dubai +04:00)",
        "Asia/Baku" to "باکو (Asia/Baku +04:00)",
        "Europe/Istanbul" to "استانبول (Europe/Istanbul +03:00)",
        "UTC" to "ساعت هماهنگ جهانی (UTC +00:00)",
    )

    companion object {
        const val EXTRA_BRANCH_ID = "extra_branch_id"

        fun createIntent(context: Context, branchId: String? = null): Intent {
            return Intent(context, AdminBranchEditActivity::class.java).apply {
                branchId?.let { putExtra(EXTRA_BRANCH_ID, it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireBusinessManagementPermission()
        editingBranchId = intent.getStringExtra(EXTRA_BRANCH_ID)
    }

    override fun setupViews() {
        val isEditing = !editingBranchId.isNullOrBlank()
        setupAdminHeader(
            title = if (isEditing) "ویرایش شعبه" else "افزودن شعبه",
            onBack = { finish() }
        )

        tilBusiness = findViewById(R.id.tilBusiness)
        autoCompleteBusiness = findViewById(R.id.autoCompleteBusiness)
        tilBranchName = findViewById(R.id.tilBranchName)
        etBranchName = findViewById(R.id.etBranchName)
        spinnerTimeZone = findViewById(R.id.spinnerTimeZone)
        tilBranchAddress = findViewById(R.id.tilBranchAddress)
        etBranchAddress = findViewById(R.id.etBranchAddress)
        weeklyHoursEditor = findViewById(R.id.weeklyHoursEditor)
        cancellationEditor = com.leon.be_nobat.helpers.CancellationNoticeEditor(this)
        cancellationEditor.setValue(null)
        (weeklyHoursEditor.parent as android.widget.LinearLayout).addView(cancellationEditor, 0)
        cardBranchDetails = findViewById(R.id.cardBranchDetails)
        switchBranchActive = findViewById(R.id.switchBranchActive)
        tvBranchId = findViewById(R.id.tvBranchId)
        btnViewTeam = findViewById(R.id.btnViewTeam)
        btnViewServices = findViewById(R.id.btnViewServices)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)

        btnSave.text = if (isEditing) "ذخیره تغییرات" else "ایجاد شعبه"
        btnCancel.setOnClickListener { finish() }

        setupBusinessSelection()
        setupTimeZoneSpinner()
        loadData()
    }

    override fun observeViewModel() {
        // Direct Coroutine/UseCase invocation
    }

    private fun setupBusinessSelection() {
        tilBusiness.setStartIconOnClickListener {
            if (tilBusiness.isEnabled) {
                showBusinessSearchDialog()
            }
        }

        autoCompleteBusiness.setOnClickListener {
            if (autoCompleteBusiness.isEnabled) {
                autoCompleteBusiness.showDropDown()
            }
        }

        autoCompleteBusiness.setOnItemClickListener { _, _, position, _ ->
            val chosen = businessFilterAdapter?.getItemAt(position)
            if (chosen != null) {
                selectedBusinessId = chosen.first
                autoCompleteBusiness.setText(chosen.second, false)
                tilBusiness.error = null
            }
        }

        autoCompleteBusiness.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val current = s?.toString()?.trim().orEmpty()
                val currentMatch = businessesList.firstOrNull { it.first == selectedBusinessId }
                if (currentMatch == null || !currentMatch.second.equals(current, ignoreCase = true)) {
                    val directMatch = businessesList.firstOrNull { it.second.equals(current, ignoreCase = true) }
                    selectedBusinessId = directMatch?.first
                }
            }
        })
    }

    private fun showBusinessSearchDialog() {
        if (businessesList.isEmpty()) {
            Toast.makeText(this, "کسب‌وکاری یافت نشد", Toast.LENGTH_SHORT).show()
            return
        }

        val context = this
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, 0)
        }

        val radius = 12 * resources.displayMetrics.density
        val searchTil = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "جستجوی نام کسب‌وکار…"
            setStartIconDrawable(R.drawable.ic_search)
            setBoxCornerRadii(radius, radius, radius, radius)
        }
        val searchEt = TextInputEditText(searchTil.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
            typeface = androidx.core.content.res.ResourcesCompat.getFont(context, R.font.app_font)
        }
        searchTil.addView(searchEt)
        dialogView.addView(searchTil)

        val listView = ListView(context).apply {
            divider = null
            dividerHeight = 0
            val listMarginTop = (8 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (300 * resources.displayMetrics.density).toInt()
            ).apply { topMargin = listMarginTop }
        }
        dialogView.addView(listView)

        var currentFiltered = businessesList
        val listAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_list_item_1,
            currentFiltered.map { it.second }.toMutableList()
        )
        listView.adapter = listAdapter

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("انتخاب کسب‌وکار")
            .setView(dialogView)
            .setNegativeButton(R.string.action_close, null)
            .create()

        searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase().orEmpty()
                currentFiltered = if (q.isEmpty()) {
                    businessesList
                } else {
                    businessesList.filter { it.second.lowercase().contains(q) }
                }
                listAdapter.clear()
                listAdapter.addAll(currentFiltered.map { it.second })
                listAdapter.notifyDataSetChanged()
            }
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val chosen = currentFiltered.getOrNull(position)
            if (chosen != null) {
                selectedBusinessId = chosen.first
                autoCompleteBusiness.setText(chosen.second, false)
                tilBusiness.error = null
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupTimeZoneSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            timeZones.map { it.second }
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerTimeZone.adapter = adapter
    }

    private fun loadData() {
        lifecycleScope.launch {
            managementRepository.listBusinesses(pageSize = 100).onSuccess { page ->
                businessesList = page.items.filter { it.active }.map { it.id to it.name }
                businessFilterAdapter = BusinessFilterAdapter(this@AdminBranchEditActivity, businessesList)
                autoCompleteBusiness.setAdapter(businessFilterAdapter)

                if (editingBranchId != null) {
                    loadBranchDetails(editingBranchId!!)
                }
            }.onFailure {
                Snackbar.make(btnSave, it.message ?: "خطا در دریافت لیست کسب‌وکارها", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun loadBranchDetails(branchId: String) {
        managementRepository.getBranch(branchId).onSuccess { branch ->
            existingBranch = branch
            cancellationEditor.setValue(branch.cancellationNoticeMinutes)
            etBranchName.setText(branch.name)
            etBranchAddress.setText(branch.address)

            // Select business
            val biz = businessesList.firstOrNull { it.first == branch.businessId }
            if (biz != null) {
                selectedBusinessId = biz.first
                autoCompleteBusiness.setText(biz.second, false)
            }
            tilBusiness.isEnabled = false
            autoCompleteBusiness.isEnabled = false

            // Select timezone
            val tzIndex = timeZones.indexOfFirst { it.first.equals(branch.timeZoneId, ignoreCase = true) }
            if (tzIndex >= 0) {
                spinnerTimeZone.setSelection(tzIndex)
            }

            // Set weekly hours
            val hours = branch.weeklyHours ?: WeeklyHours.legacy(branch.openHour, branch.closeHour)
            weeklyHoursEditor.setWeeklyHours(hours)

            // Show additional details card
            cardBranchDetails.isVisible = true
            tvBranchId.text = getString(R.string.branch_system_id, branch.id)
            switchBranchActive.isChecked = branch.active

            switchBranchActive.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    toggleBranchActiveUseCase(branch.id, isChecked).onSuccess { updated ->
                        existingBranch = updated
                        Toast.makeText(this@AdminBranchEditActivity, if (isChecked) "شعبه فعال شد" else "شعبه غیرفعال شد", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        switchBranchActive.isChecked = !isChecked
                        Snackbar.make(btnSave, err.message ?: "خطا در تغییر وضعیت", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }

            btnViewTeam.setOnClickListener {
                startActivity(Intent(this@AdminBranchEditActivity, AdminTeamActivity::class.java))
            }
            btnViewServices.setOnClickListener {
                startActivity(Intent(this@AdminBranchEditActivity, AdminServicesActivity::class.java))
            }
            findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShareBranch)?.setOnClickListener {
                val b = existingBranch ?: return@setOnClickListener
                com.leon.be_nobat.helpers.BusinessShareDialog.show(
                    context = this@AdminBranchEditActivity,
                    businessId = b.businessId,
                    businessName = b.businessName?.takeIf { it.isNotBlank() } ?: autoCompleteBusiness.text?.toString()?.takeIf { it.isNotBlank() } ?: getString(R.string.my_business),
                    branchId = b.id,
                    branchName = b.name,
                )
            }

            btnSave.setOnClickListener { saveBranch() }
        }.onFailure { err ->
            Snackbar.make(btnSave, err.message ?: "خطا در بارگذاری مشخصات شعبه", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        btnSave.setOnClickListener { saveBranch() }
    }

    private fun saveBranch() {
        if (cancellationEditor.validate() != null) return
        val name = etBranchName.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            tilBranchName.error = "نام شعبه الزامی است."
            etBranchName.requestFocus()
            return
        }
        tilBranchName.error = null

        val address = etBranchAddress.text?.toString()?.trim().orEmpty()

        val businessId = if (existingBranch != null) {
            existingBranch!!.businessId
        } else {
            selectedBusinessId ?: businessesList.firstOrNull {
                it.second.equals(autoCompleteBusiness.text?.toString()?.trim(), ignoreCase = true)
            }?.first
        }

        if (businessId.isNullOrBlank()) {
            tilBusiness.error = "لطفاً ابتدا یک کسب‌وکار انتخاب کنید."
            autoCompleteBusiness.requestFocus()
            return
        }
        tilBusiness.error = null

        val tzIndex = spinnerTimeZone.selectedItemPosition
        val timeZoneId = if (tzIndex in timeZones.indices) timeZones[tzIndex].first else "Asia/Tehran"

        val hours = weeklyHoursEditor.value
        val hoursValidation = WeeklyHours.validate(hours)
        if (hoursValidation != null) {
            Snackbar.make(btnSave, hoursValidation, Snackbar.LENGTH_LONG).show()
            return
        }

        val input = ManagementBranchInput(
            businessId = businessId,
            name = name,
            address = address,
            timeZoneId = timeZoneId,
            openHour = existingBranch?.openHour ?: 8,
            closeHour = existingBranch?.closeHour ?: 20,
            weeklyHours = hours,
            cancellationNoticeMinutes = cancellationEditor.value,
        )

        btnSave.isEnabled = false
        lifecycleScope.launch {
            if (editingBranchId == null) {
                createBranchUseCase(input).onSuccess {
                    Toast.makeText(this@AdminBranchEditActivity, "شعبه با موفقیت ایجاد شد.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { err ->
                    btnSave.isEnabled = true
                    Snackbar.make(btnSave, err.message ?: "خطا در ثبت شعبه", Snackbar.LENGTH_LONG).show()
                }
            } else {
                updateBranchUseCase(editingBranchId!!, input).onSuccess {
                    Toast.makeText(this@AdminBranchEditActivity, "شعبه با موفقیت ویرایش شد.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                }.onFailure { err ->
                    btnSave.isEnabled = true
                    Snackbar.make(btnSave, err.message ?: "خطا در ذخیره تغییرات شعبه", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private class BusinessFilterAdapter(
        context: Context,
        private val allItems: List<Pair<String, String>>
    ) : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, mutableListOf()), Filterable {

        private var filteredItems: List<Pair<String, String>> = allItems

        init {
            addAll(allItems.map { it.second })
        }

        override fun getCount(): Int = filteredItems.size

        override fun getItem(position: Int): String = filteredItems[position].second

        fun getItemAt(position: Int): Pair<String, String>? = filteredItems.getOrNull(position)

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val query = constraint?.toString()?.trim()?.lowercase().orEmpty()
                    val list = if (query.isEmpty()) {
                        allItems
                    } else {
                        allItems.filter { it.second.lowercase().contains(query) }
                    }
                    return FilterResults().apply {
                        values = list
                        count = list.size
                    }
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    filteredItems = (results?.values as? List<Pair<String, String>>) ?: allItems
                    clear()
                    addAll(filteredItems.map { it.second })
                    notifyDataSetChanged()
                }

                override fun convertResultToString(resultValue: Any?): CharSequence {
                    return (resultValue as? Pair<*, *>)?.second?.toString() ?: (resultValue as? String) ?: ""
                }
            }
        }
    }
}