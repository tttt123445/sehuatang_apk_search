package com.example.magnetcatcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.magnetcatcher.R
import com.example.magnetcatcher.model.StatusTone

@Composable
fun MagnetCatcherScreen(viewModel: MainViewModel) {
    val results by viewModel.resultState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { TopHeader() }
        item { NetworkCard(viewModel = viewModel) }
        item { CaptureConditions(viewModel = viewModel) }
        item { ResultOverviewCard(viewModel = viewModel) }
        item { DeveloperOptionsCard(viewModel = viewModel) }
        if (results.items.isEmpty()) {
            item { Spacer(modifier = Modifier.height(6.dp)) }
        } else {
            item { Text("帖子列表", color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            items(results.items, key = { it.threadId }) { item ->
                ResultItemCard(item = item, viewModel = viewModel)
            }
        }
        item { Spacer(modifier = Modifier.height(18.dp)) }
    }
}

@Composable
private fun TopHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo()
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("磁力抓取", color = TextColor, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("抓新帖，预览图片，导出到 115", color = MutedColor, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AppLogo() {
    Box(
        modifier = Modifier
            .size(58.dp)
            .shadow(10.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x12000000), spotColor = Color(0x12000000))
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_magnet_logo),
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun NetworkCard(viewModel: MainViewModel) {
    val state by viewModel.networkState.collectAsStateWithLifecycle()
    val networkTitle = if (state.useXTunnel) "内置 XTunnel" else "系统 VPN/直连"
    val networkDetail = when {
        state.status.spinning -> state.status.detail
        state.status.tone == StatusTone.Error -> state.status.detail
        state.useXTunnel -> "代理就绪后自动使用"
        else -> "当前网络可用，点按可切换"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x10000000), spotColor = Color(0x10000000)),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SolidColor(BorderColor)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !state.busy) { viewModel.toggleNetworkMode() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(networkTitle, color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(networkDetail, color = PrimaryDark, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.status.spinning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = PrimaryColor)
            } else {
                Text(if (state.useXTunnel) "XT" else "VPN", color = PrimaryDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Text("›", color = Color(0xFF5F6763), fontSize = 30.sp, modifier = Modifier.padding(start = 10.dp, bottom = 2.dp))
        }
    }
}
