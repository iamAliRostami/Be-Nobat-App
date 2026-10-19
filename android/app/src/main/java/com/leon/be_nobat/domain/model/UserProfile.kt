package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    @SerialName("user_id") val userId: String,
    @SerialName("nation_code") val nationCode: String?,
    @SerialName("birth_date") val birthDate: String?,
    val gender: String
) : BaseDomain()