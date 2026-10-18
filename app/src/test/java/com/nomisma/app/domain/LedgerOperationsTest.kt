package com.nomisma.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerOperationsTest {
    @Test
    fun sequencePolicyAcceptsOnlyTheNextContiguousOperation() {
        assertEquals(SequenceDisposition.STALE, OperationSequencePolicy.classify(4L, 4L))
        assertEquals(SequenceDisposition.STALE, OperationSequencePolicy.classify(4L, 2L))
        assertEquals(SequenceDisposition.EXPECTED, OperationSequencePolicy.classify(4L, 5L))
        assertEquals(SequenceDisposition.DEFER, OperationSequencePolicy.classify(4L, 7L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun sequencePolicyRejectsInvalidIncomingSequence() {
        OperationSequencePolicy.classify(currentSequence = 0L, incomingSequence = 0L)
    }

    @Test
    fun operationWireValuesRemainUniqueIncludingSharedProfileSettings() {
        assertEquals(OperationType.entries.size, OperationType.entries.map(OperationType::wireValue).distinct().size)
        assertEquals("ledger.metadata.set", OperationType.LEDGER_METADATA_SET.wireValue)
        assertEquals("participant.name.set", OperationType.PARTICIPANT_NAME_SET.wireValue)
    }

    @Test
    fun wireParserRejectsOversizedOperationsBeforeJsonParsing() {
        val oversized = "x".repeat(65_537)

        assertTrue(runCatching { LedgerOperation.fromWireJson(oversized) }.isFailure)
    }

    @Test
    fun pairingAdoptionOnlyDiscardsConfiguration() {
        val configuration = listOf(
            OperationType.THEME_SET,
            OperationType.PARTICIPANT_NAME_SET,
            OperationType.PARTICIPANT_COLOR_SET,
            OperationType.PARTICIPANT_THEME_IDENTITY_SET,
            OperationType.LEDGER_METADATA_SET,
            OperationType.LEDGER_CAPACITY_SET
        ).map(OperationType::wireValue)
        assertTrue(PairingAdoptionPolicy.blocking(configuration).isEmpty())

        val shared = listOf(
            OperationType.ENTRY_CREATE,
            OperationType.ENTRY_UPDATE,
            OperationType.ENTRY_DELETE,
            OperationType.CATEGORY_CREATE,
            OperationType.TAG_CREATE,
            OperationType.SETTLEMENT_ACCEPT
        ).map(OperationType::wireValue)
        assertEquals(shared, PairingAdoptionPolicy.blocking(configuration + shared + shared))
    }

    @Test
    fun pairingAdoptionTreatsUnknownOperationTypesAsBlocking() {
        assertEquals(listOf("future.type"), PairingAdoptionPolicy.blocking(listOf("theme.set", "future.type")))
        assertTrue(PairingAdoptionPolicy.blocking(emptyList()).isEmpty())
    }
}
