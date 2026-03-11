package com.philips.borealis.kmm.profileaward.platform

interface AwardJsonProvider {
    /** Returns the display-award catalog JSON (awards.json — ~54 entries). */
    fun loadAwardsJson(): String?

    /**
     * Returns the full UUID→type lookup JSON (awards_uuids.json — ~363 entries).
     * Used only for classifying award types during de-duplication (not stored in DB).
     * Default returns null so existing implementations are not broken.
     */
    fun loadAwardUuidsLookupJson(): String? = null
}
