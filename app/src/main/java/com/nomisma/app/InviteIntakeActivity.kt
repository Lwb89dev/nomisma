package com.nomisma.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.nomisma.app.nostr.PairingInviteText
import com.nomisma.app.ui.l10n.UiText
import java.util.Locale

/**
 * Invisible entry point for "Share" and the text-selection menu. It pulls the
 * pairing code out of the text and hands it to [MainActivity] like a link
 * would, so there is a single task and a single import path.
 */
class InviteIntakeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val code = PairingInviteText.find(sharedText(intent))
        if (code == null) {
            Toast.makeText(
                this,
                UiText.translate("Nessun codice invito Nomisma trovato nel testo", Locale.getDefault().language),
                Toast.LENGTH_LONG
            ).show()
        } else {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
                    .setData(Uri.parse(code))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        finish()
    }

    private fun sharedText(intent: Intent): CharSequence? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        else -> null
    }
}
