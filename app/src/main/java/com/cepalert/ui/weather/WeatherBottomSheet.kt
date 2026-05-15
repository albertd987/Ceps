package com.cepalert.ui.weather

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cepalert.data.model.WeatherData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherBottomSheet(
    weather: WeatherData,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                "Condiciones meteorológicas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            WeatherRow("Lluvia 7 días", "${weather.rain7dTotal.toInt()} mm")
            WeatherRow("Lluvia 14 días", "${weather.rain14dTotal.toInt()} mm")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            WeatherRow(
                "Temperatura 7d",
                "máx ${weather.temp7dMax.toInt()} / mín ${weather.temp7dMin.toInt()} / media ${weather.temp7dAvg.toInt()} °C"
            )
            WeatherRow("Humedad media 7d", "${weather.humidity7dAvg.toInt()} %")

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            WeatherRow(
                "Días sin lluvia significativa (>10 mm)",
                "${weather.daysSinceSignificantRain}"
            )

            Spacer(Modifier.height(8.dp))

            val formattedDate = remember(weather.updatedAtEpochMs) {
                SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                    .format(Date(weather.updatedAtEpochMs))
            }
            Text(
                "Fuente: ${weather.source} · Actualizado: $formattedDate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WeatherRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
