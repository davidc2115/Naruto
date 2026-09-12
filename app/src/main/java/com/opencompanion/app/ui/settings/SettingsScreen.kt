package com.opencompanion.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.opencompanion.app.data.EngineBackend
import com.opencompanion.app.data.UserGender
import com.opencompanion.app.engine.ModelManager
import com.opencompanion.app.engine.NanoBridge
import com.opencompanion.app.engine.RecommendedModels
import com.opencompanion.app.ui.theme.BrandGradient

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
            SectionTitle("Ton profil")
            Text(
                "Utilisé pour que les personnages s'adressent à toi de façon réaliste (par ton " +
                    "prénom, en tenant compte de ton âge/genre si tu les renseignes). Entièrement " +
                    "facultatif, jamais partagé — tout reste sur cet appareil.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = state.userProfile.name,
                onValueChange = viewModel::setUserName,
                label = { Text("Ton prénom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.userProfile.age?.toString() ?: "",
                onValueChange = { text -> viewModel.setUserAge(text.filter(Char::isDigit).take(3).toIntOrNull()) },
                label = { Text("Ton âge (facultatif)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                Text("Ton genre (facultatif)", style = MaterialTheme.typography.bodyMedium)
                UserGender.entries.forEach { gender ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = state.userProfile.gender == gender,
                            onClick = { viewModel.setUserGender(gender) },
                        )
                        Text(userGenderLabel(gender))
                    }
                }
            }

            Divider()
            SectionTitle("Moteur d'IA")
            Text(
                "Où tourne l'IA qui anime tes personnages ?",
                style = MaterialTheme.typography.bodySmall,
            )

            val currentCategory = engineCategoryOf(state.settings.enginePreference)
            EngineCategory.entries.forEach { category ->
                EngineCategoryCard(
                    category = category,
                    selected = category == currentCategory,
                    onClick = { viewModel.setEnginePreference(category.defaultBackend) },
                )
            }

            when (currentCategory) {
                EngineCategory.LOCAL -> Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        listOf(EngineBackend.AUTO, EngineBackend.AICORE, EngineBackend.LLAMA_CPP).forEach { backend ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                RadioButton(
                                    selected = state.settings.enginePreference == backend,
                                    onClick = { viewModel.setEnginePreference(backend) },
                                )
                                Text(engineBackendLabel(backend))
                            }
                        }
                        Text(
                            "« Auto » utilise en priorité le NPU matériel (Gemini Nano) pour des réponses " +
                                "instantanées s'il est actif sur l'appareil, ou bascule sur votre modèle " +
                                "local GGUF sinon.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                EngineCategory.GROQ -> Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Crée une clé API gratuite sur console.groq.com/keys.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        ApiKeysField(
                            value = state.settings.groqApiKey,
                            onValueChange = viewModel::setGroqApiKey,
                            label = "Clé(s) API Groq",
                            placeholder = "gsk_...",
                        )
                        OutlinedTextField(
                            value = state.settings.groqModelName,
                            onValueChange = viewModel::setGroqModelName,
                            label = { Text("Modèle") },
                            placeholder = { Text("openai/gpt-oss-120b") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                EngineCategory.GEMINI -> Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Crée une clé API gratuite sur aistudio.google.com/apikey.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        ApiKeysField(
                            value = state.settings.geminiApiKey,
                            onValueChange = viewModel::setGeminiApiKey,
                            label = "Clé(s) API Gemini",
                            placeholder = "AIza...",
                        )
                        OutlinedTextField(
                            value = state.settings.geminiModelName,
                            onValueChange = viewModel::setGeminiModelName,
                            label = { Text("Modèle") },
                            placeholder = { Text("gemini-3.5-flash") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                EngineCategory.CUSTOM_CLOUD -> Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Pour OpenRouter, laisse l'URL par défaut. Sinon, mets l'URL de n'importe " +
                                "quel serveur compatible OpenAI (LM Studio, Together AI…).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = state.settings.cloudEndpointUrl,
                            onValueChange = viewModel::setCloudEndpointUrl,
                            label = { Text("URL de l'API (/v1/chat/completions)") },
                            placeholder = { Text("https://openrouter.ai/api/v1/chat/completions") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.settings.cloudModelName,
                            onValueChange = viewModel::setCloudModelName,
                            label = { Text("Modèle") },
                            placeholder = { Text("nousresearch/hermes-3-llama-3.1-8b:free") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ApiKeysField(
                            value = state.settings.cloudApiKey,
                            onValueChange = viewModel::setCloudApiKey,
                            label = "Clé(s) API",
                            placeholder = "sk-or-v1-...",
                        )
                    }
                }
                EngineCategory.ANONYMOUS -> Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "⚠️ Passe par des serveurs publics tiers non vérifiés (Pollinations, " +
                                "KoboldAI Horde) : disponibilité et qualité variables. Préfère Groq ou " +
                                "Gemini avec ta propre clé si tu peux.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        listOf(EngineBackend.CLOUD_FREE_NO_KEY, EngineBackend.CLOUD_KOBOLD_HORDE).forEach { backend ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                RadioButton(
                                    selected = state.settings.enginePreference == backend,
                                    onClick = { viewModel.setEnginePreference(backend) },
                                )
                                Text(if (backend == EngineBackend.CLOUD_FREE_NO_KEY) "Auto (recommandé)" else "KoboldAI Horde uniquement")
                            }
                        }
                    }
                }
            }

            NanoAvailabilityRow(
                availability = state.nanoAvailability,
                onDownload = viewModel::downloadNano,
            )

            Divider()
            SectionTitle("Modèle (GGUF)")

            Text(
                "Préréglages recommandés : un tap pour télécharger, aucune clé ni compte requis.",
                style = MaterialTheme.typography.bodySmall,
            )
            RecommendedModels.Tier.entries.forEach { tier ->
                Text(
                    if (tier == RecommendedModels.Tier.RAPIDE) "⚡ Rapide" else "★ Qualité",
                    style = MaterialTheme.typography.labelLarge,
                )
                RecommendedModels.ALL.filter { it.tier == tier }.forEach { entry ->
                    RecommendedModelRow(
                        entry = entry,
                        downloading = state.downloadProgress != null,
                        onDownload = { viewModel.downloadRecommendedModel(entry) },
                    )
                }
            }

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
            if (state.settings.useGpu) {
                StepperRow(
                    label = "Couches déchargées sur le GPU",
                    value = state.settings.gpuLayers,
                    step = 4,
                    range = 0..999,
                    onChange = viewModel::setGpuLayers,
                )
                Text(
                    "Une valeur inférieure au nombre de couches du modèle laisse le reste au " +
                        "CPU : les deux travaillent ensemble (hybride) au lieu que tout passe " +
                        "par le GPU. 999 = toutes les couches sur GPU, 0 = revient au CPU pur.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Divider()
            SectionTitle("Performance")
            StepperRow(
                label = "Taille du contexte",
                value = state.settings.contextSize,
                step = 512,
                range = 512..16384,
                onChange = viewModel::setContextSize,
            )
            Text(
                "Combien de messages récents le personnage garde en mémoire avant d'oublier les " +
                    "plus anciens. Une valeur plus haute retient une conversation plus longue " +
                    "mais utilise plus de RAM et ralentit chaque réponse : à monter " +
                    "progressivement plutôt que de sauter directement au maximum.",
                style = MaterialTheme.typography.bodySmall,
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

private fun engineBackendLabel(backend: EngineBackend): String = when (backend) {
    // Repose sur des serveurs anonymes tiers (Pollinations AI, KoboldAI Horde) non affiliés à
    // Anthropic ni configurés par toi : tes messages transitent par leurs services. Ni "gratuit"
    // ni "illimité" ne sont garantis dans le temps. Préférer Groq/Gemini avec ta propre clé API.
    EngineBackend.CLOUD_FREE_NO_KEY -> "⚠️ Cloud anonyme sans clé (service tiers non vérifié)"
    EngineBackend.AUTO -> "Auto (⚡ NPU matériel si dispo, sinon llama.cpp)"
    EngineBackend.AICORE -> "⚡ NPU Matériel : Gemini Nano (AICore, ultra-rapide)"
    EngineBackend.LLAMA_CPP -> "Modèle local (llama.cpp sur l'appareil)"
    EngineBackend.CLOUD_GROQ -> "☁️ Groq (avec ta clé API)"
    EngineBackend.CLOUD_GEMINI -> "☁️ Gemini (avec ta clé API)"
    EngineBackend.CLOUD_OPENROUTER -> "☁️ OpenRouter Cloud (avec ta clé API)"
    EngineBackend.CLOUD_KOBOLD_HORDE -> "🌐 KoboldAI Horde (réseau communautaire tiers, sans clé)"
    EngineBackend.CLOUD_CUSTOM_OPENAI -> "🛠️ API Cloud personnalisée / compatible OpenAI"
}

/**
 * Regroupe les 9 [EngineBackend] techniques en 5 familles compréhensibles pour l'utilisateur, afin
 * de remplacer l'ancienne liste plate de 9 boutons radio par 5 grandes cartes (voir
 * [EngineCategoryCard]). Chaque famille garde son propre bloc de réglages détaillés en dessous
 * (choix précis + clé API le cas échéant).
 */
private enum class EngineCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val defaultBackend: EngineBackend,
) {
    LOCAL(
        title = "Sur l'appareil",
        subtitle = "Gratuit, privé, fonctionne sans connexion",
        icon = Icons.Filled.PhoneAndroid,
        defaultBackend = EngineBackend.AUTO,
    ),
    GROQ(
        title = "Groq",
        subtitle = "Cloud très rapide, clé API gratuite",
        icon = Icons.Filled.Bolt,
        defaultBackend = EngineBackend.CLOUD_GROQ,
    ),
    GEMINI(
        title = "Gemini",
        subtitle = "Cloud Google, clé API gratuite",
        icon = Icons.Filled.AutoAwesome,
        defaultBackend = EngineBackend.CLOUD_GEMINI,
    ),
    CUSTOM_CLOUD(
        title = "Cloud personnalisé",
        subtitle = "OpenRouter ou tout serveur compatible OpenAI",
        icon = Icons.Filled.Cloud,
        defaultBackend = EngineBackend.CLOUD_OPENROUTER,
    ),
    ANONYMOUS(
        title = "Anonyme (sans clé)",
        subtitle = "Serveurs publics tiers, qualité variable",
        icon = Icons.Filled.Public,
        defaultBackend = EngineBackend.CLOUD_FREE_NO_KEY,
    ),
}

private fun engineCategoryOf(backend: EngineBackend): EngineCategory = when (backend) {
    EngineBackend.AUTO, EngineBackend.AICORE, EngineBackend.LLAMA_CPP -> EngineCategory.LOCAL
    EngineBackend.CLOUD_GROQ -> EngineCategory.GROQ
    EngineBackend.CLOUD_GEMINI -> EngineCategory.GEMINI
    EngineBackend.CLOUD_OPENROUTER, EngineBackend.CLOUD_CUSTOM_OPENAI -> EngineCategory.CUSTOM_CLOUD
    EngineBackend.CLOUD_FREE_NO_KEY, EngineBackend.CLOUD_KOBOLD_HORDE -> EngineCategory.ANONYMOUS
}

/** Grande carte cliquable représentant une famille de moteur d'IA (remplace un simple bouton radio
 *  pour rendre le choix plus visuel et plus simple à comprendre d'un coup d'œil). */
@Composable
private fun EngineCategoryCard(category: EngineCategory, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (selected) BrandGradient else Brush.linearGradient(listOf(Color.Gray, Color.Gray)), shape = CircleShape),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Icon(category.icon, contentDescription = null, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(category.title, style = MaterialTheme.typography.titleMedium)
                Text(category.subtitle, style = MaterialTheme.typography.bodySmall)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

/** Champ multi-lignes pour saisir une ou plusieurs clés API (une par ligne), pour permettre la
 *  rotation automatique quand le quota d'une clé est atteint (voir `generateWithKeyRotation`). */
@Composable
private fun ApiKeysField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Plusieurs clés ? Une par ligne (ou séparées par une virgule) — la suivante est " +
                "utilisée automatiquement si le quota d'une clé est atteint.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun userGenderLabel(gender: UserGender): String = when (gender) {
    UserGender.NON_PRECISE -> "Non précisé"
    UserGender.FEMME -> "Femme"
    UserGender.HOMME -> "Homme"
    UserGender.AUTRE -> "Autre"
}

@Composable
private fun NanoAvailabilityRow(availability: NanoBridge.NanoAvailability, onDownload: () -> Unit) {
    val (text, showDownload) = when (availability) {
        NanoBridge.NanoAvailability.AVAILABLE -> "Gemini Nano est prêt sur cet appareil." to false
        NanoBridge.NanoAvailability.DOWNLOADABLE -> "Gemini Nano peut être téléchargé sur cet appareil." to true
        NanoBridge.NanoAvailability.DOWNLOADING -> "Téléchargement de Gemini Nano en cours…" to false
        NanoBridge.NanoAvailability.UNAVAILABLE ->
            "Gemini Nano n'est pas disponible sur cet appareil (surtout Pixel récents pour l'instant) : le modèle local sera utilisé." to false
    }
    Column {
        Text(text, style = MaterialTheme.typography.bodySmall)
        if (showDownload) {
            TextButton(onClick = onDownload) { Text("Télécharger Gemini Nano") }
        }
    }
}

@Composable
private fun RecommendedModelRow(
    entry: RecommendedModels.Entry,
    downloading: Boolean,
    onDownload: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(8.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${entry.paramCount} · %.2f Go · %s".format(entry.approxSizeGb, entry.license),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(entry.note, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDownload, enabled = !downloading) { Text("Télécharger") }
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
