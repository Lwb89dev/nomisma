package com.nomisma.app.data

import android.content.ContentValues
import android.content.Context
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import com.nomisma.app.domain.Expense
import com.nomisma.app.domain.ExpenseCategory
import com.nomisma.app.domain.ExpenseCategories
import com.nomisma.app.domain.LedgerOperation
import com.nomisma.app.domain.LedgerSnapshot
import com.nomisma.app.domain.LedgerMetadataPolicy
import com.nomisma.app.domain.Participant
import com.nomisma.app.domain.ParticipantNamePolicy
import com.nomisma.app.domain.SettlementCycle
import com.nomisma.app.domain.SettlementMethod
import com.nomisma.app.domain.SettlementPolicy
import com.nomisma.app.domain.TagDefinition
import com.nomisma.app.domain.ExpenseParser
import com.nomisma.app.domain.ExpensePolicy
import com.nomisma.app.domain.OperationType
import com.nomisma.app.domain.OperationSequencePolicy
import com.nomisma.app.domain.SequenceDisposition
import com.nomisma.app.domain.SyncState
import com.nomisma.app.theme.ThemeRegistry
import com.nomisma.app.theme.ThemeIdentityRegistry
import com.nomisma.app.theme.ParticipantColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RemoteOperationStatus {
    APPLIED,
    DEFERRED,
    DUPLICATE,
    REJECTED
}

data class RemoteOperationResult(
    val status: RemoteOperationStatus,
    val message: String? = null,
    val drainedOperations: Int = 0
)

data class OperationStats(
    val pending: Int,
    val published: Int,
    val applied: Int,
    val deferred: Int,
    val rejected: Int
)

class LocalLedgerRepository(context: Context) {
    private val database = openEncryptedLedger(context.applicationContext)

