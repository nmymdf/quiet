package com.archie.quiet

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import android.service.quicksettings.TileService

/** 一鍵安靜的核心：開啟時記下原本的設定，關閉時全部還原。 */
object Quiet {
    const val ACTION_TOGGLE = "com.archie.quiet.TOGGLE"
    const val ACTION_AUTO_OFF = "com.archie.quiet.AUTO_OFF"

    /** 忘了關的保險：開啟後約 4 小時自動恢復。 */
    const val AUTO_OFF_MS = 4L * 60 * 60 * 1000

    private const val PREFS = "quiet"
    private const val MAX_CALLS = 30

    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isOn(c: Context): Boolean = prefs(c).getBoolean("on", false)

    fun startedAt(c: Context): Long = prefs(c).getLong("startedAt", 0L)

    // ---------- 權限 ----------

    data class Perms(
        val dnd: Boolean,
        val brightness: Boolean,
        val contacts: Boolean,
        val screening: Boolean,
    ) {
        val all: Boolean get() = dnd && brightness && contacts && screening
    }

    fun perms(c: Context): Perms {
        val nm = c.getSystemService(NotificationManager::class.java)
        val dnd = nm?.isNotificationPolicyAccessGranted == true
        val bright = Settings.System.canWrite(c)
        val contacts = c.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val rm = c.getSystemService(RoleManager::class.java)
        val screening = try {
            rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } catch (_: Exception) {
            false
        }
        return Perms(dnd, bright, contacts, screening)
    }

    // ---------- 開啟時實際做到哪些 ----------

    data class Applied(
        val ringer: Boolean,
        val dnd: Boolean,
        val brightness: Boolean,
        val calls: Boolean,
        val contacts: Boolean,
    )

    fun applied(c: Context): Applied {
        val p = prefs(c)
        return Applied(
            ringer = p.getBoolean("a_ringer", false),
            dnd = p.getBoolean("a_dnd", false),
            brightness = p.getBoolean("a_bright", false),
            calls = p.getBoolean("a_calls", false),
            contacts = p.getBoolean("a_contacts", false),
        )
    }

    // ---------- 開關 ----------

    fun toggle(c: Context) {
        if (isOn(c)) turnOff(c) else turnOn(c)
    }

