package com.nomisma.app.nostr

import com.nomisma.app.data.OperationStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDiagnosticsTest {
    private val localKey = "ab".repeat(32)
    private val remoteKey = "cd".repeat(32)
    private val ready = PairingReadiness(true, true, true, true, true)

    @Test
    fun healthDistinguishesLocalReadyWorkingAndHealthyStates() {
        val local = SyncHealthEvaluator.evaluate(NostrSessionState(), PairingReadiness(false, false, false, true, true))
        assertEquals(SyncHealthLevel.LOCAL_ONLY, local.level)

        val readyState = SyncHealthEvaluator.evaluate(
            NostrSessionState(operationStats = OperationStats(2, 0, 0, 0, 0)),
            ready
        )
        assertEquals(SyncHealthLevel.READY, readyState.level)
        assertTrue(readyState.detail.contains("2"))

        val working = SyncHealthEvaluator.evaluate(
            NostrSessionState(status = SyncConnectionStatus.CONNECTED, backlogComplete = false),
            ready
        )
        assertEquals(SyncHealthLevel.WORKING, working.level)

        val healthy = SyncHealthEvaluator.evaluate(
            NostrSessionState(status = SyncConnectionStatus.CONNECTED, backlogComplete = true),
            ready
        )
        assertEquals(SyncHealthLevel.HEALTHY, healthy.level)
    }

    @Test
    fun errorsAndRejectedOperationsHavePriority() {
        val withError = NostrSessionState(
            status = SyncConnectionStatus.CONNECTED,
            backlogComplete = true,
            error = "relay failed",
            operationStats = OperationStats(0, 2, 3, 0, 0)
        )
        assertEquals(SyncHealthLevel.ATTENTION, SyncHealthEvaluator.evaluate(withError, ready).level)

        val rejected = NostrSessionState(
            operationStats = OperationStats(0, 0, 0, 0, 1)
        )
        assertEquals(SyncHealthLevel.ATTENTION, SyncHealthEvaluator.evaluate(rejected, ready).level)
    }

    @Test
    fun diagnosticsRedactFullKeysAndNeverContainLedgerContent() {
        val state = NostrSessionState(
            identity = SignerIdentity(localKey, "com.example.amber"),
            operationStats = OperationStats(1, 2, 3, 4, 5),
            error = "prima riga\nseconda riga"
        )
        val report = SyncDiagnosticsFormatter.format(
            state,
            ready,
            "wss://relay.example.test/private-path",
            remoteKey
        )

        assertFalse(report.contains(localKey))
        assertFalse(report.contains(remoteKey))
        assertTrue(report.contains("abababab…abababab"))
        assertTrue(report.contains("cdcdcdcd…cdcdcdcd"))
        assertTrue(report.contains("relay=wss://relay.example.test"))
        assertFalse(report.contains("private-path"))
        assertTrue(report.contains("errore=presente"))
        assertFalse(report.contains("prima riga"))
    }
}
