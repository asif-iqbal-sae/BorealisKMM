package com.philips.borealis.kmm.profileaward.model

data class ProfileAwardMerge(
    // ProfileAward fields
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
    val syncState: SyncState = SyncState.CREATED,
    // Award fields
    val awardUuid: String? = null,
    val awardDescription: String? = null,
    val badge: String? = null,
    val consumable: Boolean? = null,
    val criteria: String? = null,
    val inventoryAwarded: Int? = null,
    val awardName: String? = null,
    val trackingId: String? = null,
    val type: String? = null,
    val value: Int? = null,
    val unlocked: Boolean? = null,
    val repeatable: String? = null
)
