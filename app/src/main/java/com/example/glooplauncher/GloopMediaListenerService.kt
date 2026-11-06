package com.example.glooplauncher

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveMediaState(
    val title: String = "",
    val artist: String = "",
    val art: Bitmap? = null,
    val isPlaying: Boolean = false,
    val controller: MediaController? = null
)

class GloopMediaListenerService : NotificationListenerService() {

    companion object {
        private val _currentMedia = MutableStateFlow<ActiveMediaState?>(null)
        val currentMedia = _currentMedia.asStateFlow()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        updateMediaSession(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val extras = sbn?.notification?.extras ?: return
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
        if (token != null) {
            _currentMedia.value = null
        }
    }

    private fun updateMediaSession(sbn: StatusBarNotification?) {
        val extras = sbn?.notification?.extras ?: return
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(android.app.Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        } ?: return

        try {
            val controller = MediaController(this, token)
            val metadata = controller.metadata
            val playbackState = controller.playbackState

            val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
            val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
                ?: ""
            val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
                ?: ""
            val art = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

            if (title.isNotEmpty()) {
                _currentMedia.value = ActiveMediaState(
                    title = title,
                    artist = artist,
                    art = art,
                    isPlaying = isPlaying,
                    controller = controller
                )
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }
}
