package ru.app.rustoreupdater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import ru.app.rustoreupdater.data.db.TrackedAppEntity
import ru.app.rustoreupdater.install.ApkInstaller
import ru.app.rustoreupdater.install.InstallTrampolineActivity
import java.io.File

/**
 * Posts update / download-complete notifications.
 *
 * Channels are created lazily (required on Android O+) and reused afterwards.
 */
object Notifier {

    const val CHANNEL_UPDATES = "channel_updates"
    const val CHANNEL_DOWNLOADS = "channel_downloads"

    const val NOTIF_UPDATE_BASE = 1000
    const val NOTIF_DOWNLOAD_BASE = 2000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATES,
                context.getString(R.string.notif_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Уведомления о новых версиях приложений" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DOWNLOADS,
                context.getString(R.string.notif_channel_downloads),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Готовые к установке APK" }
        )
    }

    /** Notify that a new version of [app] is available on RuStore. Tapping opens the app. */
    fun notifyUpdateAvailable(context: Context, app: TrackedAppEntity) {
        ensureChannels(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = PendingIntent.getActivity(
            context,
            app.appId.hashCode(),
            launchIntent ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = app.installedVersionCode?.let { "${app.installedVersionCode} → ${app.latestVersionCode}" }
            ?: "Версия ${app.latestVersionName ?: app.latestVersionCode}"

        val notif = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("${app.appName}: ${context.getString(R.string.notif_update_title)}")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_UPDATE_BASE + (app.appId.hashCode() and 0x3ff), notif)
    }

    /**
     * Notify that an APK has finished downloading and is ready to install.
     * Tapping launches the system installer.
     */
    fun notifyDownloadComplete(context: Context, app: TrackedAppEntity, apkFile: File) {
        ensureChannels(context)

        val canInstall = ApkInstaller.canRequestInstall(context)
        // The install flow launches from a transparent trampoline ACTIVITY rather than
        // a BroadcastReceiver, because background-activity-launch is restricted on
        // Android 14+ / Samsung One UI and would silently block the installer dialog.
        val intent = InstallTrampolineActivity.newInstallIntent(context, apkFile.absolutePath)
        val pi = PendingIntent.getActivity(
            context,
            (app.appId + apkFile.absolutePath.hashCode()).hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("${app.appName}: ${context.getString(R.string.notif_download_title)}")
            .setContentText(context.getString(R.string.notif_download_text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(
                android.R.drawable.ic_menu_set_as,
                if (canInstall) "Установить" else "Разрешить установку",
                pi
            )
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_DOWNLOAD_BASE + (app.appId.hashCode() and 0x3ff), notif)
    }
}
