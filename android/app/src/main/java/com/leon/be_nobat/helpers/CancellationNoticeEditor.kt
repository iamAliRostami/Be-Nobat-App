package com.leon.be_nobat.helpers

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import android.text.InputType
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.domain.CancellationPolicy

class CancellationNoticeEditor @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val inherit = MaterialCheckBox(context).apply { text = "ارث‌بری از تنظیم بالاتر" }
    private val box = TextInputLayout(context).apply { hint = "فاصلهٔ لازم تا شروع نوبت (دقیقه)" }
    private val minutes = TextInputEditText(box.context).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText("120") }
    init {
        orientation = VERTICAL
        addView(TextView(context).apply { text = "مهلت لغو نوبت"; textSize = 16f })
        addView(inherit); box.addView(minutes); addView(box)
        addView(TextView(context).apply { text = "پیش‌فرض سامانه: ۱۲۰ دقیقه (۲ ساعت). صفر: لغو تا پیش از شروع نوبت." })
        inherit.setOnCheckedChangeListener { _, checked -> minutes.isEnabled = !checked; box.error = null }
        setValue(null)
    }
    fun setValue(value: Int?, allowInheritance: Boolean = true) {
        inherit.visibility = if (allowInheritance) VISIBLE else GONE
        inherit.isChecked = allowInheritance && value == null
        minutes.setText((value ?: 120).toString())
        minutes.isEnabled = !inherit.isChecked
    }
    private fun parsed(): Int? = minutes.text?.toString()?.map { c -> when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c
    } }?.joinToString("")?.trim()?.toIntOrNull()
    val value: Int? get() = if (inherit.visibility == VISIBLE && inherit.isChecked) null else parsed()
    fun validate(): String? {
        val error = if (inherit.visibility == VISIBLE && inherit.isChecked) null
            else if (parsed() == null || !CancellationPolicy.valid(parsed())) "مهلت لغو باید بین صفر و ۱۲۹۶۰۰ دقیقه باشد." else null
        box.error = error
        return error
    }
}
