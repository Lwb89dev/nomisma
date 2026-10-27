package com.nomisma.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomisma.app.nostr.LedgerCheckState
import com.nomisma.app.nostr.LedgerCheckStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val CheckVerified = Color(0xFFB8F36B)
private val CheckWorking = Color(0xFFFFD180)
private val CheckProblem = Color(0xFFFF8A80)

private fun LedgerCheckStatus.accent(): Color = when (this) {
    LedgerCheckStatus.VERIFIED -> CheckVerified
    LedgerCheckStatus.CHECKING, LedgerCheckStatus.REPAIRING -> CheckWorking
    LedgerCheckStatus.DIVERGED -> CheckProblem
    LedgerCheckStatus.IDLE -> Muted
}

private fun LedgerCheckStatus.title(): String = when (this) {
    LedgerCheckStatus.VERIFIED -> "Saldi verificati con l'altro dispositivo"
    LedgerCheckStatus.CHECKING -> "Verifica dei saldi in corso"
    LedgerCheckStatus.REPAIRING -> "Riallineamento: operazioni mancanti reinviate"
    LedgerCheckStatus.DIVERGED -> "I saldi non coincidono con l'altro dispositivo"
    LedgerCheckStatus.IDLE -> "La verifica dei saldi parte quando entrambi i dispositivi sono connessi"
}

private fun Long.clockTime(): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

/** One line under the balance on Home: status, shared fingerprint, time of the last agreement. */
@Composable
internal fun LedgerCheckLine(check: LedgerCheckState) {
    if (check.status == LedgerCheckStatus.IDLE) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp)
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(check.status.accent()))
        Text(
            listOfNotNull(
                t(check.status.title()),
                check.fingerprint,
                check.checkedAtMillis?.takeIf { check.status == LedgerCheckStatus.VERIFIED }?.clockTime()
            ).joinToString(" · "),
            color = Muted,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/** Settings view of the same check, with the fingerprint to compare by eye and any detail. */
@Composable
internal fun LedgerCheckCard(check: LedgerCheckState) {
    val accent = check.status.accent()
    Card(
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.09f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(11.dp).clip(CircleShape).background(accent))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t(check.status.title()), color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                check.fingerprint?.let {
                    Text(
                        "${t("Impronta del ledger")}: $it" +
                            (check.checkedAtMillis?.let { time -> " · ${time.clockTime()}" } ?: ""),
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                check.detail?.let { Text(it, color = Muted, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
