package com.leon.be_nobat.helpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.leon.be_nobat.R

/**
 * Reusable builder for "create / edit entity" dialogs used across admin/platform
 * screens. Caller declares a list of fields (label + default + optional input type
 * or dropdown options) and receives back the entered/selected values when user taps
 * "submit".
 */
class AdminFormDialog private constructor() {

    data class DropdownOption(
        val value: String,
        val label: String,
    )

    data class Field(
        val label: String,
        val default: String? = null,
        val inputType: Int? = null,
        val helperText: String? = null,
        val options: List<DropdownOption>? = null,
    )

    class Builder(private val context: Context) {
        private var title: String = ""
        private val fields = mutableListOf<Field>()
        private var onSubmit: ((List<String>) -> Unit)? = null

        fun setTitle(title: String) = apply { this.title = title }

        fun field(label: String, default: String? = null, inputType: Int? = null, helperText: String? = null) =
            apply { fields.add(Field(label, default, inputType, helperText)) }

        /** Convenience for a numeric field. */
        fun numberField(label: String, default: String? = null) = apply {
            fields.add(Field(label, default, android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL))
        }

        /** Dropdown with value and label pairs. */
        fun dropdownField(
            label: String,
            options: List<DropdownOption>,
            default: String? = null,
            helperText: String? = null,
        ) = apply {
            fields.add(Field(label, default, null, helperText, options))
        }

        /** Dropdown with Pair<String, String> (value to label). */
        fun dropdownFieldPairs(
            label: String,
            options: List<Pair<String, String>>,
            default: String? = null,
            helperText: String? = null,
        ) = apply {
            fields.add(Field(label, default, null, helperText, options.map { DropdownOption(it.first, it.second) }))
        }

        /** Dropdown where value and label are identical strings. */
        @JvmName("dropdownFieldStrings")
        fun dropdownField(
            label: String,
            options: List<String>,
            default: String? = null,
            helperText: String? = null,
        ) = apply {
            fields.add(Field(label, default, null, helperText, options.map { DropdownOption(it, it) }))
        }

        fun onSubmit(block: (List<String>) -> Unit) = apply { onSubmit = block }

        fun show() {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_admin_form, null)
            val titleView = view.findViewById<TextView>(R.id.tvFormTitle)
            titleView.text = title

            val layouts = listOf(
                R.id.field0Layout to R.id.field0,
                R.id.field1Layout to R.id.field1,
                R.id.field2Layout to R.id.field2,
                R.id.field3Layout to R.id.field3,
                R.id.field4Layout to R.id.field4,
                R.id.field5Layout to R.id.field5,
            )

            val selectedDropdownValues = arrayOfNulls<String>(fields.size)

            fields.forEachIndexed { index, fieldDef ->
                if (index >= layouts.size) return@forEachIndexed
                val (layoutId, editId) = layouts[index]
                val layout = view.findViewById<TextInputLayout>(layoutId)
                val edit = view.findViewById<TextInputEditText>(editId)
                layout.hint = fieldDef.label
                fieldDef.helperText?.let { layout.helperText = it }

                if (fieldDef.options != null && fieldDef.options.isNotEmpty()) {
                    // Dropdown / Selection picker
                    val initialVal = fieldDef.default ?: fieldDef.options.first().value
                    selectedDropdownValues[index] = initialVal
                    val initialLabel = fieldDef.options.firstOrNull { it.value == initialVal }?.label ?: initialVal
                    edit.setText(initialLabel)

                    edit.isFocusable = false
                    edit.isFocusableInTouchMode = false
                    edit.isClickable = true
                    edit.isCursorVisible = false
                    edit.keyListener = null

                    layout.endIconMode = TextInputLayout.END_ICON_CUSTOM
                    layout.setEndIconDrawable(R.drawable.ic_arrow_drop_down)

                    val openPicker = {
                        val labels = fieldDef.options.map { it.label }.toTypedArray()
                        val currentIdx = fieldDef.options.indexOfFirst { it.value == selectedDropdownValues[index] }
                        MaterialAlertDialogBuilder(context)
                            .setTitle(fieldDef.label)
                            .setSingleChoiceItems(labels, if (currentIdx >= 0) currentIdx else 0) { dialog, which ->
                                val chosen = fieldDef.options[which]
                                selectedDropdownValues[index] = chosen.value
                                edit.setText(chosen.label)
                                dialog.dismiss()
                            }
                            .setNegativeButton(R.string.action_close, null)
                            .show()
                    }

                    edit.setOnClickListener { openPicker() }
                    layout.setEndIconOnClickListener { openPicker() }
                } else {
                    fieldDef.default?.let { edit.setText(it) }
                    fieldDef.inputType?.let { edit.inputType = it }
                }
                layout.visibility = View.VISIBLE
            }

            // Hide unused slots
            for (i in fields.size until layouts.size) {
                view.findViewById<TextInputLayout>(layouts[i].first).visibility = View.GONE
            }

            MaterialAlertDialogBuilder(context)
                .setView(view)
                .setPositiveButton(R.string.save) { _, _ ->
                    val values = fields.indices.map { idx ->
                        if (fields[idx].options != null) {
                            selectedDropdownValues[idx] ?: ""
                        } else {
                            val editId = layouts[idx].second
                            view.findViewById<TextInputEditText>(editId).text?.toString()?.trim() ?: ""
                        }
                    }
                    onSubmit?.invoke(values)
                }
                .setNegativeButton(R.string.action_close, null)
                .show()
        }
    }
}
