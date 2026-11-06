package com.example.glooplauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
        val preferredKeywords = listOf(
            listOf("now tv", "nowtv", "sky.now"),
            listOf("prime video", "amazon.video", "amazon.avod", "amazon instant video"),
            listOf("disney", "disneyplus"),
            listOf("spotify"),
            listOf("paramount", "cbs.app", "cbs"),
            listOf("netflix")
        )

        val matchedFavorites = mutableListOf<AppModel>()
        for (keywords in preferredKeywords) {
            val found = sortedApps.firstOrNull { app ->
                val labelLower = app.label.lowercase(Locale.getDefault())
                val packageLower = app.packageName.lowercase(Locale.getDefault())
                keywords.any { k -> labelLower.contains(k) || packageLower.contains(k) }
            }
            if (found != null && !matchedFavorites.contains(found)) {
                matchedFavorites.add(found)
            }
        }

        val fallbackApps = sortedApps.filter { !matchedFavorites.contains(it) }
        (matchedFavorites + fallbackApps).take(6)
    }

    var isMusicPlaying by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        while (true) {
            isMusicPlaying = audioManager?.isMusicActive == true
            delay(1000)
        }
    }

    var isDrawerOpen by remember { mutableStateOf(false) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var popTrigger by remember { mutableIntStateOf(0) }

    val drawerFirstItemFocusRequester = remember { FocusRequester() }
    val homeFirstItemFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = isDrawerOpen) {
        isDrawerOpen = false
    }

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 28.dp)
            ) {
                ClockHeader(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 8.dp, start = 8.dp)
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 16.dp)
                ) {
                    val total = if (favoriteApps.isNotEmpty()) favoriteApps.size else 1
                    val eyeTargetRatio = (focusedIndex.toFloat() / (total - 1).coerceAtLeast(1))
                    GloopMascot(
                        eyeTargetRatio = eyeTargetRatio,
                        popTrigger = popTrigger,
                        isDancing = isMusicPlaying
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                ) {
                    if (isMusicPlaying) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 14.dp, start = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xE61E293B))
                                .border(2.dp, Color(0xFF10B981), RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Spotify Audio Active",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        itemsIndexed(favoriteApps) { index, app ->
                            AppIconTile(
                                app = app,
                                width = 155.dp,
                                height = 90.dp,
                                focusRequester = if (index == 0) homeFirstItemFocusRequester else null,
                                onFocused = { focusedIndex = index },
                                onClick = { launchApp(app.packageName) },
                                onKeyCode = { keyCode ->
                                    if (keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN) {
                                        isDrawerOpen = true
                                        coroutineScope.launch {
                                            delay(100)
                                            drawerFirstItemFocusRequester.requestFocus()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isDrawerOpen,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(220)
                ) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xF8020617),
                                    Color(0xFF090D1A)
                                )
                            )
                        )
                        .padding(horizontal = 48.dp, vertical = 36.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(6),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(sortedApps) { index, app ->
                            val isTopRow = index < 6
                            AppIconTile(
                                app = app,
                                width = 150.dp,
                                height = 90.dp,
                                focusRequester = if (index == 0) drawerFirstItemFocusRequester else null,
                                onFocused = {},
                                onClick = {
                                    isDrawerOpen = false
                                    launchApp(app.packageName)
                                },
                                onKeyCode = { keyCode ->
                                    if (keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP && isTopRow) {
                                        isDrawerOpen = false
                                        coroutineScope.launch {
                                            delay(100)
                                            homeFirstItemFocusRequester.requestFocus()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppIconTile(
    app: AppModel,
    width: Dp,
    height: Dp,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onKeyCode: ((Int) -> Boolean)? = null
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

    val borderColor = if (isFocused) Color(0xFF60A5FA) else Color.Transparent

    val appBitmap = remember(app.packageName) {
        try {
            app.icon?.toBitmap(width = 360, height = 210)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E293B))
            .border(3.dp, borderColor, RoundedCornerShape(14.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && onKeyCode != null) {
                    onKeyCode(keyEvent.nativeKeyEvent.keyCode)
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onFocused()
                    onClick()
                }
            )
    ) {
        if (appBitmap != null) {
            Image(
                bitmap = appBitmap,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
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
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = currentDayAndDate,
            color = Color(0xFF94A3B8),
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
fun GloopMascot(
    eyeTargetRatio: Float,
    popTrigger: Int,
    isDancing: Boolean = false
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
            animation = tween(3600, easing = LinearEasing),
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
                    color = Color.Black,
                    radius = eyeWidth * 0.52f,
                    center = Offset(size.width * 0.67f + pupilShift, size.height * 0.40f + lookDownY)
                )
            }

            val isExcited = popScale.value > 1.05f || isDancing
            if (isExcited) {
                drawOval(
                    color = Color(0xFF064E3B),
                    topLeft = Offset(size.width * 0.41f, size.height * 0.54f),
                    size = Size(size.width * 0.18f, size.height * 0.19f)
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

