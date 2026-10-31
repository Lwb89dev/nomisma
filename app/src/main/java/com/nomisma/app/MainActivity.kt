package com.nomisma.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.nomisma.app.nostr.AmberSigner
import com.nomisma.app.nostr.PairingInviteCodec
import com.nomisma.app.nostr.PairingInviteText
import com.nomisma.app.security.securePreferences
import com.nomisma.app.ui.NomismaApp

class MainActivity : ComponentActivity() {
    private lateinit var amberSigner: AmberSigner
    private var incomingPairingCode by mutableStateOf<String?>(null)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        amberSigner = AmberSigner(this)
        requestNotificationPermission()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android only lets the focused app read the clipboard.
        if (hasFocus) captureClipboardInvite()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun capturePairingIntent(intent: Intent?) {
        incomingPairingCode = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.takeIf { it.scheme.equals("nomisma1", ignoreCase = true) }
            ?.toString()
    }

    /**
     * A message copied from a chat is picked up the next time Nomisma is in front.
     * Each clip is looked at once, and our own code or an already imported one is skipped.
     */
    private fun captureClipboardInvite() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val description = clipboard.primaryClipDescription ?: return
        if (!description.hasMimeType("text/*")) return
        val stamps = getSharedPreferences(CLIPBOARD_STATE, Context.MODE_PRIVATE)
        if (description.timestamp <= stamps.getLong(KEY_LAST_CLIP_STAMP, 0L)) return
        stamps.edit().putLong(KEY_LAST_CLIP_STAMP, description.timestamp).apply()

        val code = PairingInviteText.find(
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)
        ) ?: return
        val sender = runCatching { PairingInviteCodec.decode(code).senderPubkey }.getOrNull() ?: return
        val alreadyPaired = securePreferences("nomisma_sync").getString("recipient_pubkey", null)
        if (sender == amberSigner.knownPubkey || sender == alreadyPaired) return
        incomingPairingCode = code
    }

    private companion object {
        const val CLIPBOARD_STATE = "nomisma_clipboard"
        const val KEY_LAST_CLIP_STAMP = "last_clip_stamp"
    }
}
