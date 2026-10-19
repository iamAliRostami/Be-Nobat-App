package com.leon.be_nobat.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BranchMembership(
    @SerialName("branch_id") val branchId: String?,
    @SerialName("user_id") val userId: String?,
    val roles: String?,
    val status: String
) : BaseDomain()