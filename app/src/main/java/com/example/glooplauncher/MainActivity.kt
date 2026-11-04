package com.example.glooplauncher

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val packageName: String,
    val icon: Drawable? = null
)

@Composable
fun GloopTVApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sortedApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        resolveInfos
            .map {
                val bannerOrIcon = it.activityInfo.loadBanner(pm) ?: it.loadIcon(pm)
                AppModel(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = bannerOrIcon
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    val favoriteApps = remember(sortedApps) {
        sortedApps.take(7)
    }

    var focusedIndex by remember { mutableIntStateOf(0) }
    var popTrigger by remember { mutableIntStateOf(0) }

    fun launchApp(packageName: String) {
        popTrigger++
        coroutineScope.launch {
            delay(220)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                // Top screen view (7 Favorites Dock + Clock + Gloop Mascot)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillParentMaxHeight()
                            .padding(28.dp)
                    ) {
                        ClockHeader(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 12.dp, start = 8.dp)
                        )

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 10.dp, end = 20.dp)
                        ) {
                            val total = if (favoriteApps.isNotEmpty()) favoriteApps.size else 1
                            val eyeTargetRatio = (focusedIndex.toFloat() / (total - 1).coerceAtLeast(1))
                            GloopMascot(
                                eyeTargetRatio = eyeTargetRatio,
                                popTrigger = popTrigger
                            )
                        }

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp, start = 4.dp, end = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "FAVOURITES",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.5.sp
                                )
                                Text(
                                    text = "Scroll down for all apps ↓",
                                    color = Color(0xFF64748B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(favoriteApps) { index, app ->
                                    AppTile(
                                        app = app,
                                        width = 150.dp,
                                        height = 110.dp,
                                        onFocused = { focusedIndex = index },
                                        onClick = { launchApp(app.packageName) }
                                    )
                                }
                            }
                        }
                    }
                }

                // All Apps (A-Z) Title Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 32.dp, end = 32.dp, top = 16.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = "ALL APPS (A-Z)",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp
                        )
                    }
                }

                // Grid of all apps
                item {
                    val rowCount = (sortedApps.size + 4) / 5
                    val gridHeight = (rowCount * 120).dp

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(gridHeight)
                            .padding(horizontal = 32.dp),
                        userScrollEnabled = false
                    ) {
                        itemsIndexed(sortedApps) { index, app ->
                            AppTile(
                                app = app,
                                width = 140.dp,
                                height = 105.dp,
                                onFocused = { focusedIndex = index % 7 },
                                onClick = { launchApp(app.packageName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    var currentTime by remember { mutableStateOf("") }
    var currentDayAndDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dayDateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDayAndDate = dayDateFormat.format(now)
            delay(1000)
        }
    }

    Column(modifier = modifier) {
        Text(
            text = currentTime,
            color = Color.White,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = currentDayAndDate,
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
fun GloopMascot(
    eyeTargetRatio: Float,
    popTrigger: Int
) {
    val infiniteTransition = rememberInfiniteTransition(label = "idleMotion")

    val idleOffsetY by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleOffset"
    )

    val idleRotation by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleRotation"
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
        modifier = Modifier
            .offset(y = idleOffsetY.dp)
            .rotate(idleRotation)
            .scale(popScale.value)
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

            // Left Eye
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

            // Right Eye
            drawOval(
                color = Color.White,
                topLeft = Offset(size.width * 0.67f - eyeWidth, size.height * 0.40f - eyeHeight),
                size = Size(eyeWidth * 2, eyeHeight * 2)
            )
            if (blinkProgress > 0.35f) {
                drawCircle(
                    color = Color.Black,
                    radius = eyeWidth * 0.52f,
                    center = Offset(size.width * 0.67f + pupilShift, size.height * 0.40f + lookDownY)
                )
            }

            // Mouth
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
fun AppTile(
    app: AppModel,
    width: Dp,
    height: Dp,
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

    val appBitmap = remember(app.packageName) {
        try {
            app.icon?.toBitmap(width = 96, height = 96)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = width, height = height)
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
            .padding(8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (appBitmap != null) {
                Image(
                    bitmap = appBitmap,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(
                text = app.label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
