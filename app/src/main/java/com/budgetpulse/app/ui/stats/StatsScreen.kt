package com.budgetpulse.app.ui.stats

import com.budgetpulse.app.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpulse.app.domain.logic.Bucket
import com.budgetpulse.app.domain.logic.CategoryShare
import com.budgetpulse.app.domain.logic.Period
import com.budgetpulse.app.ui.common.*
import com.budgetpulse.app.util.compactAmount
import com.budgetpulse.app.util.formatMoney

@Composable
fun StatsScreen(widthClass: WindowWidthSizeClass) {
    val container = LocalAppContainer.current
    val vm = appViewModel { StatsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    StatsContent(state, wide = widthClass != WindowWidthSizeClass.Compact, onPeriod = vm::setPeriod, onShift = vm::shift)
}

@Composable
fun StatsContent(state: StatsUiState, wide: Boolean, onPeriod: (Period) -> Unit, onShift: (Int) -> Unit) {
    ScreenScaffold(title = "Statistics") {
        val report = state.report
        if (state.loading || report == null) {
            LoadingBox()
            return@ScreenScaffold
        }
        val overview: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PeriodSelector(state, onPeriod, onShift)
                SummaryGrid(report.summary.income, report.summary.expense, report.summary.net, report.summary.savingsRate, state.currency)
                ChartCard(report.buckets, state.period, state.currency)
            }
        }
        val breakdown: @Composable () -> Unit = { BreakdownCard(report.breakdown, state.currency) }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.weight(1.2f), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)) { item { overview() } }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current)) { item { breakdown() } }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { overview() }
                item { breakdown() }
            }
        }
    }
}

@Composable
private fun PeriodSelector(state: StatsUiState, onPeriod: (Period) -> Unit, onShift: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Period.entries.forEach { p ->
                FilterChip(
                    selected = state.period == p, onClick = { onPeriod(p) }, label = { Text(p.label) },
                    modifier = Modifier.testTag("period_" + p.name)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }, modifier = Modifier.testTag("period_prev")) {
                AppIcon(R.drawable.ic_keyboard_arrow_left, contentDescription = "Previous " + state.period.label.lowercase())
            }
            Text(
                state.report?.range?.title ?: "", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { onShift(1) }, enabled = state.canGoForward) {
                AppIcon(R.drawable.ic_keyboard_arrow_right, contentDescription = "Next " + state.period.label.lowercase())
            }
        }
    }
}