    suspend fun loadSnapshot(): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.readSnapshot()
    }

    suspend fun addExpense(
        amountMinor: Long,
        description: String,
        categoryId: String,
        tags: List<String>,
        merchant: String,
        notes: String,
        occurredAtMillis: Long,
        payerId: String,
        operationAuthorId: String = payerId,
        sharedEvenly: Boolean = true
    ): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.insertExpense(
            amountMinor,
            description,
            categoryId,
            tags,
            merchant,
            notes,
            occurredAtMillis,
            payerId,
            operationAuthorId,
            sharedEvenly
        )
        database.readSnapshot()
    }

    suspend fun updateExpense(
        expenseId: String,
        amountMinor: Long,
        description: String,
        categoryId: String,
        tags: List<String>,
        merchant: String,
        notes: String,
        occurredAtMillis: Long,
        payerId: String,
        editorAuthorId: String = payerId,
        sharedEvenly: Boolean = true
    ): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.updateExpense(
            expenseId,
            amountMinor,
            description,
            categoryId,
            tags,
            merchant,
            notes,
            occurredAtMillis,
            payerId,
            editorAuthorId,
            sharedEvenly
        )
        database.readSnapshot()
    }

    suspend fun deleteExpense(expenseId: String, operationAuthorId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.deleteExpense(expenseId, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun createTag(name: String, operationAuthorId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.createTag(name, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun createCategory(name: String, operationAuthorId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.createCategory(name, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun renameCategory(categoryId: String, newName: String, operationAuthorId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.renameCategory(categoryId, newName, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun archiveCategory(categoryId: String, operationAuthorId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.archiveCategory(categoryId, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun renameTag(tagId: String, newName: String, operationAuthorId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.renameTag(tagId, newName, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun archiveTag(tagId: String, operationAuthorId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.archiveTag(tagId, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun settleCurrentCycle(
        operationAuthorId: String,
        method: SettlementMethod,
        note: String
    ): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.settleCurrentCycle(operationAuthorId, method, note)
        database.readSnapshot()
    }

    suspend fun changeTheme(themeId: String, operationAuthorId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.updateTheme(themeId, authorId = operationAuthorId)
        database.readSnapshot()
    }

    suspend fun changeCapacity(capacityMinor: Long, operationAuthorId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.updateCapacity(capacityMinor, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun changeLedgerMetadata(
        title: String,
        currency: String,
        operationAuthorId: String
    ): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.updateLedgerMetadata(title, currency, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun changeParticipantThemeIdentity(
        participantId: String,
        identityId: String,
        operationAuthorId: String
    ): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.updateParticipantThemeIdentity(participantId, identityId, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun changeParticipantColor(
        participantId: String,
        colorHex: String,
        operationAuthorId: String
    ): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.updateParticipantColor(participantId, colorHex, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun changeParticipantName(
        participantId: String,
        name: String,
        operationAuthorId: String
    ): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.updateParticipantName(participantId, name, operationAuthorId)
        database.readSnapshot()
    }

    suspend fun adoptPairingLedger(ledgerId: String): LedgerSnapshot = withContext(Dispatchers.IO) {
        database.adoptPairingLedger(ledgerId)
        database.readSnapshot()
    }

    suspend fun operationStats(): OperationStats = withContext(Dispatchers.IO) {
        database.readOperationStats()
    }

    suspend fun markOperationPublished(operationId: String, transportEventId: String) = withContext(Dispatchers.IO) {
        database.markOperationPublished(operationId, transportEventId)
    }

    suspend fun pendingOperations(): List<LedgerOperation> = withContext(Dispatchers.IO) {
        database.readPendingOperations()
    }

    suspend fun applyRemoteOperation(
        operation: LedgerOperation,
        transportEventId: String
    ): RemoteOperationResult = withContext(Dispatchers.IO) {
        database.applyRemoteOperation(operation, transportEventId)
    }
}

internal class NomismaDatabase(
    context: Context,
    passphrase: ByteArray
) : SQLiteOpenHelper(
    context.applicationContext,
    "nomisma.db",
    passphrase,
    null,
    8,
    0,
    PreserveLedgerFile(),
    null,
    false
) {
    private var cachedLedgerId: String? = null

    override fun onCreate(db: SQLiteDatabase) {
        val ledgerId = UUID.randomUUID().toString()
        db.execSQL(
            """
            CREATE TABLE ledgers (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                currency TEXT NOT NULL,
                capacity_minor INTEGER NOT NULL,
                theme_id TEXT NOT NULL,
                theme_version INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE participants (
                id TEXT PRIMARY KEY NOT NULL,
                ledger_id TEXT NOT NULL,
                name TEXT NOT NULL,
                color_hex TEXT NOT NULL,
                avatar_id TEXT NOT NULL,
                FOREIGN KEY(ledger_id) REFERENCES ledgers(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE categories (
                id TEXT PRIMARY KEY NOT NULL,
                ledger_id TEXT NOT NULL,
                name TEXT NOT NULL,
                is_default INTEGER NOT NULL DEFAULT 0,
                state TEXT NOT NULL DEFAULT 'active',
                UNIQUE(ledger_id, name),
                FOREIGN KEY(ledger_id) REFERENCES ledgers(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE settlement_cycles (
                id TEXT PRIMARY KEY NOT NULL,
                ledger_id TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                closed_at INTEGER,
                total_minor INTEGER NOT NULL DEFAULT 0,
                creditor_id TEXT,
                balance_minor INTEGER NOT NULL DEFAULT 0,
                transaction_count INTEGER NOT NULL DEFAULT 0,
                closed_by_participant_id TEXT,
                settlement_method TEXT NOT NULL DEFAULT 'other',
                settlement_note TEXT NOT NULL DEFAULT '',
                state TEXT NOT NULL,
                FOREIGN KEY(ledger_id) REFERENCES ledgers(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE expenses (
                id TEXT PRIMARY KEY NOT NULL,
                ledger_id TEXT NOT NULL,
                author_id TEXT NOT NULL,
                amount_minor INTEGER NOT NULL,
                currency TEXT NOT NULL,
                description TEXT NOT NULL,
                merchant TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                category_id TEXT NOT NULL,
                author_sequence INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                cycle_id TEXT NOT NULL,
                shared_evenly INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(ledger_id) REFERENCES ledgers(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE tags (
                id TEXT PRIMARY KEY NOT NULL,
                ledger_id TEXT NOT NULL,
                name TEXT NOT NULL,
                state TEXT NOT NULL DEFAULT 'active',
                UNIQUE(ledger_id, name),
                FOREIGN KEY(ledger_id) REFERENCES ledgers(id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE expense_tags (
                expense_id TEXT NOT NULL,
                tag_id TEXT NOT NULL,
                PRIMARY KEY(expense_id, tag_id),
                FOREIGN KEY(expense_id) REFERENCES expenses(id),
                FOREIGN KEY(tag_id) REFERENCES tags(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_expenses_ledger_created ON expenses(ledger_id, created_at)")
        db.execSQL("CREATE INDEX idx_expenses_cycle ON expenses(cycle_id, created_at)")
        db.execSQL("CREATE INDEX idx_settlement_cycles_ledger_state ON settlement_cycles(ledger_id, state, started_at)")
        db.execSQL("CREATE INDEX idx_expenses_author ON expenses(author_id)")
        db.execSQL("CREATE INDEX idx_expense_tags_tag ON expense_tags(tag_id)")
        createOperationLog(db)

        val ledger = ContentValues().apply {
            put("id", ledgerId)
            put("title", "Casa")
            put("currency", "EUR")
            put("capacity_minor", 100_000L)
            put("theme_id", ThemeRegistry.VESSEL)
            put("theme_version", 1)
        }
        db.insertOrThrow("ledgers", null, ledger)
        cachedLedgerId = ledgerId
        insertParticipant(db, ledgerId, "alice", "Alice", "#38E66B", "bear")
        insertParticipant(db, ledgerId, "bob", "Bob", "#147DFF", "squirrel")
        seedDefaultCategories(db)
        seedInitialCycle(db, System.currentTimeMillis(), initialCycleId(ledgerId))
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN author_sequence INTEGER NOT NULL DEFAULT 0")
            createOperationLog(db)
            backfillExpenseOperations(db)
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE expenses ADD COLUMN deleted_at INTEGER")
            db.execSQL("UPDATE expenses SET updated_at = created_at WHERE updated_at = 0")
        }
        if (oldVersion < 4) {
            createSettlementTable(db)
            db.execSQL("ALTER TABLE expenses ADD COLUMN cycle_id TEXT NOT NULL DEFAULT '$INITIAL_CYCLE_ID'")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_cycle ON expenses(cycle_id, created_at)")
            val startedAt = db.rawQuery(
                "SELECT COALESCE(MIN(created_at), ?) FROM expenses WHERE ledger_id = ?",
                arrayOf(System.currentTimeMillis().toString(), ledgerId(db))
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getLong(0)
            }
            seedInitialCycle(db, startedAt, INITIAL_CYCLE_ID)
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN merchant TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE expenses ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 6) {
            createCategoryTable(db)
            seedDefaultCategories(db)
        }
        if (oldVersion < 7) {
            addColumnIfMissing(
                db,
                "settlement_cycles",
                "settlement_method",
                "ALTER TABLE settlement_cycles ADD COLUMN settlement_method TEXT NOT NULL DEFAULT 'other'"
            )
            addColumnIfMissing(
                db,
                "settlement_cycles",
                "settlement_note",
                "ALTER TABLE settlement_cycles ADD COLUMN settlement_note TEXT NOT NULL DEFAULT ''"
            )
        }
        if (oldVersion < 8) {
            addColumnIfMissing(
                db,
                "expenses",
                "shared_evenly",
                "ALTER TABLE expenses ADD COLUMN shared_evenly INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    fun insertExpense(
        amountMinor: Long,
        description: String,
        categoryId: String,
        tags: List<String>,
        merchant: String,
        notes: String,
        occurredAtMillis: Long,
        payerId: String,
        operationAuthorId: String,
        sharedEvenly: Boolean
    ) {
        ExpensePolicy.validate(amountMinor, description, merchant, notes)
        ExpensePolicy.validateOccurredAt(occurredAtMillis)
        normalizeTags(tags)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val currency = ledgerCurrency(db)
            val expenseId = ExpenseParser.newId()
            val now = System.currentTimeMillis()
            val cycleId = activeCycleId(db)
            require(participantExists(db, payerId)) { "Unknown payer" }
            require(participantExists(db, operationAuthorId)) { "Unknown operation author" }
            require(activeCategoryExists(db, categoryId)) { "Unknown or archived category" }
            val authorSequence = nextAuthorSequence(db, operationAuthorId)
            val values = ContentValues().apply {
                put("id", expenseId)
                put("ledger_id", ledgerId(db))
                put("author_id", payerId)
                put("amount_minor", amountMinor)
                put("currency", currency)
                put("description", description.trim())
                put("merchant", merchant.trim())
                put("notes", notes.trim())
                put("category_id", categoryId)
                put("author_sequence", authorSequence)
                put("created_at", occurredAtMillis)
                put("updated_at", now)
                put("cycle_id", cycleId)
                put("shared_evenly", if (sharedEvenly) 1 else 0)
            }
            db.insertOrThrow("expenses", null, values)

            replaceExpenseTags(db, expenseId, tags)

            val operationId = ExpenseParser.newId()
            val operationPayload = org.json.JSONObject().apply {
                put("entry_id", expenseId)
                put("cycle_id", cycleId)
                put("payer_id", payerId)
                put("amount_minor", amountMinor)
                put("currency", currency)
                put("description", description.trim())
                put("merchant", merchant.trim())
                put("notes", notes.trim())
                put("occurred_at_ms", occurredAtMillis)
                put("category", categoryId)
                put("tags", org.json.JSONArray(normalizeTags(tags)))
                put("shared_evenly", sharedEvenly)
            }
            val operation = ContentValues().apply {
                put("operation_id", operationId)
                put("ledger_id", ledgerId(db))
                put("author_id", operationAuthorId)
                put("author_sequence", authorSequence)
                put("operation_type", OperationType.ENTRY_CREATE.wireValue)
                put("payload_json", operationPayload.toString())
                put("created_at", now)
                put("sync_state", "OUTBOX")
            }
            db.insertOrThrow("ledger_operations", null, operation)

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateExpense(
        expenseId: String,
        amountMinor: Long,
        description: String,
        categoryId: String,
        tags: List<String>,
        merchant: String,
        notes: String,
        occurredAtMillis: Long,
        payerId: String,
        editorAuthorId: String,
        sharedEvenly: Boolean
    ) {
        ExpensePolicy.validate(amountMinor, description, merchant, notes)
        ExpensePolicy.validateOccurredAt(occurredAtMillis)
        require(categoryId.isNotBlank()) { "Category is empty" }
        normalizeTags(tags)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val currency = ledgerCurrency(db)
            require(participantExists(db, payerId)) { "Unknown payer" }
            require(participantExists(db, editorAuthorId)) { "Unknown editor" }
            require(activeCategoryExists(db, categoryId)) { "Unknown or archived category" }
            require(editableExpenseExists(db, expenseId)) { "Expense does not exist, was deleted, or belongs to a closed cycle" }
            val now = System.currentTimeMillis()
            val sequence = nextAuthorSequence(db, editorAuthorId)
            db.update(
                "expenses",
                ContentValues().apply {
                    put("author_id", payerId)
                    put("amount_minor", amountMinor)
                    put("description", description.trim())
                    put("merchant", merchant.trim())
                    put("notes", notes.trim())
                    put("category_id", categoryId)
                    put("created_at", occurredAtMillis)
                    put("author_sequence", sequence)
                    put("updated_at", now)
                    put("shared_evenly", if (sharedEvenly) 1 else 0)
                },
                "id = ? AND ledger_id = ?",
                arrayOf(expenseId, ledgerId(db))
            )
            replaceExpenseTags(db, expenseId, tags)
            val payload = JSONObject().apply {
                put("entry_id", expenseId)
                put("payer_id", payerId)
                put("amount_minor", amountMinor)
                put("currency", currency)
                put("description", description.trim())
                put("merchant", merchant.trim())
                put("notes", notes.trim())
                put("occurred_at_ms", occurredAtMillis)
                put("category", categoryId)
                put("tags", JSONArray(normalizeTags(tags)))
                put("shared_evenly", sharedEvenly)
            }
            insertLocalOperation(db, editorAuthorId, sequence, OperationType.ENTRY_UPDATE, payload, now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteExpense(expenseId: String, authorId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown author" }
            require(editableExpenseExists(db, expenseId)) { "Expense does not exist, was deleted, or belongs to a closed cycle" }
            val now = System.currentTimeMillis()
            val sequence = nextAuthorSequence(db, authorId)
            db.update(
                "expenses",
                ContentValues().apply {
                    put("author_sequence", sequence)
                    put("updated_at", now)
                    put("deleted_at", now)
                },
                "id = ? AND ledger_id = ?",
                arrayOf(expenseId, ledgerId(db))
            )
            insertLocalOperation(
                db,
                authorId,
                sequence,
                OperationType.ENTRY_DELETE,
                JSONObject().put("entry_id", expenseId),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun createCategory(name: String, authorId: String) {
        val normalizedName = normalizeCategoryName(name)
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val existing = findCategoryByName(db, normalizedName)
            require(existing == null || existing.isArchived) { "Category already exists" }
            val categoryId = existing?.id ?: ExpenseParser.newId()
            if (existing == null) {
                db.insertOrThrow("categories", null, ContentValues().apply {
                    put("id", categoryId)
                    put("ledger_id", ledgerId(db))
                    put("name", normalizedName)
                    put("is_default", 0)
                    put("state", "active")
                })
            } else {
                db.update("categories", ContentValues().apply { put("state", "active") }, "id = ?", arrayOf(categoryId))
            }
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.CATEGORY_CREATE,
                JSONObject().put("category_id", categoryId).put("name", normalizedName),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun renameCategory(categoryId: String, newName: String, authorId: String) {
        val normalizedName = normalizeCategoryName(newName)
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val existing = findCategoryById(db, categoryId) ?: error("Category does not exist")
            require(!existing.isArchived) { "Archived categories cannot be renamed" }
            val collision = findCategoryByName(db, normalizedName)
            require(collision == null || collision.id == categoryId) { "A category with this name already exists" }
            db.update("categories", ContentValues().apply { put("name", normalizedName) }, "id = ?", arrayOf(categoryId))
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.CATEGORY_RENAME,
                JSONObject()
                    .put("category_id", categoryId)
                    .put("old_name", existing.name)
                    .put("new_name", normalizedName),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun archiveCategory(categoryId: String, authorId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val existing = findCategoryById(db, categoryId) ?: error("Category does not exist")
            require(!existing.isArchived) { "Category is already archived" }
            require(activeCategoryCount(db) > 1) { "At least one category must remain active" }
            db.update("categories", ContentValues().apply { put("state", "archived") }, "id = ?", arrayOf(categoryId))
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.CATEGORY_ARCHIVE,
                JSONObject().put("category_id", categoryId).put("name", existing.name),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun createTag(name: String, authorId: String) {
        val normalizedName = normalizeTagName(name)
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val existing = findTagByName(db, normalizedName)
            require(existing == null || existing.isArchived) { "Tag already exists" }
            val tagId = existing?.id ?: "${ledgerId(db)}:$normalizedName"
            if (existing == null) {
                db.insertOrThrow("tags", null, ContentValues().apply {
                    put("id", tagId)
                    put("ledger_id", ledgerId(db))
                    put("name", normalizedName)
                    put("state", "active")
                })
            } else {
                db.update("tags", ContentValues().apply { put("state", "active") }, "id = ?", arrayOf(tagId))
            }
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.TAG_CREATE,
                JSONObject().put("tag_id", tagId).put("name", normalizedName),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun renameTag(tagId: String, newName: String, authorId: String) {
        val normalizedName = normalizeTagName(newName)
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val existing = findTagById(db, tagId) ?: error("Tag does not exist")
            require(!existing.isArchived) { "Archived tags cannot be renamed" }
            val collision = findTagByName(db, normalizedName)
            require(collision == null || collision.id == tagId) { "A tag with this name already exists" }
            if (existing.name != normalizedName) {
                db.update("tags", ContentValues().apply { put("name", normalizedName) }, "id = ?", arrayOf(tagId))
            }
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.TAG_RENAME,
                JSONObject()
                    .put("tag_id", tagId)
                    .put("old_name", existing.name)
                    .put("new_name", normalizedName),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun archiveTag(tagId: String, authorId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val existing = findTagById(db, tagId) ?: error("Tag does not exist")
            require(!existing.isArchived) { "Tag is already archived" }
            db.update("tags", ContentValues().apply { put("state", "archived") }, "id = ?", arrayOf(tagId))
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.TAG_ARCHIVE,
                JSONObject().put("tag_id", tagId).put("name", existing.name),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun settleCurrentCycle(authorId: String, method: SettlementMethod, rawNote: String) {
        val note = SettlementPolicy.normalizeNote(rawNote)
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val cycleId = activeCycleId(db)
            val summary = calculateCycleSummary(db, cycleId)
            require(summary.transactionCount > 0) { "The current cycle has no expenses" }
            val now = System.currentTimeMillis()
            val nextCycleId = ExpenseParser.newId()
            closeCycle(db, cycleId, nextCycleId, now, authorId, summary, method, note)
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.SETTLEMENT_ACCEPT,
                JSONObject().apply {
                    put("cycle_id", cycleId)
                    put("next_cycle_id", nextCycleId)
                    put("closed_at_ms", now)
                    put("total_minor", summary.totalMinor)
                    put("creditor_id", summary.creditorId ?: JSONObject.NULL)
                    put("balance_minor", summary.balanceMinor)
                    put("transaction_count", summary.transactionCount)
                    put("settlement_method", method.wireValue)
                    put("settlement_note", note)
                },
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateTheme(themeId: String, authorId: String) {
        require(ThemeRegistry.contains(themeId)) { "Unknown theme: $themeId" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val values = ContentValues().apply { put("theme_id", themeId) }
            db.update("ledgers", values, "id = ?", arrayOf(ledgerId(db)))

            val now = System.currentTimeMillis()
            val operation = ContentValues().apply {
                put("operation_id", ExpenseParser.newId())
                put("ledger_id", ledgerId(db))
                put("author_id", authorId)
                put("author_sequence", nextAuthorSequence(db, authorId))
                put("operation_type", OperationType.THEME_SET.wireValue)
                put("payload_json", org.json.JSONObject().apply {
                    put("theme_id", themeId)
                    put("theme_version", 1)
                }.toString())
                put("created_at", now)
                put("sync_state", SyncState.OUTBOX.name)
            }
            db.insertOrThrow("ledger_operations", null, operation)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateCapacity(capacityMinor: Long, authorId: String) {
        require(capacityMinor in 100L..100_000_000_000L) { "Capacity is outside the supported range" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val currency = ledgerCurrency(db)
            db.update(
                "ledgers",
                ContentValues().apply { put("capacity_minor", capacityMinor) },
                "id = ?",
                arrayOf(ledgerId(db))
            )
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.LEDGER_CAPACITY_SET,
                JSONObject().put("capacity_minor", capacityMinor).put("currency", currency),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateLedgerMetadata(title: String, currency: String, authorId: String) {
        val normalizedTitle = LedgerMetadataPolicy.normalizeTitle(title)
        val normalizedCurrency = currency.trim().uppercase()
        LedgerMetadataPolicy.validate(normalizedTitle, normalizedCurrency)
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            val currentCurrency = ledgerCurrency(db)
            require(currentCurrency == normalizedCurrency || !ledgerHasExpenses(db)) {
                "La valuta può essere cambiata solo prima della prima spesa"
            }
            db.update(
                "ledgers",
                ContentValues().apply {
                    put("title", normalizedTitle)
                    put("currency", normalizedCurrency)
                },
                "id = ?",
                arrayOf(ledgerId(db))
            )
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.LEDGER_METADATA_SET,
                JSONObject().put("title", normalizedTitle).put("currency", normalizedCurrency),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateParticipantThemeIdentity(participantId: String, identityId: String, authorId: String) {
        require(ThemeIdentityRegistry.contains(identityId)) { "Unknown theme identity" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            require(participantExists(db, participantId)) { "Unknown participant" }
            require(participantId == authorId) { "Participants can only change their own theme identity" }
            db.update(
                "participants",
                ContentValues().apply { put("avatar_id", identityId) },
                "id = ? AND ledger_id = ?",
                arrayOf(participantId, ledgerId(db))
            )
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.PARTICIPANT_THEME_IDENTITY_SET,
                JSONObject().put("participant_id", participantId).put("identity_id", identityId),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateParticipantColor(participantId: String, colorHex: String, authorId: String) {
        val normalizedColor = requireNotNull(ParticipantColorPalette.normalize(colorHex)) {
            "Il colore deve essere in formato #RRGGBB"
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            require(participantExists(db, participantId)) { "Unknown participant" }
            require(participantId == authorId) { "Participants can only change their own color" }
            db.update(
                "participants",
                ContentValues().apply { put("color_hex", normalizedColor) },
                "id = ? AND ledger_id = ?",
                arrayOf(participantId, ledgerId(db))
            )
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.PARTICIPANT_COLOR_SET,
                JSONObject().put("participant_id", participantId).put("color_hex", normalizedColor),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateParticipantName(participantId: String, name: String, authorId: String) {
        val normalizedName = ParticipantNamePolicy.validate(name)
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(participantExists(db, authorId)) { "Unknown operation author" }
            require(participantExists(db, participantId)) { "Unknown participant" }
            require(participantId == authorId) { "Participants can only change their own name" }
            db.update(
                "participants",
                ContentValues().apply { put("name", normalizedName) },
                "id = ? AND ledger_id = ?",
                arrayOf(participantId, ledgerId(db))
            )
            val now = System.currentTimeMillis()
            insertLocalOperation(
                db,
                authorId,
                nextAuthorSequence(db, authorId),
                OperationType.PARTICIPANT_NAME_SET,
                JSONObject().put("participant_id", participantId).put("name", normalizedName),
                now
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun adoptPairingLedger(rawLedgerId: String) {
        val targetLedgerId = rawLedgerId.trim()
        require(targetLedgerId.isNotBlank() && targetLedgerId.length <= 120) { "Ledger pairing non valido" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val currentLedgerId = ledgerId(db)
            if (currentLedgerId == targetLedgerId) {
                db.setTransactionSuccessful()
                return
            }
            require(rowCount(db, "expenses") == 0L) {
                "Il ledger locale contiene già delle spese e non può essere sostituito da un invito"
            }
            require(rowCount(db, "ledger_operations") == 0L) {
                "Il ledger locale contiene modifiche non sincronizzate e non può essere sostituito da un invito"
            }
            require(rowCount(db, "tags") == 0L) {
                "Il ledger locale contiene tag personalizzati e non può essere sostituito da un invito"
            }
            val cycle = db.query(
                "settlement_cycles",
                arrayOf("id", "state", "transaction_count"),
                "ledger_id = ?",
                arrayOf(currentLedgerId),
                null,
                null,
                null
            ).use { cursor ->
                require(cursor.count == 1 && cursor.moveToFirst()) {
                    "Il ledger locale contiene una cronologia e non può essere sostituito da un invito"
                }
                Triple(cursor.getString(0), cursor.getString(1), cursor.getInt(2))
            }
            require(cycle.second == "active" && cycle.third == 0) {
                "Il ledger locale contiene una cronologia e non può essere sostituito da un invito"
            }
            require(
                !db.query("ledgers", arrayOf("id"), "id = ?", arrayOf(targetLedgerId), null, null, null, "1")
                    .use { it.moveToFirst() }
            ) { "Il ledger dell'invito esiste già" }

            val source = db.query(
                "ledgers",
                arrayOf("title", "currency", "capacity_minor", "theme_id", "theme_version"),
                "id = ?",
                arrayOf(currentLedgerId),
                null,
                null,
                null,
                "1"
            ).use { cursor ->
                check(cursor.moveToFirst()) { "Ledger locale non trovato" }
                ContentValues().apply {
                    put("id", targetLedgerId)
                    put("title", cursor.getString(0))
                    put("currency", cursor.getString(1))
                    put("capacity_minor", cursor.getLong(2))
                    put("theme_id", cursor.getString(3))
                    put("theme_version", cursor.getInt(4))
                }
            }
            db.insertOrThrow("ledgers", null, source)
            listOf("participants", "categories", "settlement_cycles", "tags").forEach { table ->
                db.update(
                    table,
                    ContentValues().apply { put("ledger_id", targetLedgerId) },
                    "ledger_id = ?",
                    arrayOf(currentLedgerId)
                )
            }
            db.update(
                "settlement_cycles",
                ContentValues().apply { put("id", initialCycleId(targetLedgerId)) },
                "id = ? AND ledger_id = ?",
                arrayOf(cycle.first, targetLedgerId)
            )
            check(db.delete("ledgers", "id = ?", arrayOf(currentLedgerId)) == 1) {
                "Impossibile sostituire il ledger locale"
            }
            cachedLedgerId = targetLedgerId
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun readSnapshot(): LedgerSnapshot {
        val db = readableDatabase
        val ledgerCursor = db.query("ledgers", null, "id = ?", arrayOf(ledgerId(db)), null, null, null)
        ledgerCursor.use { cursor ->
            check(cursor.moveToFirst()) { "Ledger was not created" }
            val ledger = LedgerSnapshot(
                id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                currency = cursor.getString(cursor.getColumnIndexOrThrow("currency")),
                capacityMinor = cursor.getLong(cursor.getColumnIndexOrThrow("capacity_minor")),
                participants = readParticipants(db),
                expenses = readExpenses(db),
                themeId = cursor.getString(cursor.getColumnIndexOrThrow("theme_id")),
                themeVersion = cursor.getInt(cursor.getColumnIndexOrThrow("theme_version")),
                tags = readTagCatalog(db),
                settlementCycles = readSettlementCycles(db),
                categories = readCategoryCatalog(db)
            )
            return ledger
        }
    }

    private fun readParticipants(db: SQLiteDatabase): List<Participant> {
        val result = mutableListOf<Participant>()
        db.query("participants", null, "ledger_id = ?", arrayOf(ledgerId(db)), null, null, "id ASC").use { cursor ->
            while (cursor.moveToNext()) {
                result += Participant(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    colorHex = cursor.getString(cursor.getColumnIndexOrThrow("color_hex")),
                    avatarId = cursor.getString(cursor.getColumnIndexOrThrow("avatar_id"))
                )
            }
        }
        return result
    }

    private fun readExpenses(db: SQLiteDatabase): List<Expense> {
        val result = mutableListOf<Expense>()
        db.query("expenses", null, "ledger_id = ?", arrayOf(ledgerId(db)), null, null, "created_at ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                result += Expense(
                    id = id,
                    ledgerId = cursor.getString(cursor.getColumnIndexOrThrow("ledger_id")),
                    authorId = cursor.getString(cursor.getColumnIndexOrThrow("author_id")),
                    amountMinor = cursor.getLong(cursor.getColumnIndexOrThrow("amount_minor")),
                    currency = cursor.getString(cursor.getColumnIndexOrThrow("currency")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    categoryId = cursor.getString(cursor.getColumnIndexOrThrow("category_id")),
                    tags = readTags(db, id),
                    createdAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    authorSequence = cursor.getLong(cursor.getColumnIndexOrThrow("author_sequence")),
                    updatedAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                    deletedAtMillis = cursor.getColumnIndexOrThrow("deleted_at").let { index ->
                        if (cursor.isNull(index)) null else cursor.getLong(index)
                    },
                    cycleId = cursor.getString(cursor.getColumnIndexOrThrow("cycle_id")),
                    merchant = cursor.getString(cursor.getColumnIndexOrThrow("merchant")),
                    notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
                    sharedEvenly = readSharedEvenly(cursor)
                )
            }
        }
        return result
    }

    private fun readTags(db: SQLiteDatabase, expenseId: String): List<String> {
        val tags = mutableListOf<String>()
        db.rawQuery(
            "SELECT t.name FROM tags t INNER JOIN expense_tags et ON et.tag_id = t.id WHERE et.expense_id = ? ORDER BY t.name",
            arrayOf(expenseId)
        ).use { cursor ->
            while (cursor.moveToNext()) tags += cursor.getString(0)
        }
        return tags
    }

    private fun readTagCatalog(db: SQLiteDatabase): List<TagDefinition> {
        val tags = mutableListOf<TagDefinition>()
        db.rawQuery(
            """
            SELECT t.id, t.name, t.state, COUNT(et.expense_id)
            FROM tags t
            LEFT JOIN expense_tags et ON et.tag_id = t.id
            WHERE t.ledger_id = ?
            GROUP BY t.id, t.name, t.state
            ORDER BY t.state ASC, COUNT(et.expense_id) DESC, t.name ASC
            """.trimIndent(),
            arrayOf(ledgerId(db))
        ).use { cursor ->
            while (cursor.moveToNext()) {
                tags += TagDefinition(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    isArchived = cursor.getString(2) == "archived",
                    usageCount = cursor.getInt(3)
                )
            }
        }
        return tags
    }

    private fun readCategoryCatalog(db: SQLiteDatabase): List<ExpenseCategory> {
        val categories = mutableListOf<ExpenseCategory>()
        db.rawQuery(
            """
            SELECT c.id, c.name, c.is_default, c.state, COUNT(e.id)
            FROM categories c
            LEFT JOIN expenses e ON e.category_id = c.id AND e.deleted_at IS NULL
            WHERE c.ledger_id = ?
            GROUP BY c.id, c.name, c.is_default, c.state
            ORDER BY c.state ASC, c.is_default DESC, COUNT(e.id) DESC, c.name ASC
            """.trimIndent(),
            arrayOf(ledgerId(db))
        ).use { cursor ->
            while (cursor.moveToNext()) {
                categories += ExpenseCategory(
                    id = cursor.getString(0),
                    displayName = cursor.getString(1),
                    isDefault = cursor.getInt(2) != 0,
                    isArchived = cursor.getString(3) == "archived",
                    usageCount = cursor.getInt(4)
                )
            }
        }
        return categories
    }

    private fun readSettlementCycles(db: SQLiteDatabase): List<SettlementCycle> {
        val cycles = mutableListOf<SettlementCycle>()
        db.query(
            "settlement_cycles",
            null,
            "ledger_id = ?",
            arrayOf(ledgerId(db)),
            null,
            null,
            "started_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val closedAtIndex = cursor.getColumnIndexOrThrow("closed_at")
                val creditorIndex = cursor.getColumnIndexOrThrow("creditor_id")
                val closedByIndex = cursor.getColumnIndexOrThrow("closed_by_participant_id")
                cycles += SettlementCycle(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    ledgerId = cursor.getString(cursor.getColumnIndexOrThrow("ledger_id")),
                    startedAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                    closedAtMillis = if (cursor.isNull(closedAtIndex)) null else cursor.getLong(closedAtIndex),
                    totalMinor = cursor.getLong(cursor.getColumnIndexOrThrow("total_minor")),
                    creditorId = if (cursor.isNull(creditorIndex)) null else cursor.getString(creditorIndex),
                    balanceMinor = cursor.getLong(cursor.getColumnIndexOrThrow("balance_minor")),
                    transactionCount = cursor.getInt(cursor.getColumnIndexOrThrow("transaction_count")),
                    closedByParticipantId = if (cursor.isNull(closedByIndex)) null else cursor.getString(closedByIndex),
                    method = cursor.getString(cursor.getColumnIndexOrThrow("settlement_method"))
                        .let(SettlementMethod::fromWire) ?: SettlementMethod.OTHER,
                    note = cursor.getString(cursor.getColumnIndexOrThrow("settlement_note"))
                )
            }
        }
        return cycles
    }

    private fun normalizeCategoryName(name: String): String {
        val normalized = name.trim().replace(Regex("\\s+"), " ")
        require(normalized.isNotBlank()) { "Category name is empty" }
        require(normalized.length <= 60) { "Category name is too long" }
        return normalized
    }

    private fun findCategoryById(db: SQLiteDatabase, categoryId: String): StoredCategory? =
        db.query(
            "categories",
            arrayOf("id", "name", "is_default", "state"),
            "id = ? AND ledger_id = ?",
            arrayOf(categoryId, ledgerId(db)),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else StoredCategory(cursor.getString(0), cursor.getString(1), cursor.getInt(2) != 0, cursor.getString(3) == "archived")
        }

    private fun findCategoryByName(db: SQLiteDatabase, name: String): StoredCategory? =
        db.query(
            "categories",
            arrayOf("id", "name", "is_default", "state"),
            "ledger_id = ? AND name = ? COLLATE NOCASE",
            arrayOf(ledgerId(db), name),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else StoredCategory(cursor.getString(0), cursor.getString(1), cursor.getInt(2) != 0, cursor.getString(3) == "archived")
        }

    private fun activeCategoryExists(db: SQLiteDatabase, categoryId: String): Boolean =
        db.query(
            "categories",
            arrayOf("id"),
            "id = ? AND ledger_id = ? AND state = 'active'",
            arrayOf(categoryId, ledgerId(db)),
            null,
            null,
            null,
            "1"
        ).use { it.moveToFirst() }

    private fun activeCategoryCount(db: SQLiteDatabase): Int =
        db.rawQuery(
            "SELECT COUNT(*) FROM categories WHERE ledger_id = ? AND state = 'active'",
            arrayOf(ledgerId(db))
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun normalizeTagName(name: String): String {
        val normalized = name.trim().removePrefix("#").lowercase().replace(Regex("\\s+"), "-")
        require(normalized.isNotBlank()) { "Tag name is empty" }
        require(normalized.length <= ExpensePolicy.MAX_TAG_LENGTH) { "Tag name is too long" }
        return normalized
    }

    private fun normalizeTags(tags: List<String>): List<String> {
        require(tags.size <= ExpensePolicy.MAX_TAGS) { "Too many tags" }
        return tags.map(::normalizeTagName).distinct().also {
            require(it.size <= ExpensePolicy.MAX_TAGS) { "Too many tags" }
        }
    }

    private fun replaceExpenseTags(db: SQLiteDatabase, expenseId: String, tags: List<String>) {
        db.delete("expense_tags", "expense_id = ?", arrayOf(expenseId))
        normalizeTags(tags).forEach { tagName ->
            val existing = findTagByName(db, tagName)
            val tagId = existing?.id ?: "${ledgerId(db)}:$tagName"
            if (existing == null) {
                db.insertOrThrow("tags", null, ContentValues().apply {
                    put("id", tagId)
                    put("ledger_id", ledgerId(db))
                    put("name", tagName)
                    put("state", "active")
                })
            } else if (existing.isArchived) {
                db.update("tags", ContentValues().apply { put("state", "active") }, "id = ?", arrayOf(tagId))
            }
            db.insertOrThrow("expense_tags", null, ContentValues().apply {
                put("expense_id", expenseId)
                put("tag_id", tagId)
            })
        }
    }

    private fun findTagById(db: SQLiteDatabase, tagId: String): StoredTag? =
        db.query(
            "tags",
            arrayOf("id", "name", "state"),
            "id = ? AND ledger_id = ?",
            arrayOf(tagId, ledgerId(db)),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else StoredTag(cursor.getString(0), cursor.getString(1), cursor.getString(2) == "archived")
        }

    private fun findTagByName(db: SQLiteDatabase, name: String): StoredTag? =
        db.query(
            "tags",
            arrayOf("id", "name", "state"),
            "ledger_id = ? AND name = ? COLLATE NOCASE",
            arrayOf(ledgerId(db), name),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else StoredTag(cursor.getString(0), cursor.getString(1), cursor.getString(2) == "archived")
        }

    private fun insertLocalOperation(
        db: SQLiteDatabase,
        authorId: String,
        authorSequence: Long,
        type: OperationType,
        payload: JSONObject,
        createdAtMillis: Long
    ) {
        db.insertOrThrow("ledger_operations", null, ContentValues().apply {
            put("operation_id", ExpenseParser.newId())
            put("ledger_id", ledgerId(db))
            put("author_id", authorId)
            put("author_sequence", authorSequence)
            put("operation_type", type.wireValue)
            put("payload_json", payload.toString())
            put("created_at", createdAtMillis)
            put("sync_state", SyncState.OUTBOX.name)
        })
    }

    private fun insertParticipant(
        db: SQLiteDatabase,
        ledgerId: String,
        id: String,
        name: String,
        color: String,
        avatar: String
    ) {
        val values = ContentValues().apply {
            put("id", id)
            put("ledger_id", ledgerId)
            put("name", name)
            put("color_hex", color)
            put("avatar_id", avatar)
        }
        db.insertOrThrow("participants", null, values)
    }

    fun readPendingOperations(): List<LedgerOperation> {
        val result = mutableListOf<LedgerOperation>()
        readableDatabase.query(
            "ledger_operations",
            null,
            "sync_state IN (?, ?)",
            arrayOf(SyncState.LOCAL_ONLY.name, SyncState.OUTBOX.name),
            null,
            null,
            "created_at ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += LedgerOperation(
                    operationId = cursor.getString(cursor.getColumnIndexOrThrow("operation_id")),
                    ledgerId = cursor.getString(cursor.getColumnIndexOrThrow("ledger_id")),
                    authorId = cursor.getString(cursor.getColumnIndexOrThrow("author_id")),
                    authorSequence = cursor.getLong(cursor.getColumnIndexOrThrow("author_sequence")),
                    type = OperationType.entries.first { it.wireValue == cursor.getString(cursor.getColumnIndexOrThrow("operation_type")) },
                    createdAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    payloadJson = cursor.getString(cursor.getColumnIndexOrThrow("payload_json")),
                    transportEventId = cursor.getString(cursor.getColumnIndexOrThrow("transport_event_id")),
                    syncState = SyncState.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("sync_state")))
                )
            }
        }
        return result
    }

    fun readOperationStats(): OperationStats {
        val counts = mutableMapOf<SyncState, Int>()
        readableDatabase.rawQuery(
            "SELECT sync_state, COUNT(*) FROM ledger_operations GROUP BY sync_state",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                runCatching { SyncState.valueOf(cursor.getString(0)) }
                    .getOrNull()
                    ?.let { state -> counts[state] = cursor.getInt(1) }
            }
        }
        return OperationStats(
            pending = (counts[SyncState.LOCAL_ONLY] ?: 0) + (counts[SyncState.OUTBOX] ?: 0),
            published = counts[SyncState.PUBLISHED] ?: 0,
            applied = counts[SyncState.APPLIED] ?: 0,
            deferred = counts[SyncState.DEFERRED] ?: 0,
            rejected = counts[SyncState.REJECTED] ?: 0
        )
    }

    fun markOperationPublished(operationId: String, transportEventId: String) {
        val values = ContentValues().apply {
            put("transport_event_id", transportEventId)
            put("sync_state", SyncState.PUBLISHED.name)
        }
        writableDatabase.update("ledger_operations", values, "operation_id = ?", arrayOf(operationId))
    }

    fun applyRemoteOperation(operation: LedgerOperation, transportEventId: String): RemoteOperationResult {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = when {
                operation.ledgerId != ledgerId(db) -> RemoteOperationResult(
                    RemoteOperationStatus.REJECTED,
                    "Operation belongs to another ledger"
                )

                else -> {
                    val existing = findOperation(db, operation.operationId)
                    when {
                        existing != null && existing.matches(operation) -> {
                            if (existing.transportEventId == null) {
                                db.update(
                                    "ledger_operations",
                                    ContentValues().apply { put("transport_event_id", transportEventId) },
                                    "operation_id = ?",
                                    arrayOf(operation.operationId)
                                )
                            }
                            RemoteOperationResult(RemoteOperationStatus.DUPLICATE)
                        }

                        existing != null -> RemoteOperationResult(
                            RemoteOperationStatus.REJECTED,
                            "Operation id already exists with different content"
                        )

                        !participantExists(db, operation.authorId) -> rejectRemoteOperation(
                            db,
                            operation,
                            transportEventId,
                            "Unknown participant"
                        )

                        authorSequenceExists(db, operation.authorId, operation.authorSequence) ->
                            RemoteOperationResult(
                                RemoteOperationStatus.REJECTED,
                                "Author sequence is already associated with another operation"
                            )

                        else -> {
                            val currentSequence = maxAuthorSequence(db, operation.authorId)
                            when (OperationSequencePolicy.classify(currentSequence, operation.authorSequence)) {
                                SequenceDisposition.STALE ->
                                    rejectRemoteOperation(db, operation, transportEventId, "Stale author sequence")

                                SequenceDisposition.DEFER ->
                                    deferRemoteOperation(db, operation, transportEventId, currentSequence + 1L)

                                SequenceDisposition.EXPECTED -> {
                                    val applied = applyNewRemoteOperation(db, operation, transportEventId)
                                    val drained = drainDeferredOperations(db, operation.authorId)
                                    applied.copy(
                                        drainedOperations = drained,
                                        message = if (drained > 0 && applied.status == RemoteOperationStatus.APPLIED) {
                                            "Operazione applicata · $drained operazioni in attesa riconciliate"
                                        } else applied.message
                                    )
                                }
                            }
                        }
                    }
                }
            }
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    private fun applyNewRemoteOperation(
        db: SQLiteDatabase,
        operation: LedgerOperation,
        transportEventId: String
    ): RemoteOperationResult {
        val rejection = when (operation.type) {
            OperationType.ENTRY_CREATE -> {
                val entry = runCatching {
                    parseRemoteEntry(
                        operation.payloadJson,
                        expectedCurrency = ledgerCurrency(db),
                        fallbackPayerId = operation.authorId,
                        fallbackOccurredAtMillis = operation.createdAtMillis
                    )
                }
                    .getOrElse { return rejectRemoteOperation(db, operation, transportEventId, it.message ?: "Invalid entry payload") }
                when {
                    expenseExists(db, entry.entryId) -> "Entry id already exists"
                    !participantExists(db, entry.payerId) -> "Unknown payer"
                    !activeCategoryExists(db, entry.categoryId) -> "Unknown or archived category"
                    entry.cycleId != activeCycleId(db) -> "Entry belongs to a cycle that is not active"
                    else -> {
                        insertRemoteEntry(db, operation, entry)
                        null
                    }
                }
            }

            OperationType.ENTRY_UPDATE -> {
                val entry = runCatching {
                    val entryId = JSONObject(operation.payloadJson).optString("entry_id").trim()
                    parseRemoteEntry(
                        operation.payloadJson,
                        ledgerCurrency(db),
                        fallbackOccurredAtMillis = expenseCreatedAt(db, entryId) ?: operation.createdAtMillis
                    )
                }
                    .getOrElse { return rejectRemoteOperation(db, operation, transportEventId, it.message ?: "Invalid entry payload") }
                when {
                    !editableExpenseExists(db, entry.entryId) -> "Entry does not exist, was deleted, or belongs to a closed cycle"
                    !participantExists(db, entry.payerId) -> "Unknown payer"
                    !activeCategoryExists(db, entry.categoryId) -> "Unknown or archived category"
                    expenseUpdatedAfter(db, entry.entryId, operation.createdAtMillis) -> "Entry has a newer local version"
                    else -> {
                        db.update("expenses", ContentValues().apply {
                            put("author_id", entry.payerId)
                            put("amount_minor", entry.amountMinor)
                            put("currency", entry.currency)
                            put("description", entry.description)
                            put("merchant", entry.merchant)
                            put("notes", entry.notes)
                            put("category_id", entry.categoryId)
                            put("created_at", entry.occurredAtMillis)
                            put("author_sequence", operation.authorSequence)
                            put("updated_at", operation.createdAtMillis)
                            put("shared_evenly", if (entry.sharedEvenly) 1 else 0)
                        }, "id = ?", arrayOf(entry.entryId))
                        replaceExpenseTags(db, entry.entryId, entry.tags)
                        null
                    }
                }
            }

            OperationType.ENTRY_DELETE -> {
                val entryId = runCatching { JSONObject(operation.payloadJson).getString("entry_id").trim() }
                    .getOrElse { return rejectRemoteOperation(db, operation, transportEventId, "Invalid delete payload") }
                when {
                    entryId.isBlank() -> "Entry id is empty"
                    !editableExpenseExists(db, entryId) -> "Entry does not exist, was deleted, or belongs to a closed cycle"
                    expenseUpdatedAfter(db, entryId, operation.createdAtMillis) -> "Entry has a newer local version"
                    else -> {
                        db.update("expenses", ContentValues().apply {
                            put("author_sequence", operation.authorSequence)
                            put("updated_at", operation.createdAtMillis)
                            put("deleted_at", operation.createdAtMillis)
                        }, "id = ?", arrayOf(entryId))
                        null
                    }
                }
            }

            OperationType.CATEGORY_CREATE -> applyRemoteCategoryCreate(db, operation.payloadJson)

            OperationType.CATEGORY_RENAME -> applyRemoteCategoryRename(db, operation.payloadJson)

            OperationType.CATEGORY_ARCHIVE -> applyRemoteCategoryArchive(db, operation.payloadJson)

            OperationType.TAG_CREATE -> applyRemoteTagCreate(db, operation.payloadJson)

            OperationType.TAG_RENAME -> applyRemoteTagRename(db, operation.payloadJson)

            OperationType.TAG_ARCHIVE -> applyRemoteTagArchive(db, operation.payloadJson)

            OperationType.SETTLEMENT_ACCEPT -> applyRemoteSettlement(db, operation)

            OperationType.LEDGER_METADATA_SET -> {
                val payload = runCatching { JSONObject(operation.payloadJson) }
                    .getOrElse { return rejectRemoteOperation(db, operation, transportEventId, "Invalid ledger metadata payload") }
                val title = LedgerMetadataPolicy.normalizeTitle(payload.optString("title"))
                val currency = payload.optString("currency").trim().uppercase()
                val validationError = runCatching { LedgerMetadataPolicy.validate(title, currency) }
                    .exceptionOrNull()?.message
                when {
                    validationError != null -> validationError
                    currency != ledgerCurrency(db) && ledgerHasExpenses(db) ->
                        "Ledger currency cannot change after the first expense"
                    else -> {
                        db.update(
                            "ledgers",
                            ContentValues().apply {
                                put("title", title)
                                put("currency", currency)
                            },
                            "id = ?",
                            arrayOf(ledgerId(db))
                        )
                        null
                    }
                }
            }

            OperationType.LEDGER_CAPACITY_SET -> {
                val payload = runCatching { JSONObject(operation.payloadJson) }
                    .getOrElse { return rejectRemoteOperation(db, operation, transportEventId, "Invalid capacity payload") }
                val capacityMinor = payload.optLong("capacity_minor", -1L)
                if (payload.optString("currency") != ledgerCurrency(db) || capacityMinor !in 100L..100_000_000_000L) {
                    "Invalid ledger capacity"
                } else {
                    db.update(
                        "ledgers",
                        ContentValues().apply { put("capacity_minor", capacityMinor) },
                        "id = ?",
                        arrayOf(ledgerId(db))
                    )
                    null
                }
            }

            OperationType.PARTICIPANT_THEME_IDENTITY_SET -> {
                val payload = runCatching { JSONObject(operation.payloadJson) }
                    .getOrElse { return rejectRemoteOperation(db, operation, transportEventId, "Invalid identity payload") }
                val participantId = payload.optString("participant_id").trim()
                val identityId = payload.optString("identity_id").trim()
                when {
                    !participantExists(db, participantId) -> "Unknown participant"
                    participantId != operation.authorId -> "Participants can only change their own theme identity"
                    !ThemeIdentityRegistry.contains(identityId) -> "Unknown theme identity"
                    else -> {
                        db.update(
                            "participants",
                            ContentValues().apply { put("avatar_id", identityId) },
                            "id = ? AND ledger_id = ?",
                            arrayOf(participantId, ledgerId(db))
                        )
                        null
                    }
                }
            }

            OperationType.PARTICIPANT_COLOR_SET -> {
                val payload = runCatching { JSONObject(operation.payloadJson) }
                    .getOrElse { return rejectRemoteOperation(db, operation, transportEventId, "Invalid participant color payload") }
                val participantId = payload.optString("participant_id").trim()
                val colorHex = ParticipantColorPalette.normalize(payload.optString("color_hex"))
                when {
                    !participantExists(db, participantId) -> "Unknown participant"
                    participantId != operation.authorId -> "Participants can only change their own color"
                    colorHex == null -> "Invalid participant color"
                    else -> {
                        db.update(
                            "participants",
                            ContentValues().apply { put("color_hex", colorHex) },
                            "id = ? AND ledger_id = ?",
                            arrayOf(participantId, ledgerId(db))
                        )
                        null
                    }
                }
            }

            OperationType.PARTICIPANT_NAME_SET -> {
                val payload = runCatching { JSONObject(operation.payloadJson) }
                    .getOrElse { return rejectRemoteOperation(db, operation, transportEventId, "Invalid participant name payload") }
                val participantId = payload.optString("participant_id").trim()
                val name = runCatching { ParticipantNamePolicy.validate(payload.optString("name")) }.getOrNull()
                when {
                    !participantExists(db, participantId) -> "Unknown participant"
                    participantId != operation.authorId -> "Participants can only change their own name"
                    name == null -> "Invalid participant name"
                    else -> {
                        db.update(
                            "participants",
                            ContentValues().apply { put("name", name) },
                            "id = ? AND ledger_id = ?",
                            arrayOf(participantId, ledgerId(db))
                        )
                        null
                    }
                }
            }

            OperationType.THEME_SET -> {
                val payload = runCatching { JSONObject(operation.payloadJson) }
                    .getOrElse { return rejectRemoteOperation(db, operation, transportEventId, "Invalid theme payload") }
                val themeId = payload.optString("theme_id").trim()
                if (!ThemeRegistry.contains(themeId)) {
                    "Unknown theme"
                } else {
                    db.update(
                        "ledgers",
                        ContentValues().apply {
                            put("theme_id", themeId)
                            put("theme_version", payload.optInt("theme_version", 1))
                        },
                        "id = ?",
                        arrayOf(ledgerId(db))
                    )
                    null
                }
            }

            else -> "Operation type is not supported by this client"
        }

        if (rejection != null) {
            return rejectRemoteOperation(db, operation, transportEventId, rejection)
        }

        db.insertOrThrow("ledger_operations", null, operationValues(operation, transportEventId, SyncState.APPLIED))
        return RemoteOperationResult(RemoteOperationStatus.APPLIED)
    }

    private fun applyRemoteCategoryCreate(db: SQLiteDatabase, payloadJson: String): String? {
        val payload = runCatching { JSONObject(payloadJson) }.getOrElse { return "Invalid category payload" }
        val categoryId = payload.optString("category_id").trim()
        val name = runCatching { normalizeCategoryName(payload.optString("name")) }.getOrElse { return it.message }
        if (categoryId.isBlank() || categoryId.length > 200) return "Invalid category id"
        val byId = findCategoryById(db, categoryId)
        val byName = findCategoryByName(db, name)
        if (byId != null && byId.name != name) return "Category id already has another name"
        if (byName != null && byName.id != categoryId) return "Category name already exists"
        if (byId == null) {
            db.insertOrThrow("categories", null, ContentValues().apply {
                put("id", categoryId)
                put("ledger_id", ledgerId(db))
                put("name", name)
                put("is_default", 0)
                put("state", "active")
            })
        } else if (byId.isArchived) {
            db.update("categories", ContentValues().apply { put("state", "active") }, "id = ?", arrayOf(categoryId))
        }
        return null
    }

    private fun applyRemoteCategoryRename(db: SQLiteDatabase, payloadJson: String): String? {
        val payload = runCatching { JSONObject(payloadJson) }.getOrElse { return "Invalid category payload" }
        val categoryId = payload.optString("category_id").trim()
        val newName = runCatching { normalizeCategoryName(payload.optString("new_name")) }.getOrElse { return it.message }
        val existing = findCategoryById(db, categoryId) ?: return "Category does not exist"
        if (existing.isArchived) return "Archived categories cannot be renamed"
        val collision = findCategoryByName(db, newName)
        if (collision != null && collision.id != categoryId) return "Category name already exists"
        db.update("categories", ContentValues().apply { put("name", newName) }, "id = ?", arrayOf(categoryId))
        return null
    }

    private fun applyRemoteCategoryArchive(db: SQLiteDatabase, payloadJson: String): String? {
        val payload = runCatching { JSONObject(payloadJson) }.getOrElse { return "Invalid category payload" }
        val categoryId = payload.optString("category_id").trim()
        val existing = findCategoryById(db, categoryId) ?: return "Category does not exist"
        if (existing.isArchived) return "Category is already archived"
        if (activeCategoryCount(db) <= 1) return "At least one category must remain active"
        db.update("categories", ContentValues().apply { put("state", "archived") }, "id = ?", arrayOf(categoryId))
        return null
    }

    private fun applyRemoteTagCreate(db: SQLiteDatabase, payloadJson: String): String? {
        val payload = runCatching { JSONObject(payloadJson) }.getOrElse { return "Invalid tag payload" }
        val tagId = payload.optString("tag_id").trim()
        val name = runCatching { normalizeTagName(payload.optString("name")) }.getOrElse { return it.message }
        if (tagId.isBlank() || tagId.length > 200) return "Invalid tag id"
        val byId = findTagById(db, tagId)
        val byName = findTagByName(db, name)
        if (byId != null && byId.name != name) return "Tag id already has another name"
        if (byName != null && byName.id != tagId) return "Tag name already exists"
        if (byId == null) {
            db.insertOrThrow("tags", null, ContentValues().apply {
                put("id", tagId)
                put("ledger_id", ledgerId(db))
                put("name", name)
                put("state", "active")
            })
        } else if (byId.isArchived) {
            db.update("tags", ContentValues().apply { put("state", "active") }, "id = ?", arrayOf(tagId))
        }
        return null
    }

    private fun applyRemoteTagRename(db: SQLiteDatabase, payloadJson: String): String? {
        val payload = runCatching { JSONObject(payloadJson) }.getOrElse { return "Invalid tag payload" }
        val tagId = payload.optString("tag_id").trim()
        val newName = runCatching { normalizeTagName(payload.optString("new_name")) }.getOrElse { return it.message }
        val existing = findTagById(db, tagId) ?: return "Tag does not exist"
        if (existing.isArchived) return "Archived tags cannot be renamed"
        val collision = findTagByName(db, newName)
        if (collision != null && collision.id != tagId) return "Tag name already exists"
        db.update("tags", ContentValues().apply { put("name", newName) }, "id = ?", arrayOf(tagId))
        return null
    }

    private fun applyRemoteTagArchive(db: SQLiteDatabase, payloadJson: String): String? {
        val payload = runCatching { JSONObject(payloadJson) }.getOrElse { return "Invalid tag payload" }
        val tagId = payload.optString("tag_id").trim()
        val existing = findTagById(db, tagId) ?: return "Tag does not exist"
        if (existing.isArchived) return "Tag is already archived"
        db.update("tags", ContentValues().apply { put("state", "archived") }, "id = ?", arrayOf(tagId))
        return null
    }

    private fun applyRemoteSettlement(db: SQLiteDatabase, operation: LedgerOperation): String? {
        val payload = runCatching { JSONObject(operation.payloadJson) }.getOrElse { return "Invalid settlement payload" }
        val cycleId = payload.optString("cycle_id").trim()
        val nextCycleId = payload.optString("next_cycle_id").trim()
        val closedAt = payload.optLong("closed_at_ms", 0L)
        val methodValue = payload.optString("settlement_method").takeIf(String::isNotBlank)
        val method = if (methodValue == null) SettlementMethod.OTHER else SettlementMethod.fromWire(methodValue)
            ?: return "Invalid settlement method"
        val note = runCatching { SettlementPolicy.normalizeNote(payload.optString("settlement_note")) }
            .getOrElse { return it.message ?: "Invalid settlement note" }
        if (cycleId.isBlank() || nextCycleId.isBlank() || cycleId == nextCycleId || closedAt <= 0L) {
            return "Invalid settlement identifiers"
        }
        if (runCatching { activeCycleId(db) }.getOrNull() != cycleId) return "Settlement cycle is not active"
        if (cycleExists(db, nextCycleId)) return "Next settlement cycle already exists"
        val summary = calculateCycleSummary(db, cycleId)
        val expectedCreditor = if (payload.isNull("creditor_id")) null
        else payload.optString("creditor_id").trim().takeIf { it.isNotBlank() }
        if (
            summary.transactionCount == 0 ||
            summary.totalMinor != payload.optLong("total_minor", Long.MIN_VALUE) ||
            summary.balanceMinor != payload.optLong("balance_minor", Long.MIN_VALUE) ||
            summary.transactionCount != payload.optInt("transaction_count", -1) ||
            summary.creditorId != expectedCreditor
        ) {
            return "Settlement summary does not match the local ledger"
        }
        closeCycle(db, cycleId, nextCycleId, closedAt, operation.authorId, summary, method, note)
        return null
    }

    private fun insertRemoteEntry(db: SQLiteDatabase, operation: LedgerOperation, entry: RemoteEntry) {
        db.insertOrThrow("expenses", null, ContentValues().apply {
            put("id", entry.entryId)
            put("ledger_id", ledgerId(db))
            put("author_id", entry.payerId)
            put("amount_minor", entry.amountMinor)
            put("currency", entry.currency)
            put("description", entry.description)
            put("merchant", entry.merchant)
            put("notes", entry.notes)
            put("category_id", entry.categoryId)
            put("author_sequence", operation.authorSequence)
            put("created_at", entry.occurredAtMillis)
            put("updated_at", operation.createdAtMillis)
            put("cycle_id", entry.cycleId)
            put("shared_evenly", if (entry.sharedEvenly) 1 else 0)
        })

        replaceExpenseTags(db, entry.entryId, entry.tags)
    }

    private fun parseRemoteEntry(
        payloadJson: String,
        expectedCurrency: String,
        fallbackPayerId: String? = null,
        fallbackOccurredAtMillis: Long
    ): RemoteEntry {
        val payload = JSONObject(payloadJson)
        val tagsJson = payload.optJSONArray("tags") ?: JSONArray()
        require(tagsJson.length() <= ExpensePolicy.MAX_TAGS) { "Too many tags" }
        val tags = normalizeTags(buildList {
            for (index in 0 until tagsJson.length()) {
                val tag = tagsJson.optString(index)
                if (tag.isNotBlank()) add(tag)
            }
        })
        val entry = RemoteEntry(
            entryId = payload.getString("entry_id").trim(),
            cycleId = payload.optString("cycle_id").trim().ifBlank { activeCycleId(readableDatabase) },
            payerId = payload.optString("payer_id", fallbackPayerId.orEmpty()).trim(),
            amountMinor = payload.getLong("amount_minor"),
            currency = payload.getString("currency").trim(),
            description = payload.getString("description").trim(),
            merchant = payload.optString("merchant").trim(),
            notes = payload.optString("notes").trim(),
            occurredAtMillis = payload.optLong("occurred_at_ms", fallbackOccurredAtMillis),
            categoryId = payload.getString("category").trim(),
            tags = tags,
            sharedEvenly = payload.optBoolean("shared_evenly", true)
        )
        require(entry.entryId.isNotBlank()) { "Entry id is empty" }
        require(entry.entryId.length <= ExpensePolicy.MAX_ENTITY_ID_LENGTH) { "Entry id is too long" }
        require(entry.payerId.isNotBlank()) { "Payer id is empty" }
        require(entry.payerId.length <= ExpensePolicy.MAX_ENTITY_ID_LENGTH) { "Payer id is too long" }
        ExpensePolicy.validate(entry.amountMinor, entry.description, entry.merchant, entry.notes)
        ExpensePolicy.validateOccurredAt(entry.occurredAtMillis)
        require(entry.currency == expectedCurrency) { "Currency does not match this ledger" }
        require(entry.categoryId.isNotBlank()) { "Category is empty" }
        require(entry.categoryId.length <= ExpensePolicy.MAX_ENTITY_ID_LENGTH) { "Category id is too long" }
        require(entry.tags.size <= ExpensePolicy.MAX_TAGS) { "Too many tags" }
        return entry
    }

    private fun rejectRemoteOperation(
        db: SQLiteDatabase,
        operation: LedgerOperation,
        transportEventId: String,
        reason: String
    ): RemoteOperationResult {
        db.insertWithOnConflict(
            "ledger_operations",
            null,
            operationValues(operation, transportEventId, SyncState.REJECTED),
            SQLiteDatabase.CONFLICT_IGNORE
        )
        return RemoteOperationResult(RemoteOperationStatus.REJECTED, reason)
    }

    private fun deferRemoteOperation(
        db: SQLiteDatabase,
        operation: LedgerOperation,
        transportEventId: String,
        expectedSequence: Long
    ): RemoteOperationResult {
        db.insertOrThrow(
            "ledger_operations",
            null,
            operationValues(operation, transportEventId, SyncState.DEFERRED)
        )
        return RemoteOperationResult(
            RemoteOperationStatus.DEFERRED,
            "Waiting for author sequence $expectedSequence"
        )
    }

    private fun drainDeferredOperations(db: SQLiteDatabase, authorId: String): Int {
        var drained = 0
        while (true) {
            val expectedSequence = maxAuthorSequence(db, authorId) + 1L
            val deferred = readDeferredOperation(db, authorId, expectedSequence) ?: break
            db.delete("ledger_operations", "operation_id = ?", arrayOf(deferred.operation.operationId))
            applyNewRemoteOperation(db, deferred.operation, deferred.transportEventId)
            drained += 1
        }
        return drained
    }

    private fun readDeferredOperation(
        db: SQLiteDatabase,
        authorId: String,
        authorSequence: Long
    ): DeferredOperation? = db.query(
        "ledger_operations",
        null,
        "author_id = ? AND author_sequence = ? AND sync_state = ?",
        arrayOf(authorId, authorSequence.toString(), SyncState.DEFERRED.name),
        null,
        null,
        "created_at ASC",
        "1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val typeValue = cursor.getString(cursor.getColumnIndexOrThrow("operation_type"))
        val type = OperationType.entries.firstOrNull { it.wireValue == typeValue } ?: return@use null
        DeferredOperation(
            operation = LedgerOperation(
                operationId = cursor.getString(cursor.getColumnIndexOrThrow("operation_id")),
                ledgerId = cursor.getString(cursor.getColumnIndexOrThrow("ledger_id")),
                authorId = cursor.getString(cursor.getColumnIndexOrThrow("author_id")),
                authorSequence = cursor.getLong(cursor.getColumnIndexOrThrow("author_sequence")),
                type = type,
                createdAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                payloadJson = cursor.getString(cursor.getColumnIndexOrThrow("payload_json")),
                syncState = SyncState.DEFERRED
            ),
            transportEventId = cursor.getString(cursor.getColumnIndexOrThrow("transport_event_id"))
        )
    }

    private fun operationValues(
        operation: LedgerOperation,
        transportEventId: String,
        state: SyncState
    ) = ContentValues().apply {
        put("operation_id", operation.operationId)
        put("ledger_id", operation.ledgerId)
        put("author_id", operation.authorId)
        put("author_sequence", operation.authorSequence)
        put("operation_type", operation.type.wireValue)
        put("payload_json", operation.payloadJson)
        put("created_at", operation.createdAtMillis)
        put("transport_event_id", transportEventId)
        put("sync_state", state.name)
    }

    private fun participantExists(db: SQLiteDatabase, participantId: String): Boolean =
        db.query(
            "participants",
            arrayOf("id"),
            "ledger_id = ? AND id = ?",
            arrayOf(ledgerId(db), participantId),
            null,
            null,
            null,
            "1"
        ).use { it.moveToFirst() }

    private fun ledgerCurrency(db: SQLiteDatabase): String =
        db.query(
            "ledgers",
            arrayOf("currency"),
            "id = ?",
            arrayOf(ledgerId(db)),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Ledger does not exist" }
            cursor.getString(0)
        }

    private fun ledgerHasExpenses(db: SQLiteDatabase): Boolean =
        db.query(
            "expenses",
            arrayOf("id"),
            "ledger_id = ?",
            arrayOf(ledgerId(db)),
            null,
            null,
            null,
            "1"
        ).use { it.moveToFirst() }

    private fun activeCycleId(db: SQLiteDatabase): String =
        db.query(
            "settlement_cycles",
            arrayOf("id"),
            "ledger_id = ? AND state = 'active'",
            arrayOf(ledgerId(db)),
            null,
            null,
            "started_at DESC",
            "1"
        ).use { cursor ->
            check(cursor.moveToFirst()) { "Ledger has no active settlement cycle" }
            cursor.getString(0)
        }

    private fun cycleExists(db: SQLiteDatabase, cycleId: String): Boolean =
        db.query("settlement_cycles", arrayOf("id"), "id = ?", arrayOf(cycleId), null, null, null, "1")
            .use { it.moveToFirst() }

    private fun editableExpenseExists(db: SQLiteDatabase, expenseId: String): Boolean =
        db.rawQuery(
            """
            SELECT e.id
            FROM expenses e
            INNER JOIN settlement_cycles c ON c.id = e.cycle_id
            WHERE e.id = ? AND e.ledger_id = ? AND e.deleted_at IS NULL AND c.state = 'active'
            LIMIT 1
            """.trimIndent(),
            arrayOf(expenseId, ledgerId(db))
        ).use { it.moveToFirst() }

    private fun calculateCycleSummary(db: SQLiteDatabase, cycleId: String): CycleSummary {
        val sharedByParticipant = mutableMapOf<String, Long>()
        val quotaByParticipant = mutableMapOf<String, Long>()
        var totalMinor = 0L
        var transactionCount = 0
        db.rawQuery(
            """
            SELECT author_id,
                   SUM(amount_minor),
                   SUM(CASE WHEN shared_evenly = 0 THEN 0 ELSE amount_minor END),
                   SUM(CASE WHEN shared_evenly = 0 THEN amount_minor ELSE 0 END),
                   COUNT(*)
            FROM expenses
            WHERE ledger_id = ? AND cycle_id = ? AND deleted_at IS NULL
            GROUP BY author_id
            """.trimIndent(),
            arrayOf(ledgerId(db), cycleId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val authorId = cursor.getString(0)
                sharedByParticipant[authorId] = cursor.getLong(2)
                quotaByParticipant[authorId] = cursor.getLong(3)
                totalMinor += cursor.getLong(1)
                transactionCount += cursor.getInt(4)
            }
        }
        val participantIds = readParticipants(db).map(Participant::id).sorted()
        if (participantIds.size < 2) return CycleSummary(totalMinor, null, 0L, transactionCount)
        val first = participantIds[0]
        val second = participantIds[1]
        val paidFirst = sharedByParticipant[first] ?: 0L
        val paidSecond = sharedByParticipant[second] ?: 0L
        val sharedTotal = paidFirst + paidSecond
        val dueFirst = sharedTotal / 2L + sharedTotal % 2L
        val balanceFirst = (paidFirst - dueFirst) +
            (quotaByParticipant[first] ?: 0L) -
            (quotaByParticipant[second] ?: 0L)
        return when {
            balanceFirst > 0L -> CycleSummary(totalMinor, first, balanceFirst, transactionCount)
            balanceFirst < 0L -> CycleSummary(totalMinor, second, -balanceFirst, transactionCount)
            else -> CycleSummary(totalMinor, null, 0L, transactionCount)
        }
    }

    private fun readSharedEvenly(cursor: android.database.Cursor): Boolean {
        val index = cursor.getColumnIndex("shared_evenly")
        if (index < 0 || cursor.isNull(index)) return true
        return cursor.getInt(index) != 0
    }

    private fun closeCycle(
        db: SQLiteDatabase,
        cycleId: String,
        nextCycleId: String,
        closedAtMillis: Long,
        closedByParticipantId: String,
        summary: CycleSummary,
        method: SettlementMethod,
        note: String
    ) {
        val updated = db.update(
            "settlement_cycles",
            ContentValues().apply {
                put("closed_at", closedAtMillis)
                put("total_minor", summary.totalMinor)
                if (summary.creditorId == null) putNull("creditor_id") else put("creditor_id", summary.creditorId)
                put("balance_minor", summary.balanceMinor)
                put("transaction_count", summary.transactionCount)
                put("closed_by_participant_id", closedByParticipantId)
                put("settlement_method", method.wireValue)
                put("settlement_note", note)
                put("state", "closed")
            },
            "id = ? AND ledger_id = ? AND state = 'active'",
            arrayOf(cycleId, ledgerId(db))
        )
        check(updated == 1) { "Settlement cycle is no longer active" }
        db.insertOrThrow("settlement_cycles", null, ContentValues().apply {
            put("id", nextCycleId)
            put("ledger_id", ledgerId(db))
            put("started_at", closedAtMillis)
            put("total_minor", 0L)
            put("balance_minor", 0L)
            put("transaction_count", 0)
            put("state", "active")
        })
    }

    private fun expenseExists(db: SQLiteDatabase, expenseId: String): Boolean =
        db.query("expenses", arrayOf("id"), "id = ?", arrayOf(expenseId), null, null, null, "1")
            .use { it.moveToFirst() }

    private fun expenseUpdatedAfter(db: SQLiteDatabase, expenseId: String, operationTime: Long): Boolean =
        db.query(
            "expenses",
            arrayOf("updated_at"),
            "id = ?",
            arrayOf(expenseId),
            null,
            null,
            null,
            "1"
        ).use { cursor -> cursor.moveToFirst() && cursor.getLong(0) > operationTime }

    private fun expenseCreatedAt(db: SQLiteDatabase, expenseId: String): Long? =
        db.query(
            "expenses",
            arrayOf("created_at"),
            "id = ?",
            arrayOf(expenseId),
            null,
            null,
            null,
            "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    private fun maxAuthorSequence(db: SQLiteDatabase, authorId: String): Long =
        db.rawQuery(
            "SELECT COALESCE(MAX(author_sequence), 0) FROM ledger_operations WHERE author_id = ? AND sync_state != ?",
            arrayOf(authorId, SyncState.DEFERRED.name)
        ).use {
            it.moveToFirst()
            it.getLong(0)
        }

    private fun authorSequenceExists(
        db: SQLiteDatabase,
        authorId: String,
        authorSequence: Long
    ): Boolean = db.query(
        "ledger_operations",
        arrayOf("operation_id"),
        "author_id = ? AND author_sequence = ?",
        arrayOf(authorId, authorSequence.toString()),
        null,
        null,
        null,
        "1"
    ).use { it.moveToFirst() }

    private fun findOperation(db: SQLiteDatabase, operationId: String): StoredOperation? =
        db.query(
            "ledger_operations",
            arrayOf("ledger_id", "author_id", "author_sequence", "operation_type", "payload_json", "transport_event_id"),
            "operation_id = ?",
            arrayOf(operationId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            StoredOperation(
                ledgerId = cursor.getString(0),
                authorId = cursor.getString(1),
                authorSequence = cursor.getLong(2),
                operationType = cursor.getString(3),
                payloadJson = cursor.getString(4),
                transportEventId = cursor.getString(5)
            )
        }

    private data class StoredOperation(
        val ledgerId: String,
        val authorId: String,
        val authorSequence: Long,
        val operationType: String,
        val payloadJson: String,
        val transportEventId: String?
    ) {
        fun matches(operation: LedgerOperation): Boolean =
            ledgerId == operation.ledgerId &&
                authorId == operation.authorId &&
                authorSequence == operation.authorSequence &&
                operationType == operation.type.wireValue &&
                payloadJson == operation.payloadJson
    }

    private data class StoredTag(
        val id: String,
        val name: String,
        val isArchived: Boolean
    )

    private data class StoredCategory(
        val id: String,
        val name: String,
        val isDefault: Boolean,
        val isArchived: Boolean
    )

    private data class CycleSummary(
        val totalMinor: Long,
        val creditorId: String?,
        val balanceMinor: Long,
        val transactionCount: Int
    )

    private data class RemoteEntry(
        val entryId: String,
        val cycleId: String,
        val payerId: String,
        val amountMinor: Long,
        val currency: String,
        val description: String,
        val merchant: String,
        val notes: String,
        val occurredAtMillis: Long,
        val categoryId: String,
        val tags: List<String>,
        val sharedEvenly: Boolean
    )

    private fun createCategoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS categories (
                id TEXT PRIMARY KEY NOT NULL,
                ledger_id TEXT NOT NULL,
                name TEXT NOT NULL,
                is_default INTEGER NOT NULL DEFAULT 0,
                state TEXT NOT NULL DEFAULT 'active',
                UNIQUE(ledger_id, name),
                FOREIGN KEY(ledger_id) REFERENCES ledgers(id)
            )
            """.trimIndent()
        )
    }

    private fun seedDefaultCategories(db: SQLiteDatabase) {
        ExpenseCategories.defaults.forEach { category ->
            db.insertWithOnConflict("categories", null, ContentValues().apply {
                put("id", category.id)
                put("ledger_id", ledgerId(db))
                put("name", category.displayName)
                put("is_default", 1)
                put("state", "active")
            }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private data class DeferredOperation(
        val operation: LedgerOperation,
        val transportEventId: String
    )

    private fun createSettlementTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS settlement_cycles (
                id TEXT PRIMARY KEY NOT NULL,
                ledger_id TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                closed_at INTEGER,
                total_minor INTEGER NOT NULL DEFAULT 0,
                creditor_id TEXT,
                balance_minor INTEGER NOT NULL DEFAULT 0,
                transaction_count INTEGER NOT NULL DEFAULT 0,
                closed_by_participant_id TEXT,
                settlement_method TEXT NOT NULL DEFAULT 'other',
                settlement_note TEXT NOT NULL DEFAULT '',
                state TEXT NOT NULL,
                FOREIGN KEY(ledger_id) REFERENCES ledgers(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_settlement_cycles_ledger_state ON settlement_cycles(ledger_id, state, started_at)")
    }

    private fun seedInitialCycle(db: SQLiteDatabase, startedAtMillis: Long, cycleId: String) {
        db.insertWithOnConflict("settlement_cycles", null, ContentValues().apply {
            put("id", cycleId)
            put("ledger_id", ledgerId(db))
            put("started_at", startedAtMillis)
            put("total_minor", 0L)
            put("balance_minor", 0L)
            put("transaction_count", 0)
            put("state", "active")
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun createOperationLog(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ledger_operations (
                operation_id TEXT PRIMARY KEY NOT NULL,
                ledger_id TEXT NOT NULL,
                author_id TEXT NOT NULL,
                author_sequence INTEGER NOT NULL,
                operation_type TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                transport_event_id TEXT,
                sync_state TEXT NOT NULL,
                FOREIGN KEY(ledger_id) REFERENCES ledgers(id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_operations_sync ON ledger_operations(sync_state, created_at)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_operations_author_seq ON ledger_operations(author_id, author_sequence)")
    }

    private fun backfillExpenseOperations(db: SQLiteDatabase) {
        db.query("expenses", null, null, null, null, null, "created_at ASC").use { cursor ->
            while (cursor.moveToNext()) {
                val expenseId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                val payload = org.json.JSONObject().apply {
                    put("entry_id", expenseId)
                    put("amount_minor", cursor.getLong(cursor.getColumnIndexOrThrow("amount_minor")))
                    put("currency", cursor.getString(cursor.getColumnIndexOrThrow("currency")))
                    put("description", cursor.getString(cursor.getColumnIndexOrThrow("description")))
                    put("merchant", "")
                    put("notes", "")
                    put("occurred_at_ms", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                    put("category", cursor.getString(cursor.getColumnIndexOrThrow("category_id")))
                    put("tags", org.json.JSONArray())
                }
                val operation = ContentValues().apply {
                    put("operation_id", "backfill-$expenseId")
                    put("ledger_id", cursor.getString(cursor.getColumnIndexOrThrow("ledger_id")))
                    put("author_id", cursor.getString(cursor.getColumnIndexOrThrow("author_id")))
                    put("author_sequence", cursor.getLong(cursor.getColumnIndexOrThrow("author_sequence")))
                    put("operation_type", OperationType.ENTRY_CREATE.wireValue)
                    put("payload_json", payload.toString())
                    put("created_at", cursor.getLong(cursor.getColumnIndexOrThrow("created_at")))
                    put("sync_state", SyncState.APPLIED.name)
                }
                db.insertWithOnConflict("ledger_operations", null, operation, SQLiteDatabase.CONFLICT_IGNORE)
            }
        }
    }

    private fun nextAuthorSequence(db: SQLiteDatabase, authorId: String): Long {
        db.rawQuery(
            "SELECT COALESCE(MAX(author_sequence), 0) + 1 FROM ledger_operations WHERE author_id = ?",
            arrayOf(authorId)
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getLong(0)
        }
    }

    private fun ledgerId(db: SQLiteDatabase): String {
        cachedLedgerId?.let { return it }
        return db.query("ledgers", arrayOf("id"), null, null, null, null, null, "2").use { cursor ->
            check(cursor.moveToFirst()) { "Ledger database is empty" }
            val id = cursor.getString(0)
            check(!cursor.moveToNext()) { "Multiple ledgers are not supported by this client version" }
            id.also { cachedLedgerId = it }
        }
    }

    private fun rowCount(db: SQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun initialCycleId(ledgerId: String): String = "$ledgerId:cycle:0"

    private fun addColumnIfMissing(
        db: SQLiteDatabase,
        table: String,
        column: String,
        statement: String
    ) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var found = false
            while (cursor.moveToNext() && !found) found = cursor.getString(nameIndex) == column
            found
        }
        if (!exists) db.execSQL(statement)
    }

    companion object {
        private const val INITIAL_CYCLE_ID = "cycle-initial"
    }
}

/** A bad key or a torn write must not delete the ledger file. */
private class PreserveLedgerFile : DatabaseErrorHandler {
    override fun onCorruption(
        db: SQLiteDatabase,
        exception: android.database.sqlite.SQLiteException
    ) = Unit
}
