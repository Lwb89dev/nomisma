package com.nomisma.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nomisma.app.MainActivity
import com.nomisma.app.R
import com.nomisma.app.domain.Expense
import com.nomisma.app.domain.LedgerSnapshot
import com.nomisma.app.domain.formatMinorUnits

class ExpenseNotifier(context: Context) {
    private val context = context.applicationContext

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.new_expense_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.new_expense_notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyNewExpenses(expenses: List<Expense>, snapshot: LedgerSnapshot) {
        if (expenses.isEmpty() || !canNotify()) return
        expenses.forEach { expense ->
            val participantName = snapshot.participants.firstOrNull { it.id == expense.authorId }?.name
                ?: context.getString(R.string.new_expense_notification_unknown_participant)
            val title = context.getString(R.string.new_expense_notification_title, participantName)
            val body = context.getString(
                R.string.new_expense_notification_body,
                expense.amountMinor.formatMinorUnits(expense.currency),
                expense.description
            )
            val openApp = PendingIntent.getActivity(
                context,
                expense.id.hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(expense.id.hashCode(), notification)
            } catch (_: SecurityException) {
                return
            }
        }
    }

    private fun canNotify(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED)

    private companion object {
        const val CHANNEL_ID = "new_expenses"
    }
}
