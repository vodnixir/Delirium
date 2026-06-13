package dev.vodnixir.delirium.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.ui.components.DButtonFullWidth
import dev.vodnixir.delirium.ui.components.DTextButton
import dev.vodnixir.delirium.ui.components.DTextField

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onAuthenticated: () -> Unit,
    onGoToRegister: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthEvents(viewModel, onAuthenticated)

    AuthScaffold(title = stringResource(R.string.auth_login_title)) {
        DTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = stringResource(R.string.auth_username),
            leadingIcon = Icons.Default.Person,
            keyboardType = KeyboardType.Ascii,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        DTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_password),
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AuthError(state.error)
        Spacer(Modifier.height(24.dp))
        DButtonFullWidth(
            text = stringResource(R.string.auth_login),
            onClick = viewModel::login,
            loading = state.loading,
        )
        DTextButton(
            text = stringResource(R.string.auth_to_register),
            onClick = onGoToRegister,
        )
    }
}

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onAuthenticated: () -> Unit,
    onGoToLogin: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthEvents(viewModel, onAuthenticated)

    AuthScaffold(title = stringResource(R.string.auth_register_title)) {
        DTextField(
            value = state.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = stringResource(R.string.auth_display_name),
            leadingIcon = Icons.Default.Badge,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        DTextField(
            value = state.username,
            onValueChange = viewModel::onUsernameChange,
            label = stringResource(R.string.auth_username),
            leadingIcon = Icons.Default.Person,
            keyboardType = KeyboardType.Ascii,
            supportingText = stringResource(R.string.auth_username_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        DTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.auth_password),
            leadingIcon = Icons.Default.Lock,
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AuthError(state.error)
        Spacer(Modifier.height(24.dp))
        DButtonFullWidth(
            text = stringResource(R.string.auth_create),
            onClick = viewModel::register,
            loading = state.loading,
        )
        DTextButton(
            text = stringResource(R.string.auth_to_login),
            onClick = onGoToLogin,
        )
    }
}

@Composable
private fun AuthEvents(viewModel: AuthViewModel, onAuthenticated: () -> Unit) {
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.Success -> onAuthenticated()
            }
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(colors.surface, colors.background)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = Brush.horizontalGradient(listOf(colors.primary, colors.tertiary)),
                ),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(40.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            content()
        }
    }
}

@Composable
private fun AuthError(error: String?) {
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )
    }
}
