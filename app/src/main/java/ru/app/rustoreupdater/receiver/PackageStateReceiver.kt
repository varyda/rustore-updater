package ru.app.rustoreupdater.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.download.ActiveDownloads

/**
 * Listens for app install / replace / remove events from the system so that the
 * tracked-apps list reflects the real installed state immediately.
 *
 * Registered in the manifest with an intent filter for PACKAGE_ADDED / PACKAGE_REPLACED /
 * PACKAGE_REMOVED, scoped via the data scheme to fire only for package names we track.
 *
 * When a relevant package changes, we reconcile the installed version code for every
 * tracked app that matches the changed package, which flips `updateAvailable` to false
 * right after a successful install/update.
 */
class PackageStateReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        val action = intent.action
        Log.d(TAG, "onReceive action=$action pkg=$packageName")
        when (action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // The app finished installing — clear the "installing…" UI state.
                ActiveDownloads.clearInstallingByPackage(packageName)
                scope.launch {
                    val repo = ServiceLocator.appRepository(context)
                    repo.reconcilePackage(packageName)
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                // The app was uninstalled — clear any stuck "installing…" state too.
                ActiveDownloads.clearInstallingByPackage(packageName)
                scope.launch {
                    val repo = ServiceLocator.appRepository(context)
                    repo.reconcilePackage(packageName)
                }
            }
        }
    }

    companion object {
        private const val TAG = "PackageStateReceiver"
    }
}
