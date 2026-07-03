package ru.app.rustoreupdater.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.app.rustoreupdater.di.ServiceLocator
import ru.app.rustoreupdater.ui.nav.NavGraph
import ru.app.rustoreupdater.ui.theme.RuStoreUpdaterTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result ignored */ }

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Android 13+: ask for POST_NOTIFICATIONS so update/download notifications appear.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            RuStoreUpdaterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have just installed/updated an app via the system installer.
        // Reconcile the installed state so the list immediately reflects reality.
        bgScope.launch {
            runCatching { ServiceLocator.appRepository(applicationContext).reconcileAllInstalled() }
        }
    }
}
