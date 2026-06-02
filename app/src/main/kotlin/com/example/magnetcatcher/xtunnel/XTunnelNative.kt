package com.example.magnetcatcher.xtunnel

object XTunnelNative {
    val available: Boolean = runCatching {
        System.loadLibrary("go")
        System.loadLibrary("xtbridge")
    }.isSuccess

    @JvmStatic external fun nativeSetEmbedPath(path: String)
    @JvmStatic external fun nativeSetMachineCode(code: String)
    @JvmStatic external fun nativeStartXTunnel(addr: String, dataDir: String): String
    @JvmStatic external fun nativeGetAddr(): String
    @JvmStatic external fun nativeIsRunning(): Boolean
    @JvmStatic external fun nativeGetStatusJson(): String
    @JvmStatic external fun nativeStopXTunnel(): String
}
