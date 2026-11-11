package com.leon.be_nobat.ui.activities

import android.content.Intent
import android.net.Uri
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.leon.be_nobat.R
import com.leon.be_nobat.data.local.TokenManager
import com.leon.be_nobat.domain.repository.BusinessRepository
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.DeepLinkHelper
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ContactUsActivity : BaseActivity() {

    override val layoutResourceId: Int = R.layout.activity_contact_us
    override val applyBottomInsetsOnRoot: Boolean = true

    private val tokenManager: TokenManager by inject()
    private val businessRepository: BusinessRepository by inject()
    private val authRepository: com.leon.be_nobat.domain.repository.AuthRepository by inject()

    private var defaultPhone = "02191002424"
    private var defaultMobile = "09134588221"
    private var defaultEmail = "support@benobat.ir"
    private var officeLat: Double? = 32.6546
    private var officeLng: Double? = 51.6680

    override fun observeViewModel() {
        // No shared state observation required
    }

    override fun setupViews() {
        setupMainHeader(getString(R.string.contact_us_title))

        val tvPhone = findViewById<TextView>(R.id.tvContactPhone)
        val tvMobile = findViewById<TextView>(R.id.tvContactMobile)
        val tvEmail = findViewById<TextView>(R.id.tvContactEmail)
        val tvAddress = findViewById<TextView>(R.id.tvContactAddress)

        val btnCallPhone = findViewById<MaterialButton>(R.id.btnCallPhone)
        val btnCopyPhone = findViewById<MaterialButton>(R.id.btnCopyPhone)
        val btnCallMobile = findViewById<MaterialButton>(R.id.btnCallMobile)
        val btnCopyMobile = findViewById<MaterialButton>(R.id.btnCopyMobile)
        val btnSendEmail = findViewById<MaterialButton>(R.id.btnSendEmail)
        val btnCopyEmail = findViewById<MaterialButton>(R.id.btnCopyEmail)
        val btnCopyAddress = findViewById<MaterialButton>(R.id.btnCopyAddress)
        val btnMapAddress = findViewById<MaterialButton>(R.id.btnMapAddress)
        val btnNavAddress = findViewById<MaterialButton>(R.id.btnNavAddress)

        val cardSuccess = findViewById<MaterialCardView>(R.id.cardSuccessAlert)
        val etName = findViewById<TextInputEditText>(R.id.etSenderName)
        val etContact = findViewById<TextInputEditText>(R.id.etSenderContact)
        val actvSubject = findViewById<AutoCompleteTextView>(R.id.actvSubject)
        val etMessage = findViewById<TextInputEditText>(R.id.etSenderMessage)
        val btnSend = findViewById<MaterialButton>(R.id.btnSendMessage)
        val pbSending = findViewById<ProgressBar>(R.id.pbSendingMessage)

        // Pre-fill user information if logged in
        lifecycleScope.launch {
            val sessionUser = tokenManager.currentSession()?.user
            if (sessionUser != null) {
                if (etName.text.isNullOrBlank()) {
                    etName.setText(sessionUser.displayName)
                }
                if (etContact.text.isNullOrBlank()) {
                    val phone = sessionUser.phoneNumber.orEmpty()
                    val email = sessionUser.email.orEmpty()
                    val contactInfo = phone.ifBlank { email }
                    etContact.setText(contactInfo)
                }
            }

            authRepository.getPlatformPricing().onSuccess { p ->
                if (p.address.isNotBlank()) tvAddress.text = p.address
                if (p.phone.isNotBlank()) {
                    defaultPhone = p.phone
                    tvPhone.text = p.phone
                }
                if (p.mobile.isNotBlank()) {
                    defaultMobile = p.mobile
                    tvMobile.text = p.mobile
                }
                if (p.email.isNotBlank()) {
                    defaultEmail = p.email
                    tvEmail.text = p.email
                }
                if (p.latitude != null) officeLat = p.latitude
                if (p.longitude != null) officeLng = p.longitude
            }
        }

        // Setup subject dropdown (localized for fa, en, ar)
        val subjects = listOf(
            getString(R.string.contact_subject_support),
            getString(R.string.contact_subject_business),
            getString(R.string.contact_subject_feedback),
            getString(R.string.contact_subject_bug),
            getString(R.string.contact_subject_finance)
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, subjects)
        actvSubject.setAdapter(adapter)
        actvSubject.setText(subjects[0], false)

        // Actions: Call, Mail, Copy, Map, Nav
        btnCallPhone.setOnClickListener {
            dialPhone(defaultPhone)
        }
        btnCopyPhone.setOnClickListener {
            DeepLinkHelper.copyToClipboard(this, tvPhone.text.toString(), getString(R.string.contact_phone_title))
        }

        btnCallMobile.setOnClickListener {
            dialPhone(defaultMobile)
        }
        btnCopyMobile.setOnClickListener {
            DeepLinkHelper.copyToClipboard(this, tvMobile.text.toString(), getString(R.string.contact_mobile_title))
        }

        btnSendEmail.setOnClickListener {
            sendEmail(defaultEmail)
        }
        btnCopyEmail.setOnClickListener {
            DeepLinkHelper.copyToClipboard(this, tvEmail.text.toString(), getString(R.string.contact_email_title))
        }

        btnCopyAddress.setOnClickListener {
            DeepLinkHelper.copyToClipboard(this, tvAddress.text.toString(), getString(R.string.contact_address_title))
        }

        btnMapAddress.setOnClickListener {
            val lat = officeLat
            val lng = officeLng
            if (lat != null && lng != null) {
                com.leon.be_nobat.helpers.MapHelper.showMapPreview(this, lat, lng, getString(R.string.contact_address_title), tvAddress.text.toString())
            } else {
                com.leon.be_nobat.helpers.MapHelper.openNavigation(this, null, null, getString(R.string.contact_address_title), tvAddress.text.toString())
            }
        }

        btnNavAddress.setOnClickListener {
            com.leon.be_nobat.helpers.MapHelper.openNavigation(this, officeLat, officeLng, getString(R.string.contact_address_title), tvAddress.text.toString())
        }

        // Send message form
        btnSend.setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            val contact = etContact.text?.toString()?.trim().orEmpty()
            val subject = actvSubject.text?.toString()?.trim().orEmpty()
            val message = etMessage.text?.toString()?.trim().orEmpty()

            if (name.isBlank() || contact.isBlank() || message.isBlank()) {
                Toast.makeText(this, R.string.contact_error_fill_fields, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSend.isEnabled = false
            btnSend.text = ""
            pbSending.isVisible = true

            lifecycleScope.launch {
                val result = businessRepository.sendContactMessage(
                    name = name,
                    contactInfo = contact,
                    subject = subject,
                    message = message
                )

                btnSend.isEnabled = true
                btnSend.setText(R.string.contact_btn_send)
                pbSending.isVisible = false

                result.fold(
                    onSuccess = {
                        cardSuccess.isVisible = true
                        etMessage.setText("")
                        Toast.makeText(this@ContactUsActivity, R.string.contact_send_success, Toast.LENGTH_LONG).show()
                    },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: getString(R.string.retry)
                        Toast.makeText(this@ContactUsActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun dialPhone(phone: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
            startActivity(intent)
        }.onFailure {
            DeepLinkHelper.copyToClipboard(this, phone, getString(R.string.contact_phone_title))
        }
    }

    private fun sendEmail(email: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$email")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, "پیام از اپلیکیشن به‌نوبت")
            }
            startActivity(intent)
        }.onFailure {
            DeepLinkHelper.copyToClipboard(this, email, getString(R.string.contact_email_title))
        }
    }
}
