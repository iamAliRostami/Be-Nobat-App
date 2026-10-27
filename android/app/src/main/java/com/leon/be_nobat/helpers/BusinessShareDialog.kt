package com.leon.be_nobat.helpers

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.leon.be_nobat.R

object BusinessShareDialog {

    fun show(
        context: Context,
        businessId: String,
        businessName: String,
        branchId: String? = null,
        branchName: String? = null,
    ) {
        val link = DeepLinkHelper.createBusinessWebLink(businessId, branchId)
        val subtitle = if (!branchName.isNullOrBlank()) {
            "$businessName • $branchName"
        } else {
            businessName
        }
        val title = if (!branchName.isNullOrBlank()) {
            "اشتراک و بارکد شعبه"
        } else {
            context.getString(R.string.share_booking_link_title)
        }
        val shareMessage = if (!branchName.isNullOrBlank()) {
            context.getString(R.string.share_link_message_branch, businessName, branchName)
        } else {
            context.getString(R.string.share_link_message_business, businessName)
        }

        displayDialog(context, title, subtitle, link, shareMessage)
    }

    fun showForBranch(
        context: Context,
        branchId: String,
        branchName: String,
        businessName: String = "",
    ) {
        val link = DeepLinkHelper.createBranchWebLink(branchId)
        val subtitle = if (businessName.isNotBlank()) "$businessName • $branchName" else branchName
        val title = "اشتراک و بارکد شعبه"
        val shareMessage = "نوبت‌دهی آنلاین در $branchName:\n$link"

        displayDialog(context, title, subtitle, link, shareMessage)
    }

    fun showForProvider(
        context: Context,
        providerId: String,
        providerName: String,
        businessName: String = "",
        branchName: String = "",
    ) {
        val link = DeepLinkHelper.createProviderWebLink(providerId)
        val subtitle = listOfNotNull(
            providerName.takeIf { it.isNotBlank() },
            branchName.takeIf { it.isNotBlank() },
            businessName.takeIf { it.isNotBlank() }
        ).joinToString(" • ")

        val title = "اشتراک و بارکد ارائه‌دهنده"
        val shareMessage = "رزرو مستقیم نوبت با $providerName:\n$link"

        displayDialog(context, title, subtitle, link, shareMessage)
    }

    private fun displayDialog(
        context: Context,
        title: String,
        subtitle: String,
        link: String,
        shareMessage: String,
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_business_share, null)

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvShareTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvShareSubtitle)
        val ivQrCode = dialogView.findViewById<ImageView>(R.id.ivQrCode)
        val tvUrl = dialogView.findViewById<TextView>(R.id.tvUrlDisplay)
        val btnCopyInline = dialogView.findViewById<ImageButton>(R.id.btnCopyInline)
        val btnCopy = dialogView.findViewById<MaterialButton>(R.id.btnCopyLink)
        val btnShare = dialogView.findViewById<MaterialButton>(R.id.btnShareLink)

        tvTitle.text = title
        tvSubtitle.text = subtitle
        tvUrl.text = link

        // Generate real scannable QR Code bitmap
        val qrBitmap = QrCodeHelper.generateQrBitmap(link, size = 512)
        if (qrBitmap != null) {
            ivQrCode.setImageBitmap(qrBitmap)
            ivQrCode.imageTintList = null // Clear vector tint so the QR code is crisp B&W
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .create()

        val copyAction = {
            DeepLinkHelper.copyToClipboard(context, link)
            Toast.makeText(context, R.string.link_copied_success, Toast.LENGTH_SHORT).show()
        }

        btnCopyInline.setOnClickListener { copyAction() }
        btnCopy.setOnClickListener {
            copyAction()
            dialog.dismiss()
        }

        btnShare.setOnClickListener {
            DeepLinkHelper.shareLink(context, title, shareMessage, link)
            dialog.dismiss()
        }

        dialog.show()
    }
}
