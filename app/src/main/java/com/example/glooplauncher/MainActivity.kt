package com.example.glooplauncher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GloopTVApp()
        }
    }
}

data class AppModel(
    val label: String,
    val packageName: String
)

@Composable
fun GloopTVApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val apps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        resolveInfos.map {
            AppModel(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName
            )
        }.distinctBy { it.packageName }
    }

    // Tracks which app is selected/focused so Gloop's gaze follows it
    var focusedIndex by remember { mutableIntStateOf(0) }
    var popTrigger by remember { mutableIntStateOf(0) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
        ) {
            // Top Left: Title & Hint
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 8.dp)
            ) {
                Text(
                    text = "Gloop TV",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your living room launcher",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp
                )
            }

            // Top Right: Gloop Mascot
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 20.dp)
            ) {
                val total = if (apps.isNotEmpty()) apps.size else 1
                val eyeTargetRatio = (focusedIndex.toFloat() / (total - 1).coerceAtLeast(1))
                GloopMascot(
                    eyeTargetRatio = eyeTargetRatio,
                    popTrigger = popTrigger
                )
            }

            // Bottom: Horizontal App Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "FAVORITES & APPS",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(apps) { index, app ->
                        AppDockTile(
                            app = app,
                            onFocused = { focusedIndex = index },
                            onClick = {
                                popTrigger++
                                coroutineScope.launch {
                                    delay(220) // Brief delay so the pop animation plays before launch
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (launchIntent != null) {
                                        context.startActivity(launchIntent)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GloopMascot(
    eyeTargetRatio: Float,
    popTrigger: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "idleMotion")

    // Idle floating bob
    val idleOffsetY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleOffset"
    )

    // Gentle idle wobble
    val idleRotation by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleRotation"
    )

    // Periodic blink
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

    // Smooth pupil follow transition (maps horizontal focus to pupil offset)
    val pupilOffsetX by animateFloatAsState(
        targetValue = (eyeTargetRatio - 0.5f) * 10f, // Shifts eye left/right
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pupilX"
    )

    // Click/Launch Pop Reaction
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
        modifier = Modifier
            .offset(y = idleOffsetY.dp)
            .rotate(idleRotation)
            .scale(popScale.value)
            .size(130.dp)
    ) {
        // Main Gloop Slime Body
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

        // Eyes, Pupils, and Reactive Smile Canvas
        Canvas(modifier = Modifier.size(100.dp)) {
            val eyeWidth = 7.dp.toPx()
            val eyeHeight = (7.dp.toPx()) * blinkProgress
            val pupilShift = pupilOffsetX.dp.toPx()
            val lookDownY = 3.dp.toPx() // Gloop is on top, looking down toward the bottom dock

            // Left Eye Sclera
            drawOval(
                color = Color.White,
                topLeft = Offset(size.width * 0.33f - eyeWidth, size.height * 0.40f - eyeHeight),
                size = Size(eyeWidth * 2, eyeHeight * 2)
            )
            // Left Pupil (follows selection)
            if (blinkProgress > 0.35f) {
                drawCircle(
                    color = Color.Black,
                    radius = eyeWidth * 0.52f,
                    center = Offset(size.width * 0.33f + pupilShift, size.height * 0.40f + lookDownY)
                )
            }

            // Right Eye Sclera
            drawOval(
                color = Color.White,
                topLeft = Offset(size.width * 0.67f - eyeWidth, size.height * 0.40f - eyeHeight),
                size = Size(eyeWidth * 2, eyeHeight * 2)
            )
            // Right Pupil (follows selection)
            if (blinkProgress > 0.35f) {
                drawCircle(
                    color = Color.Black,
                    radius = eyeWidth * 0.52f,
                    center = Offset(size.width * 0.67f + pupilShift, size.height * 0.40f + lookDownY)
                )
            }

            // Smile (opens into an excited 'O' or big grin when popping)
            val isPopping = popScale.value > 1.05f
            if (isPopping) {
                drawOval(
                    color = Color(0xFF064E3B),
                    topLeft = Offset(size.width * 0.42f, size.height * 0.55f),
                    size = Size(size.width * 0.16f, size.height * 0.18f)
                )
            } else {
                drawArc(
                    color = Color(0xFF064E3B),
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.38f, size.height * 0.52f),
                    size = Size(size.width * 0.24f, size.height * 0.16f),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
    }
}

@Composable
fun AppDockTile(
    app: AppModel,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tileScale"
    )

    val backgroundColor = if (isFocused) Color(0xFF3B82F6) else Color(0xFF1E293B)
    val textColor = if (isFocused) Color.White else Color(0xFFCBD5E1)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 150.dp, height = 96.dp)
            .scale(scale)
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onFocused()
                    onClick()
                }
            )
            .padding(12.dp)
    ) {
        Text(
            text = app.label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

