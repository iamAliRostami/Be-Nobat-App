package com.leon.be_nobat.ui.activities

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.PlatformPricingInput
import com.leon.be_nobat.domain.repository.PlatformRepository
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.MapHelper
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.Locale

class PlatformSettingsActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_platform_settings
    override val applyBottomInsetsOnRoot: Boolean = false

    private val platformRepository: PlatformRepository by inject()

    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    private lateinit var etAddress: TextInputEditText
    private lateinit var btnPickMap: MaterialButton
    private lateinit var layoutCoordsPreview: LinearLayout
    private lateinit var tvCoordsDisplay: TextView
    private lateinit var btnPreviewMap: MaterialButton
    private lateinit var btnClearCoords: MaterialButton
    private lateinit var btnTestNav: MaterialButton

    private lateinit var etPhone: TextInputEditText
    private lateinit var etSupportPhone: TextInputEditText
    private lateinit var etMobile: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etInfoEmail: TextInputEditText

    private lateinit var etWeekdays: TextInputEditText
    private lateinit var etThursdays: TextInputEditText
    private lateinit var etHolidays: TextInputEditText

    private lateinit var etBankName: TextInputEditText
    private lateinit var etAccountHolder: TextInputEditText
    private lateinit var etCardNumber: TextInputEditText
    private lateinit var etIban: TextInputEditText
    private lateinit var etAccountNumber: TextInputEditText
    private lateinit var etBasePrice: TextInputEditText
    private lateinit var etExtraBranchPrice: TextInputEditText
    private lateinit var etInstructions: TextInputEditText

    private lateinit var etTelegram: TextInputEditText
    private lateinit var etInstagram: TextInputEditText
    private lateinit var etEitaa: TextInputEditText

    private lateinit var btnSave: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadSettings()
    }

    override fun setupViews() {
        setupAdminHeader(getString(R.string.platform_settings_title))

        etAddress = findViewById(R.id.etSettingsAddress)
        btnPickMap = findViewById(R.id.btnSettingsPickMap)
        layoutCoordsPreview = findViewById(R.id.layoutSettingsCoordsPreview)
        tvCoordsDisplay = findViewById(R.id.tvSettingsCoordsDisplay)
        btnPreviewMap = findViewById(R.id.btnSettingsPreviewMap)
        btnClearCoords = findViewById(R.id.btnSettingsClearCoords)
        btnTestNav = findViewById(R.id.btnSettingsTestNav)

        etPhone = findViewById(R.id.etSettingsPhone)
        etSupportPhone = findViewById(R.id.etSettingsSupportPhone)
        etMobile = findViewById(R.id.etSettingsMobile)
        etEmail = findViewById(R.id.etSettingsEmail)
        etInfoEmail = findViewById(R.id.etSettingsInfoEmail)

        etWeekdays = findViewById(R.id.etSettingsWeekdays)
        etThursdays = findViewById(R.id.etSettingsThursdays)
        etHolidays = findViewById(R.id.etSettingsHolidays)

        etBankName = findViewById(R.id.etSettingsBankName)
        etAccountHolder = findViewById(R.id.etSettingsAccountHolder)
        etCardNumber = findViewById(R.id.etSettingsCardNumber)
        etIban = findViewById(R.id.etSettingsIban)
        etAccountNumber = findViewById(R.id.etSettingsAccountNumber)
        etBasePrice = findViewById(R.id.etSettingsBasePrice)
        etExtraBranchPrice = findViewById(R.id.etSettingsExtraBranchPrice)
        etInstructions = findViewById(R.id.etSettingsInstructions)

        etTelegram = findViewById(R.id.etSettingsTelegram)
        etInstagram = findViewById(R.id.etSettingsInstagram)
        etEitaa = findViewById(R.id.etSettingsEitaa)

        btnSave = findViewById(R.id.btnSavePlatformSettings)

        btnPickMap.setOnClickListener {
            MapHelper.showLocationPicker(this, selectedLatitude, selectedLongitude) { lat, lng ->
                selectedLatitude = lat
                selectedLongitude = lng
                updateCoordinatesPreview()
            }
        }

        btnPreviewMap.setOnClickListener {
            val lat = selectedLatitude
            val lng = selectedLongitude
            if (lat != null && lng != null) {
                MapHelper.showMapPreview(
                    this,
                    lat,
                    lng,
                    getString(R.string.contact_address_title),
                    etAddress.text?.toString()?.trim()
                )
            }
        }

        btnClearCoords.setOnClickListener {
            selectedLatitude = null
            selectedLongitude = null
            updateCoordinatesPreview()
        }

        btnTestNav.setOnClickListener {
            MapHelper.openNavigation(
                this,
                selectedLatitude,
                selectedLongitude,
                etAddress.text?.toString()?.trim(),
                getString(R.string.contact_address_title)
            )
        }

        btnSave.setOnClickListener {
            saveSettings()
        }
    }

    override fun observeViewModel() {
        // Form-based activity; no shared ViewModel
    }

    private fun updateCoordinatesPreview() {
        val lat = selectedLatitude
        val lng = selectedLongitude
        if (lat != null && lng != null) {
            layoutCoordsPreview.isVisible = true
            tvCoordsDisplay.text = String.format(Locale.US, "%.5f, %.5f", lat, lng)
        } else {
            layoutCoordsPreview.isVisible = false
            tvCoordsDisplay.text = ""
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            platformRepository.getPlatformPricing().onSuccess { p ->
                populateFields(p)
            }.onFailure { err ->
                // Fallback to default platform settings if offline or first load
                populateFields(
                    com.leon.be_nobat.domain.model.PlatformPricing(
                        baseBusinessPrice = 500_000.0,
                        extraBranchPrice = 200_000.0,
                        cardNumber = "6037-9918-1234-5678",
                        iban = "IR120170000000123456789012",
                        accountHolder = "سامانه نوبت‌دهی به‌نوبت",
                        paymentInstructions = "لطفاً پس از واریز مبلغ، تصویر فیش واریز را در این بخش بارگذاری نمایید.",
                        address = "اصفهان، خیابان شریعتی، مجتمع تجاری اداری به‌نوبت، طبقه ۴، واحد ۴۰۲",
                        latitude = 32.6546,
                        longitude = 51.6680,
                        phone = "021-91002424",
                        supportPhone = "021-91002425",
                        mobile = "09134588221",
                        email = "support@benobat.ir",
                        infoEmail = "info@benobat.ir",
                        bankName = "بانک ملت",
                        accountNumber = "123456789",
                        workingHoursWeekdays = "شنبه تا چهارشنبه: ۸:۳۰ الی ۱۸:۰۰",
                        workingHoursThursdays = "پنج‌شنبه‌ها: ۸:۳۰ الی ۱۳:۰۰",
                        workingHoursHolidays = "روزهای تعطیل: پشتیبانی آنلاین از طریق پیام و تیکت",
                        telegram = "benobat_support",
                        instagram = "benobat.official",
                        eitaa = "benobat_support"
                    )
                )
            }
        }
    }

    private fun populateFields(p: com.leon.be_nobat.domain.model.PlatformPricing) {
        val address = p.address.ifBlank { "اصفهان، خیابان شریعتی، مجتمع تجاری اداری به‌نوبت، طبقه ۴، واحد ۴۰۲" }
        val phone = p.phone.ifBlank { "021-91002424" }
        val supportPhone = p.supportPhone.ifBlank { "021-91002425" }
        val mobile = p.mobile.ifBlank { "09134588221" }
        val email = p.email.ifBlank { "support@benobat.ir" }
        val infoEmail = p.infoEmail.ifBlank { "info@benobat.ir" }
        val bankName = p.bankName.ifBlank { "بانک ملت" }
        val accountHolder = p.accountHolder.ifBlank { "سامانه نوبت‌دهی به‌نوبت" }
        val cardNumber = p.cardNumber.ifBlank { "6037-9918-1234-5678" }
        val iban = p.iban.ifBlank { "IR120170000000123456789012" }
        val accountNumber = p.accountNumber.ifBlank { "123456789" }
        val weekdays = p.workingHoursWeekdays.ifBlank { "شنبه تا چهارشنبه: ۸:۳۰ الی ۱۸:۰۰" }
        val thursdays = p.workingHoursThursdays.ifBlank { "پنج‌شنبه‌ها: ۸:۳۰ الی ۱۳:۰۰" }
        val holidays = p.workingHoursHolidays.ifBlank { "روزهای تعطیل: پشتیبانی آنلاین از طریق پیام و تیکت" }
        val telegram = p.telegram.ifBlank { "benobat_support" }
        val instagram = p.instagram.ifBlank { "benobat.official" }
        val eitaa = p.eitaa.ifBlank { "benobat_support" }
        val instructions = p.paymentInstructions.ifBlank { "لطفاً پس از واریز مبلغ، تصویر فیش واریز را در این بخش بارگذاری نمایید." }

        etAddress.setText(address)
        selectedLatitude = p.latitude ?: 32.6546
        selectedLongitude = p.longitude ?: 51.6680
        updateCoordinatesPreview()

        etPhone.setText(phone)
        etSupportPhone.setText(supportPhone)
        etMobile.setText(mobile)
        etEmail.setText(email)
        etInfoEmail.setText(infoEmail)

        etWeekdays.setText(weekdays)
        etThursdays.setText(thursdays)
        etHolidays.setText(holidays)

        etBankName.setText(bankName)
        etAccountHolder.setText(accountHolder)
        etCardNumber.setText(cardNumber)
        etIban.setText(iban)
        etAccountNumber.setText(accountNumber)

        etBasePrice.setText(if (p.baseBusinessPrice > 0) p.baseBusinessPrice.toLong().toString() else "500000")
        etExtraBranchPrice.setText(if (p.extraBranchPrice > 0) p.extraBranchPrice.toLong().toString() else "200000")
        etInstructions.setText(instructions)

        etTelegram.setText(telegram)
        etInstagram.setText(instagram)
        etEitaa.setText(eitaa)
    }

    private fun saveSettings() {
        val basePrice = etBasePrice.text?.toString()?.toDoubleOrNull() ?: 500_000.0
        val extraPrice = etExtraBranchPrice.text?.toString()?.toDoubleOrNull() ?: 200_000.0

        val input = PlatformPricingInput(
            baseBusinessPrice = basePrice,
            extraBranchPrice = extraPrice,
            cardNumber = etCardNumber.text?.toString()?.trim().orEmpty(),
            iban = etIban.text?.toString()?.trim().orEmpty(),
            accountHolder = etAccountHolder.text?.toString()?.trim().orEmpty(),
            paymentInstructions = etInstructions.text?.toString()?.trim().orEmpty(),
            address = etAddress.text?.toString()?.trim().orEmpty(),
            latitude = selectedLatitude,
            longitude = selectedLongitude,
            phone = etPhone.text?.toString()?.trim().orEmpty(),
            supportPhone = etSupportPhone.text?.toString()?.trim().orEmpty(),
            mobile = etMobile.text?.toString()?.trim().orEmpty(),
            email = etEmail.text?.toString()?.trim().orEmpty(),
            infoEmail = etInfoEmail.text?.toString()?.trim().orEmpty(),
            bankName = etBankName.text?.toString()?.trim().orEmpty(),
            accountNumber = etAccountNumber.text?.toString()?.trim().orEmpty(),
            workingHoursWeekdays = etWeekdays.text?.toString()?.trim().orEmpty(),
            workingHoursThursdays = etThursdays.text?.toString()?.trim().orEmpty(),
            workingHoursHolidays = etHolidays.text?.toString()?.trim().orEmpty(),
            telegram = etTelegram.text?.toString()?.trim().orEmpty(),
            instagram = etInstagram.text?.toString()?.trim().orEmpty(),
            eitaa = etEitaa.text?.toString()?.trim().orEmpty()
        )

        btnSave.isEnabled = false
        lifecycleScope.launch {
            platformRepository.updatePlatformPricing(input)
                .onSuccess {
                    btnSave.isEnabled = true
                    Snackbar.make(btnSave, getString(R.string.platform_settings_saved_success), Snackbar.LENGTH_SHORT).show()
                }
                .onFailure { err ->
                    btnSave.isEnabled = true
                    Snackbar.make(btnSave, err.message ?: getString(R.string.error_generic), Snackbar.LENGTH_LONG).show()
                }
        }
    }
}
