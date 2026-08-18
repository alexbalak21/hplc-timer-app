package com.alexbalak.hplctimer

import android.media.Ringtone
import android.os.Vibrator

/** Holds references to the currently-playing alarm sound/vibration so it can be stopped on dismiss. */
object AlarmSoundController {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    fun set(ringtone: Ringtone, vibrator: Vibrator) {
        this.ringtone = ringtone
        this.vibrator = vibrator
    }

    fun stop() {
        ringtone?.let { if (it.isPlaying) it.stop() }
        vibrator?.cancel()
        ringtone = null
        vibrator = null
    }
}
