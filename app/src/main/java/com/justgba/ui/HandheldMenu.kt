package com.justgba.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

class MenuKeys {
    var up: () -> Unit = {}
    var down: () -> Unit = {}
    var left: () -> Unit = {}
    var right: () -> Unit = {}
    var confirm: () -> Unit = {}
    var back: () -> Unit = {}
    var tab: (Boolean) -> Unit = {}
    var capture: Boolean = true
    var nestedBack: (() -> Boolean)? = null
}

data class MenuRow(
    val title: String,
    val value: String = "",
    val detail: String = "",
    val destructive: Boolean = false,
    val onClick: () -> Unit = {},
    val onStep: ((Int) -> Unit)? = null,
    val slider: Float? = null,
    val sliderRange: ClosedFloatingPointRange<Float> = 0f..1f,
    val onSlider: (Float) -> Unit = {},
)

@Composable
fun MenuList(
    rows: List<MenuRow>,
    keys: MenuKeys,
    resetKey: Any,
    modifier: Modifier = Modifier,
) {
    var focus by remember(resetKey) { mutableIntStateOf(0) }
    val last = rows.lastIndex.coerceAtLeast(0)
    val index = focus.coerceIn(0, last)
    val scroll = rememberScrollState()
    val requester = remember { FocusRequester() }
    val density = LocalDensity.current
    val cursor = remember(resetKey) { intArrayOf(0) }
    SideEffect {
        keys.capture = true
        keys.up = {
            if (rows.isNotEmpty()) {
                val next = (cursor[0] - 1).coerceAtLeast(0)
                cursor[0] = next
                focus = next
            }
        }
        keys.down = {
            if (rows.isNotEmpty()) {
                val next = (cursor[0] + 1).coerceAtMost(rows.lastIndex)
                cursor[0] = next
                focus = next
            }
        }
        keys.left = { rows.getOrNull(cursor[0])?.onStep?.invoke(-1) }
        keys.right = { rows.getOrNull(cursor[0])?.onStep?.invoke(1) }
        keys.confirm = { rows.getOrNull(cursor[0])?.onClick?.invoke() }
    }
    LaunchedEffect(resetKey) { runCatching { requester.requestFocus() } }
    LaunchedEffect(index, resetKey) {
        val y = with(density) { (index * 72).dp.toPx().toInt() }
        scroll.animateScrollTo(y.coerceIn(0, scroll.maxValue))
    }
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .focusRequester(requester)
            .focusable(),
    ) {
        rows.forEachIndexed { i, row ->
            val selected = i == index
            val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
            val titleColor = when {
                row.destructive -> MaterialTheme.colorScheme.error
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .background(background)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            cursor[0] = i
                            focus = i
                            row.onClick()
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.title,
                        modifier = Modifier.weight(1f),
                        color = titleColor,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.value.isNotEmpty()) {
                        Text(
                            row.value,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (row.detail.isNotEmpty()) {
                    Text(
                        row.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (row.slider != null) {
                    Slider(
                        value = row.slider.coerceIn(row.sliderRange),
                        onValueChange = row.onSlider,
                        valueRange = row.sliderRange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusProperties { canFocus = false },
                    )
                }
            }
        }
    }
}
