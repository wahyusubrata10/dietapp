package com.hawdietpro.app.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.hawdietpro.app.MainActivity
import com.hawdietpro.app.R

/** Aksi cepat yang bisa dibuka dari widget & shortcut ikon aplikasi. */
enum class QuickLaunch(val id: String) {
    CAMERA("camera"),
    BARCODE("barcode"),
    RELOG("relog"),
    MANUAL("manual"),
    EXERCISE("exercise"),
    HOME("home");

    /** Intent ke MainActivity; action unik per aksi agar PendingIntent tidak saling menimpa. */
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction("$ACTION_PREFIX.$id")
        .putExtra(EXTRA, id)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    companion object {
        const val EXTRA = "com.hawdietpro.app.QUICK"
        private const val ACTION_PREFIX = "com.hawdietpro.app.action.QUICK"

        fun from(intent: Intent?): QuickLaunch? = intent?.getStringExtra(EXTRA)?.let { v -> entries.firstOrNull { it.id == v } }
    }
}

/** Shortcut saat ikon aplikasi ditekan lama (dinamis agar cocok untuk varian debug & rilis). */
object AppShortcuts {
    fun publish(context: Context) {
        runCatching {
            val items = listOf(
                Triple(QuickLaunch.CAMERA, "Foto makanan", R.drawable.ic_sc_camera),
                Triple(QuickLaunch.BARCODE, "Scan barcode", R.drawable.ic_sc_barcode),
                Triple(QuickLaunch.RELOG, "Makan lagi", R.drawable.ic_sc_replay),
                Triple(QuickLaunch.MANUAL, "Input manual", R.drawable.ic_sc_edit),
            )
            val shortcuts = items.mapIndexed { i, (q, label, icon) ->
                ShortcutInfoCompat.Builder(context, q.id)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, icon))
                    .setIntent(q.intent(context))
                    .setRank(i)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }
}
