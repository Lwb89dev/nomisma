package com.nomisma.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nomisma.app.nostr.Nip19
import com.nomisma.app.nostr.NostrProfile
import com.nomisma.app.nostr.NostrProfileSearch
import com.nomisma.app.security.SecurePreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private val Verified = Color(0xFFB8F36B)
private val Caution = Color(0xFFFFD180)
private val Problem = Color(0xFFFF8A80)

private sealed interface SearchState {
    data object Idle : SearchState
    data object Searching : SearchState
    data class Found(val profiles: List<NostrProfile>) : SearchState
    data object Failed : SearchState
    data object OwnKey : SearchState
}

private const val SEARCH_DEBOUNCE_MILLIS = 450L

/** The other participant, picked by profile name rather than by pasting a key. */
@Composable
internal fun RecipientPicker(
    recipientPubkey: String,
    recipientLabel: String?,
    localPubkey: String?,
    enabled: Boolean,
    onSelected: (NostrProfile) -> Unit,
    onCleared: () -> Unit
) {
    val search = remember { NostrProfileSearch() }
    val selectedKey = recipientPubkey.trim().lowercase().takeIf(::isHexKey)
    if (selectedKey != null) {
        SelectedRecipient(search, selectedKey, recipientLabel, enabled, onSelected, onCleared)
    } else {
        RecipientSearch(search, localPubkey, enabled, onSelected)
    }
}

@Composable
private fun SelectedRecipient(
    search: NostrProfileSearch,
    pubkey: String,
    storedLabel: String?,
    enabled: Boolean,
    onProfileResolved: (NostrProfile) -> Unit,
    onCleared: () -> Unit
) {
    var profile by remember(pubkey) { mutableStateOf<NostrProfile?>(null) }
    LaunchedEffect(pubkey) {
        profile = try {
            search.lookup(pubkey)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            null
        }
        profile?.takeIf { it.name != null || it.displayName != null }?.let(onProfileResolved)
    }
    val label = profile?.label ?: storedLabel
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelLight),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(t("Destinatario"), color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(label ?: t("Profilo sconosciuto"), color = Ink, fontWeight = FontWeight.SemiBold)
                profile?.let { Nip05Line(it) }
                Text(shortNpub(pubkey), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(enabled = enabled, onClick = onCleared) { Text(t("Cambia")) }
        }
    }
}

@Composable
private fun RecipientSearch(
    search: NostrProfileSearch,
    localPubkey: String?,
    enabled: Boolean,
    onSelected: (NostrProfile) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    LaunchedEffect(query) {
        val text = query.trim()
        if (text.length < NostrProfileSearch.MIN_QUERY_LENGTH) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        pastedKey(text)?.let { key ->
            if (key == localPubkey?.lowercase()) {
                state = SearchState.OwnKey
            } else {
                query = ""
                onSelected(NostrProfile(key, null, null, null, 0L))
            }
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MILLIS)
        state = SearchState.Searching
        state = try {
            SearchState.Found(search.search(text, exclude = setOfNotNull(localPubkey?.lowercase())))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            SearchState.Failed
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(200) },
            label = { Text(t("Cerca il destinatario")) },
            placeholder = { Text(t("Nome profilo, nome@dominio o npub")) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        when (val current = state) {
            SearchState.Idle -> Hint(t("La ricerca interroga relay pubblici. Puoi anche incollare la npub."))
            SearchState.Searching -> Hint(t("Ricerca in corso…"))
            SearchState.Failed -> Hint(t("Ricerca non riuscita. Controlla la connessione e riprova."), Problem)
            SearchState.OwnKey -> Hint(t("Questa è la tua chiave: scegli l'altro partecipante."), Problem)
            is SearchState.Found -> if (current.profiles.isEmpty()) {
                Hint(t("Nessun profilo trovato. Prova con nome@dominio oppure incolla la npub."))
            } else {
                current.profiles.forEach { profile ->
                    ProfileRow(profile) {
                        query = ""
                        state = SearchState.Idle
                        onSelected(profile)
                    }
                }
                Hint(t("Più profili possono avere lo stesso nome: scegli quello verificato e confronta la npub con l'altra persona."), Caution)
            }
        }
    }
}

@Composable
private fun ProfileRow(profile: NostrProfile, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelLight),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(profile.label, color = Ink, fontWeight = FontWeight.SemiBold)
            Nip05Line(profile)
            Text(shortNpub(profile.pubkey), color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Nip05Line(profile: NostrProfile) {
    val identifier = profile.nip05 ?: return
    if (profile.nip05Verified) {
        Text("✓ $identifier · ${t("verificato")}", color = Verified, style = MaterialTheme.typography.bodySmall)
    } else {
        Text("$identifier · ${t("non verificato")}", color = Caution, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Hint(text: String, color: Color = Muted) {
    Text(text, color = color, style = MaterialTheme.typography.bodySmall)
}

private fun isHexKey(value: String) = value.length == 64 && value.all { it in "0123456789abcdef" }

private fun pastedKey(text: String): String? =
    Nip19.npubToHex(text) ?: text.lowercase().takeIf(::isHexKey)

private fun shortNpub(pubkey: String): String =
    Nip19.hexToNpub(pubkey).let { "${it.take(12)}…${it.takeLast(8)}" }

private const val LABEL_SEPARATOR = '|'

/** The label is stored with its key, so a label can never be shown next to a different key. */
internal fun SecurePreferences.recipientLabelFor(pubkey: String): String? =
    getString(KEY_RECIPIENT_LABEL, null)
        ?.takeIf { it.startsWith("$pubkey$LABEL_SEPARATOR") }
        ?.substringAfter(LABEL_SEPARATOR)
        ?.takeIf(String::isNotBlank)

internal fun SecurePreferences.saveRecipientLabel(pubkey: String, label: String?) {
    edit().putString(KEY_RECIPIENT_LABEL, label?.let { "$pubkey$LABEL_SEPARATOR$it" }).apply()
}

internal const val KEY_RECIPIENT_LABEL = "recipient_label"
