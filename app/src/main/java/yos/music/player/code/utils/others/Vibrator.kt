@file:Suppress("DEPRECATION")

package yos.music.player.code.utils.others

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.provider.Settings
import androidx.compose.runtime.Stable

/** Shared by new click handlers and existing controls, so one action gives one pulse. */
@Stable
class Vibrator {
    companion object {
        private var lastPulse = Long.MIN_VALUE

        @Synchronized
        private fun pulse(context: Context, effect: Int) {
            // Respect the phone's touch-feedback preference; never vibrate for media state updates.
            if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
            val now = SystemClock.uptimeMillis()
            if (lastPulse != Long.MIN_VALUE && now - lastPulse < 60L) return
            val motor = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator ?: return
            if (!motor.hasVibrator()) return
            lastPulse = now
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                motor.vibrate(VibrationEffect.createPredefined(effect))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                motor.vibrate(VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else motor.vibrate(12L)
        }

        fun click(context: Context) = pulse(context, VibrationEffect.EFFECT_TICK)
        fun longClick(context: Context) = pulse(context, VibrationEffect.EFFECT_HEAVY_CLICK)
        fun doubleClick(context: Context) = pulse(context, VibrationEffect.EFFECT_DOUBLE_CLICK)
    }
}