    fun turnOn(c: Context) {
        if (isOn(c)) return
        val p = prefs(c)
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val cr = c.contentResolver
        val e = p.edit()

        // 1. 記下原本的狀態
        e.putInt("prevFilter", nm.currentInterruptionFilter)
        e.putInt("prevRinger", am.ringerMode)
        e.putBoolean("prevPowerSave", isPowerSave(c))
        val canBright = Settings.System.canWrite(c)
        if (canBright) {
            e.putInt("prevBright", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
            e.putInt(
                "prevBrightMode",
                Settings.System.getInt(
                    cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                ),
            )
        }

        // 2. 鈴聲靜音（先做，再開勿擾）
        val ringerOk = try {
            am.ringerMode = AudioManager.RINGER_MODE_SILENT
            true
        } catch (_: Exception) {
            false
        }

        // 3. 勿擾：用三星自己的勿擾方式，但暫時不留任何例外（鬧鐘、媒體、重複來電都擋），
        //    並且通知進來不亮螢幕、不跳出。原本的勿擾例外先記下，關閉時還原。
        //    （v0.1 用「完全靜音」，三星仍會放行鬧鐘，所以改成這樣）
        e.putBoolean("policySaved", false)
        val dndOk = if (nm.isNotificationPolicyAccessGranted) {
            try {
                val old = nm.notificationPolicy
                e.putInt("pCats", old.priorityCategories)
                    .putInt("pCall", old.priorityCallSenders)
                    .putInt("pMsg", old.priorityMessageSenders)
                    .putInt("pVis", old.suppressedVisualEffects)
                if (Build.VERSION.SDK_INT >= 30) e.putInt("pConv", old.priorityConversationSenders)
                e.putBoolean("policySaved", true)
            } catch (_: Exception) {
            }
            try {
                nm.notificationPolicy = quietPolicy()
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                true
            } catch (_: Exception) {
                // 退回 v0.1 的做法
                try {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } else {
            false
        }

        // 4. 螢幕調到最暗
        val brightOk = if (canBright) {
            try {
                Settings.System.putInt(
                    cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                )
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 1)
                true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        val perms = perms(c)
        val now = System.currentTimeMillis()
        e.putBoolean("on", true)
            .putLong("startedAt", now)
            .putBoolean("a_ringer", ringerOk)
            .putBoolean("a_dnd", dndOk)
            .putBoolean("a_bright", brightOk)
            .putBoolean("a_calls", perms.screening)
            .putBoolean("a_contacts", perms.contacts)
            .putString("calls", "")
            .commit()

        scheduleAutoOff(c, now + AUTO_OFF_MS)
        refreshViews(c)
    }

    fun turnOff(c: Context) {
        val p = prefs(c)
        if (!p.getBoolean("on", false)) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val cr = c.contentResolver

        // 先還原勿擾例外與勿擾，再還原鈴聲（勿擾開著時改鈴聲可能被擋）
        if (nm.isNotificationPolicyAccessGranted) {
            if (p.getBoolean("policySaved", false)) {
                try {
                    nm.notificationPolicy = savedPolicy(p)
                } catch (_: Exception) {
                }
            }
            var prev = p.getInt("prevFilter", NotificationManager.INTERRUPTION_FILTER_ALL)
            if (prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                prev = NotificationManager.INTERRUPTION_FILTER_ALL
            }
            try {
                nm.setInterruptionFilter(prev)
            } catch (_: Exception) {
            }
        }
        try {
            am.ringerMode = p.getInt("prevRinger", AudioManager.RINGER_MODE_NORMAL)
        } catch (_: Exception) {
        }
        if (p.getBoolean("a_bright", false) && Settings.System.canWrite(c)) {
            try {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, p.getInt("prevBright", 128))
                Settings.System.putInt(
                    cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    p.getInt("prevBrightMode", Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL),
                )
            } catch (_: Exception) {
            }
        }

        p.edit().putBoolean("on", false).putLong("endedAt", System.currentTimeMillis()).commit()
        cancelAutoOff(c)
        refreshViews(c)
    }

    // ---------- 勿擾例外 ----------

    /** 安靜中的勿擾：沒有任何例外；通知進來不亮螢幕、不跳出、不閃燈（通知列仍看得到）。 */
    private fun quietPolicy(): NotificationManager.Policy {
        val vis = NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
            NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or
            NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or
            NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT
        val starred = NotificationManager.Policy.PRIORITY_SENDERS_STARRED
        return if (Build.VERSION.SDK_INT >= 30) {
            NotificationManager.Policy(
                0, starred, starred, vis,
                NotificationManager.Policy.CONVERSATION_SENDERS_NONE,
            )
        } else {
            NotificationManager.Policy(0, starred, starred, vis)
        }
    }

    private fun savedPolicy(p: SharedPreferences): NotificationManager.Policy {
        val cats = p.getInt("pCats", 0)
        val call = p.getInt("pCall", NotificationManager.Policy.PRIORITY_SENDERS_STARRED)
        val msg = p.getInt("pMsg", NotificationManager.Policy.PRIORITY_SENDERS_STARRED)
        val vis = p.getInt("pVis", 0)
        return if (Build.VERSION.SDK_INT >= 30 && p.contains("pConv")) {
            NotificationManager.Policy(cats, call, msg, vis, p.getInt("pConv", 0))
        } else {
            NotificationManager.Policy(cats, call, msg, vis)
        }
    }

    // ---------- 省電模式（系統不讓 App 直接開關，只能幫忙打開設定頁） ----------

    fun isPowerSave(c: Context): Boolean = try {
        (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
    } catch (_: Exception) {
        false
    }

    /** 關閉安靜後，省電模式是安靜期間才打開的 → 提醒要不要關。 */
    fun powerSaveLeftOn(c: Context): Boolean =
        !isOn(c) && isPowerSave(c) && !prefs(c).getBoolean("prevPowerSave", true)

    // ---------- 開機後補排自動恢復 ----------

    fun onBoot(c: Context) {
        if (!isOn(c)) {
            refreshViews(c)
            return
        }
        val at = startedAt(c) + AUTO_OFF_MS
        if (at <= System.currentTimeMillis()) turnOff(c) else scheduleAutoOff(c, at)
        refreshViews(c)
    }

    private fun autoOffIntent(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 1,
        Intent(c, QuietReceiver::class.java).setAction(ACTION_AUTO_OFF),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleAutoOff(c: Context, at: Long) {
        try {
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, autoOffIntent(c))
        } catch (_: Exception) {
        }
    }

    private fun cancelAutoOff(c: Context) {
        try {
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(autoOffIntent(c))
        } catch (_: Exception) {
        }
    }

    // ---------- 安靜期間擋下的來電 ----------

    data class BlockedCall(val time: Long, val number: String, val name: String?)

    fun recordCall(c: Context, number: String) {
        val name = contactName(c, number)
        val p = prefs(c)
        synchronized(this) {
            val lines = p.getString("calls", "").orEmpty()
                .split('\n').filter { it.isNotBlank() }.toMutableList()
            val clean = { s: String -> s.replace('\t', ' ').replace('\n', ' ') }
            lines.add("${System.currentTimeMillis()}\t${clean(number)}\t${clean(name.orEmpty())}")
            while (lines.size > MAX_CALLS) lines.removeAt(0)
            p.edit().putString("calls", lines.joinToString("\n")).commit()
        }
    }

    fun calls(c: Context): List<BlockedCall> =
        prefs(c).getString("calls", "").orEmpty().split('\n').mapNotNull { line ->
            val f = line.split('\t')
            val t = f.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            BlockedCall(t, f.getOrNull(1).orEmpty(), f.getOrNull(2)?.takeIf { it.isNotBlank() })
        }

    fun clearCalls(c: Context) {
        prefs(c).edit().putString("calls", "").apply()
    }

    private fun contactName(c: Context, number: String): String? {
        if (number.isBlank()) return null
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number),
            )
            c.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null,
            )?.use { cur -> if (cur.moveToFirst()) cur.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    // ---------- 更新桌面小工具與快速設定 ----------

    fun refreshViews(c: Context) {
        try {
            QuietWidget.updateAll(c)
        } catch (_: Exception) {
        }
        try {
            TileService.requestListeningState(c, ComponentName(c, QuietTile::class.java))
        } catch (_: Exception) {
        }
    }
}
