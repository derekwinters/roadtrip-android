package com.roadtrip.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.roadtrip.core.location.BatteryOptimizationExempter

/**
 * Requests a Doze/OEM battery-optimization exemption for the app so the tracker foreground
 * service is not killed under Doze or aggressive OEM battery managers (ANDLOC-011). Wired in
 * only from the parent tracker-enable flow, so kid profiles never reach it (ANDLOC-005).
 */
class SystemBatteryOptimizationExempter(private val context: Context) : BatteryOptimizationExempter {

    @SuppressLint("BatteryLife")
    override fun requestExemptionIfNeeded() {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (power.isIgnoringBatteryOptimizations(context.packageName)) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Some OEMs/emulators lack this settings activity; tracking still works, it is
            // just more killable. Non-fatal.
        }
    }
}
