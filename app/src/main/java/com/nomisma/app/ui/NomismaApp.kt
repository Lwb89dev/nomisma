package com.nomisma.app.ui

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nomisma.app.data.LocalLedgerRepository
import com.nomisma.app.data.ExpenseEntryPreferences
import com.nomisma.app.data.SavedFilterStore
import com.nomisma.app.domain.AnalyticsSnapshot
import com.nomisma.app.domain.AggregationPeriod
import com.nomisma.app.domain.BalanceSummary
import com.nomisma.app.domain.Expense
import com.nomisma.app.domain.ExpenseCategory
import com.nomisma.app.domain.ExpenseCategories
import com.nomisma.app.domain.ExpenseFilterDraft
import com.nomisma.app.domain.ExpenseParser
import com.nomisma.app.domain.ExpensePolicy
import com.nomisma.app.domain.LedgerSnapshot
import com.nomisma.app.domain.Participant
import com.nomisma.app.domain.PeriodComparison
import com.nomisma.app.domain.TimeRangePreset
import com.nomisma.app.domain.TagDefinition
import com.nomisma.app.domain.SavedExpenseFilter
import com.nomisma.app.domain.SettlementCycle
import com.nomisma.app.domain.SettlementMethod
import com.nomisma.app.domain.SettlementPolicy
import com.nomisma.app.domain.SupportedCurrencies
import com.nomisma.app.domain.formatMinorUnits
import com.nomisma.app.nostr.NostrSessionState
import com.nomisma.app.nostr.PairingInvite
import com.nomisma.app.nostr.PairingInviteCodec
import com.nomisma.app.nostr.PairingReadiness
import com.nomisma.app.nostr.PairingReadinessEvaluator
import com.nomisma.app.nostr.RelayTransportPolicy
import com.nomisma.app.nostr.Signer
import com.nomisma.app.nostr.SyncDiagnosticsFormatter
import com.nomisma.app.nostr.SyncConfiguration
import com.nomisma.app.nostr.SyncConnectionStatus
import com.nomisma.app.nostr.SyncHealthEvaluator
import com.nomisma.app.nostr.SyncHealthLevel
import com.nomisma.app.nostr.SyncHealthSummary
import com.nomisma.app.nostr.pubkeyFingerprint
import com.nomisma.app.theme.ThemeFinancialState
import com.nomisma.app.theme.ThemeIdentityRegistry
import com.nomisma.app.theme.ParticipantColorPalette
import com.nomisma.app.theme.ThemeRegistry
import com.nomisma.app.ui.theme.NomismaThemeScene
import com.nomisma.app.ui.theme.ThemeMotionPreference
import com.nomisma.app.ui.theme.ThemeQualityPreference
import java.util.Locale
import java.time.ZoneId
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Night = Color(0xFF090B11)
private val Panel = Color(0xFF151923)
private val PanelLight = Color(0xFF202634)
private val Ink = Color(0xFFF4F6FA)
private val Muted = Color(0xFF9BA5B7)
private const val DEFAULT_RELAY_URL = "ws://192.168.1.10:7777"
private const val APP_PREFERENCES = "nomisma_app"
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete_v1"

