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
}
