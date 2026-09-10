package com.opencompanion.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Palette « façon SpicyChat/RosyTalk » : fond très sombre + accent en dégradé rose→violet — ces
 * apps de personnages IA adoptent presque toutes un thème sombre unique assumé comme identité
 * visuelle plutôt que comme simple réglage d'accessibilité, d'où un thème volontairement FIXE
 * (pas de bascule clair/sombre, pas de couleur dynamique Material You façon "app système" — on
 * veut que l'app ait sa propre identité, cohérente d'un appareil à l'autre).
 */
val BgDeep = Color(0xFF0F0D14)
val BgSurface = Color(0xFF1B1721)
val BgSurfaceVariant = Color(0xFF262030)
val BgCard = Color(0xFF211C29)
val AccentPink = Color(0xFFFF3D77)
val AccentPinkDeep = Color(0xFFE91E63)
val AccentViolet = Color(0xFFA855F7)
val AccentVioletDeep = Color(0xFF7C3AED)
val AccentAmber = Color(0xFFFFB74D)
val TextPrimary = Color(0xFFF5F1F8)
val TextSecondary = Color(0xFFB9AFC4)
val TextMuted = Color(0xFF7D7488)

/** Dégradé de marque, réutilisé pour les FAB, en-têtes de carte et boutons d'accent. */
val BrandGradient = Brush.linearGradient(listOf(AccentPink, AccentViolet))

/** Voile dégradé du clair (transparent) vers le sombre, pour superposer nom/tags sur une image
 *  d'avatar en bas de carte sans avoir besoin de connaître la couleur du fond de l'image. */
val ScrimGradient = Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0100D16)))

private val AppDarkColors = darkColorScheme(
    primary = AccentPink,
    onPrimary = Color.White,
    primaryContainer = AccentPinkDeep,
    onPrimaryContainer = Color.White,
    secondary = AccentViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A2B4D),
    onSecondaryContainer = Color(0xFFE9D9FF),
    tertiary = AccentAmber,
    onTertiary = Color(0xFF241900),
    tertiaryContainer = Color(0xFF4A3A1C),
    onTertiaryContainer = Color(0xFFFFDDAA),
    background = BgDeep,
    onBackground = TextPrimary,
    surface = BgSurface,
    onSurface = TextPrimary,
    surfaceVariant = BgSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = BgCard,
    surfaceContainerHigh = BgSurfaceVariant,
    surfaceContainerLow = BgSurface,
    error = Color(0xFFFF5C6C),
    onError = Color.White,
    errorContainer = Color(0xFF4C1B23),
    onErrorContainer = Color(0xFFFFD9DD),
    outline = Color(0xFF433A50),
    outlineVariant = Color(0xFF2E2838),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Style utilisé pour le nom d'un personnage superposé sur son avatar (cartes de découverte). */
val CharacterCardTitleStyle = TextStyle(
    color = Color.White,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
)

@Composable
fun OpenCompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppDarkColors, shapes = AppShapes, typography = AppTypography, content = content)
}
