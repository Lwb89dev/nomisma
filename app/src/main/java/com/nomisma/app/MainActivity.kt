package com.nomisma.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nomisma.app.nostr.AmberSigner
import com.nomisma.app.ui.NomismaApp

class MainActivity : ComponentActivity() {
    private lateinit var amberSigner: AmberSigner
    private var incomingPairingCode by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        amberSigner = AmberSigner(this)
        capturePairingIntent(intent)
        setContent {
            NomismaApp(
                signer = amberSigner,
                incomingPairingCode = incomingPairingCode,
                onIncomingPairingCodeConsumed = { incomingPairingCode = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        capturePairingIntent(intent)
    }

    private fun capturePairingIntent(intent: Intent?) {
        incomingPairingCode = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.takeIf { it.scheme.equals("nomisma1", ignoreCase = true) }
            ?.toString()
    }
}
