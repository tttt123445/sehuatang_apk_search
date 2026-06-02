package com.example.magnetcatcher.model

enum class StatusTone {
    Neutral,
    Success,
    Warning,
    Error,
}

data class StatusUpdate(
    val title: String,
    val detail: String,
    val meta: String = "",
    val tone: StatusTone = StatusTone.Neutral,
    val spinning: Boolean = false,
)
