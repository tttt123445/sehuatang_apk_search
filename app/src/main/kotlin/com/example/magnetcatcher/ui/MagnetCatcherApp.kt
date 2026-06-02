package com.example.magnetcatcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle

val Background = Color(0xFFFFFFFF)
val SurfaceColor = Color.White
val SurfaceAlt = Color(0xFFF7FAF8)
val TextColor = Color(0xFF17231F)
val MutedColor = Color(0xFF6E7671)
val BorderColor = Color(0xFFE5EAE6)
val PrimaryColor = Color(0xFF0FA35A)
val PrimaryDark = Color(0xFF0B7F45)
val WarningColor = Color(0xFFD7682A)
val WarningBg = Color(0xFFFFF3E5)
val ErrorColor = Color(0xFFC2463A)
val ErrorBg = Color(0xFFFFECE9)
val SuccessBg = Color(0xFFEAF6F0)
val SoftGreen = Color(0xFFF0FAF4)
val DisabledSwitch = Color(0xFFE6EAEE)
const val IMAGE_ACCEPT = "image/avif,image/webp,image/apng,image/*,*/*;q=0.8"


@Composable
fun MagnetCatcherApp(viewModel: MainViewModel, handleEvent: (UiEvent) -> Unit) {
    val dialogs by viewModel.dialogState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { handleEvent(it) }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = PrimaryColor,
            onPrimary = Color.White,
            secondary = WarningColor,
            background = Background,
            surface = SurfaceColor,
            onSurface = TextColor,
        )
    ) {
        MagnetCatcherScreen(viewModel = viewModel)
        dialogs.preview?.let { preview ->
            PreviewDialog(preview = preview, viewModel = viewModel)
        }
        dialogs.webPage?.let { webPage ->
            ThreadWebViewDialog(webPage = webPage, viewModel = viewModel)
        }
        dialogs.datePicker?.let { datePicker ->
            StartDatePickerDialog(datePicker = datePicker, viewModel = viewModel)
        }
    }
}
