package com.example.magnetcatcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.example.magnetcatcher.AppConstants.BASE_URL
import com.example.magnetcatcher.AppConstants.THUMBNAIL_HEIGHT_DP
import com.example.magnetcatcher.AppConstants.THUMBNAIL_WIDTH_DP
import com.example.magnetcatcher.model.CrawlerItem
import com.example.magnetcatcher.parser.DateParser
import com.example.magnetcatcher.parser.UrlTools

@Composable
fun ResultOverviewCard(viewModel: MainViewModel) {
    val state by viewModel.resultState.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("结果概览", color = TextColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, SolidColor(BorderColor)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("▧", color = Color(0xFFC5C8C7), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("暂无内容", color = MutedColor, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("抓取完成后显示帖子、磁力和图片统计。", color = MutedColor, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp), textAlign = TextAlign.Center)
                }
            } else {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatCell("帖子", state.items.size.toString())
                        StatCell("磁力", state.totalMagnetCount.toString())
                        StatCell("带图", state.imageItemCount.toString())
                        StatCell("可导出", state.selectedMagnetCount.toString())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = viewModel::copySelected,
                            enabled = !state.busy && state.selectedMagnetCount > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("复制到 115")
                        }
                        Button(
                            onClick = viewModel::shareSelected,
                            enabled = !state.busy && state.selectedMagnetCount > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("分享/导出")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MutedColor, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun DeveloperOptionsCard(viewModel: MainViewModel) {
    val state by viewModel.developerState.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, SolidColor(BorderColor)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFFF1F2F2)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("≛", color = TextColor, fontSize = 28.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("开发者选项", color = TextColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("端口、代理健康、XTunnel 日志等默认隐藏", color = MutedColor, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Switch(checked = state.showDeveloperOptions, onCheckedChange = viewModel::onDeveloperOptionsChange)
            }
        }
        if (state.showDeveloperOptions) {
            DeveloperPanel(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun DeveloperPanel(state: DeveloperPanelUiState, viewModel: MainViewModel) {
    AppCard {
        Text("网络调试", color = TextColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
            Checkbox(checked = state.useXTunnel, onCheckedChange = viewModel::onUseXTunnelChange)
            Text("优先使用内置 XTunnel", color = TextColor, fontSize = 14.sp)
        }
        Label("XTunnel 本机代理端口")
        NumberField(value = state.proxyPort, onChange = viewModel::onProxyPortChange)
        Label("每个板块抓取页数")
        NumberField(value = state.pages, onChange = viewModel::onPagesChange)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            OutlinedButton(
                onClick = viewModel::runCheckAndCookie,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("检查访问")
            }
            OutlinedButton(
                onClick = viewModel::launchXTunnel,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("启动 XTunnel")
            }
        }
    }
}

fun endTimeFor(state: CapturePanelUiState): String {
    val date = when (state.timeRange) {
        TimeRangeOption.Last7Days -> DateParser.todayString()
        else -> state.sinceTime.takeIf { it.length >= 10 }?.substring(0, 10) ?: DateParser.todayString()
    }
    return "$date 23:59"
}

fun rangeLabel(range: TimeRangeOption): String {
    return when (range) {
        TimeRangeOption.Today -> "今天"
        TimeRangeOption.Yesterday -> "昨天"
        TimeRangeOption.Last7Days -> "近 7 天"
        TimeRangeOption.Custom -> "自定义"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultItemCard(item: CrawlerItem, viewModel: MainViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (item.selected) SurfaceColor else Color(0xFFE8E2D4)),
        border = CardDefaults.outlinedCardBorder(),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { viewModel.openOriginal(item) },
                onLongClick = { viewModel.showPreview(item) },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            SelectionButton(item, viewModel)
            Spacer(Modifier.width(8.dp))
            ThumbnailBox(item, viewModel)
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(item.title, color = TextColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val time = if (item.publishedAt.isNotEmpty()) "发帖 ${item.publishedAt}" else "发帖时间未知"
                Text("$time · ${item.magnets.size} 条磁力 · 图片 ${item.images.size}", color = MutedColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                TextButton(onClick = { viewModel.openOriginal(item) }) {
                    Text("打开原帖", color = WarningColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SelectionButton(item: CrawlerItem, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .width(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (item.selected) SoftGreen else Color(0xFFF7F3EA))
            .clickable { viewModel.toggleItemSelected(item.threadId) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Checkbox(
            checked = item.selected,
            onCheckedChange = { viewModel.toggleItemSelected(item.threadId) },
            modifier = Modifier.size(32.dp),
        )
        Text(
            if (item.selected) "已选" else "选择",
            color = if (item.selected) PrimaryDark else MutedColor,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThumbnailBox(item: CrawlerItem, viewModel: MainViewModel) {
    val useProxy by viewModel.useXTunnelState.collectAsStateWithLifecycle()
    val candidates = remember(item.threadId, item.thumbnailUrl, item.images) { thumbnailCandidates(item) }
    var candidateIndex by remember(item.threadId, item.thumbnailUrl, item.images) { mutableStateOf(0) }
    var failed by remember(item.threadId, item.thumbnailUrl, item.images) { mutableStateOf(false) }
    val imageUrl = candidates.getOrNull(candidateIndex)
    Box(
        modifier = Modifier
            .size(THUMBNAIL_WIDTH_DP.dp, THUMBNAIL_HEIGHT_DP.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE6E0D3))
            .combinedClickable(
                onClick = { viewModel.showPreview(item) },
                onLongClick = { viewModel.showPreview(item) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            item.images.isEmpty() -> Text("无图", color = MutedColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            failed || imageUrl == null -> Text("加载失败", color = MutedColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else -> AsyncImage(
                model = rememberThreadImageRequest(imageUrl, item.url),
                imageLoader = viewModel.imageLoader(useProxy),
                contentDescription = "图片 ${item.images.size}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    if (candidateIndex < candidates.lastIndex) {
                        candidateIndex += 1
                    } else {
                        failed = true
                    }
                },
            )
        }
        if (item.images.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xAA17231F))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("${item.images.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun rememberThreadImageRequest(url: String, referer: String): ImageRequest {
    val context = LocalContext.current
    return remember(url, referer) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(threadImageHeaders(referer))
            .build()
    }
}

private fun threadImageHeaders(referer: String): NetworkHeaders {
    return NetworkHeaders.Builder().apply {
        this["Accept"] = IMAGE_ACCEPT
        this["Referer"] = referer.takeIf { it.isNotEmpty() } ?: BASE_URL
    }.build()
}

private fun thumbnailCandidates(item: CrawlerItem): List<String> {
    if (item.images.isEmpty()) return emptyList()
    val preferred = item.thumbnailUrl.takeIf { it.isNotEmpty() && item.images.contains(it) } ?: item.images.first()
    val ordered = buildList {
        add(preferred)
        item.images.filterNot { it == preferred }.forEach { add(it) }
    }
    return ordered.flatMap { UrlTools.imageUrlCandidates(it) }.distinct()
}
