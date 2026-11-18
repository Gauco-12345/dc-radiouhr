package de.datacreate.radiouhr

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build

/**
 * Zeigt in der Benachrichtigungsleiste (und auf dem Sperrbildschirm / Kopfhörer-Tasten),
 * was gerade läuft – mit Pause/Weiter-Taste.
 */
class NowPlaying(private val a: Activity, private val onToggle: () -> Unit, private val onStop: () -> Unit) {
    private val nm = a.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val session = MediaSession(a, "DC Radiouhr")
    private var lastKey = ""
    private var shown = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                ACT_TOGGLE -> onToggle()
                ACT_STOP -> onStop()
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Wiedergabe", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Zeigt, welcher Sender, Podcast oder Titel gerade läuft"
                setShowBadge(false)
            })
        }
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = onToggle()
            override fun onPause() = onToggle()
            override fun onStop() = onStop()
        })
        val f = IntentFilter().apply { addAction(ACT_TOGGLE); addAction(ACT_STOP) }
        if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED)
        else a.registerReceiver(receiver, f)
    }

    private fun pi(action: String, code: Int): PendingIntent {
        val fl = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getBroadcast(a, code, Intent(action).setPackage(a.packageName), fl)
    }

    /** title = was läuft, sub = Sender/Podcast; active=false → Benachrichtigung entfernen. */
    fun update(active: Boolean, playing: Boolean, title: String, sub: String, art: Bitmap?) {
        if (!active) { clear(); return }
        val key = "$playing|$title|$sub|${art?.hashCode() ?: 0}"
        if (key == lastKey && shown) return
        lastKey = key
        try {
            session.setMetadata(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, sub)
                .apply { if (art != null) putBitmap(MediaMetadata.METADATA_KEY_ART, art) }
                .build())
            session.setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build())
            session.isActive = true

            val open = PendingIntent.getActivity(a, 0,
                Intent(a, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
            @Suppress("DEPRECATION")
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(a, CH) else Notification.Builder(a).setPriority(Notification.PRIORITY_LOW)
            @Suppress("DEPRECATION")
            b.setSmallIcon(R.drawable.ic_play)
                .setContentTitle(title)
                .setContentText(sub)
                .setContentIntent(open)
                .setOngoing(playing)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDeleteIntent(pi(ACT_STOP, 2))
                .addAction(Notification.Action.Builder(
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (playing) "Pause" else "Weiter", pi(ACT_TOGGLE, 1)).build())
                .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Aus", pi(ACT_STOP, 2)).build())
                .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1))
            if (art != null) b.setLargeIcon(art)
            nm.notify(ID, b.build())
            shown = true
        } catch (_: Exception) { }
    }

    fun clear() {
        if (!shown) return
        shown = false
        lastKey = ""
        try {
            nm.cancel(ID)
            session.isActive = false
        } catch (_: Exception) { }
    }

    fun release() {
        clear()
        try { a.unregisterReceiver(receiver) } catch (_: Exception) { }
        session.release()
    }

    companion object {
        private const val CH = "playback"
        private const val ID = 42
        private const val ACT_TOGGLE = "de.datacreate.radiouhr.TOGGLE"
        private const val ACT_STOP = "de.datacreate.radiouhr.STOP"
    }
}
