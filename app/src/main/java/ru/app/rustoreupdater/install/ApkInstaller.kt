package ru.app.rustoreupdater.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * Triggers the system package installer for a downloaded APK file.
 *
 * Normal (non-root) apps cannot silently install APKs. We launch the system
 * "Install" UI via [Intent.ACTION_VIEW]; the user confirms in the system dialog.
 *
 * On Android 8+ the install intent requires the REQUEST_INSTALL_PACKAGES
 * permission (declared in the manifest) and the user must grant it on first use.
 *
 * Result codes from [install]:
 *  - [RESULT_OK]: the system installer activity was launched.
 *  - [RESULT_NEED_PERMISSION]: the "install unknown apps" permission is missing;
 *    the caller should guide the user to the settings screen first.
 *  - [RESULT_ERROR]: the file does not exist or the intent could not be started.
 */
object ApkInstaller {

    const val RESULT_OK = 0
    const val RESULT_NEED_PERMISSION = 1
    const val RESULT_ERROR = 2

    private const val TAG = "ApkInstaller"

    /**
     * Launch the system installer for [apkFile].
     */
    fun install(context: Context, apkFile: File): Int {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
            return RESULT_ERROR
        }

        // On Android O+, ensure we have the install-unknown-apps privilege.
        if (!canRequestInstall(context)) {
            Log.w(TAG, "REQUEST_INSTALL_PACKAGES not granted — opening settings.")
            openInstallPermissionSettings(context)
            return RESULT_NEED_PERMISSION
        }

        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = try {
            FileProvider.getUriForFile(context, authority, apkFile)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "FileProvider could not share ${apkFile.absolutePath}", e)
            apkFile.toUri()
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Keep the installer on top so the user sees the confirmation dialog.
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        return try {
            context.startActivity(intent)
            Log.d(TAG, "Installer launched for ${apkFile.name}")
            RESULT_OK
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start installer for ${apkFile.absolutePath}", e)
            RESULT_ERROR
        }
    }

    fun canRequestInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Opens the system "Install unknown apps" screen for this app. */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
