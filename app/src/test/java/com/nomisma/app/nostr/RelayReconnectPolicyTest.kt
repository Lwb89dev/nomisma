package com.nomisma.app.nostr

import org.junit.Assert.assertEquals
import org.junit.Test

class RelayReconnectPolicyTest {
    @Test
    fun reconnectDelayUsesCappedExponentialBackoff() {
        assertEquals(1_000L, RelayReconnectPolicy.delayMillis(1))
        assertEquals(2_000L, RelayReconnectPolicy.delayMillis(2))
        assertEquals(16_000L, RelayReconnectPolicy.delayMillis(5))
        assertEquals(30_000L, RelayReconnectPolicy.delayMillis(6))
        assertEquals(30_000L, RelayReconnectPolicy.delayMillis(100))
    }
}
