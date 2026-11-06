package com.example.glooplauncher

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun GloopMascot(
    eyeTargetRatio: Float,
    popTrigger: Int,
    isDancing: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascotMotion")
    val animDuration = if (isDancing) 480 else 1400

    val idleOffsetY by infiniteTransition.animateFloat(
        initialValue = if (isDancing) -26f else -12f,
        targetValue = if (isDancing) 18f else 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration, easing = if (isDancing) FastOutLinearInEasing else EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleOffset"
    )

    val idleRotation by infiniteTransition.animateFloat(
        initialValue = if (isDancing) -12f else -4f,
        targetValue = if (isDancing) 12f else 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isDancing) animDuration * 2 else 1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleRotation"
    )

    val danceScaleY by infiniteTransition.animateFloat(
        initialValue = if (isDancing) 0.88f else 1.0f,
        targetValue = if (isDancing) 1.12f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "danceScaleY"
    )

    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3600
                1f at 0
                1f at 3400
                0.05f at 3500
                1f at 3600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    val pupilOffsetX by animateFloatAsState(
        targetValue = (eyeTargetRatio - 0.5f) * 10f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pupilX"
    )

    val popScale = remember { Animatable(1f) }
    LaunchedEffect(popTrigger) {
        if (popTrigger > 0) {
            popScale.animateTo(1.32f, animationSpec = tween(100, easing = FastOutLinearInEasing))
            popScale.animateTo(0.85f, animationSpec = tween(90, easing = LinearOutSlowInEasing))
            popScale.animateTo(1.0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .offset(y = idleOffsetY.dp)
            .rotate(idleRotation)
            .scale(scaleX = popScale.value, scaleY = popScale.value * danceScaleY)
            .size(130.dp)
    ) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(
                    color = Color(0xFF10B981),
                    shape = RoundedCornerShape(
                        topStart = 48.dp,
                        topEnd = 48.dp,
                        bottomStart = 34.dp,
                        bottomEnd = 34.dp
                    )
                )
        )

        Canvas(modifier = Modifier.size(100.dp)) {
            val eyeWidth = 7.dp.toPx()
            val eyeHeight = (7.dp.toPx()) * blinkProgress
            val pupilShift = pupilOffsetX.dp.toPx()
            val lookDownY = 3.dp.toPx()

            drawOval(
                color = Color.White,
                topLeft = Offset(size.width * 0.33f - eyeWidth, size.height * 0.40f - eyeHeight),
                size = Size(eyeWidth * 2, eyeHeight * 2)
            )
            if (blinkProgress > 0.35f) {
                drawCircle(
                    color = Color.Black,
                    radius = eyeWidth * 0.52f,
                    center = Offset(size.width * 0.33f + pupilShift, size.height * 0.40f + lookDownY)
                )
            }

            drawOval(
                color = Color.White,
                topLeft = Offset(size.width * 0.67f - eyeWidth, size.height * 0.40f - eyeHeight),
                size = Size(eyeWidth * 2, eyeHeight * 2)
            )
            if (blinkProgress > 0.35f) {
                drawCircle(
                    
