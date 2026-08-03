package dev.stade.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.stade.ui.components.BrandMark
import dev.stade.ui.i18n.LocalStrings

@Composable
fun WelcomeUsernameScreen(onNext: (String) -> Unit) {
    var nickname by remember { mutableStateOf("") }
    val strings = LocalStrings.current

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BrandMark(size = 96.dp)
            Spacer(Modifier.height(20.dp))
            Text(strings.welcomeTitle, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                strings.welcomeDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text(strings.nicknamePlaceholder) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.widthIn(min = 280.dp, max = 420.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(
                enabled = nickname.isNotBlank(),
                onClick = { onNext(nickname.trim()) },
                modifier = Modifier.widthIn(min = 280.dp, max = 420.dp).fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) { Text(strings.continueAction) }
        }
    }
}
