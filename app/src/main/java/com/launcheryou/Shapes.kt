package com.launcheryou

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Bentuk bunga (cookie). depth 0 = lingkaran, makin besar makin bergelombang. */
class FlowerShape(
    private val petals: Int = 8,
    private val depth: Float = 0.08f,
    private val rotation: Float = 0f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = min(size.width, size.height) / 2f
        val steps = 120
        for (i in 0..steps) {
            val a = (2.0 * PI * i / steps).toFloat()
            val rad = r * (1f - depth + depth * cos(petals * a + rotation))
            val x = cx + rad * cos(a)
            val y = cy + rad * sin(a)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Tombol bunga: kelopak "mengempis" dan memantul saat ditekan. */
@Composable
fun FlowerButton(
    icon: ImageVector,
    desc: String,
    bg: Color,
    fg: Color,
    size: Dp = 56.dp,
    onClick: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val depth by animateFloatAsState(
        if (pressed) 0.015f else 0.075f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "depth"
    )
    val sc by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "sc"
    )
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .size(size)
            .graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(FlowerShape(8, depth))
            .background(bg)
            .clickable(interactionSource = src, indication = LocalIndication.current, onClickLabel = desc) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = fg)
    }
}
