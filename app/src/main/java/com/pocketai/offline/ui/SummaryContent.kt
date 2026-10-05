package com.pocketai.offline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SelectableSummaryText(
    text: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("summaryText"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            text.lineSequence().forEachIndexed { index, line ->
                Text(
                    text = line.ifBlank { " " },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summaryLine-$index"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
