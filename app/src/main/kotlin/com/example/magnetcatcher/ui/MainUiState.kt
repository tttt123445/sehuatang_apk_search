package com.example.magnetcatcher.ui

import com.example.magnetcatcher.model.CrawlerItem
import com.example.magnetcatcher.model.ForumOption
import com.example.magnetcatcher.model.StatusTone
import com.example.magnetcatcher.model.StatusUpdate

enum class TimeRangeOption {
    Today,
    Yesterday,
    Last7Days,
    Custom,
}

data class StatusCardUiState(
    val title: String = "网络状态",
    val detail: String = "当前走系统 VPN/直连。",
    val meta: String = "需要时可启用内置 XTunnel。",
    val tone: StatusTone = StatusTone.Neutral,
    val spinning: Boolean = false,
) {
    companion object {
        fun from(update: StatusUpdate): StatusCardUiState {
            return StatusCardUiState(update.title, update.detail, update.meta, update.tone, update.spinning)
        }
    }
}

data class PreviewUiState(
    val item: CrawlerItem,
    val index: Int,
    val useProxy: Boolean = false,
    val preparingProxy: Boolean = false,
    val error: String = "",
) {
    val position: String = "${index + 1}/${item.images.size}"
    val title: String = "${item.title} · $position"
}

data class WebPageUiState(
    val url: String,
    val title: String = "原帖",
    val useProxy: Boolean = false,
    val proxyPort: Int = 0,
    val cookieHeader: String = "",
)

data class DatePickerUiState(
    val initialSelectedDateMillis: Long? = null,
)

data class MainUiState(
    val forumOptions: List<ForumOption> = emptyList(),
    val selectedFids: Set<Int> = emptySet(),
    val useXTunnel: Boolean = false,
    val proxyPort: String = "",
    val pages: String = "1",
    val sinceTime: String = "",
    val timeRange: TimeRangeOption = TimeRangeOption.Today,
    val forumsExpanded: Boolean = true,
    val showDeveloperOptions: Boolean = false,
    val items: List<CrawlerItem> = emptyList(),
    val status: StatusCardUiState = StatusCardUiState(),
    val busy: Boolean = false,
    val datePicker: DatePickerUiState? = null,
    val preview: PreviewUiState? = null,
    val webPage: WebPageUiState? = null,
) {
    val selectedMagnetCount: Int
        get() = items.sumOf { item -> if (item.selected) item.enabledMagnets.size else 0 }

    val totalMagnetCount: Int
        get() = items.sumOf { it.enabledMagnets.size }

    val imageItemCount: Int
        get() = items.count { it.images.isNotEmpty() }
}

data class NetworkPanelUiState(
    val useXTunnel: Boolean = false,
    val status: StatusCardUiState = StatusCardUiState(),
    val busy: Boolean = false,
)

data class CapturePanelUiState(
    val forumOptions: List<ForumOption> = emptyList(),
    val selectedFids: Set<Int> = emptySet(),
    val pages: String = "1",
    val sinceTime: String = "",
    val timeRange: TimeRangeOption = TimeRangeOption.Today,
    val forumsExpanded: Boolean = true,
    val busy: Boolean = false,
)

data class ResultPanelUiState(
    val items: List<CrawlerItem> = emptyList(),
    val busy: Boolean = false,
) {
    val selectedMagnetCount: Int
        get() = items.sumOf { item -> if (item.selected) item.enabledMagnets.size else 0 }

    val totalMagnetCount: Int
        get() = items.sumOf { it.enabledMagnets.size }

    val imageItemCount: Int
        get() = items.count { it.images.isNotEmpty() }
}

data class DeveloperPanelUiState(
    val useXTunnel: Boolean = false,
    val proxyPort: String = "",
    val pages: String = "1",
    val showDeveloperOptions: Boolean = false,
    val busy: Boolean = false,
)

data class DialogUiState(
    val datePicker: DatePickerUiState? = null,
    val preview: PreviewUiState? = null,
    val webPage: WebPageUiState? = null,
)

sealed interface UiEvent {
    data class Toast(val message: String) : UiEvent
    data class OpenUrl(val url: String) : UiEvent
    data class CopyText(val label: String, val text: String, val toast: String) : UiEvent
    data class ShareText(val subject: String, val text: String) : UiEvent
}
