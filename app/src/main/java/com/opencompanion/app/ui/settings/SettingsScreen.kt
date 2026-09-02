package com.opencompanion.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opencompanion.app.engine.ModelManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var showUrlDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFromUri(it, it.lastPathSegment ?: "modele.gguf") }
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Retour") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("Modèle (GGUF)")

            state.downloadProgress?.let { progress ->
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            if (state.localModels.isEmpty()) {
                Text(
                    "Aucun modèle importé. Ajoute un fichier .gguf depuis ton appareil, ou colle un " +
                        "lien direct — aucune clé, aucun compte requis.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.localModels.forEach { model ->
                ModelRow(
                    model = model,
                    selected = model.file.absolutePath == state.settings.selectedModelPath,
                    onSelect = { viewModel.selectModel(model.file.absolutePath) },
                    onDelete = { viewModel.deleteModel(model) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) }) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(" Importer un fichier")
                }
                Button(onClick = { showUrlDialog = true }) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Text(" Depuis une URL")
                }
            }

            Divider()
            SectionTitle("Matériel")
            Text(
                if (state.vulkanCompiledIn) {
                    if (state.deviceReportsVulkan) "Vulkan compilé et détecté sur cet appareil." else
                        "Vulkan compilé, mais non annoncé par cet appareil (le CPU sera utilisé)."
                } else {
                    "Ce build ne contient pas le backend Vulkan (CPU uniquement)."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            SettingRow(label = "Utiliser le GPU (Vulkan) si possible") {
                Switch(
                    checked = state.settings.useGpu,
                    onCheckedChange = viewModel::setUseGpu,
                    enabled = state.vulkanCompiledIn && state.deviceReportsVulkan,
                )
            }

            Divider()
            SectionTitle("Performance")
            StepperRow(
                label = "Taille du contexte",
                value = state.settings.contextSize,
                step = 512,
                range = 512..8192,
                onChange = viewModel::setContextSize,
            )
            StepperRow(
                label = "Threads CPU (0 = auto)",
                value = state.settings.threads,
                step = 1,
                range = 0..16,
                onChange = viewModel::setThreads,
            )
            StepperRow(
                label = "Longueur max. de réponse",
                value = state.settings.maxResponseTokens,
                step = 64,
                range = 64..2048,
                onChange = viewModel::setMaxResponseTokens,
            )

            Divider()
            SectionTitle("Génération")
            SliderRow(
                label = "Température",
                value = state.settings.temperature,
                range = 0f..2f,
                onChange = viewModel::setTemperature,
            )
            StepperRow(
                label = "Top-K",
                value = state.settings.topK,
                step = 5,
                range = 0..100,
                onChange = viewModel::setTopK,
            )
            SliderRow(
                label = "Top-P",
                value = state.settings.topP,
                range = 0f..1f,
                onChange = viewModel::setTopP,
            )
            SliderRow(
                label = "Pénalité de répétition",
                value = state.settings.repeatPenalty,
                range = 1f..2f,
                onChange = viewModel::setRepeatPenalty,
            )
        }
    }

    if (showUrlDialog) {
        UrlImportDialog(
            onDismiss = { showUrlDialog = false },
            onConfirm = { url -> showUrlDialog = false; viewModel.importFromUrl(url) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ModelRow(
    model: ModelManager.LocalModel,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                RadioButton(selected = selected, onClick = onSelect)
                Column {
                    Text(model.displayName, style = MaterialTheme.typography.bodyLarge)
                    val sizeGb = model.sizeBytes / (1024f * 1024f * 1024f)
                    val details = listOfNotNull(
                        model.architecture,
                        model.contextLength?.let { "ctx $it" },
                        "%.2f Go".format(sizeGb),
                    ).joinToString(" · ")
                    Text(details, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer") }
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        control()
    }
}

@Composable
private fun StepperRow(label: String, value: Int, step: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - step).coerceIn(range)) }) { Text("−") }
            Text("$value", modifier = Modifier.padding(horizontal = 12.dp))
            IconButton(onClick = { onChange((value + step).coerceIn(range)) }) { Text("+") }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label : %.2f".format(value), style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun UrlImportDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importer un modèle depuis une URL") },
        text = {
            Column {
                Text(
                    "Lien direct vers un fichier .gguf (n'importe quelle source : serveur " +
                        "personnel, dépôt communautaire…). Aucune clé d'API requise.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://…/modele.gguf") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (url.isNotBlank()) onConfirm(url.trim()) }, enabled = url.isNotBlank()) {
                Text("Télécharger")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
