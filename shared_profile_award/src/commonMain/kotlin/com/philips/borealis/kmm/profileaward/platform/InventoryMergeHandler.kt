package com.philips.borealis.kmm.profileaward.platform

import com.philips.borealis.kmm.profileaward.model.AwardInventoryItem

interface InventoryMergeHandler {
    fun mergeInventoryItems(items: Map<String, AwardInventoryItem>)
}
