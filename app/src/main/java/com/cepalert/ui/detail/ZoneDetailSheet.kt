package com.cepalert.ui.detail

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cepalert.ui.map.ScoredZone
import com.cepalert.ui.theme.ScoreHigh
import com.cepalert.ui.theme.ScoreLow
import com.cepalert.ui.theme.ScoreMid

private fun scoreColor(score: Int?): Color = when {
    score == null -> ScoreLow
    score >= 67 -> ScoreHigh
    score >= 34 -> ScoreMid
    else -> ScoreLow
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneDetailSheet(
    scoredZone: ScoredZone,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Score — large, colored
            val score = scoredZone.score?.score
            Text(
                text = score?.toString() ?: "—",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor(score)
            )
            Text(
                text = if (score != null) "Probabilitat de sortida" else "Sense dades",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Breakdown rows
            scoredZone.score?.rows?.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(row.label, style = MaterialTheme.typography.bodyMedium)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            row.rawValueText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "pes ${(row.weight * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Zone metadata
            val zone = scoredZone.zone
            Text(
                text = "Bosc: ${zone.bosqueTipo.replaceFirstChar { it.uppercase() }} · ${zone.altitud} m · ${zone.orientacion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            // Legal notice
            Text(
                text = "Predicció orientativa. Les condicions reals poden variar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
