package com.leon.be_nobat.helpers

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.CancellationPolicy

class CancellationNoticeEditor @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val titleView: TextView
    private val inherit: MaterialCheckBox
    private val box: TextInputLayout
    private val minutes: TextInputEditText
    private val hintView: TextView

    init {
        orientation = VERTICAL
        LayoutInflater.from(context).inflate(R.layout.view_cancellation_notice_editor, this, true)

        titleView = findViewById(R.id.tvCancellationTitle)
        inherit = findViewById(R.id.cbInherit)
        box = findViewById(R.id.tilCancellationMinutes)
        minutes = findViewById(R.id.etCancellationMinutes)
        hintView = findViewById(R.id.tvCancellationHint)

        inherit.setOnCheckedChangeListener { _, checked ->
            minutes.isEnabled = !checked
            box.error = null
        }
        setValue(null)
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
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
