package com.leon.be_nobat.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
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

    private lateinit var spinnerBusiness: Spinner
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
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val tvHeaderTitle = findViewById<TextView>(R.id.tvHeaderTitle)
        val isEditing = !editingBranchId.isNullOrBlank()
        tvHeaderTitle.text = if (isEditing) "ویرایش شعبه" else "افزودن شعبه"

        spinnerBusiness = findViewById(R.id.spinnerBusiness)
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

        setupTimeZoneSpinner()
        loadData()
    }

    override fun observeViewModel() {
        // Direct Coroutine/UseCase invocation
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
                val bizAdapter = ArrayAdapter(
                    this@AdminBranchEditActivity,
                    android.R.layout.simple_spinner_item,
                    businessesList.map { it.second }
                ).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                spinnerBusiness.adapter = bizAdapter

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
            val bizIndex = businessesList.indexOfFirst { it.first == branch.businessId }
            if (bizIndex >= 0) {
                spinnerBusiness.setSelection(bizIndex)
            }
            spinnerBusiness.isEnabled = false // Business cannot be changed once branch is created

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
            tvBranchId.text = "شناسه سیستمی شعبه: ${branch.id}"
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

        val selectedBizIndex = spinnerBusiness.selectedItemPosition
        val businessId = if (existingBranch != null) {
            existingBranch!!.businessId
        } else if (selectedBizIndex in businessesList.indices) {
            businessesList[selectedBizIndex].first
        } else {
            Snackbar.make(btnSave, "لطفاً ابتدا یک کسب‌وکار انتخاب کنید.", Snackbar.LENGTH_LONG).show()
            return
        }

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
}
