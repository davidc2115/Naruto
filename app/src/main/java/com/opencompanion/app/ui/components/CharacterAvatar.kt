package com.opencompanion.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.opencompanion.app.ui.theme.AccentPink
import com.opencompanion.app.ui.theme.AccentViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Vignette d'avatar de personnage : décode [avatarPath] (fichier local ou asset embarqué)
 * en arrière-plan, sous-échantillonné à la taille d'affichage réelle. En cas d'absence
 * de fichier local, tente de charger l'avatar embarqué dans assets/avatars/ correspondant
 * au personnage, puis retombe sur un avatar généré avec initiale.
 */
@Composable
fun CharacterAvatar(
    avatarPath: String?,
    name: String,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    Box(modifier = modifier.clip(shape)) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val targetPx = with(density) { 200.dp.toPx() }.toInt().coerceAtLeast(64)
        val bitmapState = produceState<Bitmap?>(initialValue = null, avatarPath, name) {
            value = withContext(Dispatchers.IO) {
                if (!avatarPath.isNullOrBlank() && !avatarPath.startsWith("asset://")) {
                    decodeSampledBitmap(avatarPath, targetPx)
                } else null
            } ?: withContext(Dispatchers.IO) {
                loadAssetAvatar(context, avatarPath, name, targetPx)
            }
        }
        val bitmap = bitmapState.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            InitialAvatarFallback(name)
        }
    }
}

@Composable
private fun InitialAvatarFallback(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(AccentPink, AccentViolet))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).ifEmpty { "?" }.uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** Décode [path] avec un `inSampleSize` choisi pour que la plus grande dimension du bitmap
 *  résultant reste proche de [targetPx] — évite de charger en mémoire une image bien plus
 *  grande que ce qui sera jamais affiché (les fiches PNG peuvent embarquer des avatars en très
 *  haute résolution). Renvoie null si le fichier est absent ou n'est pas une image valide. */
private fun decodeSampledBitmap(path: String, targetPx: Int): Bitmap? {
    val file = File(path)
    if (!file.exists() || !file.isFile) return null

    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, boundsOptions)
    val (rawWidth, rawHeight) = boundsOptions.outWidth to boundsOptions.outHeight
    if (rawWidth <= 0 || rawHeight <= 0) return null

    var sampleSize = 1
    val largestDimension = maxOf(rawWidth, rawHeight)
    while (largestDimension / (sampleSize * 2) >= targetPx) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(path, decodeOptions)
}

/** Tente de charger un avatar embarqué dans les assets de l'application (assets/avatars/...)
 *  par chemin ou par nom de personnage si aucun fichier local n'existe. */
private fun loadAssetAvatar(context: Context, avatarPath: String?, name: String, targetPx: Int): Bitmap? {
    val cleanPath = avatarPath?.removePrefix("asset:///")?.removePrefix("assets/")
    val candidates = mutableListOf<String>()
    if (!cleanPath.isNullOrBlank()) {
        candidates.add(cleanPath)
        if (!cleanPath.startsWith("avatars/")) {
            candidates.add("avatars/$cleanPath")
        }
    }
    val normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    val sanitized = normalized.lowercase()
        .replace(".", "")
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
    candidates.add("avatars/$sanitized.jpg")
    candidates.add("avatars/$sanitized.png")
    candidates.add("avatars/$sanitized.webp")

    for (assetName in candidates) {
        try {
            val sampleSize = context.assets.open(assetName).use { stream ->
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, boundsOptions)
                val (rawWidth, rawHeight) = boundsOptions.outWidth to boundsOptions.outHeight
                if (rawWidth > 0 && rawHeight > 0) {
                    var s = 1
                    val largest = maxOf(rawWidth, rawHeight)
                    while (largest / (s * 2) >= targetPx) {
                        s *= 2
                    }
                    s
                } else {
                    -1
                }
            }
            if (sampleSize > 0) {
                context.assets.open(assetName).use { stream ->
                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val bmp = BitmapFactory.decodeStream(stream, null, decodeOptions)
                    if (bmp != null) return bmp
                }
            }
        } catch (_: Exception) {
            // Ignorer et essayer le candidat suivant
        }
    }
    return null
}

