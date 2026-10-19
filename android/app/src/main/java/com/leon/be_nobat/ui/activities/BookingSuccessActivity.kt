package com.leon.be_nobat.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.leon.be_nobat.R
import com.leon.be_nobat.helpers.AppToast
import com.leon.be_nobat.helpers.BaseActivity
import com.leon.be_nobat.helpers.PersianUtils

class BookingSuccessActivity : BaseActivity() {
    override val layoutResourceId: Int = R.layout.activity_booking_success

    override fun setupViews() {
        setupAdminHeader(getString(R.string.booking_confirmed_title))

        val trackingCode = intent.getStringExtra(EXTRA_TRACKING_CODE).orEmpty()
        val services = intent.getStringExtra(EXTRA_SERVICES).orEmpty()
        val provider = intent.getStringExtra(EXTRA_PROVIDER).orEmpty()
        val rawTime = intent.getStringExtra(EXTRA_TIME).orEmpty()
        val time = if (rawTime.contains("T") && !rawTime.contains("ساعت") && !rawTime.contains("at")) {
            val dateDisplay = com.leon.be_nobat.helpers.JalaliDateHelper.formatIsoToJalaliDisplay(rawTime.take(10))
            val timeDisplay = com.leon.be_nobat.helpers.JalaliDateHelper.formatIsoTime(rawTime)
            val atSeparator = if (com.leon.be_nobat.helpers.PersianUtils.isEnglishLocale()) "at" else "ساعت"
            if (timeDisplay.isNotBlank()) "$dateDisplay • $atSeparator $timeDisplay" else dateDisplay
        } else {
            rawTime
        }
        val businessName = intent.getStringExtra(EXTRA_BUSINESS_NAME).orEmpty()
        val price = intent.getDoubleExtra(EXTRA_PRICE, 0.0)
        val currency = intent.getStringExtra(EXTRA_CURRENCY) ?: "IRR"

        findViewById<TextView>(R.id.tvTrackingCode).text = PersianUtils.toPersianDigits(trackingCode)
        findViewById<TextView>(R.id.tvServices).text = services
        findViewById<TextView>(R.id.tvProvider).text = provider.ifBlank { getString(R.string.any_provider) }
        findViewById<TextView>(R.id.tvTime).text = time

        val rowBusiness = findViewById<View>(R.id.rowBusiness)
        if (businessName.isNotBlank()) {
            rowBusiness.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvBusiness).text = businessName
        } else {
            rowBusiness.visibility = View.GONE
        }

        val rowPrice = findViewById<View>(R.id.rowPrice)
        if (price > 0.0) {
            rowPrice.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tvPrice).text = PersianUtils.formatPrice(price, currency)
        } else {
            rowPrice.visibility = View.GONE
        }

        val copyAction = View.OnClickListener {
            if (trackingCode.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("tracking_code", trackingCode)
                clipboard.setPrimaryClip(clip)
                AppToast.show(this, getString(R.string.tracking_code_copied))
            }
        }
        findViewById<View>(R.id.btnCopyTracking).setOnClickListener(copyAction)
        findViewById<View>(R.id.rowTrackingCode).setOnClickListener(copyAction)

        val navigateToAppointments = {
            val nextIntent = Intent(this, AppointmentsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(nextIntent)
            finish()
        }

        findViewById<View>(R.id.btnBack).setOnClickListener {
            navigateToAppointments()
        }

        findViewById<View>(R.id.btnViewAppointments).setOnClickListener {
            navigateToAppointments()
        }

        findViewById<View>(R.id.btnGoHome).setOnClickListener {
            val homeIntent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToAppointments()
            }
        })
    }

    override fun observeViewModel() {
        // No ViewModel observation required for static confirmation display
    }

    companion object {
        const val EXTRA_TRACKING_CODE = "extra_tracking_code"
        const val EXTRA_SERVICES = "extra_services"
        const val EXTRA_PROVIDER = "extra_provider"
        const val EXTRA_TIME = "extra_time"
        const val EXTRA_BUSINESS_NAME = "extra_business_name"
        const val EXTRA_PRICE = "extra_price"
        const val EXTRA_CURRENCY = "extra_currency"
    }
}
