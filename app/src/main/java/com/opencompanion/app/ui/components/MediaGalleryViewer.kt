package com.opencompanion.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Miniature d'un média de galerie (photo, GIF, vidéo).
 */
@Composable
fun MediaThumbnailItem(
    mediaPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isVideo = mediaPath.endsWith(".mp4", ignoreCase = true) || mediaPath.endsWith(".webm", ignoreCase = true)
    val isGif = mediaPath.endsWith(".gif", ignoreCase = true)

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(Modifier.fillMaxSize()) {
            MediaDisplay(
                mediaPath = mediaPath,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                isThumbnail = true,
            )

            // Badge type de média
            if (isGif) {
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                ) {
                    Text(
                        "GIF",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            } else if (isVideo) {
                Surface(
                    color = Color(0xCC000000),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Vidéo",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(24.dp),
                    )
                }
            }
        }
    }
}

/**
 * Visionneuse grand format plein écran (Lightbox) pour parcourir les images, GIFs et vidéos.
 */
@Composable
fun MediaLightboxDialog(
    mediaList: List<String>,
    initialIndex: Int = 0,
    characterName: String = "",
    onDismiss: () -> Unit,
    onDeleteMedia: ((String) -> Unit)? = null,
) {
    if (mediaList.isEmpty()) {
        onDismiss()
        return
    }

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, mediaList.lastIndex)) }
    val currentMedia = mediaList.getOrNull(currentIndex) ?: return

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) {
            offset += offsetChange
        } else {
            offset = Offset.Zero
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0050508)),
        ) {
            // Zone centrale avec zoom/pan pour l'image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 60.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center,
            ) {
                MediaDisplay(
                    mediaPath = currentMedia,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    isThumbnail = false,
                )
            }

            // Barre supérieure d'actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color(0x99000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = characterName.ifBlank { "Galerie" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        text = "${currentIndex + 1} / ${mediaList.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDeleteMedia != null && !currentMedia.startsWith("asset://")) {
                        IconButton(onClick = {
                            onDeleteMedia(currentMedia)
                            if (mediaList.size <= 1) {
                                onDismiss()
                            } else {
                                currentIndex = currentIndex.coerceAtMost(mediaList.size - 2)
                            }
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = Color.Red)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
                    }
                }
            }

            // Boutons de navigation Précédent / Suivant si plusieurs médias
            if (mediaList.size > 1) {
                if (currentIndex > 0) {
                    Surface(
                        color = Color(0x88000000),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .clickable {
                                currentIndex--
                                scale = 1f
                                offset = Offset.Zero
                            },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Précédent",
                            tint = Color.White,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (currentIndex < mediaList.lastIndex) {
                    Surface(
                        color = Color(0x88000000),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .clickable {
                                currentIndex++
                                scale = 1f
                                offset = Offset.Zero
                            },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Suivant",
                            tint = Color.White,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Affiche une image, un GIF animé ou une vidéo avec décodage matériel et mise en cache disque.
 */
@Composable
private fun MediaDisplay(
    mediaPath: String,
    modifier: Modifier,
    contentScale: ContentScale,
    isThumbnail: Boolean,
) {
    val context = LocalContext.current
    val isVideo = mediaPath.endsWith(".mp4", ignoreCase = true) || mediaPath.endsWith(".webm", ignoreCase = true)
    val isGif = mediaPath.endsWith(".gif", ignoreCase = true)

    val fileState = produceState<File?>(initialValue = null, mediaPath) {
        value = resolveMediaFile(context, mediaPath)
    }
    val file = fileState.value

    if (file == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (isThumbnail) 24.dp else 36.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
        return
    }

    if (isVideo) {
        if (isThumbnail) {
            val thumbBitmap = produceState<Bitmap?>(initialValue = null, file) {
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(file.absolutePath)
                        val bmp = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                        retriever.release()
                        bmp
                    }.getOrNull()
                }
            }.value

            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                if (thumbBitmap != null) {
                    Image(
                        bitmap = thumbBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color(0xFF161426)))
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0x99000000),
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val mc = MediaController(ctx)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setVideoPath(file.absolutePath)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                        setOnErrorListener { _, _, _ -> true }
                    }
                },
                modifier = modifier,
            )
        }
    } else if (isGif && Build.VERSION.SDK_INT >= 28) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    this.scaleType = if (isThumbnail) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
                    runCatching {
                        val source = ImageDecoder.createSource(file)
                        val drawable = ImageDecoder.decodeDrawable(source)
                        setImageDrawable(drawable)
                        if (drawable is AnimatedImageDrawable) {
                            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                            drawable.start()
                        }
                    }
                }
            },
            modifier = modifier,
        )
    } else {
        val bitmapState = produceState<Bitmap?>(initialValue = null, file) {
            value = withContext(Dispatchers.IO) {
                decodeSampledBitmap(file.absolutePath, if (isThumbnail) 300 else 1600)
            }
        }
        val bitmap = bitmapState.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier,
                contentScale = contentScale,
            )
        } else {
            Box(modifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

/**
 * Résout n'importe quel média (asset embarqué, URL web ou fichier local) en un fichier local réel
 * avec mise en cache disque automatique pour lecture directe, fluide et instantanée.
 */
private suspend fun resolveMediaFile(context: Context, path: String): File? = withContext(Dispatchers.IO) {
    try {
        if (path.startsWith("asset:///") || path.startsWith("assets/")) {
            val assetRel = path.removePrefix("asset:///").removePrefix("assets/")
            val cacheDir = File(context.cacheDir, "bundled_media").apply { mkdirs() }
            val cleanName = assetRel.replace('/', '_')
            val cached = File(cacheDir, cleanName)
            if (cached.exists() && cached.length() > 0L) {
                return@withContext cached
            }
            context.assets.open(assetRel).use { input ->
                cached.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext cached
        }

        if (path.startsWith("http://") || path.startsWith("https://")) {
            val cacheDir = File(context.cacheDir, "network_media").apply { mkdirs() }
            val cleanName = "${path.hashCode()}_" + path.substringAfterLast('/', "media.bin").take(30)
            val cached = File(cacheDir, cleanName)
            if (cached.exists() && cached.length() > 0L) {
                return@withContext cached
            }
            val conn = (URL(path).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Mozilla/5.0 OpenCompanion/1.0")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    cached.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext cached
            }
            return@withContext null
        }

        val f = File(path)
        if (f.exists() && f.isFile) return@withContext f
        null
    } catch (_: Exception) {
        null
    }
}

private fun decodeSampledBitmap(path: String, targetPx: Int): Bitmap? {
    val file = File(path)
    if (!file.exists() || !file.isFile) return null

    val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, boundsOptions)
    val (rawWidth, rawHeight) = boundsOptions.outWidth to boundsOptions.outHeight
    if (rawWidth <= 0 || rawHeight <= 0) return null

    var sampleSize = 1
    val largest = maxOf(rawWidth, rawHeight)
    while (largest / (sampleSize * 2) >= targetPx) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(path, decodeOptions)
}
