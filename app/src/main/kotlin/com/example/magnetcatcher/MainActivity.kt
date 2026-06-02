package com.example.magnetcatcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.magnetcatcher.ui.MainViewModel
import com.example.magnetcatcher.ui.MagnetCatcherApp
import com.example.magnetcatcher.ui.UiEvent

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MagnetCatcherApp(viewModel = viewModel, handleEvent = ::handleEvent)
        }
    }

    private fun handleEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Toast -> toast(event.message)
            is UiEvent.CopyText -> {
                clipboard().setPrimaryClip(ClipData.newPlainText(event.label, event.text))
                toast(event.toast)
            }
            is UiEvent.ShareText -> {
                val intent = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, event.subject)
                    .putExtra(Intent.EXTRA_TEXT, event.text)
                startActivity(Intent.createChooser(intent, "导出 115 文本"))
            }
            is UiEvent.OpenUrl -> openUrl(event.url)
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            clipboard().setPrimaryClip(ClipData.newPlainText("thread url", url))
            toast("无法直接打开，已复制原帖链接")
        }
    }

    private fun clipboard(): ClipboardManager {
        return getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, (40 * resources.displayMetrics.density).toInt())
        }.show()
    }
}
