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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Gold = Color(0xFFC9A227)
val QuietGreen = Color(0xFF2E7D32)

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
            val on = Quiet.isOn(this)
            val perms = Quiet.perms(this)
            val applied = Quiet.applied(this)
            val calls = Quiet.calls(this)
            val startedAt = Quiet.startedAt(this)
            val powerSave = Quiet.isPowerSave(this)
            val powerLeftOn = Quiet.powerSaveLeftOn(this)
            QuietTheme {
                MainScreen(
                    on = on,
                    perms = perms,
                    applied = applied,
                    calls = calls,
                    startedAt = startedAt,
                    powerSave = powerSave,
                    powerLeftOn = powerLeftOn,
                    version = version,
                    onToggle = {
                        Quiet.toggle(this)
                        tick++
                    },
                    onDnd = { open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                    onBrightness = {
                        open(
                            Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:$packageName"),
                            ),
                        )
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

// ---------------- 畫面 ----------------

@Composable
fun QuietTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF81C784),
            onPrimary = Color(0xFF0B2E10),
            background = Color(0xFF121417),
            onBackground = Color(0xFFECEFF1),
            surface = Color(0xFF1C1F24),
            onSurface = Color(0xFFECEFF1),
            surfaceVariant = Color(0xFF2A2E35),
            onSurfaceVariant = Color(0xFFB0BEC5),
            outline = Color(0xFF78909C),
        )
    } else {
        lightColorScheme(
            primary = QuietGreen,
            onPrimary = Color.White,
            background = Color(0xFFF5F6F8),
            onBackground = Color(0xFF1B1B1F),
            surface = Color.White,
            onSurface = Color(0xFF1B1B1F),
            surfaceVariant = Color(0xFFE8F5E9),
            onSurfaceVariant = Color(0xFF455A64),
            outline = Color(0xFF90A4AE),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun okColor(): Color = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

@Composable
private fun badColor(): Color = if (isSystemInDarkTheme()) Color(0xFFEF9A9A) else Color(0xFFD32F2F)

private fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.TAIWAN).format(Date(t))

@Composable
fun MainScreen(
    on: Boolean,
    perms: Quiet.Perms,
    applied: Quiet.Applied,
    calls: List<Quiet.BlockedCall>,
    startedAt: Long,
    powerSave: Boolean,
    powerLeftOn: Boolean,
    version: String,
    onToggle: () -> Unit,
    onDnd: () -> Unit,
    onBrightness: () -> Unit,
    onContacts: () -> Unit,
    onScreening: () -> Unit,
    onAppInfo: () -> Unit,
    onPowerSave: () -> Unit,
    onClearCalls: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "一鍵安靜",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))

                if (!perms.all) {
                    SetupCard(perms, onDnd, onBrightness, onContacts, onScreening, onAppInfo)
                    Spacer(Modifier.height(24.dp))
                }

                BigButton(on = on, onClick = onToggle)
                Spacer(Modifier.height(20.dp))

                if (on) {
                    OnStatus(applied, calls.size, startedAt, powerSave, onPowerSave)
                } else {
                    Text(
                        "點一下圓圈，全部安靜",
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (powerLeftOn) {
                        Spacer(Modifier.height(20.dp))
                        PowerLeftCard(onPowerSave)
                    }
                    if (calls.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        CallsCard(calls, onClearCalls)
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text(
                    "作者：ArchieKUO　v$version",
                    color = Gold,
                    fontStyle = FontStyle.Italic,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun BigButton(on: Boolean, onClick: () -> Unit) {
    val fg = if (on) Color.White else MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(CircleShape)
            .background(if (on) QuietGreen else MaterialTheme.colorScheme.surface)
            .then(if (on) Modifier else Modifier.border(3.dp, ring, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(if (on) R.drawable.ic_quiet else R.drawable.ic_sound),
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (on) "安靜中" else "開始安靜",
                fontSize = if (on) 38.sp else 32.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                textAlign = TextAlign.Center,
            )
            if (on) {
                Spacer(Modifier.height(4.dp))
                Text("點一下恢復", fontSize = 20.sp, color = fg, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun OnStatus(
    applied: Quiet.Applied,
    blocked: Int,
    startedAt: Long,
    powerSave: Boolean,
    onPowerSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth(),
    ) {
        StatusRow(applied.ringer, "鈴聲靜音")
        StatusRow(applied.dnd, "勿擾全擋（含鬧鐘）")
        StatusRow(applied.brightness, "螢幕調到最暗")
        StatusRow(applied.calls && applied.contacts, "來電全部拒接（含連續來電）")
        PowerRow(powerSave, onPowerSave)
    }
    if (!powerSave) {
        Text(
            "省電要自己按一下開關（系統不讓 App 自動開）",
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Spacer(Modifier.height(12.dp))
    if (blocked > 0) {
        Text(
            "已擋下 $blocked 通來電",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
    Text(
        "${hhmm(startedAt + Quiet.AUTO_OFF_MS)} 左右會自動恢復",
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(20.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "記得手錶也開「劇院模式」",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "手錶從上往下滑，點「劇院模式」",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun StatusRow(ok: Boolean, label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (ok) "✓" else "✗",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) okColor() else badColor(),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        Text(
            if (ok) label else "$label（未設定）",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun PowerRow(on: Boolean, onGo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (on) "✓" else "•",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = if (on) okColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(44.dp),
        )
        Text(
            "省電模式",
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        if (!on) {
            Button(
                onClick = onGo,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("去打開", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun PowerLeftCard(onGo: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "省電模式還開著",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "不需要的話，按下面去關掉",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onGo,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Text("去關閉省電", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun SetupCard(
    perms: Quiet.Perms,
    onDnd: () -> Unit,
    onBrightness: () -> Unit,
    onContacts: () -> Unit,
    onScreening: () -> Unit,
    onAppInfo: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                "第一次使用，請完成下面設定",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SetupRow(perms.dnd, "1. 勿擾權限", "找到「一鍵安靜」，把開關打開。", onDnd)
            HorizontalDivider()
            SetupRow(perms.brightness, "2. 調整亮度", "把「允許修改系統設定」打開。", onBrightness)
            HorizontalDivider()
            SetupRow(perms.contacts, "3. 讀取聯絡人", "聯絡人打來也能拒接，並顯示是誰打來。", onContacts)
            HorizontalDivider()
            SetupRow(perms.screening, "4. 來電過濾", "出現詢問時，選「一鍵安靜」並設為預設。", onScreening)
            Spacer(Modifier.height(12.dp))
            Text(
                "如果開關是灰色、顯示「受限設定」：按下面按鈕，點右上角「⋮」→「允許受限設定」，再回來設定。",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAppInfo,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Text("打開應用程式資訊", fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun SetupRow(done: Boolean, title: String, desc: String, onGo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (done) {
                Text("✓ 已完成", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = okColor())
            } else {
                Button(
                    onClick = onGo,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("去設定", fontSize = 20.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(desc, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CallsCard(calls: List<Quiet.BlockedCall>, onClear: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                "剛才安靜時的來電（${calls.size} 通）",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            calls.asReversed().forEach { call ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        call.name ?: call.number.ifBlank { "未顯示號碼" },
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        hhmm(call.time),
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "電話的通話紀錄裡也查得到",
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClear,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                Text("知道了", fontSize = 20.sp)
            }
        }
    }
}
