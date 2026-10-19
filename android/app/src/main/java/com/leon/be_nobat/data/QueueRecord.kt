package com.leon.be_nobat.data

import kotlinx.serialization.Serializable

@Serializable
data class QueueRecord(
    val id: String,
    /*val collectionId: String,
    val customerName: String,
    val status: String,*/
    val title: String
    // بقیه فیلدهایی که در PocketBase تعریف کرده‌اید
)