package dev.vodnixir.delirium.ui.friends

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vodnixir.delirium.R

@Composable
fun AddFriendScreen(
    viewModel: AddFriendViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AddFriendEvent.Done -> onDone()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        when (state.phase) {
            AddFriendPhase.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            AddFriendPhase.ShowingCode -> ShowingCodeContent(
                code = state.generatedCode.orEmpty(),
                onShared = viewModel::onCodeShared,
                onBack = viewModel::onBackToChoosing,
            )

            AddFriendPhase.EnteringCode -> EnteringCodeContent(
                name = state.name,
                code = state.inputCode,
                error = state.error,
                onNameChange = viewModel::onNameChange,
                onCodeChange = viewModel::onInputCodeChange,
                onSubmit = viewModel::onJoinClicked,
                onBack = { viewModel.dismissError(); viewModel.onBackToChoosing() },
            )

            AddFriendPhase.Choosing -> ChoosingContent(
                name = state.name,
                error = state.error,
                onNameChange = viewModel::onNameChange,
                onCreate = viewModel::onCreateCodeClicked,
                onEnter = viewModel::onEnterCodeClicked,
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun ChoosingContent(
    name: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onEnter: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.addfriend_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.addfriend_your_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.addfriend_create_code))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onEnter, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.addfriend_enter_code))
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
    }
}

@Composable
private fun ShowingCodeContent(code: String, onShared: () -> Unit, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.addfriend_share),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = code,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = MaterialTheme.typography.titleLarge.fontSize * 2,
            ),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { clipboard.setText(AnnotatedString(code)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.common_copy)) }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onShared, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.addfriend_shared))
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
    }
}

@Composable
private fun EnteringCodeContent(
    name: String,
    code: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.addfriend_enter_code),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.addfriend_your_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.addfriend_code_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = code.length == 6 && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.addfriend_join)) }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
    }
}