@Composable
fun NomismaApp(
    signer: Signer? = null,
    incomingPairingCode: String? = null,
    onIncomingPairingCodeConsumed: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { LocalLedgerRepository(context.applicationContext) }
    val viewModel: NomismaViewModel = viewModel(factory = NomismaViewModel.Factory(repository, signer))
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val busy by viewModel.isBusy.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val visualPreferences = remember {
        context.getSharedPreferences(VISUAL_PREFERENCES, Context.MODE_PRIVATE)
    }
    val appPreferences = remember {
        context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
    }
    val syncPreferences = remember {
        context.getSharedPreferences("nomisma_sync", Context.MODE_PRIVATE)
    }
    var qualityPreference by rememberSaveable {
        mutableStateOf(ThemeQualityPreference.fromStorage(visualPreferences.getString(KEY_VISUAL_QUALITY, null)))
    }
    var motionPreference by rememberSaveable {
        mutableStateOf(ThemeMotionPreference.fromStorage(visualPreferences.getString(KEY_VISUAL_MOTION, null)))
    }
    var autoConnectAttempted by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(AppTab.HOME) }
    var showAddExpense by rememberSaveable { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var onboardingDecisionMade by rememberSaveable { mutableStateOf(false) }
    var showOnboarding by rememberSaveable { mutableStateOf(false) }
    var onboardingStep by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(snapshot.id, incomingPairingCode) {
        if (!onboardingDecisionMade && snapshot.id.isNotBlank()) {
            val alreadyConfigured = snapshot.activeExpenses.isNotEmpty() ||
                syncPreferences.getString(KEY_RECIPIENT_PUBKEY, null)?.isNotBlank() == true
            val completed = appPreferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
            val openedFromPairing = !incomingPairingCode.isNullOrBlank()
            showOnboarding = !completed && !alreadyConfigured && !openedFromPairing
            if ((alreadyConfigured || openedFromPairing) && !completed) {
                appPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
            }
            onboardingDecisionMade = true
        }
    }

    LaunchedEffect(snapshot.id, incomingPairingCode) {
        if (snapshot.id.isNotBlank() && !incomingPairingCode.isNullOrBlank()) {
            showOnboarding = false
            onboardingStep = 0
            tab = AppTab.SETTINGS
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(snapshot.id, snapshot.participants) {
        if (!autoConnectAttempted && snapshot.participants.size >= 2) {
            autoConnectAttempted = true
            val preferences = context.getSharedPreferences("nomisma_sync", Context.MODE_PRIVATE)
            if (preferences.getBoolean(KEY_AUTO_CONNECT, false)) {
                val localId = preferences.getString(KEY_LOCAL_PARTICIPANT, snapshot.participants[0].id)
                    ?: snapshot.participants[0].id
                val remoteId = preferences.getString(KEY_REMOTE_PARTICIPANT, snapshot.participants[1].id)
                    ?: snapshot.participants[1].id
                viewModel.connectSync(
                    SyncConfiguration(
                        relayUrl = preferences.getString(KEY_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL,
                        recipientPubkey = preferences.getString(KEY_RECIPIENT_PUBKEY, "").orEmpty(),
                        localParticipantId = localId,
                        remoteParticipantId = remoteId
                    )
                )
            }
        }
    }

    fun finishOnboarding(openSettings: Boolean) {
        appPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, true).apply()
        showOnboarding = false
        onboardingStep = 0
        if (openSettings) tab = AppTab.SETTINGS
    }

    NomismaTheme {
        when {
            snapshot.id.isBlank() -> AppLoadingScreen()
            showOnboarding -> NomismaOnboarding(
                step = onboardingStep,
                identityPubkey = syncState.identity?.pubkey,
                identityError = syncState.error,
                isVerifyingIdentity = syncState.isVerifyingIdentity,
                signerAvailable = signer != null,
                onStepChange = { onboardingStep = it.coerceIn(0, 2) },
                onVerifyIdentity = viewModel::verifySignerIdentity,
                onFinish = ::finishOnboarding,
                onSkip = { finishOnboarding(openSettings = false) }
            )
            else -> Scaffold(
            containerColor = Night,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddExpense = true },
                    containerColor = Color(0xFFB8F36B),
                    contentColor = Color(0xFF10140E),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .semantics { contentDescription = "Aggiungi spesa" }
                ) { Text("+") }
            },
            bottomBar = {
                NomismaNavigation(tab = tab, onTabSelected = { tab = it })
            }
        ) { insets ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(insets)
            ) {
                when (tab) {
                    AppTab.HOME -> HomeScreen(
                        snapshot = snapshot,
                        syncState = syncState,
                        qualityPreference = qualityPreference,
                        motionPreference = motionPreference,
                        onThemeSelected = { themeId ->
                            viewModel.selectTheme(themeId, localOperationAuthor(context, snapshot))
                        },
                        onSettle = { method, note ->
                            viewModel.settleCurrentCycle(localOperationAuthor(context, snapshot), method, note)
                        },
                        onEditExpense = { editingExpense = it },
                        onPublishPending = viewModel::publishPendingOperations,
                        onOpenSyncSettings = { tab = AppTab.SETTINGS }
                    )
                    AppTab.INSIGHTS -> InsightsScreen(snapshot)
                    AppTab.SETTLE -> SettleScreen(snapshot) { method, note ->
                        viewModel.settleCurrentCycle(localOperationAuthor(context, snapshot), method, note)
                    }
                    AppTab.HISTORY -> HistoryScreen(snapshot, onEdit = { editingExpense = it })
                    AppTab.SETTINGS -> SettingsScreen(
                        signer = signer,
                        snapshot = snapshot,
                        syncState = syncState,
                        qualityPreference = qualityPreference,
                        motionPreference = motionPreference,
                        onQualityPreference = { preference ->
                            qualityPreference = preference
                            visualPreferences.edit().putString(KEY_VISUAL_QUALITY, preference.name).apply()
                        },
                        onMotionPreference = { preference ->
                            motionPreference = preference
                            visualPreferences.edit().putString(KEY_VISUAL_MOTION, preference.name).apply()
                        },
                        onConnect = viewModel::connectSync,
                        onDisconnect = viewModel::disconnectSync,
                        onPublishPending = viewModel::publishPendingOperations,
                        onVerifyIdentity = viewModel::verifySignerIdentity,
                        onCreateTag = { name ->
                            viewModel.createTag(name, localOperationAuthor(context, snapshot))
                        },
                        onRenameTag = { tagId, name ->
                            viewModel.renameTag(tagId, name, localOperationAuthor(context, snapshot))
                        },
                        onArchiveTag = { tagId ->
                            viewModel.archiveTag(tagId, localOperationAuthor(context, snapshot))
                        },
                        onChangeCapacity = { capacityMinor ->
                            viewModel.changeCapacity(capacityMinor, localOperationAuthor(context, snapshot))
                        },
                        onChangeLedgerMetadata = { title, currency ->
                            viewModel.changeLedgerMetadata(title, currency, localOperationAuthor(context, snapshot))
                        },
                        onCreateCategory = { name ->
                            viewModel.createCategory(name, localOperationAuthor(context, snapshot))
                        },
                        onRenameCategory = { categoryId, name ->
                            viewModel.renameCategory(categoryId, name, localOperationAuthor(context, snapshot))
                        },
                        onArchiveCategory = { categoryId ->
                            viewModel.archiveCategory(categoryId, localOperationAuthor(context, snapshot))
                        },
                        onParticipantIdentity = { participantId, identityId ->
                            viewModel.changeParticipantThemeIdentity(
                                participantId,
                                identityId,
                                localOperationAuthor(context, snapshot)
                            )
                        },
                        onParticipantColor = { participantId, colorHex ->
                            viewModel.changeParticipantColor(
                                participantId,
                                colorHex,
                                localOperationAuthor(context, snapshot)
                            )
                        },
                        onParticipantName = { participantId, name ->
                            viewModel.changeParticipantName(
                                participantId,
                                name,
                                localOperationAuthor(context, snapshot)
                            )
                        },
                        onThemeSelected = { themeId ->
                            viewModel.selectTheme(themeId, localOperationAuthor(context, snapshot))
                        },
                        onReplayOnboarding = {
                            onboardingStep = 0
                            showOnboarding = true
                        },
                        onAdoptPairingLedger = viewModel::adoptPairingLedger,
                        incomingPairingCode = incomingPairingCode,
                        onIncomingPairingCodeConsumed = onIncomingPairingCodeConsumed
                    )
                }
                if (busy) {
                    Surface(
                        color = Color(0xEE202634),
                        shape = CircleShape,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp, color = Color(0xFFB8F36B))
                            Text("Salvataggio…", color = Ink, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        }
    }

    if (showAddExpense) {
        ExpenseEditorDialog(
            snapshot = snapshot,
            expense = null,
            onDismiss = { showAddExpense = false },
            onSave = { amount, description, category, tags, merchant, notes, occurredAt, author ->
                viewModel.addExpense(
                    amount,
                    description,
                    category,
                    tags,
                    merchant,
                    notes,
                    occurredAt,
                    author,
                    localOperationAuthor(context, snapshot)
                )
                showAddExpense = false
            },
            onDelete = null
        )
    }

    editingExpense?.let { expense ->
        ExpenseEditorDialog(
            snapshot = snapshot,
            expense = expense,
            onDismiss = { editingExpense = null },
            onSave = { amount, description, category, tags, merchant, notes, occurredAt, author ->
                viewModel.updateExpense(
                    expense,
                    amount,
                    description,
                    category,
                    tags,
                    merchant,
                    notes,
                    occurredAt,
                    author,
                    localOperationAuthor(context, snapshot)
                )
                editingExpense = null
            },
            onDelete = {
                viewModel.deleteExpense(expense, localOperationAuthor(context, snapshot))
                editingExpense = null
            }
        )
    }
}

private fun localOperationAuthor(context: Context, snapshot: LedgerSnapshot): String {
    val fallback = snapshot.participants.firstOrNull()?.id ?: "alice"
    return context.getSharedPreferences("nomisma_sync", Context.MODE_PRIVATE)
        .getString(KEY_LOCAL_PARTICIPANT, fallback)
        ?.takeIf { candidate -> snapshot.participants.any { it.id == candidate } }
        ?: fallback
}

private fun canonicalTagInput(raw: String): String = raw
    .trim()
    .removePrefix("#")
    .lowercase()
    .replace(Regex("\\s+"), "-")

private enum class AppTab { HOME, INSIGHTS, SETTLE, HISTORY, SETTINGS }

@Composable
private fun NomismaNavigation(tab: AppTab, onTabSelected: (AppTab) -> Unit) {
    NavigationBar(containerColor = Color(0xFF10131B)) {
        listOf(
            AppTab.HOME to "Home",
            AppTab.INSIGHTS to "Analisi",
            AppTab.SETTLE to "Saldo",
            AppTab.HISTORY to "Storico",
            AppTab.SETTINGS to "Impostazioni"
        ).forEach { (item, label) ->
            NavigationBarItem(
                selected = tab == item,
                onClick = { onTabSelected(item) },
                icon = { NavigationGlyph(item, tab == item) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun NavigationGlyph(item: AppTab, selected: Boolean) {
    val color = if (selected) Color(0xFFB8F36B) else Muted
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        when (item) {
            AppTab.HOME -> {
                val vessel = Path().apply {
                    moveTo(size.width * 0.38f, size.height * 0.16f)
                    lineTo(size.width * 0.40f, size.height * 0.34f)
                    cubicTo(size.width * 0.22f, size.height * 0.43f, size.width * 0.22f, size.height * 0.83f, size.width * 0.50f, size.height * 0.86f)
                    cubicTo(size.width * 0.78f, size.height * 0.83f, size.width * 0.78f, size.height * 0.43f, size.width * 0.60f, size.height * 0.34f)
                    lineTo(size.width * 0.62f, size.height * 0.16f)
                }
                drawPath(vessel, color, style = stroke)
                drawLine(color.copy(alpha = 0.7f), Offset(size.width * 0.32f, size.height * 0.64f), Offset(size.width * 0.68f, size.height * 0.64f), 1.5.dp.toPx(), StrokeCap.Round)
            }
            AppTab.INSIGHTS -> {
                repeat(3) { index ->
                    val barHeight = size.height * (0.25f + index * 0.16f)
                    drawRoundRect(
                        color.copy(alpha = 0.62f + index * 0.16f),
                        Offset(size.width * (0.18f + index * 0.24f), size.height * 0.82f - barHeight),
                        Size(size.width * 0.14f, barHeight),
                        CornerRadius(2.dp.toPx())
                    )
                }
            }
            AppTab.SETTLE -> {
                drawArc(color, 205f, 245f, false, Offset(size.width * 0.16f, size.height * 0.16f), Size(size.width * 0.68f, size.height * 0.68f), style = stroke)
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.47f), Offset(size.width * 0.12f, size.height * 0.30f), 1.9.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.47f), Offset(size.width * 0.33f, size.height * 0.41f), 1.9.dp.toPx(), StrokeCap.Round)
            }
            AppTab.HISTORY -> {
                drawLine(color.copy(alpha = 0.56f), Offset(size.width * 0.28f, size.height * 0.16f), Offset(size.width * 0.28f, size.height * 0.84f), 1.5.dp.toPx())
                repeat(3) { index ->
                    val y = size.height * (0.24f + index * 0.25f)
                    drawCircle(color, 2.7.dp.toPx(), Offset(size.width * 0.28f, y))
                    drawLine(color.copy(alpha = 0.82f), Offset(size.width * 0.45f, y), Offset(size.width * (0.80f - index * 0.06f), y), 1.8.dp.toPx(), StrokeCap.Round)
                }
            }
            AppTab.SETTINGS -> {
                drawCircle(color, size.minDimension * 0.18f, center)
                drawCircle(color, size.minDimension * 0.38f, center, style = stroke)
                repeat(8) { index ->
                    val angle = index * PI.toFloat() / 4f
                    val start = center + Offset(cos(angle) * size.minDimension * 0.38f, sin(angle) * size.minDimension * 0.38f)
                    val end = center + Offset(cos(angle) * size.minDimension * 0.47f, sin(angle) * size.minDimension * 0.47f)
                    drawLine(color, start, end, 2.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun AppLoadingScreen() {
    Box(Modifier.fillMaxSize().background(Night), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            CircularProgressIndicator(color = Color(0xFFB8F36B), strokeWidth = 2.dp)
            Text("Apertura del ledger…", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NomismaOnboarding(
    step: Int,
    identityPubkey: String?,
    identityError: String?,
    isVerifyingIdentity: Boolean,
    signerAvailable: Boolean,
    onStepChange: (Int) -> Unit,
    onVerifyIdentity: () -> Unit,
    onFinish: (Boolean) -> Unit,
    onSkip: () -> Unit
) {
    val titles = listOf(
        "Il vostro denaro, raccontato insieme",
        "Privato e locale per impostazione",
        "Collega i due dispositivi"
    )
    val descriptions = listOf(
        "Nomisma unisce un ledger condiviso, analisi finanziarie e scene che reagiscono alle vostre spese.",
        "Ogni operazione viene salvata prima sul dispositivo. Amber custodisce la chiave Nostr e firma senza consegnarla a Nomisma.",
        "Ogni persona verifica Amber e scambia il proprio codice. L'importazione configura i ruoli, ma la connessione parte solo quando la confermi."
    )
    BackHandler { if (step > 0) onStepChange(step - 1) else onSkip() }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF111720), Night, Color(0xFF07090D))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("NOMISMA", color = Ink, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            TextButton(onClick = onSkip) { Text("Salta") }
        }
        OnboardingArtwork(step)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .width(if (index == step) 28.dp else 8.dp)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(if (index == step) Color(0xFFB8F36B) else PanelLight)
                )
            }
        }
        Text(titles[step], color = Ink, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
        Text(descriptions[step], color = Muted, style = MaterialTheme.typography.bodyLarge)

        when (step) {
            0 -> {
                OnboardingFeature("Ledger event-based", "Spese, modifiche e settlement restano ricostruibili.")
                OnboardingFeature("Tre scene condivise", "Vessel, Woodland e Mempool rappresentano gli stessi dati.")
                OnboardingFeature("Analisi locali", "Filtri e grafici non interrogano il relay.")
            }
            1 -> {
                OnboardingFeature("Local-first", "Puoi registrare spese anche senza rete.")
                OnboardingFeature("Cifratura NIP-44/NIP-59", "Il contenuto finanziario non finisce nei tag pubblici.")
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Identità Amber", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text(
                            identityPubkey?.let { "Verificata · ${pubkeyFingerprint(it)}" }
                                ?: "Puoi collegarla ora oppure continuare in modalità locale.",
                            color = if (identityPubkey == null) Muted else Color(0xFFB8F36B),
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(enabled = signerAvailable && !isVerifyingIdentity, onClick = onVerifyIdentity) {
                            Text(
                                when {
                                    isVerifyingIdentity -> "Apertura di Amber…"
                                    identityPubkey == null -> "Verifica con Amber"
                                    else -> "Verifica di nuovo"
                                }
                            )
                        }
                        identityError?.let { Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall) }
                        if (!signerAvailable) Text("Amber non disponibile su questo dispositivo", color = Color(0xFFFFD180), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            else -> {
                OnboardingFeature("1 · Verifica Amber", "Ogni dispositivo ottiene la propria chiave pubblica.")
                OnboardingFeature("2 · Scambia i codici", "Il codice non contiene spese né chiavi private.")
                OnboardingFeature("3 · Controlla e connetti", "Confronta l'impronta e avvia manualmente il relay.")
            }
        }

        if (step < 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) {
                    OutlinedButton(onClick = { onStepChange(step - 1) }, modifier = Modifier.weight(1f)) { Text("Indietro") }
                }
                Button(onClick = { onStepChange(step + 1) }, modifier = Modifier.weight(1f)) { Text("Continua") }
            }
        } else {
            Button(onClick = { onFinish(true) }, modifier = Modifier.fillMaxWidth()) { Text("Configura sincronizzazione") }
            OutlinedButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth()) { Text("Inizia in modalità locale") }
        }
        Text(
            "Puoi riaprire questa guida dalle Impostazioni.",
            color = Muted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun OnboardingFeature(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 6.dp).size(9.dp).clip(CircleShape).background(Color(0xFFB8F36B))
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OnboardingArtwork(step: Int) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(210.dp)
            .semantics {
                contentDescription = when (step) {
                    0 -> "Due partecipanti contribuiscono a un contenitore condiviso"
                    1 -> "Ledger locale protetto e identità custodita da Amber"
                    else -> "Due dispositivi collegati attraverso un relay cifrato"
                }
            }
    ) {
        drawRoundRect(
            Brush.linearGradient(listOf(Color(0xFF182330), Color(0xFF0B0E14))),
            cornerRadius = CornerRadius(30.dp.toPx())
        )
        val green = Color(0xFF38E66B)
        val blue = Color(0xFF147DFF)
        drawCircle(Brush.radialGradient(listOf(green.copy(alpha = 0.28f), Color.Transparent)), size.height * 0.55f, Offset(0f, size.height * 0.7f))
        drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = 0.28f), Color.Transparent)), size.height * 0.55f, Offset(size.width, size.height * 0.7f))
        when (step) {
            0 -> {
                val vessel = Path().apply {
                    moveTo(size.width * 0.43f, size.height * 0.20f)
                    lineTo(size.width * 0.43f, size.height * 0.38f)
                    cubicTo(size.width * 0.30f, size.height * 0.48f, size.width * 0.31f, size.height * 0.82f, size.width * 0.50f, size.height * 0.84f)
                    cubicTo(size.width * 0.69f, size.height * 0.82f, size.width * 0.70f, size.height * 0.48f, size.width * 0.57f, size.height * 0.38f)
                    lineTo(size.width * 0.57f, size.height * 0.20f)
                    close()
                }
                drawPath(vessel, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color(0xFF2B6C7D).copy(alpha = 0.36f))))
                drawPath(vessel, Color.White.copy(alpha = 0.72f), style = Stroke(2.dp.toPx()))
                drawLine(green, Offset(size.width * 0.18f, size.height * 0.29f), Offset(size.width * 0.43f, size.height * 0.48f), 5.dp.toPx(), StrokeCap.Round)
                drawLine(blue, Offset(size.width * 0.82f, size.height * 0.29f), Offset(size.width * 0.57f, size.height * 0.48f), 5.dp.toPx(), StrokeCap.Round)
                drawCircle(green, 17.dp.toPx(), Offset(size.width * 0.16f, size.height * 0.26f))
                drawCircle(blue, 17.dp.toPx(), Offset(size.width * 0.84f, size.height * 0.26f))
            }
            1 -> {
                val shield = Path().apply {
                    moveTo(size.width * 0.50f, size.height * 0.16f)
                    lineTo(size.width * 0.70f, size.height * 0.27f)
                    lineTo(size.width * 0.66f, size.height * 0.63f)
                    quadraticTo(size.width * 0.61f, size.height * 0.80f, size.width * 0.50f, size.height * 0.87f)
                    quadraticTo(size.width * 0.39f, size.height * 0.80f, size.width * 0.34f, size.height * 0.63f)
                    lineTo(size.width * 0.30f, size.height * 0.27f)
                    close()
                }
                drawPath(shield, Brush.verticalGradient(listOf(Color(0xFFB8F36B).copy(alpha = 0.28f), Color(0xFF22382C))))
                drawPath(shield, Color(0xFFB8F36B), style = Stroke(2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
                drawRoundRect(Color(0xFF080B0E), Offset(size.width * 0.43f, size.height * 0.47f), Size(size.width * 0.14f, size.height * 0.20f), CornerRadius(8.dp.toPx()))
                drawArc(Color.White, 180f, 180f, false, Offset(size.width * 0.445f, size.height * 0.34f), Size(size.width * 0.11f, size.height * 0.22f), style = Stroke(3.dp.toPx()))
                drawCircle(Color(0xFFB8F36B), 4.dp.toPx(), Offset(size.width * 0.50f, size.height * 0.56f))
            }
            else -> {
                val left = Offset(size.width * 0.19f, size.height * 0.53f)
                val right = Offset(size.width * 0.81f, size.height * 0.53f)
                drawLine(Color.White.copy(alpha = 0.16f), left, right, 10.dp.toPx(), StrokeCap.Round)
                drawLine(Brush.horizontalGradient(listOf(green, Color(0xFFB8F36B), blue)), left, right, 2.dp.toPx(), StrokeCap.Round)
                drawCircle(Color(0xFF0B0E14), 28.dp.toPx(), left)
                drawCircle(green, 28.dp.toPx(), left, style = Stroke(3.dp.toPx()))
                drawCircle(Color(0xFF0B0E14), 28.dp.toPx(), right)
                drawCircle(blue, 28.dp.toPx(), right, style = Stroke(3.dp.toPx()))
                drawRoundRect(Color(0xFF1E2B25), Offset(size.width * 0.43f, size.height * 0.40f), Size(size.width * 0.14f, size.height * 0.25f), CornerRadius(12.dp.toPx()))
                drawCircle(Color(0xFFB8F36B), 5.dp.toPx(), Offset(size.width * 0.50f, size.height * 0.51f))
                drawLine(Color(0xFFB8F36B), Offset(size.width * 0.50f, size.height * 0.55f), Offset(size.width * 0.50f, size.height * 0.59f), 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    signer: Signer?,
    snapshot: LedgerSnapshot,
    syncState: NostrSessionState,
    qualityPreference: ThemeQualityPreference,
    motionPreference: ThemeMotionPreference,
    onQualityPreference: (ThemeQualityPreference) -> Unit,
    onMotionPreference: (ThemeMotionPreference) -> Unit,
    onConnect: (SyncConfiguration) -> Unit,
    onDisconnect: () -> Unit,
    onPublishPending: () -> Unit,
    onVerifyIdentity: () -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (String, String) -> Unit,
    onArchiveTag: (String) -> Unit,
    onChangeCapacity: (Long) -> Unit,
    onChangeLedgerMetadata: (String, String) -> Unit,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onArchiveCategory: (String) -> Unit,
    onParticipantIdentity: (String, String) -> Unit,
    onParticipantColor: (String, String) -> Unit,
    onParticipantName: (String, String) -> Unit,
    onThemeSelected: (String) -> Unit,
    onReplayOnboarding: () -> Unit,
    onAdoptPairingLedger: (String, () -> Unit) -> Unit,
    incomingPairingCode: String?,
    onIncomingPairingCodeConsumed: () -> Unit
) {
    val participants = snapshot.participants
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncPreferences = remember {
        context.getSharedPreferences("nomisma_sync", Context.MODE_PRIVATE)
    }
    var relayUrl by rememberSaveable {
        mutableStateOf(syncPreferences.getString(KEY_RELAY_URL, DEFAULT_RELAY_URL) ?: DEFAULT_RELAY_URL)
    }
    var recipientPubkey by rememberSaveable {
        mutableStateOf(syncPreferences.getString(KEY_RECIPIENT_PUBKEY, "").orEmpty())
    }
    val defaultLocalParticipantId = participants.firstOrNull()?.id ?: "alice"
    val defaultRemoteParticipantId = participants.drop(1).firstOrNull()?.id ?: "bob"
    var localParticipantId by rememberSaveable(defaultLocalParticipantId) {
        mutableStateOf(syncPreferences.getString(KEY_LOCAL_PARTICIPANT, defaultLocalParticipantId) ?: defaultLocalParticipantId)
    }
    var remoteParticipantId by rememberSaveable(defaultRemoteParticipantId) {
        mutableStateOf(syncPreferences.getString(KEY_REMOTE_PARTICIPANT, defaultRemoteParticipantId) ?: defaultRemoteParticipantId)
    }
    var autoConnect by rememberSaveable {
        mutableStateOf(syncPreferences.getBoolean(KEY_AUTO_CONNECT, false))
    }
    var newTagName by rememberSaveable { mutableStateOf("") }
    var tagToRename by remember { mutableStateOf<TagDefinition?>(null) }
    var tagToArchive by remember { mutableStateOf<TagDefinition?>(null) }
    var renamedTag by rememberSaveable { mutableStateOf("") }
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var categoryToRename by remember { mutableStateOf<ExpenseCategory?>(null) }
    var categoryToArchive by remember { mutableStateOf<ExpenseCategory?>(null) }
    var renamedCategory by rememberSaveable { mutableStateOf("") }
    var showCategoryManager by rememberSaveable { mutableStateOf(false) }
    var showArchivedCategories by rememberSaveable { mutableStateOf(false) }
    var showArchivedTags by rememberSaveable { mutableStateOf(false) }
    var showPairingImport by rememberSaveable { mutableStateOf(false) }
    var showForgetPairing by rememberSaveable { mutableStateOf(false) }
    var pairingInput by rememberSaveable { mutableStateOf("") }
    var pairingMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var capacityText by rememberSaveable(snapshot.capacityMinor) {
        mutableStateOf(snapshot.capacityMinor.toEditableAmount())
    }
    val parsedCapacity = ExpenseParser.parseMinorUnits(capacityText)
    val localParticipant = participants.firstOrNull { it.id == localParticipantId }
    var participantNameText by rememberSaveable(localParticipantId, localParticipant?.name) {
        mutableStateOf(localParticipant?.name.orEmpty())
    }
    var ledgerTitleText by rememberSaveable(snapshot.title) { mutableStateOf(snapshot.title) }
    var selectedCurrency by rememberSaveable(snapshot.currency) { mutableStateOf(snapshot.currency) }
    val currencyCanChange = snapshot.expenses.isEmpty()
    val pairingReadiness = remember(
        relayUrl,
        recipientPubkey,
        localParticipantId,
        remoteParticipantId,
        syncState.identity
    ) {
        PairingReadinessEvaluator.evaluate(
            relayUrl = relayUrl,
            localPubkey = syncState.identity?.pubkey,
            recipientPubkey = recipientPubkey,
            localParticipantId = localParticipantId,
            remoteParticipantId = remoteParticipantId
        )
    }
    val outgoingPairingCode = remember(
        snapshot.id,
        relayUrl,
        localParticipantId,
        remoteParticipantId,
        syncState.identity,
        pairingReadiness.relayReady
    ) {
        syncState.identity?.takeIf { pairingReadiness.relayReady }?.let { identity ->
            runCatching {
                PairingInviteCodec.encode(
                    PairingInvite(
                        ledgerId = snapshot.id,
                        relayUrl = relayUrl,
                        senderPubkey = identity.pubkey,
                        senderParticipantId = localParticipantId,
                        receiverParticipantId = remoteParticipantId
                    )
                )
            }.getOrNull()
        }
    }
    val syncHealth = remember(syncState, pairingReadiness) {
        SyncHealthEvaluator.evaluate(syncState, pairingReadiness)
    }

    LaunchedEffect(incomingPairingCode) {
        incomingPairingCode?.takeIf(String::isNotBlank)?.let { code ->
            pairingInput = code
            showPairingImport = true
            onIncomingPairingCodeConsumed()
        }
    }

    fun selectParticipant(participantId: String, local: Boolean) {
        if ((local && participantId == remoteParticipantId) || (!local && participantId == localParticipantId)) return
        if (local) {
            localParticipantId = participantId
            syncPreferences.edit().putString(KEY_LOCAL_PARTICIPANT, participantId).apply()
        } else {
            remoteParticipantId = participantId
            syncPreferences.edit().putString(KEY_REMOTE_PARTICIPANT, participantId).apply()
        }
        if (syncState.status != SyncConnectionStatus.DISCONNECTED) onDisconnect()
        autoConnect = false
        syncPreferences.edit().putBoolean(KEY_AUTO_CONNECT, false).apply()
    }

    fun storePairingInvite(invite: PairingInvite) {
        require(participants.any { it.id == invite.senderParticipantId }) { "Partecipante mittente sconosciuto" }
        require(participants.any { it.id == invite.receiverParticipantId }) { "Partecipante destinatario sconosciuto" }
        require(syncState.identity?.pubkey != invite.senderPubkey) { "Non puoi importare il tuo stesso codice" }
        if (syncState.status != SyncConnectionStatus.DISCONNECTED) onDisconnect()
        relayUrl = invite.relayUrl
        recipientPubkey = invite.senderPubkey
        localParticipantId = invite.receiverParticipantId
        remoteParticipantId = invite.senderParticipantId
        autoConnect = false
        syncPreferences.edit()
            .putString(KEY_RELAY_URL, relayUrl)
            .putString(KEY_RECIPIENT_PUBKEY, recipientPubkey)
            .putString(KEY_LOCAL_PARTICIPANT, localParticipantId)
            .putString(KEY_REMOTE_PARTICIPANT, remoteParticipantId)
            .putBoolean(KEY_AUTO_CONNECT, false)
            .apply()
        pairingMessage = "Pairing importato · verifica Amber e connetti il relay"
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Impostazioni", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("Identità e connessione locale", color = Muted)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Guida iniziale", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("Rivedi privacy, Amber e scambio dei codici.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onReplayOnboarding) { Text("Apri") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Amber / NIP-55", color = Ink, fontWeight = FontWeight.SemiBold)
                Text(
                    syncState.identity?.let { "Connesso: ${it.pubkey.take(16)}…" } ?: "Nessuna identità collegata",
                    color = if (syncState.identity == null) Muted else Color(0xFFB8F36B)
                )
                Button(
                    enabled = signer != null && !syncState.isVerifyingIdentity &&
                        syncState.status == SyncConnectionStatus.DISCONNECTED,
                    onClick = onVerifyIdentity
                ) {
                    Text(
                        when {
                            syncState.isVerifyingIdentity -> "Apertura di Amber…"
                            syncState.identity == null -> "Collega Amber"
                            else -> "Verifica identità"
                        }
                    )
                }
                if (syncState.status != SyncConnectionStatus.DISCONNECTED) {
                    Text("Disconnetti il relay per cambiare o verificare l'identità.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                syncState.error?.let { Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall) }
                if (signer == null) Text("Signer non configurato", color = Muted)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Partecipanti Nostr", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("Associa le chiavi firmatarie ai partecipanti del ledger.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Questo dispositivo registra come", color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    participants.forEach { participant ->
                        FilterChip(
                            selected = localParticipantId == participant.id,
                            onClick = { selectParticipant(participant.id, local = true) },
                            label = { Text(participant.name) }
                        )
                    }
                }
                Text("Il destinatario è", color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    participants.forEach { participant ->
                        FilterChip(
                            selected = remoteParticipantId == participant.id,
                            onClick = { selectParticipant(participant.id, local = false) },
                            label = { Text(participant.name) }
                        )
                    }
                }
                Text("Il tuo nome nel ledger", color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = participantNameText,
                        onValueChange = { participantNameText = it.take(40) },
                        label = { Text("Nome") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = participantNameText.trim().isNotBlank() &&
                            participantNameText.trim() != localParticipant?.name,
                        onClick = { onParticipantName(localParticipantId, participantNameText) }
                    ) { Text("Salva") }
                }
                HorizontalDivider(color = PanelLight)
                Text("Identità nel tema", color = Ink, style = MaterialTheme.typography.labelMedium)
                participants.forEach { participant ->
                    val isOwnedByThisDevice = participant.id == localParticipantId
                    Text(
                        if (isOwnedByThisDevice) "${participant.name} · la tua identità" else "${participant.name} · gestita dall'altro partecipante",
                        color = if (isOwnedByThisDevice) Ink else Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeIdentityRegistry.identities.forEach { identityOption ->
                            FilterChip(
                                selected = participant.avatarId == identityOption.id,
                                enabled = isOwnedByThisDevice,
                                onClick = {
                                    if (isOwnedByThisDevice) onParticipantIdentity(participant.id, identityOption.id)
                                },
                                label = {
                                    Text(
                                        "${identityOption.symbol} ${identityOption.displayName}" +
                                            if (snapshot.themeId == ThemeRegistry.WOODLAND) " · ${identityOption.contributionMaterial}" else ""
                                    )
                                }
                            )
                        }
                    }
                    Text("Colore contributi", color = Muted, style = MaterialTheme.typography.labelSmall)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        ParticipantColorPalette.colors.forEach { colorHex ->
                            val swatch = colorHex.toComposeColor(Color.White)
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (participant.colorHex.equals(colorHex, ignoreCase = true)) 3.dp else 1.dp,
                                        color = if (participant.colorHex.equals(colorHex, ignoreCase = true)) Ink else Ink.copy(alpha = 0.22f),
                                        shape = CircleShape
                                    )
                                    .semantics {
                                        contentDescription = "Colore $colorHex" +
                                            if (participant.colorHex.equals(colorHex, ignoreCase = true)) ", selezionato" else ""
                                    }
                                    .clickable(enabled = isOwnedByThisDevice) {
                                        onParticipantColor(participant.id, colorHex)
                                    }
                            )
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Grafica", color = Ink, fontWeight = FontWeight.SemiBold)
                Text("Tema condiviso", color = Muted, style = MaterialTheme.typography.labelMedium)
                ThemeSelector(snapshot.themeId, onThemeSelected)
                Text(
                    "Il tema è parte della configurazione condivisa del ledger.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Qualità delle scene", color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ThemeQualityPreference.AUTO to "Auto",
                        ThemeQualityPreference.LOW to "Bassa",
                        ThemeQualityPreference.MEDIUM to "Media",
                        ThemeQualityPreference.HIGH to "Alta"
                    ).forEach { (preference, label) ->
                        FilterChip(
                            selected = qualityPreference == preference,
                            onClick = { onQualityPreference(preference) },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    "Auto adatta particelle e dettagli a RAM, risparmio energetico e versione Android.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Movimento", color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = motionPreference == ThemeMotionPreference.SYSTEM,
                        onClick = { onMotionPreference(ThemeMotionPreference.SYSTEM) },
                        label = { Text("Sistema") }
                    )
                    FilterChip(
                        selected = motionPreference == ThemeMotionPreference.REDUCED,
                        onClick = { onMotionPreference(ThemeMotionPreference.REDUCED) },
                        label = { Text("Ridotto") }
                    )
                }
                Text("La preferenza di accessibilità del sistema resta sempre prioritaria.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ledger", color = Ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = ledgerTitleText,
                    onValueChange = { ledgerTitleText = it.take(60) },
                    label = { Text("Nome del ledger") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Valuta", color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupportedCurrencies.all.forEach { currency ->
                        FilterChip(
                            selected = selectedCurrency == currency.code,
                            enabled = currencyCanChange || currency.code == snapshot.currency,
                            onClick = { selectedCurrency = currency.code },
                            label = { Text("${currency.symbol.trim()} ${currency.code}") }
                        )
                    }
                }
                if (!currencyCanChange) {
                    Text(
                        "La valuta non può cambiare dopo la prima spesa: gli importi storici non vengono reinterpretati.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    enabled = ledgerTitleText.trim().isNotBlank() &&
                        (ledgerTitleText.trim() != snapshot.title || selectedCurrency != snapshot.currency),
                    onClick = { onChangeLedgerMetadata(ledgerTitleText, selectedCurrency) }
                ) { Text("Salva configurazione") }
                HorizontalDivider(color = PanelLight)
                OutlinedTextField(
                    value = capacityText,
                    onValueChange = { capacityText = it },
                    label = { Text("Capacità del ciclo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = parsedCapacity != null && parsedCapacity != snapshot.capacityMinor,
                    onClick = { parsedCapacity?.let(onChangeCapacity) }
                ) { Text("Aggiorna capacità") }
                Text(
                    "Nome, valuta e capacità sono condivisi via Nostr. La capacità guida il progresso dei temi.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Categorie", color = Ink, fontWeight = FontWeight.SemiBold)
                        Text("${snapshot.activeCategories.size} attive", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { showCategoryManager = !showCategoryManager }) {
                        Text(if (showCategoryManager) "Chiudi" else "Gestisci")
                    }
                }
                if (showCategoryManager) {
                    val archivedCategories = snapshot.categories.filter(ExpenseCategory::isArchived)
                    val matchingArchivedCategory = archivedCategories.firstOrNull {
                        it.displayName.equals(newCategoryName.trim(), ignoreCase = true)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it.take(60) },
                            label = { Text("Nuova categoria") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            enabled = newCategoryName.trim().isNotBlank() &&
                                snapshot.activeCategories.none { it.displayName.equals(newCategoryName.trim(), ignoreCase = true) },
                            onClick = {
                                onCreateCategory(newCategoryName)
                                newCategoryName = ""
                            }
                        ) { Text(if (matchingArchivedCategory == null) "Aggiungi" else "Ripristina") }
                    }
                    snapshot.activeCategories.forEach { category ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(category.displayName, color = Ink)
                                Text(
                                    "${if (category.isDefault) "Predefinita · " else ""}${category.usageCount} utilizzi",
                                    color = Muted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            TextButton(onClick = {
                                renamedCategory = category.displayName
                                categoryToRename = category
                            }) { Text("Rinomina") }
                            TextButton(
                                enabled = snapshot.activeCategories.size > 1,
                                onClick = { categoryToArchive = category }
                            ) { Text("Archivia", color = Color(0xFFFFB4A9)) }
                        }
                    }
                    if (archivedCategories.isNotEmpty()) {
                        HorizontalDivider(color = PanelLight)
                        TextButton(onClick = { showArchivedCategories = !showArchivedCategories }) {
                            Text(
                                if (showArchivedCategories) "Nascondi archiviate"
                                else "Archiviate · ${archivedCategories.size}"
                            )
                        }
                        if (showArchivedCategories) {
                            archivedCategories.forEach { category ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(category.displayName, color = Muted)
                                        Text(
                                            "${category.usageCount} utilizzi nello storico",
                                            color = Muted,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    OutlinedButton(onClick = { onCreateCategory(category.displayName) }) {
                                        Text("Ripristina")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val archivedTags = snapshot.tags.filter(TagDefinition::isArchived)
                val matchingArchivedTag = archivedTags.firstOrNull {
                    it.name == canonicalTagInput(newTagName)
                }
                Text("Tag condivisi", color = Ink, fontWeight = FontWeight.SemiBold)
                Text(
                    "Crea, rinomina o archivia i tag. I tag archiviati restano nello storico.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = { Text("Nuovo tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = canonicalTagInput(newTagName).let { candidate ->
                            candidate.isNotBlank() && snapshot.activeTags.none { it.name == candidate }
                        },
                        onClick = {
                            onCreateTag(newTagName)
                            newTagName = ""
                        }
                    ) { Text(if (matchingArchivedTag == null) "Aggiungi" else "Ripristina") }
                }
                if (snapshot.activeTags.isEmpty()) {
                    Text("Nessun tag attivo", color = Muted)
                } else {
                    snapshot.activeTags.forEach { tag ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("#${tag.name}", color = Ink)
                                Text("${tag.usageCount} utilizzi", color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = {
                                renamedTag = tag.name
                                tagToRename = tag
                            }) { Text("Rinomina") }
                            TextButton(onClick = { tagToArchive = tag }) {
                                Text("Archivia", color = Color(0xFFFFB4A9))
                            }
                        }
                    }
                }
                if (archivedTags.isNotEmpty()) {
                    HorizontalDivider(color = PanelLight)
                    TextButton(onClick = { showArchivedTags = !showArchivedTags }) {
                        Text(if (showArchivedTags) "Nascondi archiviati" else "Archiviati · ${archivedTags.size}")
                    }
                    if (showArchivedTags) {
                        archivedTags.forEach { tag ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("#${tag.name}", color = Muted)
                                    Text(
                                        "${tag.usageCount} utilizzi nello storico",
                                        color = Muted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                OutlinedButton(onClick = { onCreateTag(tag.name) }) {
                                    Text("Ripristina")
                                }
                            }
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Relay", color = Ink, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = relayUrl,
                    onValueChange = {
                        relayUrl = it
                        syncPreferences.edit().putString(KEY_RELAY_URL, it).apply()
                    },
                    label = { Text("WebSocket URL") },
                    singleLine = true,
                    enabled = syncState.status == SyncConnectionStatus.DISCONNECTED,
                    modifier = Modifier.fillMaxWidth()
                )
                if (RelayTransportPolicy.isCleartext(relayUrl)) {
                    Text(
                        if (pairingReadiness.relayReady) {
                            "Relay LAN senza TLS: usalo solo su una rete fidata. Le operazioni restano cifrate NIP-44/NIP-59."
                        } else {
                            "Per sicurezza ws:// è consentito solo su ${RelayTransportPolicy.ALLOWED_CLEARTEXT_HOST}; per altri relay usa wss://."
                        },
                        color = Color(0xFFFFD180),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = recipientPubkey,
                    onValueChange = {
                        recipientPubkey = it
                        syncPreferences.edit().putString(KEY_RECIPIENT_PUBKEY, it).apply()
                    },
                    label = { Text("Chiave pubblica destinatario") },
                    placeholder = { Text("hex 64 caratteri") },
                    singleLine = true,
                    enabled = syncState.status == SyncConnectionStatus.DISCONNECTED,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = PanelLight)
                Text("Pairing", color = Ink, fontWeight = FontWeight.SemiBold)
                Text(
                    "Il codice contiene solo relay, chiave pubblica e associazione dei partecipanti. Non contiene spese o chiavi private.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = outgoingPairingCode != null,
                        onClick = {
                            outgoingPairingCode?.let { code ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Pairing Nomisma", code))
                                pairingMessage = "Codice pairing copiato"
                            }
                        }
                    ) { Text("Copia il mio codice") }
                    OutlinedButton(
                        enabled = outgoingPairingCode != null,
                        onClick = {
                            outgoingPairingCode?.let { code ->
                                val shareText = "Invito Nomisma per ${snapshot.title}\n\n$code"
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Pairing Nomisma")
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(intent, "Condividi pairing"))
                                }.onFailure { pairingMessage = "Nessuna app disponibile per condividere" }
                            }
                        }
                    ) { Text("Condividi") }
                    OutlinedButton(onClick = { showPairingImport = true }) { Text("Importa") }
                }
                pairingMessage?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                PairingReadinessCard(pairingReadiness)
                SyncHealthCard(syncHealth)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = signer != null && pairingReadiness.isReady &&
                            syncState.status == SyncConnectionStatus.DISCONNECTED,
                        onClick = {
                            autoConnect = true
                            syncPreferences.edit().putBoolean(KEY_AUTO_CONNECT, true).apply()
                            onConnect(
                                SyncConfiguration(
                                    relayUrl = relayUrl,
                                    recipientPubkey = recipientPubkey,
                                    localParticipantId = localParticipantId,
                                    remoteParticipantId = remoteParticipantId
                                )
                            )
                        }
                    ) { Text("Connetti") }
                    if (syncState.status != SyncConnectionStatus.DISCONNECTED) {
                        OutlinedButton(onClick = {
                            autoConnect = false
                            syncPreferences.edit().putBoolean(KEY_AUTO_CONNECT, false).apply()
                            onDisconnect()
                        }) { Text("Disconnetti") }
                    }
                }
                Text(
                    when (syncState.status) {
                        SyncConnectionStatus.CONNECTED -> if (syncState.backlogComplete) {
                            "Connesso · storico allineato · ${syncState.identity?.pubkey?.take(12) ?: "Amber"}…"
                        } else {
                            "Connesso · recupero storico…"
                        }
                        SyncConnectionStatus.BACKOFF -> "Riconnessione #${syncState.reconnectAttempt} programmata…"
                        SyncConnectionStatus.CONNECTING -> "Connessione in corso…"
                        SyncConnectionStatus.DISCONNECTED -> "Relay non connesso"
                    },
                    color = if (syncState.isConnected) Color(0xFFB8F36B) else Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                FilterChip(
                    selected = autoConnect,
                    onClick = {
                        autoConnect = !autoConnect
                        syncPreferences.edit().putBoolean(KEY_AUTO_CONNECT, autoConnect).apply()
                    },
                    label = { Text("Riconnetti all'avvio") }
                )
                val operationStats = syncState.operationStats
                Text(
                    "Outbox ${operationStats.pending} · pubblicate ${operationStats.published} · ricevute ${operationStats.applied}" +
                        (if (operationStats.deferred > 0) " · in attesa ${operationStats.deferred}" else "") +
                        (if (operationStats.rejected > 0) " · rifiutate ${operationStats.rejected}" else ""),
                    color = if (operationStats.pending > 0) Color(0xFFFFD180) else Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val report = SyncDiagnosticsFormatter.format(
                                state = syncState,
                                readiness = pairingReadiness,
                                relayUrl = relayUrl,
                                recipientPubkey = recipientPubkey
                            )
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostica Nomisma", report))
                            pairingMessage = "Diagnostica copiata senza dati finanziari né chiavi complete"
                        }
                    ) { Text("Copia diagnostica") }
                    OutlinedButton(
                        enabled = recipientPubkey.isNotBlank(),
                        onClick = { showForgetPairing = true }
                    ) { Text("Rimuovi pairing") }
                }
                Button(
                    enabled = syncState.isConnected && !syncState.isPublishing && operationStats.pending > 0,
                    onClick = onPublishPending
                ) { Text(if (syncState.isPublishing) "Sincronizzazione…" else "Pubblica outbox") }
                syncState.message?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "La sessione resta attiva cambiando schermata; inbound, EOSE e reconnessione continuano in background finché l'app è aperta.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Text("La chiave privata resta in Amber e non viene salvata da Nomisma.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }

    if (showPairingImport) {
        PairingImportDialog(
            input = pairingInput,
            snapshot = snapshot,
            localPubkey = syncState.identity?.pubkey,
            onInputChange = { pairingInput = it },
            onPaste = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                pairingInput = clipboard.primaryClip
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    .orEmpty()
            },
            onDismiss = { showPairingImport = false },
            onImport = { invite ->
                fun finishImport() {
                    runCatching { storePairingInvite(invite) }.onSuccess {
                        pairingInput = ""
                        showPairingImport = false
                    }.onFailure { pairingMessage = it.message ?: "Pairing non valido" }
                }
                if (invite.ledgerId == snapshot.id) {
                    finishImport()
                } else {
                    onAdoptPairingLedger(invite.ledgerId, ::finishImport)
                }
            }
        )
    }

    if (showForgetPairing) {
        AlertDialog(
            onDismissRequest = { showForgetPairing = false },
            title = { Text("Rimuovere il pairing?") },
            text = {
                Text(
                    "Verranno rimossi la chiave dell'altro dispositivo, i ruoli locali e la riconnessione automatica. " +
                        "Il ledger, le spese e l'outbox non verranno eliminati."
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (syncState.status != SyncConnectionStatus.DISCONNECTED) onDisconnect()
                    recipientPubkey = ""
                    localParticipantId = defaultLocalParticipantId
                    remoteParticipantId = defaultRemoteParticipantId
                    autoConnect = false
                    pairingInput = ""
                    syncPreferences.edit()
                        .remove(KEY_RECIPIENT_PUBKEY)
                        .remove(KEY_LOCAL_PARTICIPANT)
                        .remove(KEY_REMOTE_PARTICIPANT)
                        .putBoolean(KEY_AUTO_CONNECT, false)
                        .apply()
                    pairingMessage = "Pairing rimosso · il ledger locale è rimasto intatto"
                    showForgetPairing = false
                }) { Text("Rimuovi") }
            },
            dismissButton = { TextButton(onClick = { showForgetPairing = false }) { Text("Annulla") } }
        )
    }

    tagToArchive?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToArchive = null },
            title = { Text("Archiviare #${tag.name}?") },
            text = {
                Text(
                    "Il tag non sarà più proposto nelle nuove spese. I suoi ${tag.usageCount} utilizzi " +
                        "resteranno nello storico e potrai ripristinarlo in qualsiasi momento."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onArchiveTag(tag.id)
                    tagToArchive = null
                }) { Text("Archivia", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { tagToArchive = null }) { Text("Annulla") } }
        )
    }

    categoryToArchive?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToArchive = null },
            title = { Text("Archiviare ${category.displayName}?") },
            text = {
                Text(
                    "La categoria non sarà più disponibile per le nuove spese. I suoi ${category.usageCount} utilizzi " +
                        "resteranno nello storico e potrai ripristinarla in qualsiasi momento."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onArchiveCategory(category.id)
                    categoryToArchive = null
                }) { Text("Archivia", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { categoryToArchive = null }) { Text("Annulla") } }
        )
    }

    tagToRename?.let { tag ->
        AlertDialog(
            onDismissRequest = { tagToRename = null },
            title = { Text("Rinomina #${tag.name}") },
            text = {
                OutlinedTextField(
                    value = renamedTag,
                    onValueChange = { renamedTag = it },
                    label = { Text("Nuovo nome") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = canonicalTagInput(renamedTag).let { candidate ->
                        candidate.isNotBlank() && candidate != tag.name &&
                            snapshot.activeTags.none { it.id != tag.id && it.name == candidate }
                    },
                    onClick = {
                        onRenameTag(tag.id, renamedTag)
                        tagToRename = null
                    }
                ) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { tagToRename = null }) { Text("Annulla") } }
        )
    }

    categoryToRename?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToRename = null },
            title = { Text("Rinomina ${category.displayName}") },
            text = {
                OutlinedTextField(
                    value = renamedCategory,
                    onValueChange = { renamedCategory = it.take(60) },
                    label = { Text("Nuovo nome") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = renamedCategory.trim().isNotBlank() &&
                        !renamedCategory.trim().equals(category.displayName, ignoreCase = true) &&
                        snapshot.activeCategories.none {
                            it.id != category.id && it.displayName.equals(renamedCategory.trim(), ignoreCase = true)
                        },
                    onClick = {
                        onRenameCategory(category.id, renamedCategory)
                        categoryToRename = null
                    }
                ) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { categoryToRename = null }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun PairingReadinessCard(readiness: PairingReadiness) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PanelLight.copy(alpha = 0.72f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Stato configurazione", color = Ink, fontWeight = FontWeight.SemiBold)
            PairingCheck("Relay sicuro o LAN autorizzato", readiness.relayReady)
            PairingCheck("Identità verificata con Amber", readiness.identityReady)
            PairingCheck("Chiave dell'altro partecipante", readiness.recipientReady)
            PairingCheck("Ruoli dei partecipanti distinti", readiness.participantsReady)
            PairingCheck("Chiavi locale e remota diverse", readiness.keysAreDistinct)
        }
    }
}

