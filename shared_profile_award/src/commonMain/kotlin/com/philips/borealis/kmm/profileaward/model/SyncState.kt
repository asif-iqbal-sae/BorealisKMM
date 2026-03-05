package com.philips.borealis.kmm.profileaward.model

import kotlinx.serialization.Serializable

@Serializable
enum class SyncState(val value: Int) {
    CREATED(0),
    SYNCED(1),
    UPDATED(2);

    companion object {
        fun fromInt(value: Int): SyncState = entries.firstOrNull { it.value == value } ?: CREATED
    }
}
