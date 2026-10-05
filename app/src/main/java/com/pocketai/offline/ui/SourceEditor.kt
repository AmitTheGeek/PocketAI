package com.pocketai.offline.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction

@Composable
internal fun SourceTextEditor(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("sourceTextField")
                .semantics { contentDescription = "Source text input" },
            placeholder = { Text("Paste or edit text to summarise.") },
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
        )
    }
}
