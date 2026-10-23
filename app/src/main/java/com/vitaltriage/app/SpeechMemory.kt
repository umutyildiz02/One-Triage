package com.vitaltriage.app

import android.app.ActivityManager
import android.content.Context

internal class SpeechMemoryException:IllegalStateException("Speech memory pressure")

/** API34+ does not deliver RUNNING_CRITICAL callbacks. Query the real system flag instead. */
internal object SpeechMemory {
    fun isLow(context:Context):Boolean {
        val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info=ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.lowMemory
    }
}
