package com.example.glooplauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
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

data class TrackInfo(
    val title: String,
    val artist: String,
    val art: Bitmap? = null,
    val isPlaying: Boolean = false,
    val onPlayPause: () -> Unit = {},
    val onSkipNext: () -> Unit = {},
    val onSkipPrev: () -> Unit = {}
)

enum class HatType {
    NONE,
    PARTY,
    CHRISTMAS,
    SCROOGE_NIGHTCAP
}

@Composable
fun GloopTVApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val sortedApps = remember {
        val pm = context.packageManager

        val standardIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val standardApps = pm.queryIntentActivities(standardIntent, 0)

        val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val leanbackApps = pm.queryIntentActivities(leanbackIntent, 0)

        (standardApps + leanbackApps)
            .filter { it.activityInfo.packageName != context.packageName }
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
            listOf("paramount", "cbs.app", "cbs")
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
        (matchedFavorites + fallbackApps).take(5)
    }

    var activeTrack by remember { mutableStateOf<TrackInfo?>(null) }
    LaunchedEffect(Unit) {
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        while (true) {
            try {
                if (sessionManager != null) {
                    val getActiveMethod = sessionManager.javaClass.getMethod(
                        "getActiveSessions",
                        ComponentName::class.java
                    )
                    val rawList = getActiveMethod.invoke(sessionManager, null) as? List<*>
                    var foundTrack: TrackInfo? = null

                    if (!rawList.isNullOrEmpty()) {
                        for (item in rawList) {
                            if (item == null) continue
                            val getPlaybackState = item.javaClass.getMethod("getPlaybackState")
                            val state = getPlaybackState.invoke(item) as? PlaybackState
                            val isPlaying = state?.state == PlaybackState.STATE_PLAYING
                            val isPaused = state?.state == PlaybackState.STATE_PAUSED

                            if (isPlaying || isPaused) {
                                val getMetadata = item.javaClass.getMethod("getMetadata")
                                val metadata = getMetadata.invoke(item) as? MediaMetadata
                                val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                                val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                                val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

                                val getControls = item.javaClass.getMethod("getTransportControls")
                                val controls = getControls.invoke(item)

                                foundTrack = TrackInfo(
                                    title = if (title.isNotEmpty()) title else "Spotify Playback",
                                    artist = artist,
                                    art = art,
                                    isPlaying = isPlaying,
                                    onPlayPause = {
                                        try {
                                            if (isPlaying) {
                                                controls.javaClass.getMethod("pause").invoke(controls)
                                            } else {
                                                controls.javaClass.getMethod("play").invoke(controls)
                                            }
                                        } catch (e: Exception) {}
                                    },
                                    onSkipNext = {
                                        try {
                                            controls.javaClass.getMethod("skipToNext").invoke(controls)
                                        } catch (e: Exception) {}
                                    },
                                    onSkipPrev = {
                                        try {
                                            controls.javaClass.getMethod("skipToPrevious").invoke(controls)
                                        } catch (e: Exception) {}
                                    }
                                )
                                break
                            }
                        }
                    }
                    activeTrack = foundTrack
                }
            } catch (e: Exception) {
                activeTrack = null
            }
            delay(1500)
        }
    }

    // Hat and sleep state logic
    var currentHat by remember { mutableStateOf(HatType.NONE) }
    var isSleeping by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val month = cal.get(Calendar.MONTH) // 0-based: Jan=0, May=4, Nov=10, Dec=11
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val hour = cal.get(Calendar.HOUR_OF_DAY) // 24-hr format

            val nightTime = hour >= 21 || hour < 6
            isSleeping = nightTime

            currentHat = when {
                nightTime -> HatType.SCROOGE_NIGHTCAP
                (month == Calendar.MAY && day == 16) || (month == Calendar.NOVEMBER && day == 27) -> HatType.PARTY
                month == Calendar.DECEMBER && day in 1..26 -> HatType.CHRISTMAS
                else -> HatType.NONE
            }
            delay(15000)
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
                ?: context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
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
                        .padding(top = 16.dp, end = 24.dp)
                ) {
                    val total = if (favoriteApps.isNotEmpty()) favoriteApps.size else 1
                    val eyeTargetRatio = (focusedIndex.toFloat() / (total - 1).coerceAtLeast(1))
                    GloopMascot(
                        eyeTargetRatio = eyeTargetRatio,
                        popTrigger = popTrigger,
                        isDancing = activeTrack?.isPlaying == true && !isSleeping,
                        hatType = currentHat,
                        isSleeping = isSleeping
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    activeTrack?.let { track ->
                        NowPlayingWidget(
                            track = track,
                            modifier = Modifier.padding(bottom = 12.dp, start = 14.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        favoriteApps.forEachIndexed { index, app ->
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
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(sortedApps) { index, app ->
                            val isTopRow = index < 5
                            AppIconTile(
                                app = app,
                                width = 155.dp,
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
fun NowPlayingWidget(
    track: TrackInfo,
    modifier: Modifier = Modifier
) {
    val artBitmap = remember(track.art) { track.art?.asImageBitmap() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE61E293B))
            .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (artBitmap != null) {
            Image(
                bitmap = artBitmap,
                contentDescription = "Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 210.dp)
        ) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.artist.isNotEmpty()) {
                Text(
                    text = track.artist,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Transport buttons: Prev, Play/Pause, Next
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaControlButton(
                onClick = track.onSkipPrev,
                label = "⏮"
            )

            MediaControlButton(
                onClick = track.onPlayPause,
                label = if (track.isPlaying) "⏸" else "▶",
                isPrimary = true
            )

            MediaControlButton(
                onClick = track.onSkipNext,
                label = "⏭"
            )
        }
    }
}

@Composable
fun MediaControlButton(
    onClick: () -> Unit,
    label: String,
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.18f else 1.0f,
        label = "btnScale"
    )

    val bgColor = when {
        isFocused -> Color(0xFF10B981)
        isPrimary -> Color(0xFF334155)
        else -> Color(0xFF1E293B)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = CircleShape
            )
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
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
    isDancing: Boolean = false,
    hatType: HatType = HatType.NONE,
    isSleeping: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascotMotion")
    val animDuration = when {
        isSleeping -> 2600
        isDancing -> 480
        else -> 1400
    }

    val idleOffsetY by infiniteTransition.animateFloat(
        initialValue = if (isSleeping) -4f else (if (isDancing) -26f else -12f),
        targetValue = if (isSleeping) 4f else (if (isDancing) 18f else 12f),
        animationSpec = infiniteRepeatable(
            animation = tween(animDuration, easing = if (isDancing) FastOutLinearInEasing else EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleOffset"
    )

    val idleRotation by infiniteTransition.animateFloat(
        initialValue = if (isSleeping) -1f else (if (isDancing) -12f else -4f),
        targetValue = if (isSleeping) 1f else (if (isDancing) 12f else 4f),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isDancing) animDuration * 2 else animDuration + 400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleRotation"
    )

    val danceScaleY by infiniteTransition.animateFloat(
        initialValue = if (isDancing) 0.88f else (if (isSleeping) 0.98f else 1.0f),
        targetValue = if (isDancing) 1.12f else (if (isSleeping) 1.02f else 1.0f),
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
        if (popTrigger > 0 && !isSleeping) {
            popScale.animateTo(1.32f, animationSpec = tween(100, easing = FastOutLinearInEasing))
            popScale.animateTo(0.85f, animationSpec = tween(90, easing = LinearOutSlowInEasing))
            popScale.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .offset(y = idleOffsetY.dp)
            .rotate(idleRotation)
            .scale(scaleX = popScale.value, scaleY = popScale.value * danceScaleY)
            .size(150.dp)
    ) {
        // Mascot Main Body
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
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

        // Mascot Face & Expressions
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .size(100.dp)
        ) {
            val eyeWidth = 7.dp.toPx()

            if (isSleeping) {
                // Closed sleeping curved eyes
                drawArc(
                    color = Color(0xFF064E3B),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.33f - eyeWidth, size.height * 0.40f),
                    size = Size(eyeWidth * 2, eyeWidth),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawArc(
                    color = Color(0xFF064E3B),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.67f - eyeWidth, size.height * 0.40f),
                    size = Size(eyeWidth * 2, eyeWidth),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Soft smiling sleeping mouth
                drawArc(
                    color = Color(0xFF064E3B),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.42f, size.height * 0.55f),
                    size = Size(size.width * 0.16f, size.height * 0.10f),
                    style = Stroke(width = 3.dp.toPx())
                )
            } else {
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

        // Animated Sleeping Zzz
        if (isSleeping) {
            val zzzOffset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -16f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "zzzAnim"
            )
            Text(
                text = "z z Z",
                color = Color(0x99FFFFFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (10 + zzzOffset).dp)
            )
        }

        // Mascot Headwear
        if (hatType != HatType.NONE) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 110.dp, height = 55.dp)
                    .offset(y = (-6).dp)
            ) {
                when (hatType) {
                    HatType.PARTY -> {
                        // Cone Party Hat
                        val hatPath = Path().apply {
                            moveTo(size.width * 0.50f, 4f)
                            lineTo(size.width * 0.28f, size.height * 0.88f)
                            lineTo(size.width * 0.72f, size.height * 0.88f)
                            close()
                        }
                        drawPath(path = hatPath, color = Color(0xFFF59E0B))

                        // Hat Pom-pom
                        drawCircle(
                            color = Color(0xFFEC4899),
                            radius = 6.dp.toPx(),
                            center = Offset(size.width * 0.50f, 4f)
                        )
                    }

                    HatType.CHRISTMAS -> {
                        // Red Santa Hat
                        val santaPath = Path().apply {
                            moveTo(size.width * 0.24f, size.height * 0.74f)
                            cubicTo(
                                size.width * 0.30f, size.height * 0.15f,
                                size.width * 0.65f, size.height * 0.10f,
                                size.width * 0.82f, size.height * 0.35f
                            )
                            lineTo(size.width * 0.76f, size.height * 0.74f)
                            close()
                        }
                        drawPath(path = santaPath, color = Color(0xFFDC2626))

                        // White Fluffy Trim
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(size.width * 0.20f, size.height * 0.68f),
                            size = Size(size.width * 0.60f, size.height * 0.22f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                        )

                        // Santa Pom-pom
                        drawCircle(
                            color = Color.White,
                            radius = 7.dp.toPx(),
                            center = Offset(size.width * 0.84f, size.height * 0.38f)
                        )
                    }

                    HatType.SCROOGE_NIGHTCAP -> {
                        // Slouching Scrooge Sleeping Nightcap
                        val nightcapPath = Path().apply {
                            moveTo(size.width * 0.24f, size.height * 0.75f)
                            cubicTo(
                                size.width * 0.28f, size.height * 0.05f,
                                size.width * 0.75f, size.height * 0.05f,
                                size.width * 0.86f, size.height * 0.65f
                            )
                            lineTo(size.width * 0.76f, size.height * 0.75f)
                            close()
                        }
                        drawPath(path = nightcapPath, color = Color(0xFF1E3A8A)) // Midnight blue cap

                        // White fuzzy brim
                        drawRoundRect(
                            color = Color(0xFFE2E8F0),
                            topLeft = Offset(size.width * 0.20f, size.height * 0.70f),
                            size = Size(size.width * 0.60f, size.height * 0.20f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                        )

                        // Hanging pom-pom on the side
                        drawCircle(
                            color = Color(0xFFE2E8F0),
                            radius = 6.dp.toPx(),
                            center = Offset(size.width * 0.86f, size.height * 0.68f)
                        )
                    }

                    HatType.NONE -> {}
                }
            }
        }
    }
}
