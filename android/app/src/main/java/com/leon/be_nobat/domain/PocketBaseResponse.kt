package com.leon.be_nobat.domain

import kotlinx.serialization.Serializable

@Serializable
data class PocketBaseResponse<T>(
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
    val items: List<T> // لیست اصلی نوبت‌های شما اینجا قرار دارد
)