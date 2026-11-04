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

data class MediaTrackInfo(
    val title: String,
    val artist: String,
    val art: Bitmap? = null,
    val isPlaying: Boolean = false,
    val controller: MediaController? = null
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

    var activeMedia by remember { mutableStateOf<MediaTrackInfo?>(null) }

    DisposableEffect(Unit) {
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val listenerComponent = ComponentName(context, GloopMediaListenerService::class.java)

        fun updateMedia() {
            try {
                val controllers = sessionManager?.getActiveSessions(listenerComponent)
                val activeController = controllers?.firstOrNull {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING ||
                    it.playbackState?.state == PlaybackState.STATE_PAUSED
                } ?: controllers?.firstOrNull()

                if (activeController != null) {
                    val metadata = activeController.metadata
                    val playbackState = activeController.playbackState
                    val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

                    val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                    val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                        ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

                    if (!title.isNullOrEmpty()) {
                        activeMedia = MediaTrackInfo(
                            title = title,
                            artist = artist,
                            art = art,
                            isPlaying = isPlaying,
                            controller = activeController
                        )
                    }
                } else {
                    activeMedia = null
                }
            } catch (e: Exception) {
                // Media listener access pending or inactive
            }
        }

        val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener {
            updateMedia()
        }

        try {
            sessionManager?.addOnActiveSessionsChangedListener(sessionListener, listenerComponent)
            updateMedia()
        } catch (e: Exception) {}

        onDispose {
            try {
                sessionManager?.removeOnActiveSessionsChangedListener(sessionListener)
            } catch (e: Exception) {}
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
                        isDancing = activeMedia?.isPlaying == true
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                ) {
                    activeMedia?.let { media ->
                        NowPlayingBar(
                            media = media,
                            onTogglePlayPause = {
                                if (media.isPlaying) {
                                    media.controller?.transportControls?.pause()
                                } else {
                                    media.controller?.transportControls?.play()
                                }
                            },
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        itemsIndexed(favoriteApps) { index, app ->
                            AppIconTile(
                                app = app,
                                width = 145.dp,
                                height = 86.dp,
                                focusRequester = if (index == 0) homeFirstItemFocusRequester else null,
                                onFocused = { focusedIndex = index },
                                onClick = { launchApp(app.packageName) },
                                onDirectionKey = { key ->
                                    if (key == Key.DirectionDown) {
                                        isDrawerOpen = true
                                        coroutineScope.launch {
                                            delay(100)
                                            drawerFirstItemFocusRequester.requestFocus()
                                        }
                                        true
                                    } else false
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
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(sortedApps) { index, app ->
                            val isTopRow = index < 6
                            AppIconTile(
                                app = app,
                                width = 145.dp,
                                height = 86.dp,
                                focusRequester = if (index == 0) drawerFirstItemFocusRequester else null,
                                onFocused = {},
                                onClick = {
                                    isDrawerOpen = false
                                    launchApp(app.packageName)
                                },
                                onDirectionKey = { key ->
                                    if (key == Key.DirectionUp && isTopRow) {
                                        isDrawerOpen = false
                                        coroutineScope.launch {
                                            delay(100)
                                            homeFirstItemFocusRequester.requestFocus()
                                        }
                                        true
                                    } else false
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
fun NowPlayingBar(
    media: MediaTrackInfo,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor = if (isFocused) Color(0xFF10B981) else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xD01E293B))
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onTogglePlayPause
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (media.art != null) {
            Image(
                bitmap = media.art.asImageBitmap(),
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.widthIn(max = 240.dp)) {
            Text(
                text = media.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = media.artist,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(0xFF334155))
        ) {
            Canvas(modifier = Modifier.size(14.dp)) {
                if (media.isPlaying) {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(3.5.dp.toPx(), 12.dp.toPx())
                    )
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(9.dp.toPx(), 1.dp.toPx()),
                        size = Size(3.5.dp.toPx(), 12.dp.toPx())
                    )
                } else {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(2.dp.toPx(), 1.dp.toPx())
                        lineTo(13.dp.toPx(), 7.dp.toPx())
                        lineTo(2.dp.toPx(), 13.dp.toPx())
                        close()
                    }
                    drawPath(path = path, color = Color.White)
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
    onDirectionKey: ((Key) -> Boolean)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) {
            onFocused()
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.14f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tileScale"
    )

    val borderColor = if (isFocused) Color(0xFF60A5FA) else Color.Transparent

    val appBitmap = remember(app.packageName) {
        try {
            app.icon?.toBitmap(width = 320, height = 180)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = width, height = height)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1E293B))
            .border(3.dp, borderColor, RoundedCornerShape(14.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable(interactionSource = interactionSource)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && onDirectionKey != null) {
                    onDirectionKey(keyEvent.key)
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
            .padding(8.dp)
    ) {
        if (appBitmap != null) {
            Image(
                bitmap = appBitmap,
                contentDescription = app.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ClockHeader(modifier: Modifier = Modifier) {
    var curre
