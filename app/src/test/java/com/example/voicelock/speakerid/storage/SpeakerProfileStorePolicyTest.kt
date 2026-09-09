package com.example.voicelock.speakerid.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SpeakerProfileStorePolicyTest {
    @Test
    fun normalizesValidProfileNames() {
        assertEquals("Ada Lovelace", SpeakerProfileStore.requireValidName("  Ada   Lovelace  "))
    }

    @Test
    fun rejectsEmptyAndOverlongNames() {
        assertFailure<ProfileStoreException.InvalidName> { SpeakerProfileStore.requireValidName("   ") }
        assertFailure<ProfileStoreException.InvalidName> { SpeakerProfileStore.requireValidName("x".repeat(33)) }
    }

    @Test
    fun rejectsDuplicateNamesIgnoringCase() {
        val profiles = listOf(SpeakerProfile("1", "Owner", floatArrayOf(1f), 0))
        assertFailure<ProfileStoreException.DuplicateName> { SpeakerProfileStore.requireUniqueName(profiles, "owner") }
        SpeakerProfileStore.requireUniqueName(profiles, "owner", excludingId = "1")
    }

    @Test
    fun rejectsEleventhProfile() {
        SpeakerProfileStore.requireCapacity(9)
        assertFailure<ProfileStoreException.CapacityReached> { SpeakerProfileStore.requireCapacity(10) }
    }

    private inline fun <reified T : Throwable> assertFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected ${T::class.simpleName}")
        } catch (error: Throwable) {
            if (error !is T) throw error
        }
    }
}
