package com.example.glooplauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ActivePlayback(
    val title: String = "",
    val artist: String = "",
    val art: Bitmap? = null,
    val isPlaying: Boolean = false,
    val controller: MediaController? = null
)

class GloopNotificationListener : NotificationListenerService() {
    companion object {
        private val _currentMedia = MutableStateFlow<ActivePlayback?>(null)
        val currentMedia = _currentMedia.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        syncActiveSessions()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        syncActiveSessions()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        syncActiveSessions()
    }

    fun syncActiveSessions() {
        try {
            val sessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = ComponentName(this, GloopNotificationListener::class.java)
            val controllers = sessionManager?.getActiveSessions(component)

            if (!controllers.isNullOrEmpty()) {
                val active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    ?: controllers.firstOrNull()

                if (active != null) {
                    val metadata = active.metadata
                    val playbackState = active.playbackState
                    val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                        ?: active.queueTitle?.toString()
                        ?: "Playing on TV"
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

                    _currentMedia.value = ActivePlayback(
                        title = title,
                        artist = artist,
                        art = art,
                        isPlaying = isPlaying,
                        controller = active
                    )
                }
            }
        } catch (e: Exception) {}
    }
}

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

    val sortedApps: List<AppModel> = remember {
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

    val favoriteApps: List<AppModel> = remember(sortedApps) {
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

    val activePlayback by GloopNotificationListener.currentMedia.collectAsState()

    var isAudioActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val component = ComponentName(context, GloopNotificationListener::class.java)

        while (true) {
            isAudioActive = audioManager?.isMusicActive == true
            try {
                val controllers = sessionManager?.getActiveSessions(component)
                if (!controllers.isNullOrEmpty()) {
                    val active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                        ?: controllers.firstOrNull()
                    if (active != null) {
                        val metadata = active.metadata
                        val playbackState = active.playbackState
                        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                            ?: active.queueTitle?.toString()
                            ?: "Spotify"
                        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                        val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

                        val field = GloopNotificationListener::class.java.getDeclaredField("_currentMedia")
                        field.isAccessible = true
                        @Suppress("UNCHECKED_CAST")
                        val stateFlow = field.get(null) as? MutableStateFlow<ActivePlayback?>
                        stateFlow?.value = ActivePlayback(
                            title = title,
                            artist = artist,
                            art = art,
                            isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING,
                            controller = active
                        )
                    }
                }
            } catch (e: Exception) {}

            delay(1500)
        }
    }

    val isMusicPlaying = activePlayback?.isPlaying == true || isAudioActive

    var currentHat by remember { mutableStateOf(HatType.NONE) }
    var isSleeping by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            val month = cal.get(Calendar.MONTH)
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val hour = cal.get(Calendar.HOUR_OF_DAY)

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
                        isDancing = isMusicPlaying && !isSleeping,
                        hatType = currentHat,
                        isSleeping = isSleeping
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    if (activePlayback != null) {
                        NowPlayingWidget(
                            playback = activePlayback!!,
                            modifier = Modifier.padding(bottom = 12.dp, start = 14.dp)
                        )
                    } else if (isMusicPlaying) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 14.dp, start = 14.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xE61E293B))
                                .border(2.dp, Color(0xFF10B981), RoundedCornerShape(14.dp))
                                .clickable {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    } catch (e: Exception) {}
                                }
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        favoriteApps.forEachIndexed { index: Int, app: AppModel ->
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
                        itemsIndexed(sortedApps) { index: Int, app: AppModel ->
                            val isTopRow = index < 5
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
fun NowPlayingWidget(
    playback: ActivePlayback,
    modifier: Modifier = Modifier
) {
    val artBitmap = remember(playback.art) { playback.art?.asImageBitmap() }

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
                text = playback.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (playback.artist.isNotEmpty()) {
                Text(
                    text = playback.artist,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaControlButton(
                onClick = {
                    try {
                        playback.controller?.transportControls?.skipToPrevious()
                    } catch (e: Exception) {}
                },
                label = "⏮"
            )

            MediaControlButton(
                onClick = {
                    try {
                        if (playback.isPlaying) {
                            playback.controller?.transportControls?.pause()
                        } else {
                            playback.controller?.transportControls?.play()
                        }
                    } catch (e: Exception) {}
                },
                label = if (playback.isPlaying) "⏸" else "▶",
                isPrimary = true
            )

            MediaControlButton(
                onClick = {
                    try {
                        playback.controller?.transportControls?.skipToNext()
                    } catch (e: Exception) {}
                },
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

        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .size(100.dp)
        ) {
            val eyeWidth = 7.dp.toPx()

            if (isSleeping) {
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

        if (hatType != HatType.NONE) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 110.dp, height = 55.dp)
                    .offset(y = (-6).dp)
            ) {
                when (hatType) {
                    HatType.PARTY -> {
                        val hatPath = Path().apply {
                            moveTo(size.width * 0.50f, 4f)
                            lineTo(size.width * 0.28f, size.height * 0.88f)
                            lineTo(size.width * 0.72f, size.height * 0.88f)
                            close()
                        }
                        drawPath(path = hatPath, color = Color(0xFFF59E0B))
                        drawCircle(
                            color = Color(0xFFEC4899),
                            radius = 6.dp.toPx(),
                            center = Offset(size.width * 0.50f, 4f)
                        )
                    }

                    HatType.CHRISTMAS -> {
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
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(size.width * 0.20f, size.height * 0.68f),
                            size = Size(size.width * 0.60f, size.height * 0.22f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 7.dp.toPx(),
                            center = Offset(size.width * 0.84f, size.height * 0.38f)
                        )
                    }

                    HatType.SCROOGE_NIGHTCAP -> {
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
                        drawPath(path = nightcapPath, color = Color(0xFF1E3A8A))
                        drawRoundRect(
                            color = Color(0xFFE2E8F0),
                            topLeft = Offset(size.width * 0.20f, size.height * 0.70f),
                            size = Size(size.width * 0.60f, size.height * 0.20f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                        )
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
