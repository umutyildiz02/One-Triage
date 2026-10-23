package com.vitaltriage.app

/** Measured on the four-core x86_64 emulator. ARM64 keeps its unmeasured two-thread baseline. */
internal object WhisperThreads {
    fun select(abi:String,cores:Int):Int = minOf(if(abi=="x86_64")4 else 2,cores.coerceAtLeast(1))
}
