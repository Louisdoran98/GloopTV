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
                    return
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

    val activePlayback by GloopNotificationListener.currentMedia.collectAsState()

    var isAudioActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val component = ComponentName(context, GloopNotificationListener::class.java)

        while (true) {
            isAudioActive = audioManager?.isMusicActive == true
            // Periodically check sessions directly in case service callback was delayed
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

                        // Force state update through listener companion
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
                