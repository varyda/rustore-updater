package ru.app.rustoreupdater.install

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.File

/**
 * Invisible "trampoline" activity launched from notification PendingIntents and UI
 * actions to perform APK install / uninstall operations.
 *
 * Why this exists: on Android 14+ (and Samsung One UI) starting the package
 * installer/uninstaller activity directly from a BroadcastReceiver or a transient
 * Compose recomposition can be blocked or immediately dismissed by the
 * background-activity-launch (BAL) restriction. Launching a transparent activity
 * first gives a stable foreground context from which the system installer /
 * uninstaller runs reliably.
 *
 * The activity finishes immediately after handing off the intent.
 */
class InstallTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent.action) {
            ACTION_UNINSTALL -> {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                Log.d(TAG, "Trampoline uninstall pkg=$packageName")
                if (packageName != null) {
                    val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        startActivity(uninstallIntent)
                        Log.d(TAG, "Uninstaller launched for $packageName")
                        // Do NOT finish immediately: on Samsung One UI the uninstaller
                        // is a transient activity tied to this one, and finishing now
                        // collapses it before the dialog is shown. Finish on resume,
                        // once the uninstaller has taken over the foreground.
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch uninstaller for $packageName", e)
                        finish()
                    }
                } else {
                    finish()
                }
            }
            else -> {
                // Install path.
                val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
                Log.d(TAG, "Trampoline onCreate, apkPath=$apkPath, canInstall=${ApkInstaller.canRequestInstall(this)}")
                if (apkPath != null) {
                    val result = ApkInstaller.install(this, File(apkPath))
                    Log.d(TAG, "ApkInstaller.install result=$result")
                }
                // Always finish — we never show any UI.
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // For the uninstall flow we delayed finish() so the system uninstaller could
        // come to the foreground. By the time we reach onResume again the uninstaller
        // has taken over (or was dismissed), so it's safe to close the trampoline.
        if (intent.action == ACTION_UNINSTALL) {
            finish()
        }
    }

    companion object {
        private const val TAG = "InstallTrampoline"
        const val EXTRA_APK_PATH = "extra_apk_path"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val ACTION_UNINSTALL = "ru.app.rustoreupdater.ACTION_UNINSTALL"

        /** Build a launch intent for the install trampoline carrying the apk path. */
        fun newInstallIntent(context: Context, apkPath: String): Intent =
            Intent(context, InstallTrampolineActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                putExtra(EXTRA_APK_PATH, apkPath)
            }

        /** Build a launch intent for the uninstall trampoline carrying the package name. */
        fun newUninstallIntent(context: Context, packageName: String): Intent =
            Intent(context, InstallTrampolineActivity::class.java).apply {
                action = ACTION_UNINSTALL
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
    }
}