@Composable
private fun SyncHealthCard(summary: SyncHealthSummary) {
    val accent = when (summary.level) {
        SyncHealthLevel.HEALTHY -> Color(0xFFB8F36B)
        SyncHealthLevel.READY -> Color(0xFF7DCBFF)
        SyncHealthLevel.WORKING -> Color(0xFFFFD180)
        SyncHealthLevel.ATTENTION -> Color(0xFFFF8A80)
        SyncHealthLevel.LOCAL_ONLY, SyncHealthLevel.SETUP_REQUIRED -> Muted
    }
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
            Column(Modifier.weight(1f)) {
                Text(summary.title, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                Text(summary.detail, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PairingCheck(label: String, complete: Boolean) {
    val statusColor = if (complete) Color(0xFFB8F36B) else Color(0xFFFFD180)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Text(
            text = if (complete) "$label · pronto" else "$label · da completare",
            color = if (complete) Ink else Muted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PairingImportDialog(
    input: String,
    snapshot: LedgerSnapshot,
    localPubkey: String?,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onDismiss: () -> Unit,
    onImport: (PairingInvite) -> Unit
) {
    val decoded = remember(input) {
        input.takeIf(String::isNotBlank)?.let { runCatching { PairingInviteCodec.decode(it) } }
    }
    val invite = decoded?.getOrNull()
    val canReplaceEmptyLedger = snapshot.expenses.isEmpty() &&
        snapshot.previousCycles.isEmpty() && snapshot.tags.isEmpty()
    val validationMessage = when {
        decoded?.isFailure == true -> decoded.exceptionOrNull()?.message ?: "Codice pairing non valido"
        invite == null -> null
        invite.ledgerId != snapshot.id && !canReplaceEmptyLedger ->
            "Questo invito appartiene a un altro ledger e quello locale contiene già dati"
        !RelayTransportPolicy.isSupported(invite.relayUrl) ->
            "Relay non consentito: usa wss:// oppure ws://${RelayTransportPolicy.ALLOWED_CLEARTEXT_HOST}"
        snapshot.participants.none { it.id == invite.senderParticipantId } -> "Il mittente non esiste in questo ledger"
        snapshot.participants.none { it.id == invite.receiverParticipantId } -> "Il destinatario non esiste in questo ledger"
        localPubkey?.trim()?.lowercase() == invite.senderPubkey -> "Questo è il codice del dispositivo corrente"
        else -> null
    }
    val canImport = invite != null && validationMessage == null
    val sender = invite?.let { candidate -> snapshot.participants.firstOrNull { it.id == candidate.senderParticipantId } }
    val receiver = invite?.let { candidate -> snapshot.participants.firstOrNull { it.id == candidate.receiverParticipantId } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importa pairing") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Incolla il codice ricevuto dall'altro partecipante. Nomisma controllerà ledger, ruoli e chiave prima di salvarlo.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    label = { Text("Codice Nomisma") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onPaste) { Text("Incolla dagli appunti") }
                invite?.takeIf { canImport }?.let { verifiedInvite ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PanelLight),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Invito verificato", color = Color(0xFFB8F36B), fontWeight = FontWeight.SemiBold)
                            Text("Relay: ${verifiedInvite.relayUrl}", color = Ink, style = MaterialTheme.typography.bodySmall)
                            if (verifiedInvite.ledgerId != snapshot.id) {
                                Text(
                                    "Il ledger locale è vuoto: verrà associato in modo permanente a questo invito.",
                                    color = Color(0xFFFFD180),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                "Questo dispositivo: ${receiver?.name ?: verifiedInvite.receiverParticipantId}",
                                color = Ink,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Altro dispositivo: ${sender?.name ?: verifiedInvite.senderParticipantId}",
                                color = Ink,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Chiave: ${verifiedInvite.senderPubkey.take(12)}…${verifiedInvite.senderPubkey.takeLast(8)}",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Confronta questa impronta con l'altra persona prima di connetterti.",
                                color = Color(0xFFFFD180),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                validationMessage?.let {
                    Text(it, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "L'importazione non avvia la connessione: potrai verificare i dati e connetterti manualmente.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(enabled = canImport, onClick = { invite?.let(onImport) }) { Text("Importa") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

private const val KEY_RELAY_URL = "relay_url"
private const val KEY_RECIPIENT_PUBKEY = "recipient_pubkey"
private const val KEY_LOCAL_PARTICIPANT = "local_participant"
private const val KEY_REMOTE_PARTICIPANT = "remote_participant"
private const val KEY_AUTO_CONNECT = "auto_connect"
private const val VISUAL_PREFERENCES = "nomisma_visual"
private const val KEY_VISUAL_QUALITY = "quality"
private const val KEY_VISUAL_MOTION = "motion"

@Composable
private fun HomeScreen(
    snapshot: LedgerSnapshot,
    syncState: NostrSessionState,
    qualityPreference: ThemeQualityPreference,
    motionPreference: ThemeMotionPreference,
    onThemeSelected: (String) -> Unit,
    onSettle: (SettlementMethod, String) -> Unit,
    onEditExpense: (Expense) -> Unit,
    onPublishPending: () -> Unit,
    onOpenSyncSettings: () -> Unit
) {
    val themeState = remember(snapshot) { com.nomisma.app.theme.ThemeStateMapper.map(snapshot) }
    val balance = snapshot.balance()
    val creditor = snapshot.participants.firstOrNull { it.id == balance.creditorId }
    var showSettlementConfirmation by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("NOMISMA", color = Ink, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Text(snapshot.title, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            val syncBadge = when (syncState.status) {
                SyncConnectionStatus.CONNECTED -> "SYNC"
                SyncConnectionStatus.CONNECTING -> "CONNESSIONE"
                SyncConnectionStatus.BACKOFF -> "RETRY"
                SyncConnectionStatus.DISCONNECTED -> "LOCAL"
            }
            val syncBadgeColor = if (syncState.isConnected) Color(0xFF9BE76A) else Muted
            Surface(shape = RoundedCornerShape(50), color = Color(0xFF17231D)) {
                Text(syncBadge, color = syncBadgeColor, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }

        if (syncState.operationStats.pending > 0 || syncState.error != null) {
            HomeSyncBanner(
                syncState = syncState,
                onPublishPending = onPublishPending,
                onOpenSettings = onOpenSyncSettings
            )
        }

        val balanceAccent = creditor?.colorHex.toComposeColor(Color(0xFFB8F36B))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(balanceAccent.copy(alpha = 0.17f), Color.Transparent)))
                .border(1.dp, balanceAccent.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                Text("SALDO NETTO", color = Muted, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.2.sp)
                Text(
                    text = if (creditor == null) "In pari" else "${creditor.name.uppercase(Locale.ROOT)} +${balance.amountMinor.formatMinorUnits(snapshot.currency)}",
                    color = if (creditor == null) Ink else balanceAccent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        ThemeSelector(snapshot.themeId, onThemeSelected)
        NomismaThemeScene(
            state = themeState,
            themeId = snapshot.themeId,
            qualityPreference = qualityPreference,
            motionPreference = motionPreference
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("CICLO", snapshot.cycleVolumeMinor.formatMinorUnits(snapshot.currency), Modifier.weight(1f))
            MetricCard("SOGLIA", snapshot.capacityMinor.formatMinorUnits(snapshot.currency), Modifier.weight(1f))
            MetricCard("VOCI", snapshot.currentCycleExpenses.size.toString(), Modifier.weight(1f))
        }

        OutlinedButton(
            enabled = snapshot.currentCycleExpenses.isNotEmpty(),
            onClick = { showSettlementConfirmation = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Chiudi e riconcilia il ciclo") }

        Text("Ultime spese", color = Ink, fontWeight = FontWeight.SemiBold)
        if (snapshot.currentCycleExpenses.isEmpty()) {
            EmptyStatePanel(
                title = "Il ciclo è ancora vuoto",
                detail = "Tocca + per aggiungere la prima spesa. Verrà salvata subito sul dispositivo."
            )
        } else {
            snapshot.currentCycleExpenses.takeLast(3).reversed().forEach { expense ->
                ExpenseRow(expense, snapshot, onClick = { onEditExpense(expense) })
            }
        }
        Spacer(Modifier.height(72.dp))
    }

    if (showSettlementConfirmation) {
        SettlementConfirmationDialog(
            title = "Chiudere il ciclo?",
            summary =
                if (creditor == null) {
                    "${snapshot.currentCycleExpenses.size} spese per ${snapshot.cycleVolumeMinor.formatMinorUnits(snapshot.currency)}. Il saldo è in pari."
                } else {
                    "${snapshot.currentCycleExpenses.size} spese per ${snapshot.cycleVolumeMinor.formatMinorUnits(snapshot.currency)}. ${creditor.name} ha un credito di ${balance.amountMinor.formatMinorUnits(snapshot.currency)}."
                },
            onDismiss = { showSettlementConfirmation = false },
            onConfirm = { method, note ->
                showSettlementConfirmation = false
                onSettle(method, note)
            }
        )
    }
}

@Composable
private fun HomeSyncBanner(
    syncState: NostrSessionState,
    onPublishPending: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val hasError = syncState.error != null
    val accent = if (hasError) Color(0xFFFF8A80) else Color(0xFFFFD180)
    Card(
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.09f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
                Text(
                    if (hasError) "Sincronizzazione da controllare"
                    else "${syncState.operationStats.pending} operazioni nell'outbox",
                    color = Ink,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                syncState.error ?: if (syncState.isConnected) {
                    "Le modifiche sono al sicuro in locale e pronte per la pubblicazione."
                } else {
                    "Le modifiche sono al sicuro in locale. Connetti il relay quando vuoi condividerle."
                },
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (syncState.isConnected && syncState.operationStats.pending > 0) {
                    Button(
                        enabled = !syncState.isPublishing,
                        onClick = onPublishPending
                    ) { Text(if (syncState.isPublishing) "Sincronizzazione…" else "Sincronizza ora") }
                }
                OutlinedButton(onClick = onOpenSettings) { Text("Apri impostazioni") }
            }
        }
    }
}

@Composable
private fun SettleScreen(snapshot: LedgerSnapshot, onSettle: (SettlementMethod, String) -> Unit) {
    val balance = snapshot.balance()
    val creditor = snapshot.participants.firstOrNull { it.id == balance.creditorId }
    val debtor = snapshot.participants.firstOrNull { it.id != balance.creditorId }
    var showConfirmation by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Riconciliazione", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(
            snapshot.currentCycle?.startedAtMillis?.let { "Ciclo iniziato il ${it.formatLocalDate()}" }
                ?: "Ciclo corrente",
            color = Muted
        )
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Posizione corrente", color = Muted, style = MaterialTheme.typography.labelMedium)
                Text(
                    if (creditor == null) "Siete in pari"
                    else "${debtor?.name ?: "Il partecipante"} deve ${balance.amountMinor.formatMinorUnits(snapshot.currency)} a ${creditor.name}",
                    color = if (creditor == null) Ink else Color(0xFFB8F36B),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                SettlementBalanceVisual(snapshot)
                HorizontalDivider(color = PanelLight)
                snapshot.participants.forEach { participant ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(participant.name, color = Muted)
                        Text(
                            snapshot.contributionFor(participant.id).formatMinorUnits(snapshot.currency),
                            color = Ink,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("VOLUME", snapshot.cycleVolumeMinor.formatMinorUnits(snapshot.currency), Modifier.weight(1f))
            MetricCard("CAPACITÀ", snapshot.capacityMinor.formatMinorUnits(snapshot.currency), Modifier.weight(1f))
            MetricCard("VOCI", snapshot.currentCycleExpenses.size.toString(), Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(PanelLight)) {
            Box(
                Modifier.fillMaxWidth(snapshot.normalizedFill.coerceIn(0f, 1f))
                    .height(10.dp)
                    .background(if (snapshot.overflowRatio > 0f) Color(0xFFFF9B73) else Color(0xFFB8F36B), CircleShape)
            )
        }
        Button(
            enabled = snapshot.currentCycleExpenses.isNotEmpty(),
            onClick = { showConfirmation = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Conferma e apri un nuovo ciclo") }
        Text(
            "Il settlement salva uno snapshot immutabile. Non elimina le spese e non dipende dal tema selezionato.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall
        )
        SettlementHistory(snapshot)
        Spacer(Modifier.height(72.dp))
    }

    if (showConfirmation) {
        SettlementConfirmationDialog(
            title = "Confermare il settlement?",
            summary = if (creditor == null) {
                "Verranno chiuse ${snapshot.currentCycleExpenses.size} spese per " +
                    "${snapshot.cycleVolumeMinor.formatMinorUnits(snapshot.currency)}. Il ciclo è già in pari."
            } else {
                "${debtor?.name ?: "Il partecipante"} riconcilia " +
                    "${balance.amountMinor.formatMinorUnits(snapshot.currency)} con ${creditor.name}. " +
                    "Il nuovo ciclo partirà senza saldo residuo."
            },
            onDismiss = { showConfirmation = false },
            onConfirm = { method, note ->
                showConfirmation = false
                onSettle(method, note)
            }
        )
    }
}

@Composable
private fun SettlementConfirmationDialog(
    title: String,
    summary: String,
    onDismiss: () -> Unit,
    onConfirm: (SettlementMethod, String) -> Unit
) {
    var method by rememberSaveable { mutableStateOf(SettlementMethod.BANK_TRANSFER) }
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(summary)
                Text("Metodo", color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettlementMethod.entries.forEach { option ->
                        FilterChip(
                            selected = method == option,
                            onClick = { method = option },
                            label = { Text(option.displayName) }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(SettlementPolicy.MAX_NOTE_LENGTH) },
                    label = { Text("Nota opzionale") },
                    supportingText = { Text("${note.length}/${SettlementPolicy.MAX_NOTE_LENGTH}") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Metodo e nota vengono salvati nel ciclo e sincronizzati nel payload cifrato.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(method, note) }) { Text("Conferma") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun SettlementBalanceVisual(snapshot: LedgerSnapshot) {
    val first = snapshot.participants.getOrNull(0)
    val second = snapshot.participants.getOrNull(1)
    val balance = snapshot.balance()
    val firstColor = first?.colorHex.toComposeColor(Color(0xFF38E66B))
    val secondColor = second?.colorHex.toComposeColor(Color(0xFF147DFF))
    val total = snapshot.cycleVolumeMinor.coerceAtLeast(1L)
    val firstRatio = snapshot.contributionFor(first?.id.orEmpty()).toFloat() / total.toFloat()

    val balanceDescription = balance.creditorId?.let { creditorId ->
        val creditor = snapshot.participants.firstOrNull { it.id == creditorId }?.name ?: creditorId
        "Saldo del ciclo: $creditor a credito di ${balance.amountMinor.formatMinorUnits(snapshot.currency)}"
    } ?: "Saldo del ciclo in pari"
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(118.dp)
            .semantics { contentDescription = balanceDescription }
    ) {
        val left = Offset(size.width * 0.18f, size.height * 0.52f)
        val right = Offset(size.width * 0.82f, size.height * 0.52f)
        val radius = 23.dp.toPx()
        drawCircle(Brush.radialGradient(listOf(firstColor.copy(alpha = 0.36f), Color.Transparent)), radius * 2.1f, left)
        drawCircle(Brush.radialGradient(listOf(secondColor.copy(alpha = 0.36f), Color.Transparent)), radius * 2.1f, right)
        drawCircle(Color(0xFF0C1016), radius, left)
        drawCircle(Color(0xFF0C1016), radius, right)
        drawArc(firstColor, -90f, 360f * firstRatio.coerceIn(0f, 1f), false, left - Offset(radius, radius), Size(radius * 2f, radius * 2f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
        drawArc(secondColor, -90f, 360f * (1f - firstRatio).coerceIn(0f, 1f), false, right - Offset(radius, radius), Size(radius * 2f, radius * 2f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))

        val connection = Path().apply {
            moveTo(left.x + radius + 6.dp.toPx(), left.y)
            cubicTo(size.width * 0.40f, size.height * 0.24f, size.width * 0.60f, size.height * 0.24f, right.x - radius - 6.dp.toPx(), right.y)
        }
        val connectionColor = balance.creditorId?.let { id ->
            if (id == first?.id) firstColor else secondColor
        } ?: Color.White.copy(alpha = 0.42f)
        drawPath(connection, connectionColor.copy(alpha = 0.24f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(connection, connectionColor.copy(alpha = 0.92f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(connectionColor, 4.dp.toPx(), Offset(size.width * 0.5f, size.height * 0.30f))
        drawLine(Color.White.copy(alpha = 0.12f), Offset(size.width * 0.5f, size.height * 0.38f), Offset(size.width * 0.5f, size.height * 0.78f), 1.dp.toPx())
    }
}

@Composable
private fun ThemeSelector(selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeRegistry.definitions().forEach { theme ->
            val isSelected = selected == theme.id
            Box(
                Modifier
                    .width(136.dp)
                    .height(62.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        when (theme.id) {
                            ThemeRegistry.WOODLAND -> Brush.linearGradient(listOf(Color(0xFF2B432D), Color(0xFF151E16)))
                            ThemeRegistry.MEMPOOL -> Brush.linearGradient(listOf(Color(0xFF35302C), Color(0xFF15171A)))
                            else -> Brush.linearGradient(listOf(Color(0xFF1A2E3A), Color(0xFF10171D)))
                        }
                    )
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) Color(0xFFB8F36B) else Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onSelected(theme.id) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemePreviewGlyph(theme.id, isSelected)
                    Column {
                        Text(theme.displayName, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        Text(if (isSelected) "ATTIVO" else "SELEZIONA", color = if (isSelected) Color(0xFFB8F36B) else Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewGlyph(themeId: String, selected: Boolean) {
    val accent = if (selected) Color(0xFFB8F36B) else Color.White.copy(alpha = 0.72f)
    Canvas(Modifier.size(30.dp)) {
        when (themeId) {
            ThemeRegistry.WOODLAND -> {
                drawRoundRect(Color(0xFF8B5B36), Offset(size.width * 0.25f, size.height * 0.48f), Size(size.width * 0.50f, size.height * 0.36f), CornerRadius(5.dp.toPx()))
                drawCircle(Color(0xFF5D873F), size.width * 0.18f, Offset(size.width * 0.28f, size.height * 0.28f))
                drawCircle(Color(0xFF739B50), size.width * 0.20f, Offset(size.width * 0.55f, size.height * 0.22f))
                drawLine(accent, Offset(size.width * 0.39f, size.height * 0.47f), Offset(size.width * 0.61f, size.height * 0.47f), 2.dp.toPx(), StrokeCap.Round)
            }
            ThemeRegistry.MEMPOOL -> {
                val cart = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.35f)
                    lineTo(size.width * 0.82f, size.height * 0.35f)
                    lineTo(size.width * 0.70f, size.height * 0.70f)
                    lineTo(size.width * 0.30f, size.height * 0.70f)
                    close()
                }
                drawPath(cart, Color(0xFF717980))
                drawCircle(Color(0xFFF7932E), size.width * 0.09f, Offset(size.width * 0.42f, size.height * 0.51f))
                drawCircle(Color(0xFFF7932E), size.width * 0.07f, Offset(size.width * 0.59f, size.height * 0.53f))
                drawCircle(Color(0xFF202326), size.width * 0.09f, Offset(size.width * 0.34f, size.height * 0.79f))
                drawCircle(Color(0xFF202326), size.width * 0.09f, Offset(size.width * 0.66f, size.height * 0.79f))
            }
            else -> {
                val vessel = Path().apply {
                    moveTo(size.width * 0.40f, size.height * 0.12f)
                    lineTo(size.width * 0.40f, size.height * 0.31f)
                    cubicTo(size.width * 0.24f, size.height * 0.42f, size.width * 0.24f, size.height * 0.84f, size.width * 0.50f, size.height * 0.87f)
                    cubicTo(size.width * 0.76f, size.height * 0.84f, size.width * 0.76f, size.height * 0.42f, size.width * 0.60f, size.height * 0.31f)
                    lineTo(size.width * 0.60f, size.height * 0.12f)
                    close()
                }
                drawPath(vessel, Brush.horizontalGradient(listOf(Color(0xFF38E66B), Color(0xFF7B5DFF), Color(0xFF147DFF))))
                drawPath(vessel, Color.White.copy(alpha = 0.62f), style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

private fun String?.toComposeColor(fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(this ?: ""))
}.getOrDefault(fallback)

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1D2330), Color(0xFF12161F))))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(label, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Ink, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyStatePanel(title: String, detail: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel.copy(alpha = 0.82f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                Modifier
                    .size(48.dp)
                    .semantics { contentDescription = "Ledger vuoto" }
            ) {
                drawCircle(Color(0xFFB8F36B).copy(alpha = 0.10f), size.minDimension * 0.5f)
                drawCircle(Color(0xFFB8F36B).copy(alpha = 0.75f), size.minDimension * 0.33f, style = Stroke(1.5.dp.toPx()))
                drawLine(
                    Color(0xFFB8F36B),
                    Offset(size.width * 0.50f, size.height * 0.34f),
                    Offset(size.width * 0.50f, size.height * 0.66f),
                    2.dp.toPx(),
                    StrokeCap.Round
                )
                drawLine(
                    Color(0xFFB8F36B),
                    Offset(size.width * 0.34f, size.height * 0.50f),
                    Offset(size.width * 0.66f, size.height * 0.50f),
                    2.dp.toPx(),
                    StrokeCap.Round
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
                Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InsightsScreen(snapshot: LedgerSnapshot) {
    var filterDraft by rememberSaveable {
        mutableStateOf(ExpenseFilterDraft(timeRange = TimeRangePreset.THIS_MONTH))
    }
    val filter = remember(filterDraft) { filterDraft.toExpenseFilter() }
    val analytics = remember(snapshot, filter) {
        com.nomisma.app.domain.AnalyticsCalculator.calculate(snapshot, filter)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Analisi", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text("Analisi locale · filtri combinabili", color = Muted)
        SavedFilterControls(filterDraft, onApply = { filterDraft = it })
        ExpenseFilterControls(
            snapshot = snapshot,
            draft = filterDraft,
            defaultRange = TimeRangePreset.THIS_MONTH,
            onChange = { filterDraft = it }
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("TOTALE", analytics.totalMinor.formatMinorUnits(snapshot.currency), Modifier.weight(1f))
            MetricCard("MEDIA", analytics.averageMinor.formatMinorUnits(snapshot.currency), Modifier.weight(1f))
            MetricCard("VOCI", analytics.transactionCount.toString(), Modifier.weight(1f))
        }
        if (analytics.transactionCount > 0) {
            FilteredBalanceCard(
                balance = analytics.filteredBalance,
                participants = snapshot.participants,
                currency = snapshot.currency
            )
        }
        analytics.periodComparison
            ?.takeIf { it.currentTransactionCount > 0 || it.previousTransactionCount > 0 }
            ?.let { PeriodComparisonCard(it, snapshot.currency) }
        if (analytics.transactionCount == 0) {
            EmptyStatePanel(
                title = "Nessun dato per questi filtri",
                detail = "Cambia periodo o filtri, oppure registra una nuova spesa."
            )
        } else {
            CategoryDonut(
                values = analytics.byCategory.mapKeys { snapshot.categoryDisplayName(it.key) },
                totalMinor = analytics.totalMinor,
                currency = snapshot.currency
            )
            SpendingTimeline(analytics, snapshot.currency)
        }
        SettlementHistory(snapshot)
        if (analytics.transactionCount > 0) {
            AnalyticsSection(
                "Per categoria",
                analytics.byCategory.mapKeys { snapshot.categoryDisplayName(it.key) },
                snapshot.currency
            )
            AnalyticsSection("Per partecipante", analytics.byParticipant.mapKeys { key -> snapshot.participants.firstOrNull { it.id == key.key }?.name ?: key.key }, snapshot.currency)
            if (analytics.byTag.isNotEmpty()) AnalyticsSection("Per tag", analytics.byTag, snapshot.currency)
            if (analytics.byMerchant.isNotEmpty()) AnalyticsSection("Per esercente", analytics.byMerchant, snapshot.currency)
        }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun SavedFilterControls(
    current: ExpenseFilterDraft,
    onApply: (ExpenseFilterDraft) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { SavedFilterStore(context) }
    var savedFilters by remember { mutableStateOf(store.load()) }
    var showSaveFilter by rememberSaveable { mutableStateOf(false) }
    var showManageFilters by rememberSaveable { mutableStateOf(false) }
    var filterName by rememberSaveable { mutableStateOf("") }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            enabled = current.validationError() == null,
            onClick = { showSaveFilter = true }
        ) { Text("Salva vista") }
        if (savedFilters.isNotEmpty()) {
            OutlinedButton(onClick = { showManageFilters = true }) { Text("Gestisci viste") }
        }
    }
    if (savedFilters.isNotEmpty()) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            savedFilters.forEach { saved ->
                FilterChip(
                    selected = saved.filter == current,
                    onClick = { onApply(saved.filter) },
                    label = { Text(saved.name) }
                )
            }
        }
    }

    if (showSaveFilter) {
        AlertDialog(
            onDismissRequest = { showSaveFilter = false },
            title = { Text("Salva vista") },
            text = {
                OutlinedTextField(
                    value = filterName,
                    onValueChange = { filterName = it.take(40) },
                    label = { Text("Nome") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = filterName.isNotBlank(),
                    onClick = {
                        savedFilters = store.upsert(
                            SavedExpenseFilter(
                                name = filterName.trim(),
                                filter = current
                            )
                        )
                        filterName = ""
                        showSaveFilter = false
                    }
                ) { Text("Salva") }
            },
            dismissButton = { TextButton(onClick = { showSaveFilter = false }) { Text("Annulla") } }
        )
    }

    if (showManageFilters) {
        AlertDialog(
            onDismissRequest = { showManageFilters = false },
            title = { Text("Viste salvate") },
            text = {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    savedFilters.forEach { saved ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(saved.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                onApply(saved.filter)
                                showManageFilters = false
                            }) { Text("Carica") }
                            TextButton(onClick = {
                                savedFilters = store.delete(saved.id)
                                if (savedFilters.isEmpty()) showManageFilters = false
                            }) { Text("Elimina", color = Color(0xFFFF8A80)) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showManageFilters = false }) { Text("Chiudi") } }
        )
    }
}

@Composable
private fun ExpenseFilterControls(
    snapshot: LedgerSnapshot,
    draft: ExpenseFilterDraft,
    defaultRange: TimeRangePreset,
    onChange: (ExpenseFilterDraft) -> Unit
) {
    val availableTags = remember(snapshot) {
        (snapshot.activeTags.map(TagDefinition::name) + snapshot.activeExpenses.flatMap(Expense::tags))
            .distinct()
            .sorted()
    }
    OutlinedTextField(
        value = draft.searchQuery,
        onValueChange = { onChange(draft.copy(searchQuery = it.take(120))) },
        label = { Text("Cerca descrizione, esercente, note o tag") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Text("Periodo", color = Ink, style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimeRangePreset.entries.forEach { preset ->
            FilterChip(
                selected = draft.timeRange == preset,
                onClick = { onChange(draft.copy(timeRange = preset)) },
                label = { Text(preset.displayName) }
            )
        }
    }
    if (draft.timeRange == TimeRangePreset.CUSTOM) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.customStart,
                onValueChange = { onChange(draft.copy(customStart = it.take(10))) },
                label = { Text("Da (AAAA-MM-GG)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = draft.customEnd,
                onValueChange = { onChange(draft.copy(customEnd = it.take(10))) },
                label = { Text("A (AAAA-MM-GG)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Text("Partecipanti", color = Ink, style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(draft.participantIds.isEmpty(), { onChange(draft.copy(participantIds = emptySet())) }, label = { Text("Tutti") })
        snapshot.participants.forEach { participant ->
            FilterChip(
                selected = participant.id in draft.participantIds,
                onClick = { onChange(draft.copy(participantIds = draft.participantIds.toggled(participant.id))) },
                label = { Text(participant.name) }
            )
        }
    }
    Text("Categorie", color = Ink, style = MaterialTheme.typography.labelMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(draft.categoryIds.isEmpty(), { onChange(draft.copy(categoryIds = emptySet())) }, label = { Text("Tutte") })
        snapshot.categories.filter { !it.isArchived || it.usageCount > 0 }.forEach { category ->
            FilterChip(
                selected = category.id in draft.categoryIds,
                onClick = { onChange(draft.copy(categoryIds = draft.categoryIds.toggled(category.id))) },
                label = { Text(category.displayName) }
            )
        }
    }
    if (availableTags.isNotEmpty()) {
        Text("Tag · devono corrispondere tutti", color = Ink, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(draft.tags.isEmpty(), { onChange(draft.copy(tags = emptySet())) }, label = { Text("Tutti") })
            availableTags.forEach { tag ->
                FilterChip(
                    selected = tag in draft.tags,
                    onClick = { onChange(draft.copy(tags = draft.tags.toggled(tag))) },
                    label = { Text("#$tag") }
                )
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.minAmount,
            onValueChange = { onChange(draft.copy(minAmount = it.take(14))) },
            label = { Text("Importo minimo") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = draft.maxAmount,
            onValueChange = { onChange(draft.copy(maxAmount = it.take(14))) },
            label = { Text("Importo massimo") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
    draft.validationError()?.let { error ->
        Text(error, color = Color(0xFFFF8A80), style = MaterialTheme.typography.bodySmall)
    }
    if (draft.activeCriteriaCount > 0) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${draft.activeCriteriaCount} filtri attivi", color = Muted, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onChange(ExpenseFilterDraft(timeRange = defaultRange)) }) { Text("Azzera") }
        }
    }
}

private fun Set<String>.toggled(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun SettlementHistory(snapshot: LedgerSnapshot) {
    var selectedCycle by remember { mutableStateOf<SettlementCycle?>(null) }
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cicli precedenti", color = Ink, fontWeight = FontWeight.SemiBold)
                if (snapshot.previousCycles.isNotEmpty()) {
                    Text(snapshot.previousCycles.size.toString(), color = Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (snapshot.previousCycles.isEmpty()) {
                Text("Nessun ciclo ancora riconciliato", color = Muted)
            } else {
                snapshot.previousCycles.take(6).forEach { cycle ->
                    val creditor = snapshot.participants.firstOrNull { it.id == cycle.creditorId }
                    Row(
                        Modifier.fillMaxWidth().clickable { selectedCycle = cycle }.padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(cycle.closedAtMillis?.formatLocalDate().orEmpty(), color = Ink)
                            Text(
                                "${cycle.transactionCount} voci · ${cycle.method.displayName} · " +
                                    (creditor?.let { "credito ${it.name} ${cycle.balanceMinor.formatMinorUnits(snapshot.currency)}" } ?: "in pari"),
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(cycle.totalMinor.formatMinorUnits(snapshot.currency), color = Ink, fontWeight = FontWeight.SemiBold)
                            Text("Dettagli ›", color = Color(0xFFB8F36B), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider(color = PanelLight)
                }
            }
        }
    }

    selectedCycle?.let { cycle ->
        SettlementCycleDialog(snapshot, cycle, onDismiss = { selectedCycle = null })
    }
}

@Composable
private fun SettlementCycleDialog(
    snapshot: LedgerSnapshot,
    cycle: SettlementCycle,
    onDismiss: () -> Unit
) {
    val expenses = remember(snapshot.expenses, cycle.id) {
        snapshot.activeExpenses.filter { it.cycleId == cycle.id }.sortedByDescending(Expense::createdAtMillis)
    }
    val creditor = snapshot.participants.firstOrNull { it.id == cycle.creditorId }
    val closedBy = snapshot.participants.firstOrNull { it.id == cycle.closedByParticipantId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ciclo del ${cycle.closedAtMillis?.formatLocalDate().orEmpty()}") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text(
                    "${cycle.startedAtMillis.formatLocalDate()} — ${cycle.closedAtMillis?.formatLocalDate().orEmpty()}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("TOTALE", cycle.totalMinor.formatMinorUnits(snapshot.currency), Modifier.weight(1f))
                    MetricCard("VOCI", cycle.transactionCount.toString(), Modifier.weight(1f))
                }
                Text(
                    creditor?.let { "Credito finale: ${it.name} · ${cycle.balanceMinor.formatMinorUnits(snapshot.currency)}" }
                        ?: "Ciclo chiuso in pari",
                    color = if (creditor == null) Ink else Color(0xFFB8F36B),
                    fontWeight = FontWeight.SemiBold
                )
                snapshot.participants.forEach { participant ->
                    val contribution = expenses.filter { it.authorId == participant.id }.sumOf(Expense::amountMinor)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(participant.name, color = Muted)
                        Text(contribution.formatMinorUnits(snapshot.currency), color = Ink)
                    }
                }
                closedBy?.let {
                    Text("Riconciliato da ${it.name}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("Metodo: ${cycle.method.displayName}", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (cycle.note.isNotBlank()) {
                    Text("Nota", color = Muted, style = MaterialTheme.typography.labelMedium)
                    Text(cycle.note, color = Ink, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(color = PanelLight)
                Text("Movimenti", color = Ink, fontWeight = FontWeight.SemiBold)
                if (expenses.isEmpty()) {
                    Text("Nessun movimento disponibile", color = Muted)
                } else {
                    expenses.forEach { expense ->
                        ExpenseRow(expense, snapshot, isLocked = true)
                        HorizontalDivider(color = PanelLight)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
}

private fun Long.formatLocalDate(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ITALIAN))

private val ChartPalette = listOf(
    Color(0xFFB8F36B),
    Color(0xFF5BC0EB),
    Color(0xFFFFA552),
    Color(0xFFB49CFF),
    Color(0xFFFF6F91),
    Color(0xFF63D2B0)
)

@Composable
private fun CategoryDonut(values: Map<String, Long>, totalMinor: Long, currency: String) {
    val entries = values.toList().sortedByDescending { it.second }.take(6)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A202B), Color(0xFF10141C))))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Composizione della spesa", color = Ink, fontWeight = FontWeight.SemiBold)
            if (entries.isEmpty() || totalMinor <= 0L) {
                Text("Nessun dato nel periodo selezionato", color = Muted)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                        val chartDescription = entries.joinToString(", ", prefix = "Spesa per categoria: ") { (name, amount) ->
                            "$name ${amount.formatMinorUnits(currency)}"
                        }
                        Canvas(
                            Modifier
                                .fillMaxSize()
                                .semantics { contentDescription = chartDescription }
                        ) {
                            val strokeWidth = 17.dp.toPx()
                            val inset = strokeWidth / 2f + 2.dp.toPx()
                            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                            var startAngle = -90f
                            entries.forEachIndexed { index, entry ->
                                val sweep = entry.second.toFloat() / totalMinor.toFloat() * 360f
                                drawArc(
                                    color = ChartPalette[index % ChartPalette.size],
                                    startAngle = startAngle + 1.4f,
                                    sweepAngle = (sweep - 2.8f).coerceAtLeast(0.8f),
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += sweep
                            }
                            drawCircle(Color.White.copy(alpha = 0.06f), radius = size.minDimension * 0.28f, center = center, style = Stroke(1.dp.toPx()))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("TOTALE", color = Muted, style = MaterialTheme.typography.labelSmall)
                            Text(totalMinor.formatMinorUnits(currency), color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.take(5).forEachIndexed { index, (name, amount) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(ChartPalette[index % ChartPalette.size], CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(name, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${(amount * 100L / totalMinor).coerceIn(0L, 100L)}%", color = Ink, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendingTimeline(analytics: AnalyticsSnapshot, currency: String) {
    val points = analytics.spendingOverTime
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Spesa nel tempo", color = Ink, fontWeight = FontWeight.SemiBold)
            Text(
                when (analytics.aggregationPeriod) {
                    AggregationPeriod.DAY -> "Aggregazione giornaliera"
                    AggregationPeriod.WEEK -> "Aggregazione settimanale"
                    AggregationPeriod.MONTH -> "Aggregazione mensile"
                },
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
            if (points.isEmpty()) {
                Text("Nessun dato nel periodo selezionato", color = Muted)
            } else {
                val max = points.maxOf { it.totalMinor }.coerceAtLeast(1L)
                val timelineDescription = points.joinToString(", ", prefix = "Spesa nel tempo: ") { point ->
                    "${point.label} ${point.totalMinor.formatMinorUnits(currency)}"
                }
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .semantics { contentDescription = timelineDescription }
                ) {
                    val chartTop = 10.dp.toPx()
                    val chartBottom = size.height - 12.dp.toPx()
                    repeat(4) { grid ->
                        val y = chartTop + (chartBottom - chartTop) * grid / 3f
                        drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                    val line = Path()
                    val area = Path()
                    points.forEachIndexed { index, point ->
                        val x = if (points.size == 1) size.width / 2f else size.width * index / (points.size - 1f)
                        val y = chartBottom - (chartBottom - chartTop) * (point.totalMinor.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                        if (index == 0) {
                            line.moveTo(x, y)
                            area.moveTo(x, chartBottom)
                            area.lineTo(x, y)
                        } else {
                            line.lineTo(x, y)
                            area.lineTo(x, y)
                        }
                        if (points.size <= 14 || index % (points.size / 10).coerceAtLeast(1) == 0) {
                            drawCircle(Color(0xFFB8F36B), 2.7.dp.toPx(), Offset(x, y))
                            drawCircle(Color(0xFFB8F36B).copy(alpha = 0.18f), 7.dp.toPx(), Offset(x, y))
                        }
                    }
                    area.lineTo(if (points.size == 1) size.width / 2f else size.width, chartBottom)
                    area.close()
                    drawPath(area, Brush.verticalGradient(listOf(Color(0xFFB8F36B).copy(alpha = 0.28f), Color.Transparent), chartTop, chartBottom))
                    drawPath(line, Color(0xFFB8F36B), style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(points.first().label, color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text(max.formatMinorUnits(currency), color = Muted, style = MaterialTheme.typography.labelSmall)
                    Text(points.last().label, color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PeriodComparisonCard(comparison: PeriodComparison, currency: String) {
    val scale = maxOf(comparison.currentTotalMinor, comparison.previousTotalMinor, 1L)
    val changeColor = when {
        comparison.changeMinor > 0L -> Color(0xFFFFB86B)
        comparison.changeMinor < 0L -> Color(0xFF74C7FF)
        else -> Muted
    }
    val changeLabel = when {
        comparison.previousTotalMinor == 0L && comparison.currentTotalMinor > 0L ->
            "Nessuna spesa nel periodo precedente"
        comparison.changeMinor == 0L -> "Spesa invariata rispetto al periodo precedente"
        else -> {
            val direction = if (comparison.changeMinor > 0L) "+" else "−"
            val amount = kotlin.math.abs(comparison.changeMinor).formatMinorUnits(currency)
            val percent = comparison.changePercent?.let {
                " · ${kotlin.math.abs(it).toInt()}%"
            }.orEmpty()
            "$direction$amount$percent rispetto al periodo precedente"
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Confronto temporale", color = Ink, fontWeight = FontWeight.SemiBold)
            ComparisonBar(
                label = "Periodo attuale · ${comparison.currentTransactionCount} voci",
                amountMinor = comparison.currentTotalMinor,
                scaleMinor = scale,
                currency = currency,
                color = Color(0xFFB8F36B)
            )
            ComparisonBar(
                label = "Periodo precedente · ${comparison.previousTransactionCount} voci",
                amountMinor = comparison.previousTotalMinor,
                scaleMinor = scale,
                currency = currency,
                color = Color(0xFF7B8CA8)
            )
            Text(changeLabel, color = changeColor, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FilteredBalanceCard(
    balance: BalanceSummary,
    participants: List<Participant>,
    currency: String
) {
    val creditor = participants.firstOrNull { it.id == balance.creditorId }
    val accent = creditor?.colorHex.toComposeColor(Color(0xFFB8F36B))
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Saldo della selezione", color = Ink, fontWeight = FontWeight.SemiBold)
            Text(
                creditor?.let { "${it.name} +${balance.amountMinor.formatMinorUnits(currency)}" } ?: "In pari",
                color = if (creditor == null) Ink else accent,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold
            )
            participants.take(2).forEach { participant ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pagato da ${participant.name}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text(
                        (balance.paidByParticipant[participant.id] ?: 0L).formatMinorUnits(currency),
                        color = Ink,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                "È un calcolo analitico sui filtri attivi: non modifica il saldo del ciclo corrente.",
                color = Muted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ComparisonBar(
    label: String,
    amountMinor: Long,
    scaleMinor: Long,
    currency: String,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(amountMinor.formatMinorUnits(currency), color = Ink, style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(PanelLight)) {
            if (amountMinor > 0L) {
                Box(
                    Modifier
                        .fillMaxWidth((amountMinor.toFloat() / scaleMinor.toFloat()).coerceIn(0f, 1f))
                        .height(7.dp)
                        .background(color, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun AnalyticsSection(title: String, values: Map<String, Long>, currency: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
            if (values.isEmpty()) Text("Nessun dato", color = Muted)
            val max = values.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
            values.toList().sortedByDescending { it.second }.take(6).forEachIndexed { index, (name, amount) ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name.replaceFirstChar { it.uppercase() }, color = Muted)
                        Text(amount.formatMinorUnits(currency), color = Ink)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(PanelLight)) {
                        Box(
                            Modifier
                                .fillMaxWidth((amount.toFloat() / max.toFloat()).coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            ChartPalette[index % ChartPalette.size],
                                            ChartPalette[index % ChartPalette.size].copy(alpha = 0.52f)
                                        )
                                    ),
                                    CircleShape
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(snapshot: LedgerSnapshot, onEdit: (Expense) -> Unit) {
    var filterDraft by rememberSaveable { mutableStateOf(ExpenseFilterDraft()) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    val filter = remember(filterDraft) { filterDraft.toExpenseFilter() }
    val filtered = remember(snapshot, filter) {
        snapshot.activeExpenses.filter(filter::matches).sortedByDescending(Expense::createdAtMillis)
    }
    val grouped = remember(filtered) {
        filtered.groupBy { expense ->
            Instant.ofEpochMilli(expense.createdAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(Modifier.height(18.dp)) }
        item { Text("Cronologia", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold) }
        item { Text("Cerca, filtra o tocca una spesa per modificarla", color = Muted) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { filtersExpanded = !filtersExpanded }) {
                    Text(
                        if (filterDraft.activeCriteriaCount == 0) "Filtri"
                        else "Filtri · ${filterDraft.activeCriteriaCount}"
                    )
                }
                if (filterDraft.activeCriteriaCount > 0) {
                    TextButton(onClick = { filterDraft = ExpenseFilterDraft() }) { Text("Azzera") }
                }
            }
        }
        if (filtersExpanded) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SavedFilterControls(filterDraft, onApply = {
                        filterDraft = it
                        filtersExpanded = true
                    })
                    ExpenseFilterControls(
                        snapshot = snapshot,
                        draft = filterDraft,
                        defaultRange = TimeRangePreset.ALL_TIME,
                        onChange = { filterDraft = it }
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("RISULTATI", filtered.size.toString(), Modifier.weight(1f))
                MetricCard(
                    "TOTALE",
                    filtered.sumOf(Expense::amountMinor).formatMinorUnits(snapshot.currency),
                    Modifier.weight(1f)
                )
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyStatePanel(
                    title = if (filterDraft.activeCriteriaCount == 0) "Nessuna spesa registrata" else "Nessun risultato",
                    detail = if (filterDraft.activeCriteriaCount == 0) {
                        "Tocca + per creare la prima voce del ledger."
                    } else {
                        "Prova ad allargare il periodo o ad azzerare alcuni filtri."
                    }
                )
            }
        } else {
            grouped.forEach { (date, expenses) ->
                item(key = "date-$date") {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN))
                            .replaceFirstChar { it.uppercase() },
                        color = Muted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(expenses, key = Expense::id) { expense ->
                    val isClosedCycle = snapshot.currentCycle != null && expense.cycleId != snapshot.currentCycle?.id
                    ExpenseRow(
                        expense,
                        snapshot,
                        onClick = if (isClosedCycle) null else ({ onEdit(expense) }),
                        isLocked = isClosedCycle
                    )
                    HorizontalDivider(color = PanelLight)
                }
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    snapshot: LedgerSnapshot,
    onClick: (() -> Unit)? = null,
    isLocked: Boolean = false
) {
    val participant = snapshot.participants.firstOrNull { it.id == expense.authorId }
    val modifier = if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onClick)
    Row(modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        val accent = participant?.colorHex.toComposeColor(Color.White)
        Box(
            Modifier
                .size(43.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.14f))
                .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            CategoryGlyph(expense.categoryId, accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(expense.description, color = Ink, fontWeight = FontWeight.Medium)
            if (expense.merchant.isNotBlank()) {
                Text(expense.merchant, color = Ink.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${expense.createdAtMillis.formatLocalDateTime()} · ${if (isLocked) "Ciclo chiuso · " else ""}${participant?.name ?: expense.authorId} · ${snapshot.categoryDisplayName(expense.categoryId)}${if (expense.tags.isEmpty()) "" else " · ${expense.tags.joinToString(" ") { "#$it" }}"}",
                color = Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(expense.amountMinor.formatMinorUnits(snapshot.currency), color = Ink, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryGlyph(categoryId: String, color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        when (categoryId) {
            "groceries", "shopping" -> {
                val basket = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.40f)
                    lineTo(size.width * 0.28f, size.height * 0.80f)
                    lineTo(size.width * 0.72f, size.height * 0.80f)
                    lineTo(size.width * 0.82f, size.height * 0.40f)
                    close()
                }
                drawPath(basket, color, style = stroke)
                drawArc(color, 200f, 140f, false, Offset(size.width * 0.30f, size.height * 0.15f), Size(size.width * 0.40f, size.height * 0.45f), style = stroke)
            }
            "dining" -> {
                drawLine(color, Offset(size.width * 0.34f, size.height * 0.15f), Offset(size.width * 0.34f, size.height * 0.84f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.22f, size.height * 0.16f), Offset(size.width * 0.22f, size.height * 0.42f), stroke.width)
                drawLine(color, Offset(size.width * 0.46f, size.height * 0.16f), Offset(size.width * 0.46f, size.height * 0.42f), stroke.width)
                drawArc(color, 0f, 180f, false, Offset(size.width * 0.22f, size.height * 0.29f), Size(size.width * 0.24f, size.height * 0.24f), style = stroke)
                drawLine(color, Offset(size.width * 0.68f, size.height * 0.15f), Offset(size.width * 0.68f, size.height * 0.84f), stroke.width, StrokeCap.Round)
                drawArc(color, 90f, 180f, false, Offset(size.width * 0.58f, size.height * 0.14f), Size(size.width * 0.20f, size.height * 0.36f), style = stroke)
            }
            "housing", "home", "utilities" -> {
                val house = Path().apply {
                    moveTo(size.width * 0.14f, size.height * 0.48f)
                    lineTo(size.width * 0.50f, size.height * 0.18f)
                    lineTo(size.width * 0.86f, size.height * 0.48f)
                    lineTo(size.width * 0.76f, size.height * 0.48f)
                    lineTo(size.width * 0.76f, size.height * 0.82f)
                    lineTo(size.width * 0.24f, size.height * 0.82f)
                    lineTo(size.width * 0.24f, size.height * 0.48f)
                }
                drawPath(house, color, style = stroke)
                drawRect(color.copy(alpha = 0.72f), Offset(size.width * 0.44f, size.height * 0.58f), Size(size.width * 0.14f, size.height * 0.24f))
            }
            "transport", "fuel", "travel" -> {
                drawRoundRect(color, Offset(size.width * 0.15f, size.height * 0.38f), Size(size.width * 0.70f, size.height * 0.30f), CornerRadius(5.dp.toPx()), style = stroke)
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.38f), Offset(size.width * 0.38f, size.height * 0.23f), stroke.width)
                drawLine(color, Offset(size.width * 0.38f, size.height * 0.23f), Offset(size.width * 0.67f, size.height * 0.23f), stroke.width)
                drawLine(color, Offset(size.width * 0.67f, size.height * 0.23f), Offset(size.width * 0.77f, size.height * 0.38f), stroke.width)
                drawCircle(color, size.width * 0.09f, Offset(size.width * 0.31f, size.height * 0.72f), style = stroke)
                drawCircle(color, size.width * 0.09f, Offset(size.width * 0.69f, size.height * 0.72f), style = stroke)
            }
            "health", "personal_care" -> {
                drawRoundRect(color, Offset(size.width * 0.40f, size.height * 0.16f), Size(size.width * 0.20f, size.height * 0.68f), CornerRadius(2.dp.toPx()))
                drawRoundRect(color, Offset(size.width * 0.16f, size.height * 0.40f), Size(size.width * 0.68f, size.height * 0.20f), CornerRadius(2.dp.toPx()))
            }
            "entertainment", "subscriptions" -> {
                drawCircle(color, size.width * 0.36f, center, style = stroke)
                val play = Path().apply {
                    moveTo(size.width * 0.44f, size.height * 0.34f)
                    lineTo(size.width * 0.68f, size.height * 0.50f)
                    lineTo(size.width * 0.44f, size.height * 0.66f)
                    close()
                }
                drawPath(play, color)
            }
            else -> {
                drawCircle(color, size.width * 0.34f, center, style = stroke)
                drawCircle(color, size.width * 0.08f, center)
            }
        }
    }
}

private fun Long.formatLocalDateTime(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

@Composable
private fun ExpenseEditorDialog(
    snapshot: LedgerSnapshot,
    expense: Expense?,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, List<String>, String, String, Long, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val entryPreferences = remember { ExpenseEntryPreferences(context) }
    val persistedRecents = remember(expense?.id) { entryPreferences.load() }
    val recentCategoryIds = remember(snapshot.expenses, snapshot.activeCategories, persistedRecents) {
        val activeIds = snapshot.activeCategories.map(ExpenseCategory::id).toSet()
        (persistedRecents.categoryIds + snapshot.activeExpenses.asReversed().map(Expense::categoryId))
            .distinct()
            .filter(activeIds::contains)
            .take(6)
    }
    val categoryOptions = remember(recentCategoryIds, snapshot.activeCategories) {
        val recent = recentCategoryIds.mapNotNull { id -> snapshot.activeCategories.firstOrNull { it.id == id } }
        recent + snapshot.activeCategories.filterNot { option -> recent.any { it.id == option.id } }
    }
    val quickTags = remember(snapshot.activeTags, persistedRecents) {
        val activeNames = snapshot.activeTags.map(TagDefinition::name).toSet()
        val recent = persistedRecents.tags.filter(activeNames::contains)
        val byUsage = snapshot.activeTags.sortedByDescending(TagDefinition::usageCount).map(TagDefinition::name)
        (recent + byUsage).distinct()
    }
    var amount by rememberSaveable(expense?.id) { mutableStateOf(expense?.amountMinor?.toEditableAmount().orEmpty()) }
    var description by rememberSaveable(expense?.id) { mutableStateOf(expense?.description.orEmpty()) }
    var category by rememberSaveable(expense?.id) {
        mutableStateOf(expense?.categoryId ?: recentCategoryIds.firstOrNull() ?: snapshot.activeCategories.firstOrNull()?.id.orEmpty())
    }
    var tags by rememberSaveable(expense?.id) { mutableStateOf(expense?.tags?.joinToString(", ").orEmpty()) }
    var merchant by rememberSaveable(expense?.id) { mutableStateOf(expense?.merchant.orEmpty()) }
    var notes by rememberSaveable(expense?.id) { mutableStateOf(expense?.notes.orEmpty()) }
    var occurredDate by rememberSaveable(expense?.id) {
        mutableStateOf((expense?.createdAtMillis ?: System.currentTimeMillis()).toEditableDate())
    }
    val expenseIsFromToday = expense?.createdAtMillis?.let { millis ->
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
    } ?: true
    var showAdvanced by rememberSaveable(expense?.id) {
        mutableStateOf(!expense?.merchant.isNullOrBlank() || !expense?.notes.isNullOrBlank() || !expenseIsFromToday)
    }
    var showDeleteConfirmation by rememberSaveable(expense?.id) { mutableStateOf(false) }
    val defaultAuthor = persistedRecents.participantId
        ?.takeIf { id -> snapshot.participants.any { it.id == id } }
        ?: localOperationAuthor(context, snapshot)
    var author by rememberSaveable(expense?.id) { mutableStateOf(expense?.authorId ?: defaultAuthor) }
    val amountMinor = ExpenseParser.parseMinorUnits(amount)
    val occurredAtMillis = remember(occurredDate, expense?.createdAtMillis) {
        parseExpenseDate(occurredDate, expense?.createdAtMillis)
    }
    val normalizedTags = remember(tags) {
        tags.split(',').map(::canonicalTagInput).filter(String::isNotBlank).distinct()
    }
    val tagsAreValid = normalizedTags.size <= ExpensePolicy.MAX_TAGS &&
        normalizedTags.all { it.length <= ExpensePolicy.MAX_TAG_LENGTH }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (expense == null) "Nuova spesa" else "Modifica spesa") },
        text = {
            Column(
                Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Importo") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(
                    description,
                    { description = it.take(ExpensePolicy.MAX_DESCRIPTION_LENGTH) },
                    label = { Text("Descrizione") },
                    singleLine = true
                )
                Text("Chi ha pagato?", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    snapshot.participants.forEach { participant ->
                        FilterChip(author == participant.id, { author = participant.id }, label = { Text(participant.name) })
                    }
                }
                Text("Categoria", style = MaterialTheme.typography.labelMedium)
                if (recentCategoryIds.isNotEmpty()) {
                    Text("Le più recenti sono mostrate per prime", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    categoryOptions.forEach { option ->
                        FilterChip(category == option.id, { category = option.id }, label = { Text(option.displayName) })
                    }
                }
                OutlinedTextField(
                    tags,
                    { tags = it.take((ExpensePolicy.MAX_TAG_LENGTH + 2) * ExpensePolicy.MAX_TAGS) },
                    label = { Text("Tag opzionali, separati da virgola") },
                    singleLine = true
                )
                if (!tagsAreValid) {
                    Text(
                        "Massimo ${ExpensePolicy.MAX_TAGS} tag, ${ExpensePolicy.MAX_TAG_LENGTH} caratteri ciascuno",
                        color = Color(0xFFFF8A80),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (quickTags.isNotEmpty()) {
                    Text("Tag rapidi", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        quickTags.forEach { tag ->
                            val selected = tags.split(',').map(::canonicalTagInput).contains(tag)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val selectedTags = tags.split(',')
                                        .map(::canonicalTagInput)
                                        .filter(String::isNotBlank)
                                        .toMutableList()
                                    if (tag in selectedTags) selectedTags.remove(tag) else selectedTags += tag
                                    tags = selectedTags.distinct().joinToString(", ")
                                },
                                label = { Text("#$tag") }
                            )
                        }
                    }
                }
                val previewParticipant = snapshot.participants.firstOrNull { it.id == author }
                val previewAccent = previewParticipant?.colorHex.toComposeColor(Color(0xFFB8F36B))
                Surface(color = PanelLight, shape = RoundedCornerShape(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            Modifier.size(38.dp).background(previewAccent.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            CategoryGlyph(category, previewAccent)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(description.ifBlank { "Anteprima spesa" }, color = Ink, fontWeight = FontWeight.Medium)
                            Text(
                                "${previewParticipant?.name.orEmpty()} · ${snapshot.categoryDisplayName(category)}",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(amountMinor?.formatMinorUnits(snapshot.currency) ?: "—", color = Ink, fontWeight = FontWeight.SemiBold)
                    }
                }
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "Nascondi dettagli opzionali" else "Aggiungi data, esercente e note")
                }
                if (showAdvanced) {
                    OutlinedTextField(
                        value = occurredDate,
                        onValueChange = { occurredDate = it.take(10) },
                        label = { Text("Data della spesa (AAAA-MM-GG)") },
                        supportingText = {
                            if (occurredAtMillis == null) Text("Inserisci una data valida, non futura")
                        },
                        isError = occurredAtMillis == null,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = merchant,
                        onValueChange = { merchant = it.take(ExpensePolicy.MAX_MERCHANT_LENGTH) },
                        label = { Text("Esercente") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(ExpensePolicy.MAX_NOTES_LENGTH) },
                        label = { Text("Note") },
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = amountMinor != null && description.isNotBlank() && author.isNotBlank() &&
                    category.isNotBlank() && tagsAreValid && occurredAtMillis != null,
                onClick = {
                    onSave(
                        amountMinor!!,
                        description.trim(),
                        category,
                        normalizedTags,
                        merchant.trim(),
                        notes.trim(),
                        occurredAtMillis!!,
                        author
                    )
                    entryPreferences.record(category, normalizedTags, author)
                }
            ) { Text("Salva") }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = { showDeleteConfirmation = true }) {
                        Text("Elimina", color = Color(0xFFFF8A80))
                    }
                }
                TextButton(onClick = onDismiss) { Text("Annulla") }
            }
        }
    )

    if (showDeleteConfirmation && expense != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminare questa spesa?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(expense.description, color = Ink, fontWeight = FontWeight.SemiBold)
                    Text(expense.amountMinor.formatMinorUnits(snapshot.currency), color = Muted)
                    Text(
                        "La spesa verrà rimossa dal ledger tramite un'operazione sincronizzabile. Questa azione non può essere annullata dall'app.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDelete()
                }) { Text("Elimina definitivamente", color = Color(0xFFFF8A80)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Mantieni") }
            }
        )
    }
}

private fun Long.toEditableAmount(): String = "${this / 100}.${(this % 100).toString().padStart(2, '0')}"

private fun Long.toEditableDate(): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .toString()

private fun parseExpenseDate(raw: String, existingMillis: Long?): Long? = runCatching {
    val zone = ZoneId.systemDefault()
    val date = LocalDate.parse(raw.trim())
    val existingDate = existingMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val now = System.currentTimeMillis()
    val value = when {
        existingMillis != null && existingDate == date -> existingMillis
        date == Instant.ofEpochMilli(now).atZone(zone).toLocalDate() -> now
        else -> date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    }
    ExpensePolicy.validateOccurredAt(value, now)
    value
}.getOrNull()

@Composable
private fun NomismaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFB8F36B),
            onPrimary = Color(0xFF10140E),
            background = Night,
            surface = Panel,
            onBackground = Ink,
            onSurface = Ink
        ),
        content = content
    )
}
