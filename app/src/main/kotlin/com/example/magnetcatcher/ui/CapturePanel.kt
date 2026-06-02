package com.example.magnetcatcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.magnetcatcher.model.ForumOption
import com.example.magnetcatcher.parser.DateParser

@Composable
fun CaptureConditions(viewModel: MainViewModel) {
    val state by viewModel.captureState.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("抓取条件", color = TextColor, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("时间范围", color = MutedColor, fontSize = 14.sp)
            TimeRangeSegments(state = state, viewModel = viewModel)
            DateRangeCard(state = state, viewModel = viewModel)
        }
        ForumBlock(state = state, viewModel = viewModel)
        ActionSummary(state = state)
        Button(
            onClick = viewModel::runCrawl,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .shadow(12.dp, RoundedCornerShape(8.dp), ambientColor = Color(0x220D7F45), spotColor = Color(0x220D7F45)),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryColor,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFFB8D8C5),
                disabledContentColor = Color.White,
            ),
        ) {
            Text("⌕  开始抓取", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TimeRangeSegments(state: CapturePanelUiState, viewModel: MainViewModel) {
    val options = listOf(
        TimeRangeOption.Today to "今天",
        TimeRangeOption.Yesterday to "昨天",
        TimeRangeOption.Last7Days to "近 7 天",
        TimeRangeOption.Custom to "自定义",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
    ) {
        options.forEachIndexed { index, (option, label) ->
            val selected = state.timeRange == option
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) SoftGreen else Color.Transparent)
                    .clickable { viewModel.selectTimeRange(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected) PrimaryDark else TextColor, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
            if (index < options.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .background(BorderColor)
                )
            }
        }
    }
}

@Composable
private fun DateRangeCard(state: CapturePanelUiState, viewModel: MainViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SolidColor(BorderColor)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clickable { viewModel.openDatePicker() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("开始", color = PrimaryDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    state.sinceTime.ifBlank { DateParser.defaultSinceTime() },
                    color = TextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .width(1.dp)
                    .background(BorderColor)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text("结束", color = PrimaryDark, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    endTimeFor(state),
                    color = TextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", color = Color(0xFF5F6763), fontSize = 28.sp)
        }
    }
}

@Composable
private fun ForumBlock(state: CapturePanelUiState, viewModel: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { viewModel.toggleForumsExpanded() }
                .padding(vertical = 2.dp),
        ) {
            Text("板块", color = MutedColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(
                "已选 ${state.selectedFids.size} 个  ${if (state.forumsExpanded) "⌃" else "⌄"}",
                color = MutedColor,
                fontSize = 14.sp,
            )
        }
        if (state.forumsExpanded) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FCFA)),
                border = BorderStroke(1.dp, SolidColor(Color(0xFFD8EFE2))),
                shape = RoundedCornerShape(8.dp),
            )
            {
                ForumSelector(
                    options = state.forumOptions,
                    selectedFids = state.selectedFids,
                    onToggle = viewModel::toggleForum,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartDatePickerDialog(datePicker: DatePickerUiState, viewModel: MainViewModel) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = datePicker.initialSelectedDateMillis,
    )
    DatePickerDialog(
        onDismissRequest = viewModel::dismissDatePicker,
        confirmButton = {
            TextButton(onClick = { viewModel.confirmDatePicker(datePickerState.selectedDateMillis) }) {
                Text("确定", color = PrimaryDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::dismissDatePicker) {
                Text("取消", color = MutedColor)
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            showModeToggle = false,
            title = {
                Text(
                    "选择开始日期",
                    color = TextColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp),
                )
            },
        )
    }
}

@Composable
private fun ForumSelector(options: List<ForumOption>, selectedFids: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (options.isEmpty()) {
        Text("未找到内置 BT 区模块，请重新启动应用。", color = MutedColor, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        options.chunked(3).forEach { rowOptions ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowOptions.forEach { option ->
                    val selected = selectedFids.contains(option.fid)
                    OutlinedButton(
                        onClick = { onToggle(option.fid) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = TextColor,
                        ),
                        border = BorderStroke(1.dp, SolidColor(if (selected) Color(0xFFB7E0C7) else BorderColor)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = option.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(PrimaryColor),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                if (rowOptions.size <= 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionSummary(state: CapturePanelUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SoftGreen)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("已选 ${state.selectedFids.size} 个板块 · ${state.pages.ifBlank { "1" }} 页 · ${rangeLabel(state.timeRange)}新帖", color = TextColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
