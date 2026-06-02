package com.example.magnetcatcher.xtunnel

import android.content.Context
import android.provider.Settings
import com.example.magnetcatcher.AppConstants.DEFAULT_PROXY_PORT
import com.example.magnetcatcher.AppConstants.XTUNNEL_FAILURE_COOLDOWN_MS
import com.example.magnetcatcher.AppConstants.XTUNNEL_PROXY_HEALTH_TIMEOUT_MS
import com.example.magnetcatcher.AppConstants.XTUNNEL_READY_ATTEMPTS
import com.example.magnetcatcher.AppConstants.XTUNNEL_RESTART_READY_ATTEMPTS
import com.example.magnetcatcher.data.SettingsStore
import com.example.magnetcatcher.model.StatusTone
import com.example.magnetcatcher.model.StatusUpdate
import com.example.magnetcatcher.network.HttpProxyController
import com.example.magnetcatcher.parser.HtmlText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket

class XTunnelManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
) : HttpProxyController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val xtunnelLock = Any()
    private val taskLock = Any()
    private var startTask: Deferred<String>? = null

    @Volatile var ready: Boolean = false
        private set
    @Volatile var proxyReady: Boolean = false
        private set
    @Volatile var starting: Boolean = false
        private set
    @Volatile var proxyFallbackNotified: Boolean = false
        private set
    @Volatile var lastFailureMs: Long = 0L
        private set
    @Volatile var startMs: Long = 0L
        private set
    @Volatile var statusDetail: String = "未启动"
        private set

    var useXTunnel: Boolean
        get() = settingsStore.useXTunnel
        set(value) {
            settingsStore.useXTunnel = value
        }

    fun currentPort(): Int = settingsStore.proxyPort.takeIf { it > 0 } ?: DEFAULT_PROXY_PORT

    override fun requestProxy(useProxy: Boolean): Proxy {
        return if (useProxy && isReadyFast()) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", currentPort()))
        } else {
            Proxy.NO_PROXY
        }
    }

    override fun networkModeLabel(useProxy: Boolean): String {
        if (!useProxy) return "系统 VPN/直连"
        if (isReadyFast()) return "内置 XTunnel"
        return if (starting) "内置 XTunnel（启动中）" else "系统 VPN/直连（XTunnel 未就绪）"
    }

    fun isFailureCooldownActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        return lastFailureMs > 0 && nowMs - lastFailureMs < XTUNNEL_FAILURE_COOLDOWN_MS
    }

    override fun markProxyUnhealthy(message: String, status: (StatusUpdate) -> Unit) {
        proxyReady = false
        lastFailureMs = System.currentTimeMillis()
        notifyFallbackOnce(message, status)
    }

    override fun notifyFallbackOnce(message: String, status: (StatusUpdate) -> Unit) {
        if (proxyFallbackNotified) return
        proxyFallbackNotified = true
        status(
            StatusUpdate(
                title = "临时切回直连",
                detail = message,
                meta = "可稍后重新启动 XTunnel；当前任务会继续尝试完成。",
                tone = StatusTone.Warning,
            )
        )
    }

    suspend fun prepareOrFallback(taskName: String, status: (StatusUpdate) -> Unit): Boolean {
        if (isFailureCooldownActive()) {
            notifyFallbackOnce("XTunnel 刚刚失败，${taskName} 暂时走系统 VPN/直连，避免重复等待。", status)
            return false
        }
        return try {
            ensureReady(status)
            true
        } catch (error: Exception) {
            ready = false
            proxyReady = false
            lastFailureMs = System.currentTimeMillis()
            notifyFallbackOnce("XTunnel 启动失败，${taskName} 临时走系统 VPN/直连：${conciseError(error)}", status)
            false
        }
    }

    suspend fun ensureReady(status: (StatusUpdate) -> Unit): String {
        if (isReadyFast()) return statusDetail
        val task = synchronized(taskLock) {
            if (isReadyFast()) return@synchronized null
            val existing = startTask
            if (existing != null && !existing.isCompleted) {
                existing
            } else {
                starting = true
                ready = false
                proxyReady = false
                proxyFallbackNotified = false
                lastFailureMs = 0L
                startMs = System.currentTimeMillis()
                status(
                    StatusUpdate(
                        title = "正在启动 XTunnel",
                        detail = "初始化本地代理...",
                        meta = "已等待 0s，期间会阻止重复启动。",
                        tone = StatusTone.Warning,
                        spinning = true,
                    )
                )
                scope.async { startAndWait(status) }.also { startTask = it }
            }
        }
        if (task == null) return statusDetail
        return try {
            val detail = task.await()
            statusDetail = detail
            ready = true
            proxyReady = true
            proxyFallbackNotified = false
            starting = false
            status(
                StatusUpdate(
                    title = "XTunnel 已就绪",
                    detail = "本机代理端口 ${currentPort()} 已连通：$detail",
                    meta = if (useXTunnel) "抓取、检查和图片预览会优先走内置 XTunnel。" else "内置代理已待命；当前仍走系统 VPN/直连。",
                    tone = StatusTone.Success,
                )
            )
            detail
        } catch (error: Exception) {
            markFailed(task)
            throw error
        }
    }

    override fun isReadyFast(): Boolean {
        if (!ready || !proxyReady || !XTunnelNative.available) return false
        return try {
            if (!XTunnelNative.nativeIsRunning()) {
                ready = false
                proxyReady = false
                statusDetail = "未启动"
                false
            } else {
                true
            }
        } catch (_: Throwable) {
            ready = false
            proxyReady = false
            statusDetail = "未启动"
            false
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun markFailed(task: Deferred<String>) {
        ready = false
        proxyReady = false
        proxyFallbackNotified = false
        lastFailureMs = System.currentTimeMillis()
        starting = false
        synchronized(taskLock) {
            if (startTask == task) startTask = null
        }
    }

    private fun startAndWait(status: (StatusUpdate) -> Unit): String {
        synchronized(xtunnelLock) {
            if (!XTunnelNative.available) throw IllegalStateException("内置 XTunnel native 库加载失败")
            val dataDir = File(context.filesDir, "xtunnel")
            if (!dataDir.exists() && !dataDir.mkdirs()) throw IllegalStateException("无法创建 XTunnel 数据目录")
            if (!XTunnelNative.nativeIsRunning()) startEmbeddedXTunnel(dataDir)

            var statusJson = waitForReady(XTUNNEL_READY_ATTEMPTS, "启动", status)
            if (isReadyStatus(statusJson) && proxyReady) return conciseStatus(statusJson)

            status(
                StatusUpdate(
                    title = "正在重启 XTunnel",
                    detail = "首次启动未就绪，自动重启 1 次。",
                    meta = "如果仍失败，会回退到系统 VPN/直连。",
                    tone = StatusTone.Warning,
                    spinning = true,
                )
            )
            XTunnelNative.nativeStopXTunnel()
            Thread.sleep(1200)
            startEmbeddedXTunnel(dataDir)
            statusJson = waitForReady(XTUNNEL_RESTART_READY_ATTEMPTS, "重启", status)
            if (isReadyStatus(statusJson) && proxyReady) return conciseStatus(statusJson)
            XTunnelNative.nativeStopXTunnel()
            throw IllegalStateException("内置 XTunnel 启动超时或端口不可达：${conciseStatus(statusJson)}")
        }
    }

    private fun startEmbeddedXTunnel(dataDir: File) {
        XTunnelNative.nativeSetEmbedPath(copyAssetToFiles("embed.data"))
        XTunnelNative.nativeSetMachineCode(machineCode())
        val addr = XTunnelNative.nativeStartXTunnel("127.0.0.1:${currentPort()}", dataDir.absolutePath)
        val port = portFromAddr(addr)
        if (port > 0) settingsStore.proxyPort = port
    }

    private fun waitForReady(attempts: Int, phase: String, status: (StatusUpdate) -> Unit): String {
        var statusJson = ""
        val startedAt = if (startMs > 0) startMs else System.currentTimeMillis()
        for (i in 0 until attempts) {
            statusJson = XTunnelNative.nativeGetStatusJson()
            val readyJson = isReadyStatus(statusJson)
            if (readyJson) {
                if (isLocalProxyReachable(XTUNNEL_PROXY_HEALTH_TIMEOUT_MS)) {
                    proxyReady = true
                    return statusJson
                }
                proxyReady = false
                val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0)
                status(
                    StatusUpdate(
                        title = "XTunnel ${phase}中",
                        detail = "状态已 ready，等待本机代理端口 ${currentPort()}...",
                        meta = "第 ${i + 1}/$attempts 次探测 · 已等待 ${elapsedSeconds}s",
                        tone = StatusTone.Warning,
                        spinning = true,
                    )
                )
            }
            if (!readyJson && i % 2 == 0) {
                val detail = conciseStatus(statusJson).ifEmpty { "等待状态返回..." }
                val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0)
                status(
                    StatusUpdate(
                        title = "XTunnel ${phase}中",
                        detail = detail,
                        meta = "第 ${i + 1}/$attempts 次探测 · 已等待 ${elapsedSeconds}s",
                        tone = StatusTone.Warning,
                        spinning = true,
                    )
                )
            }
            Thread.sleep(500)
        }
        return statusJson
    }

    private fun isLocalProxyReachable(timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", currentPort()), timeoutMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isReadyStatus(statusJson: String?): Boolean {
        if (statusJson.isNullOrEmpty()) return false
        return runCatching { JSONObject(statusJson).optBoolean("ready", false) }
            .getOrElse { statusJson.contains("\"ready\":true") }
    }

    fun conciseStatus(statusJson: String?): String {
        if (statusJson.isNullOrEmpty()) return "无状态"
        return runCatching {
            val objectValue = JSONObject(statusJson)
            val stage = objectValue.optString("stage", "")
            val detail = objectValue.optString("stage_detail", "")
            val version = objectValue.optInt("config_version", 0)
            if (version > 0) detail.ifEmpty { "$stage v$version" } else detail.ifEmpty { stage }
        }.getOrElse {
            if (statusJson.length > 120) statusJson.substring(0, 120) + "..." else statusJson
        }
    }

    override fun conciseError(error: Throwable?): String {
        val message = error?.message?.takeIf { it.isNotEmpty() } ?: error?.javaClass?.simpleName ?: "未知错误"
        return if (message.length > 96) message.substring(0, 96) + "..." else message
    }

    private fun copyAssetToFiles(assetName: String): String {
        val outFile = File(context.filesDir, assetName)
        if (outFile.exists() && outFile.length() > 0) return outFile.absolutePath
        context.assets.open(assetName).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output, 8192) }
        }
        return outFile.absolutePath
    }

    private fun machineCode(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotEmpty() } ?: "unknown"
        return "magnet-catcher-$androidId"
    }

    private fun portFromAddr(addr: String?): Int {
        if (addr.isNullOrEmpty()) return 0
        val index = addr.lastIndexOf(':')
        if (index < 0 || index + 1 >= addr.length) return 0
        return HtmlText.parseInt(addr.substring(index + 1))
    }
}
