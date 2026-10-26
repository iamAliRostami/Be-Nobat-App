package com.leon.be_nobat.helpers

import android.content.Context
import android.view.LayoutInflater
import android.widget.ImageButton
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
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_business_share, null)

        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvShareSubtitle)
        val tvUrl = dialogView.findViewById<TextView>(R.id.tvUrlDisplay)
        val btnCopyInline = dialogView.findViewById<ImageButton>(R.id.btnCopyInline)
        val btnCopy = dialogView.findViewById<MaterialButton>(R.id.btnCopyLink)
        val btnShare = dialogView.findViewById<MaterialButton>(R.id.btnShareLink)

        val subtitleText = if (!branchName.isNullOrBlank()) {
            "$businessName • $branchName"
        } else {
            businessName
        }
        tvSubtitle.text = subtitleText
        tvUrl.text = link

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
            val shareTitle = context.getString(R.string.share_booking_link_title)
            val shareText = if (!branchName.isNullOrBlank()) {
                context.getString(R.string.share_link_message_branch, businessName, branchName)
            } else {
                context.getString(R.string.share_link_message_business, businessName)
            }
            DeepLinkHelper.shareLink(context, shareTitle, shareText, link)
            dialog.dismiss()
        }

        dialog.show()
    }
}
