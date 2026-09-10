package com.opencompanion.app.ui.components

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.opencompanion.app.ui.theme.AccentPink
import com.opencompanion.app.ui.theme.AccentViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Vignette d'avatar de personnage : décode [avatarPath] (fichier local, extrait de la fiche
 * Character Card à l'import — voir CharacterImportManager.saveAvatar) en arrière-plan, sous-
 * échantillonné à la taille d'affichage réelle pour éviter de charger une image potentiellement
 * volumineuse en pleine résolution juste pour une vignette. Sans [avatarPath] (personnage créé
 * à la main, sans fiche importée) ou en cas d'échec de décodage, retombe sur un avatar généré :
 * dégradé de marque + initiale du nom, plutôt que de laisser un espace vide façon "image cassée".
 */
@Composable
fun CharacterAvatar(
    avatarPath: String?,
    name: String,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
) {
    Box(modifier = modifier.clip(shape)) {
        if (avatarPath.isNullOrBlank()) {
            InitialAvatarFallback(name)
        } else {
            val density = LocalDensity.current
            // On ne connaît la taille réelle en px qu'une fois la contrainte de layout posée par
            // le modifier de l'appelant ; en pratique les avatars sont affichés entre 40dp et
            // ~160dp selon l'écran, donc un sous-échantillonnage ciblant ~200px suffit largement
            // pour une vignette nette sans jamais décoder l'image source en pleine résolution.
            val targetPx = with(density) { 200.dp.toPx() }.toInt().coerceAtLeast(64)
            val bitmapState = produceState<Bitmap?>(initialValue = null, avatarPath) {
                value = withContext(Dispatchers.IO) {
                    runCatching { decodeSampledBitmap(avatarPath, targetPx) }.getOrNull()
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
