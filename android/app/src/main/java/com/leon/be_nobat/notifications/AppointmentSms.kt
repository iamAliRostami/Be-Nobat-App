package com.leon.be_nobat.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.leon.be_nobat.BuildConfig
import com.leon.be_nobat.domain.model.ManagementAppointment
import com.leon.be_nobat.helpers.AppToast

object AppointmentSms {
    fun send(context: Context, appointment: ManagementAppointment): String = DeviceSms.send(
        context, appointment.customerPhoneNumber, AppointmentMessages.body(appointment), "وضعیت ذخیره شد")
}

/** Reusable SIM transport. Sensitive message bodies are never logged or persisted here. */
object DeviceSms {
    fun send(context: Context, phoneNumber: String?, body: String, successPrefix: String): String {
        if (BuildConfig.DEMO_MODE) return "$successPrefix؛ در نسخهٔ دمو پیامک واقعی ارسال نمی‌شود."
        val phone = AppointmentMessages.phone(phoneNumber)
            ?: return "$successPrefix؛ شمارهٔ کاربر برای ارسال پیامک معتبر نیست."
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            return "$successPrefix؛ مجوز پیامک داده نشد و پیامک ارسال نشد."
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            return "$successPrefix؛ این دستگاه امکان ارسال پیامک ندارد."
        val subscription = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (!SubscriptionManager.isValidSubscriptionId(subscription))
            return "$successPrefix؛ سیم‌کارت پیش‌فرض پیامک را در تنظیمات گوشی انتخاب کنید."
        return try {
            @Suppress("DEPRECATION")
            val manager = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscription)
                else SmsManager.getSmsManagerForSubscriptionId(subscription)
            val parts = manager.divideMessage(body)
            val sent = ArrayList(parts.indices.map { index ->
                val intent = Intent(context, SmsSentReceiver::class.java).apply {
                    action = "${context.packageName}.SMS_SENT"
                    data = android.net.Uri.parse("benobat-sms://${java.util.UUID.randomUUID()}/$index")
                }
                PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            })
            manager.sendMultipartTextMessage(phone, null, parts, sent, null)
            "$successPrefix؛ پیامک برای ارسال به سیم‌کارت تحویل داده شد."
        } catch (_: Exception) { "$successPrefix؛ ارسال پیامک انجام نشد. سیم‌کارت و مجوز پیامک را بررسی کنید." }
    }
}

/** A modem failure must not turn a successful status update into a failed booking operation. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (resultCode != android.app.Activity.RESULT_OK)
            AppToast.show(context, "ارسال بخشی از پیامک ناموفق بود؛ آنتن و اعتبار سیم‌کارت را بررسی کنید.")
    }
}
