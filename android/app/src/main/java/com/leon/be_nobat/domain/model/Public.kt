package com.leon.be_nobat.domain.model

import kotlinx.serialization.Serializable

/**
 * Public discovery DTOs.
 *
 * These mirror the wire shapes returned by `/api/v1/businesses`,
 * `/businesses/{id}`, `/businesses/{id}/branches`,
 * `/businesses/{id}/branches/{branchId}/services`,
 * `/businesses/{id}/branches/{branchId}/providers`, `/businesses/{id}/reviews`,
 * `/categories`, and `/catalog`.
 */

@Serializable
data class Business(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val category: String = "",
    val city: String = "",
    val description: String = "",
    val requiresApproval: Boolean = false,
    val branchCount: Int = 0,
    val serviceCount: Int = 0,
    val rating: Double? = null,
    val reviewCount: Int = 0,
)

@Serializable
data class BusinessDetail(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val category: String = "",
    val city: String = "",
    val description: String = "",
    val requiresApproval: Boolean = false,
    val branchCount: Int = 0,
    val serviceCount: Int = 0,
    val rating: Double? = null,
    val reviewCount: Int = 0,
    val branches: List<Branch> = emptyList(),
    val services: List<Service> = emptyList(),
    val reviews: List<PublicReview> = emptyList(),
)

@Serializable
data class Branch(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val address: String = "",
    val timeZoneId: String = "Asia/Tehran",
    val openHour: Int = 9,
    val closeHour: Int = 18,
)

@Serializable
data class Service(
    val id: String = "",
    val businessId: String = "",
    val name: String = "",
    val description: String = "",
    val durationMinutes: Int = 30,
    val price: Double = 0.0,
    val currency: String = "IRR",
)

@Serializable
data class Provider(
    val id: String = "",
    val branchId: String = "",
    val name: String = "",
    val kind: String = "staff",
    val userId: String? = null,
)

@Serializable
data class PublicReview(
    val id: String = "",
    val rating: Int = 0,
    val comment: String = "",
    val managerReply: String? = null,
    val customerName: String = "",
    val createdAt: String = "",
)

@Serializable
data class Category(
    val id: String = "",
    val name: String = "",
    val kind: String = "Business", // "Business" | "Service"
    val sortOrder: Int = 0,
)

@Serializable
data class ServiceCatalogItem(
    val id: String = "",
    val name: String = "",
    val slug: String = "",
    val category: String = "",
    val description: String = "",
    val suggestedDurationMinutes: Int = 30,
)
