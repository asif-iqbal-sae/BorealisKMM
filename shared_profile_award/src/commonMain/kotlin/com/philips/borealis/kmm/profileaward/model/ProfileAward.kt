package com.philips.borealis.kmm.profileaward.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfileAward(
    val uuid: String,
    val award: String? = null,
    val dateTimeAwarded: String? = null,
    val dateTimeAwardedLocal: String? = null,
    val notified: Boolean = false,
    val profileUuid: String,
    val awardLevelValue: Int = 0,
    val momentId: String? = null,
    val momentVersion: Int = 0,
    val momentCreatedDate: String? = null,
    val momentLastModifiedDate: String? = null,
    val syncState: SyncState = SyncState.CREATED
)
