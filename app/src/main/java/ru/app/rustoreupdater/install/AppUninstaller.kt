package ru.app.rustoreupdater.install

import android.content.Context

/**
 * Launches the system "Uninstall" dialog for the given package.
 *
 * Routes through [InstallTrampolineActivity] so the uninstall intent runs from a
 * stable foreground context (avoids the uninstaller being instantly dismissed by
 * the background-activity-launch restriction on Android 14+ / Samsung One UI).
 */
object AppUninstaller {

    fun uninstall(context: Context, packageName: String) {
        val intent = InstallTrampolineActivity.newUninstallIntent(context, packageName)
        runCatching { context.startActivity(intent) }
    }
}
