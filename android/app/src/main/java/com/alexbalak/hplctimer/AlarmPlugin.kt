package com.alexbalak.hplctimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "BatchAlarm")
class AlarmPlugin : Plugin() {

    @PluginMethod
    fun scheduleAlarm(call: PluginCall) {
        val triggerAtMillis = call.getLong("triggerAtMillis") ?: return call.reject("Missing triggerAtMillis")
        val context = context
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Android 12+ requires the user to explicitly grant exact-alarm scheduling.
        // Without this check, setExactAndAllowWhileIdle silently fails on some OEMs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            val settingsIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:" + context.packageName)
            }
            context.startActivity(settingsIntent)
            call.reject("Exact alarm permission not granted. Opened system settings — please enable and try again.")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        call.resolve()
    }

    @PluginMethod
    fun checkExactAlarmPermission(call: PluginCall) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            alarmManager.canScheduleExactAlarms() else true
        val result = com.getcapacitor.JSObject()
        result.put("granted", granted)
        call.resolve(result)
    }
}