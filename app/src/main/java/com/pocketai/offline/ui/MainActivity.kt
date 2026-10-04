package com.pocketai.offline.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.pocketai.offline.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = ViewModelProvider(
            this,
            MainViewModel.factory(application)
        )[MainViewModel::class.java]

        setContent {
            PocketAiTheme {
                PocketAiScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun PocketAiScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importModel)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F8F3)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header(state)

            ActionRow(
                state = state,
                onImport = {
                    filePicker.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onSummarize = viewModel::summarize,
                onCancel = viewModel::cancelSummary
            )

            state.loadingMessage?.let {
                LoadingBand(message = it)
            }

            state.errorMessage?.let {
                MessageBand(message = it)
            }

            state.warningMessage?.let {
                WarningBand(message = it)
            }

            InputSection(
                text = state.inputText,
                enabled = !state.isBusy,
                onTextChange = viewModel::updateInput
            )

            OutputSection(state = state)
        }
    }
}

@Composable
private fun Header(state: PocketAiUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "PocketAI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF13201A)
        )
        Text(
            text = state.modelLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF425148),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActionRow(
    state: PocketAiUiState,
    onImport: () -> Unit,
    onSummarize: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onImport,
            enabled = state.canImport,
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_upload_file),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("Import")
        }

        Button(
            onClick = onSummarize,
            enabled = state.canSummarize,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C6B4A))
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_play),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("Summarize")
        }

        OutlinedButton(
            onClick = onCancel,
            enabled = state.isGenerating,
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cancel),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.size(8.dp))
            Text("Cancel")
        }
    }
}

@Composable
private fun LoadingBand(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF27352E)
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1C6B4A),
            trackColor = Color(0xFFD8E3D5)
        )
    }
}

@Composable
private fun MessageBand(message: String) {
    Surface(
        color = Color(0xFFFFE8E4),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = Color(0xFF8A1F11),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WarningBand(message: String) {
    Surface(
        color = Color(0xFFFFF1CC),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = Color(0xFF6B4B00),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun InputSection(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Input",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF13201A)
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            minLines = 7,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun OutputSection(state: PocketAiUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF13201A)
            )
            Text(
                text = formatElapsed(state.elapsedMs),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF5D544B)
            )
        }

        HorizontalDivider(color = Color(0xFFD8DAD3))

        Text(
            text = state.outputText.ifBlank {
                if (state.isGenerating) "" else "Output will stream here."
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(top = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF16221B)
        )
    }
}

@Composable
private fun PocketAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF1C6B4A),
            secondary = Color(0xFF5B6C94),
            background = Color(0xFFF7F8F3),
            surface = Color(0xFFF7F8F3),
            error = Color(0xFF9B2C1D)
        ),
        content = content
    )
}

private fun formatElapsed(ms: Long): String {
    val seconds = ms / 1000
    val tenths = (ms % 1000) / 100
    return "$seconds.${tenths}s"
}