@Composable
private fun SummaryGrid(income: Double, expense: Double, net: Double, rate: Double, currency: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Income", formatMoney(income, currency), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            StatCard("Expenses", formatMoney(expense, currency), MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Net", (if (net >= 0) "+" else "-") + formatMoney(kotlin.math.abs(net), currency), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            StatCard("Savings rate", Math.round(rate * 100).toString() + "%", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    AppCard(modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun ChartCard(buckets: List<Bucket>, period: Period, currency: String) {
    var bars by rememberSaveable { mutableStateOf(false) }
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cash flow", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FilterChip(selected = !bars, onClick = { bars = false }, label = { Text("Area") }, modifier = Modifier.testTag("chart_area"))
                FilterChip(selected = bars, onClick = { bars = true }, label = { Text("Bars") }, modifier = Modifier.testTag("chart_bars"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(MaterialTheme.colorScheme.tertiary, "Income")
                LegendDot(MaterialTheme.colorScheme.primary, "Expenses")
            }
            if (buckets.all { it.income == 0.0 && it.expense == 0.0 }) {
                EmptyHint("No transactions in this period.")
            } else {
                IncomeExpenseChart(buckets, period, currency, bars = bars)
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * Cash-flow chart that animates in whenever the data, period or style changes:
 * smooth gradient areas with a line (default, like the reference) or grouped bars.
 */
@Composable
fun IncomeExpenseChart(
    buckets: List<Bucket>,
    period: Period,
    currency: String,
    modifier: Modifier = Modifier,
    bars: Boolean = false
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(buckets, bars) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val incomeColor = MaterialTheme.colorScheme.tertiary
    val expenseColor = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axis = MaterialTheme.colorScheme.onSurfaceVariant
    val maxValue = maxOf(buckets.maxOf { maxOf(it.income, it.expense) }, 1.0)
    val description = (if (bars) "Bar chart" else "Area chart") + " of income and expenses per " + period.label.lowercase() +
        ". Total income " + formatMoney(buckets.sumOf { it.income }, currency) +
        ", total expenses " + formatMoney(buckets.sumOf { it.expense }, currency) + "."

    Canvas(
        modifier.fillMaxWidth().height(220.dp).testTag("stats_chart").semantics { contentDescription = description }
    ) {
        val left = 44.dp.toPx()
        val bottom = 22.dp.toPx()
        val top = 8.dp.toPx()
        val chartW = size.width - left
        val chartH = size.height - bottom - top
        val labelStyle = TextStyle(fontSize = 10.sp, color = axis)
        val slot = chartW / buckets.size

        for (i in 0..3) {
            val y = top + chartH * (1f - i / 3f)
            drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(compactAmount(maxValue * i / 3.0), labelStyle)
            drawText(layout, topLeft = Offset(left - layout.size.width - 6.dp.toPx(), y - layout.size.height / 2f))
        }

        fun px(i: Int): Float = left + slot * i + slot / 2f
        fun py(v: Double): Float = top + chartH * (1f - (v / maxValue).toFloat())

        if (bars) {
            val barW = slot * 0.34f
            buckets.forEachIndexed { i, b ->
                val cx = px(i)
                val ih = (b.income / maxValue).toFloat() * chartH * progress.value
                val eh = (b.expense / maxValue).toFloat() * chartH * progress.value
                if (ih > 0f) drawRoundRect(incomeColor, Offset(cx - barW - 1f, top + chartH - ih), Size(barW, ih), CornerRadius(3.dp.toPx()))
                if (eh > 0f) drawRoundRect(expenseColor, Offset(cx + 1f, top + chartH - eh), Size(barW, eh), CornerRadius(3.dp.toPx()))
            }
        } else {
            fun series(values: List<Double>, color: Color) {
                val line = Path()
                values.forEachIndexed { i, v ->
                    if (i == 0) {
                        line.moveTo(px(0), py(v))
                    } else {
                        val mid = (px(i - 1) + px(i)) / 2f
                        line.cubicTo(mid, py(values[i - 1]), mid, py(v), px(i), py(v))
                    }
                }
                val area = Path()
                area.addPath(line)
                area.lineTo(px(values.size - 1), top + chartH)
                area.lineTo(px(0), top + chartH)
                area.close()
                // The reveal grows from left to right while the animation runs.
                clipRect(left = left, top = 0f, right = left + chartW * progress.value, bottom = size.height) {
                    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)), startY = top, endY = top + chartH))
                    drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    if (values.size <= 12) {
                        values.forEachIndexed { i, v -> drawCircle(color, radius = 3.5.dp.toPx(), center = Offset(px(i), py(v))) }
                    }
                }
            }
            series(buckets.map { it.expense }, expenseColor)
            series(buckets.map { it.income }, incomeColor)
        }

        val step = Math.ceil(buckets.size / 8.0).toInt().coerceAtLeast(1)
        buckets.forEachIndexed { i, b ->
            if (i % step == 0) {
                val layout = measurer.measure(b.label, labelStyle)
                drawText(layout, topLeft = Offset(px(i) - layout.size.width / 2f, size.height - layout.size.height))
            }
        }
    }
}

@Composable
private fun BreakdownCard(shares: List<CategoryShare>, currency: String) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Spending by category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (shares.isEmpty()) EmptyHint("No expenses in this period.")
            shares.forEach { s ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(s.category, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(s.category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(formatMoney(s.amount, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                    AnimatedProgressBar(
                        (s.percent / 100.0).toFloat(), s.category.color(),
                        s.category.label + " " + Math.round(s.percent) + " percent of spending", height = 8.dp
                    )
                }
            }
        }
    }
}
