package com.philips.borealis.kmm.profileaward.model

import kotlinx.serialization.Serializable

@Serializable
data class Award(
    val uuid: String,
    val awardDescription: String? = null,
    val badge: String? = null,
    val consumable: Boolean? = null,
    val criteria: String? = null,
    val inventoryAwarded: Int? = null,
    val name: String? = null,
    val trackingId: String? = null,
    val type: String? = null,
    val value: Int? = null,
    val unlocked: Boolean? = null,
    val repeatable: String? = null
)
