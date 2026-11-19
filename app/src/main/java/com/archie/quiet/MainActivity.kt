package com.archie.quiet

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> tick++ }

    private val contactsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                openAppInfo()
            }
            tick++
        }

    private val roleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        setContent {
            // 讀 tick：狀態一變就重新讀取下面的資料
            @Suppress("UNUSED_VARIABLE")
            val refresh = tick
            val state = ScreenState(
                on = Quiet.isOn(this),
                perms = Quiet.perms(this),
                applied = Quiet.applied(this),
                calls = Quiet.calls(this),
                startedAt = Quiet.startedAt(this),
                powerSave = Quiet.isPowerSave(this),
                powerLeftOn = Quiet.powerSaveLeftOn(this),
                version = version,
            )
            val actions = ScreenActions(
                onToggle = {
                    Quiet.toggle(this)
                    tick++
                },
                onDnd = { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                onBrightness = {
                    open(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
                },
                onContacts = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) },
                onScreening = { requestScreeningRole() },
                onAppInfo = { openAppInfo() },
                onPowerSave = { openPowerSave() },
                onClearCalls = {
                    Quiet.clearCalls(this)
                    tick++
                },
            )
            QuietTheme {
                MainScreen(state, actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Quiet.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        tick++
    }

    override fun onPause() {
        super.onPause()
        Quiet.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            openAppInfo()
        }
    }

    private fun openAppInfo() {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            )
        } catch (_: Exception) {
        }
    }

    /** 系統不讓 App 直接開關省電模式，只能打開設定頁讓使用者按一下。 */
    private fun openPowerSave() {
        try {
            startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        } catch (_: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            } catch (_: Exception) {
            }
        }
    }

    private fun requestScreeningRole() {
        val rm = getSystemService(RoleManager::class.java)
        try {
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            }
        } catch (_: Exception) {
        }
    }
}
