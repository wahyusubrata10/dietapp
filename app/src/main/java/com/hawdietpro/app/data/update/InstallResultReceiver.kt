package com.hawdietpro.app.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun updates(): UpdateRepository
}

/** Menerima status sesi PackageInstaller dan menampilkan dialog konfirmasi instal sistem. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val updates = EntryPointAccessors.fromApplication(context.applicationContext, UpdateEntryPoint::class.java).updates()
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // aplikasi akan dimulai ulang oleh sistem
            PackageInstaller.STATUS_FAILURE_ABORTED -> updates.reportInstallError("Pemasangan dibatalkan. Ketuk Install lagi kapan saja.")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                updates.reportInstallError(
                    when (status) {
                        PackageInstaller.STATUS_FAILURE_CONFLICT,
                        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Update tidak bisa dipasang: tanda tangan/versi tidak cocok."
                        PackageInstaller.STATUS_FAILURE_STORAGE -> "Ruang penyimpanan tidak cukup untuk memasang update."
                        else -> "Pemasangan gagal${msg?.let { ": $it" } ?: ""}"
                    },
                )
            }
        }
    }
}
