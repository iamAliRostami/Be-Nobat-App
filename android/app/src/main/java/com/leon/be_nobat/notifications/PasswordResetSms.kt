package com.leon.be_nobat.notifications

/** Kept only in ViewModel memory while asking for SMS permission; never saved in a Bundle or file. */
class PasswordResetSms(val userId: String, val displayName: String, val phoneNumber: String?,
    val email: String?, val password: String) {
    fun body(): String = buildString {
        append("به‌نوبت\n")
        if (displayName.isNotBlank()) append("$displayName عزیز\n")
        if (!email.isNullOrBlank()) append("نام کاربری: $email\n")
        append("رمز موقت ورود شما: $password\n")
        append("پس از ورود، رمز عبور خود را تغییر دهید.")
    }
    override fun toString(): String = "PasswordResetSms(redacted)"
}
