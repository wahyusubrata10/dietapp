package com.hawdietpro.app.core

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
import com.hawdietpro.app.MainActivity
import com.hawdietpro.app.R

object Notifications {
    const val CHANNEL_ANALYSIS = "analisa"
    const val CHANNEL_UPDATE = "update"

    const val EXTRA_OPEN_MEAL = "open_meal_id"
    const val EXTRA_OPEN_UPDATE = "open_update"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ANALYSIS, "Hasil analisa antrian", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pemberitahuan saat foto di antrian selesai dianalisa"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATE, "Update aplikasi", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Pemberitahuan versi baru Haw Diet Pro"
            },
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun intent(context: Context, block: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            block()
        }
        return PendingIntent.getActivity(
            context,
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun mealAnalyzed(context: Context, mealId: Long, title: String, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_ANALYSIS)
            .setSmallIcon(R.drawable.ic_stat_haw)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(intent(context) { putExtra(EXTRA_OPEN_MEAL, mealId) })
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(mealId.toInt(), n) }
    }

    fun analysisFailed(context: Context, text: String) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_ANALYSIS)
            .setSmallIcon(R.drawable.ic_stat_haw)
            .setContentTitle("Analisa antrian gagal")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(intent(context) {})
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(9001, n) }
    }

    fun updateAvailable(context: Context, versionName: String, ready: Boolean) {
        if (!canPost(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_UPDATE)
            .setSmallIcon(R.drawable.ic_stat_haw)
            .setContentTitle(if (ready) "Update siap dipasang" else "Update tersedia")
            .setContentText("Haw Diet Pro $versionName — ketuk untuk ${if (ready) "memasang" else "melihat"}.")
            .setAutoCancel(true)
            .setContentIntent(intent(context) { putExtra(EXTRA_OPEN_UPDATE, true) })
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(9002, n) }
    }
}
