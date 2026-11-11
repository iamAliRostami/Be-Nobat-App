package com.leon.be_nobat.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.leon.be_nobat.R
import com.leon.be_nobat.domain.model.ManagementBranch
import com.leon.be_nobat.domain.model.ReceiptUploadInput
import com.leon.be_nobat.domain.repository.ManagementRepository
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

object BranchPaymentReceiptHelper {

    fun showReceiptDialog(context: Context, branch: ManagementBranch) {
        val iv = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (320 * context.resources.displayMetrics.density).toInt()
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        var imageLoaded = false
        val imageUrl = branch.receiptImageUrl
        if (!imageUrl.isNullOrBlank() && imageUrl.startsWith("data:")) {
            try {
                val commaIdx = imageUrl.indexOf(',')
                if (commaIdx >= 0) {
                    val raw = imageUrl.substring(commaIdx + 1)
                    val bytes = Base64.decode(raw, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        iv.setImageBitmap(bmp)
                        imageLoaded = true
                    }
                }
            } catch (_: Exception) {}
        }

        if (!imageLoaded) {
            iv.setImageResource(R.drawable.ic_document)
        }

        val trackInfo = if (!branch.receiptTrackingCode.isNullOrBlank()) {
            "\nکد پیگیری: ${PersianUtils.toPersianDigits(branch.receiptTrackingCode)}"
        } else ""

        val feeInfo = if ((branch.fee ?: 0.0) > 0) {
            "\n${context.getString(R.string.branch_fee_display, PersianUtils.formatPrice(branch.fee!!, "تومان"))}"
        } else ""

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.branch_view_receipt)
            .setMessage("${context.getString(R.string.branch_dialog_receipt_title, branch.name)}$feeInfo$trackInfo")
            .setView(iv)
            .setPositiveButton(R.string.action_close, null)
            .show()
    }

    fun showPaymentAndUploadDialog(
        activity: AppCompatActivity,
        branch: ManagementBranch,
        managementRepository: ManagementRepository,
        pickImageLauncher: ActivityResultLauncher<String>,
        onSelectedUriCallbackSetter: ((Uri) -> Unit) -> Unit,
        onReceiptUploaded: (ManagementBranch) -> Unit,
        onDismiss: () -> Unit = {},
    ) {
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_branch_payment_receipt, null)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvPaymentSubtitle)
        val tvPaymentFee = dialogView.findViewById<TextView>(R.id.tvPaymentFee)
        val tvCardNumber = dialogView.findViewById<TextView>(R.id.tvCardNumber)
        val btnCopyCard = dialogView.findViewById<ImageButton>(R.id.btnCopyCard)
        val layoutIban = dialogView.findViewById<View>(R.id.layoutIban)
        val tvIban = dialogView.findViewById<TextView>(R.id.tvIban)
        val btnCopyIban = dialogView.findViewById<ImageButton>(R.id.btnCopyIban)
        val tvAccountHolder = dialogView.findViewById<TextView>(R.id.tvAccountHolder)
        val tvPaymentInstructions = dialogView.findViewById<TextView>(R.id.tvPaymentInstructions)
        val etTrackingCode = dialogView.findViewById<TextInputEditText>(R.id.etTrackingCode)
        val btnSelectReceiptImage = dialogView.findViewById<MaterialButton>(R.id.btnSelectReceiptImage)
        val cardReceiptPreview = dialogView.findViewById<MaterialCardView>(R.id.cardReceiptPreview)
        val ivReceiptPreview = dialogView.findViewById<ImageView>(R.id.ivReceiptPreview)
        val btnRemoveReceiptImage = dialogView.findViewById<ImageButton>(R.id.btnRemoveReceiptImage)
        val pbUploading = dialogView.findViewById<ProgressBar>(R.id.pbUploading)
        val btnSubmitReceipt = dialogView.findViewById<MaterialButton>(R.id.btnSubmitReceipt)
        val btnCancelReceipt = dialogView.findViewById<MaterialButton>(R.id.btnCancelReceipt)

        tvSubtitle.text = branch.name
        if (!branch.receiptTrackingCode.isNullOrBlank()) {
            etTrackingCode.setText(branch.receiptTrackingCode)
        }

        var selectedBase64: String? = null
        var selectedContentType = "image/jpeg"

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .setOnDismissListener { onDismiss() }
            .create()

        // Load Platform Pricing
        activity.lifecycleScope.launch {
            managementRepository.getPlatformPricing().onSuccess { pricing ->
                val feeToPay = branch.fee?.takeIf { it > 0 } ?: pricing.extraBranchPrice
                tvPaymentFee.text = activity.getString(R.string.branch_payment_fee_label, PersianUtils.formatPrice(feeToPay, "تومان"))

                val cardStr = pricing.cardNumber.trim()
                if (cardStr.isNotBlank()) {
                    tvCardNumber.text = activity.getString(R.string.business_request_bank_card_label, PersianUtils.toPersianDigits(cardStr))
                    btnCopyCard.setOnClickListener {
                        DeepLinkHelper.copyToClipboard(activity, cardStr)
                        Toast.makeText(activity, R.string.link_copied_success, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    tvCardNumber.isVisible = false
                    btnCopyCard.isVisible = false
                }

                val ibanStr = pricing.iban.trim()
                if (ibanStr.isNotBlank()) {
                    layoutIban.isVisible = true
                    tvIban.text = activity.getString(R.string.business_request_bank_iban_label, ibanStr, pricing.accountHolder)
                    btnCopyIban.setOnClickListener {
                        DeepLinkHelper.copyToClipboard(activity, ibanStr)
                        Toast.makeText(activity, R.string.link_copied_success, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    layoutIban.isVisible = false
                }

                tvAccountHolder.text = "${activity.getString(R.string.platform_pricing_holder_label)}: ${pricing.accountHolder}"
                tvPaymentInstructions.text = pricing.paymentInstructions
            }.onFailure {
                val fallbackFee = branch.fee ?: 200_000.0
                tvPaymentFee.text = activity.getString(R.string.branch_payment_fee_label, PersianUtils.formatPrice(fallbackFee, "تومان"))
            }
        }

        // Handle image selection callback from activity launcher
        onSelectedUriCallbackSetter { uri ->
            try {
                activity.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        val maxDim = 1200
                        val scaled = if (bmp.width > maxDim || bmp.height > maxDim) {
                            val ratio = bmp.width.toFloat() / bmp.height.toFloat()
                            val (w, h) = if (ratio > 1) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                            Bitmap.createScaledBitmap(bmp, w, h, true)
                        } else bmp

                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                        val bytes = baos.toByteArray()
                        selectedBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        selectedContentType = "image/jpeg"

                        ivReceiptPreview.setImageBitmap(scaled)
                        cardReceiptPreview.isVisible = true
                        btnSelectReceiptImage.text = activity.getString(R.string.business_request_receipt_change_btn)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(activity, "خطا در بارگذاری تصویر: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectReceiptImage.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnRemoveReceiptImage.setOnClickListener {
            selectedBase64 = null
            cardReceiptPreview.isVisible = false
            btnSelectReceiptImage.text = activity.getString(R.string.business_request_receipt_upload_btn)
        }

        btnCancelReceipt.setOnClickListener {
            dialog.dismiss()
        }

        btnSubmitReceipt.setOnClickListener {
            if (selectedBase64.isNullOrBlank()) {
                Toast.makeText(activity, R.string.branch_receipt_image_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            pbUploading.isVisible = true
            btnSubmitReceipt.isEnabled = false
            btnCancelReceipt.isEnabled = false

            val tracking = etTrackingCode.text?.toString()?.trim()
            val input = ReceiptUploadInput(
                trackingCode = tracking?.takeIf { it.isNotBlank() },
                contentType = selectedContentType,
                dataBase64 = selectedBase64!!,
                imageBase64 = selectedBase64!!
            )

            activity.lifecycleScope.launch {
                managementRepository.uploadBranchReceipt(branch.id, input).onSuccess { updated ->
                    pbUploading.isVisible = false
                    Toast.makeText(activity, R.string.branch_receipt_success, Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    onReceiptUploaded(updated)
                }.onFailure { err ->
                    pbUploading.isVisible = false
                    btnSubmitReceipt.isEnabled = true
                    btnCancelReceipt.isEnabled = true
                    Toast.makeText(activity, err.message ?: "خطا در ارسال فیش", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }
}
