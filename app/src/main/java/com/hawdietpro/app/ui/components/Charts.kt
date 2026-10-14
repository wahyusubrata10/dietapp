package com.hawdietpro.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries

/** Grafik batang kalori per hari + garis target (Vico). */
@Composable
fun CalorieTrendChart(
    values: List<Double>,
    labels: List<String>,
    target: Int?,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
) {
    if (values.isEmpty() || values.all { it <= 0.0 }) {
        ChartPlaceholder("Belum ada data kalori di periode ini", modifier, height)
        return
    }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values, target) {
        producer.runTransaction {
            columnSeries { series(values) }
            if (target != null) lineSeries { series(List(values.size) { target }) }
        }
    }
    val formatter = remember(labels) { CartesianValueFormatter { _, x, _ -> labels.getOrNull(x.toInt()) ?: " " } }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        val chart = if (target != null) {
            rememberCartesianChart(
                rememberColumnCartesianLayer(),
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = formatter),
            )
        } else {
            rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = formatter),
            )
        }
        CartesianChartHost(chart = chart, modelProducer = producer, modifier = modifier.fillMaxWidth().height(height))
    }
}

/** Grafik garis (mis. tren berat badan). */
@Composable
fun LineTrendChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    emptyText: String = "Belum ada data",
) {
    if (values.size < 2) {
        ChartPlaceholder(if (values.isEmpty()) emptyText else "Butuh minimal 2 catatan untuk grafik tren", modifier, height)
        return
    }
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        producer.runTransaction { lineSeries { series(values) } }
    }
    val formatter = remember(labels) { CartesianValueFormatter { _, x, _ -> labels.getOrNull(x.toInt()) ?: " " } }
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = formatter),
            ),
            modelProducer = producer,
            modifier = modifier.fillMaxWidth().height(height),
        )
    }
}

@Composable
private fun ChartPlaceholder(text: String, modifier: Modifier, height: Dp) {
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
