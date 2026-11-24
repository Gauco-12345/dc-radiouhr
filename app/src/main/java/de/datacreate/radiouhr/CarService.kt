package de.datacreate.radiouhr

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors

/**
 * Android Auto: Sender, Podcast-Abos und heruntergeladene Folgen im Auto-Display.
 * Eigener Player, damit es auch ohne geöffnete App läuft.
 */
class CarService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private var session: MediaLibrarySession? = null
    private val bg = Executors.newSingleThreadExecutor()
    private val prefs by lazy { Wx.prefs(this) }
    private val main by lazy { android.os.Handler(mainLooper) }

    companion object {
        private const val ROOT = "root"
        private const val RADIO = "radio"
        private const val PODS = "pods"
        private const val DLS = "dls"
        // Darstellung im Auto: Kacheln statt Liste
        private const val STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val GRID = 2
        private const val LIST = 1
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) savePos() }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                epOf(id)?.let { (pod, ep) -> Podcast.saveLast(prefs, pod, ep) }
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) epOf(player.currentMediaItem?.mediaId ?: "")?.let { Podcast.setDone(prefs, it.second, true) }
            }
        })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaLibrarySession.Builder(this, player, Callback())
            .setSessionActivity(open)
            .build()
        // Position regelmäßig sichern
        main.postDelayed(object : Runnable {
            override fun run() {
                if (player.isPlaying) savePos()
                main.postDelayed(this, 15_000)
            }
        }, 15_000)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        savePos()
        main.removeCallbacksAndMessages(null)
        session?.release()
        session = null
        player.release()
        bg.shutdown()
        super.onDestroy()
    }

    private fun savePos() {
        val id = player.currentMediaItem?.mediaId ?: return
        val e = epOf(id) ?: return
        val pos = player.currentPosition
        if (pos > 0) Podcast.setPos(prefs, e.second, pos)
    }

    // ------------------------------------------------------------ Inhalte

    private fun art(u: String): Uri? = if (u.startsWith("http")) Uri.parse(u) else null

    private fun folder(id: String, title: String, type: Int, grid: Boolean = false, art: String = ""): MediaItem {
        val extras = Bundle().apply { putInt(STYLE_PLAYABLE, if (grid) GRID else LIST) }
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(type).setArtworkUri(art(art)).setExtras(extras).build()
        ).build()
    }

    private fun stations(): List<Station> = try {
        val a = JSONArray(prefs.getString("stations", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Station(o.getString("name"), o.getString("url"), o.optString("icon", "")) }
    } catch (_: Exception) { emptyList() }

    private fun stationItem(i: Int, s: Station): MediaItem = MediaItem.Builder()
        .setMediaId("r:$i")
        .setUri(s.url)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(s.name).setStation(s.name).setArtist("Radio")
            .setArtworkUri(art(s.icon)).setIsBrowsable(false).setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION).build())
        .build()

    private fun epItem(id: String, pod: Podcast.Pod, ep: Podcast.Ep): MediaItem {
        val f = Podcast.localFile(this, ep)
        val uri = if (Podcast.isDownloaded(this, ep)) Uri.fromFile(f) else Uri.parse(ep.url)
        val done = Podcast.done(prefs, ep)
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder()
                .setTitle(ep.title)
                .setArtist(pod.title)
                .setAlbumTitle(pod.title)
                .setDisplayTitle(ep.title)
                .setSubtitle((if (done) "✓ " else "") + pod.title)
                .setArtworkUri(art(ep.art.ifEmpty { pod.art }))
                .setDurationMs(if (ep.durationSec > 0) ep.durationSec * 1000 else null)
                .setIsBrowsable(false).setIsPlayable(true)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE).build())
            .build()
    }

    /** "e:<feed>\n<url>" oder "d:<url>" → Podcast und Folge. */
    private fun epOf(id: String): Pair<Podcast.Pod, Podcast.Ep>? {
        try {
            if (id.startsWith("d:")) {
                val url = id.substring(2)
                return Podcast.downloads(this, prefs).firstOrNull { it.second.url == url }
            }
            if (id.startsWith("e:")) {
                val feed = id.substring(2).substringBefore('\n')
                val url = id.substringAfter('\n')
                val f = Podcast.cachedFeed(this, feed) ?: return null
                val sub = Podcast.subs(prefs).firstOrNull { it.feed == feed }
                val pod = if (f.first.art.isEmpty() && sub != null) sub else f.first
                return f.second.firstOrNull { it.url == url }?.let { pod to it }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun children(parent: String): List<MediaItem> = when {
        parent == ROOT -> listOf(
            folder(RADIO, "Radio", MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS, grid = true),
            folder(PODS, "Podcasts", MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS, grid = false),
            folder(DLS, "Heruntergeladen", MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS, grid = false)
        )
        parent == RADIO -> stations().mapIndexed { i, s -> stationItem(i, s) }
        parent == PODS -> Podcast.subs(prefs).map { p ->
            folder("p:" + p.feed, p.title, MediaMetadata.MEDIA_TYPE_PODCAST, art = p.art)
        }
        parent == DLS -> Podcast.downloads(this, prefs).map { (pod, ep) -> epItem("d:" + ep.url, pod, ep) }
        parent.startsWith("p:") -> {
            val feed = parent.substring(2)
            val sub = Podcast.subs(prefs).firstOrNull { it.feed == feed }
            val f = Podcast.cachedFeed(this, feed) ?: try { Podcast.feed(this, feed, true) } catch (_: Exception) { null }
            if (f == null) emptyList()
            else {
                val pod = if (f.first.art.isEmpty() && sub != null) sub else f.first
                f.second.take(60).map { epItem("e:$feed\n${it.url}", pod, it) }
            }
        }
        else -> emptyList()
    }

    /** Ein Eintrag nach Kennung (zum Abspielen). */
    private fun resolve(id: String): MediaItem? {
        if (id.startsWith("r:")) {
            val i = id.substring(2).toIntOrNull() ?: return null
            return stations().getOrNull(i)?.let { stationItem(i, it) }
        }
        val e = epOf(id) ?: return null
        return epItem(id, e.first, e.second)
    }

    /** Sprachbefehl „Spiele … in DC Radiouhr“: zuerst Sender, dann Podcast-Abo (neueste Folge). */
    private fun search(q: String): List<MediaItem> {
        val low = q.lowercase().trim()
        val st = stations()
        if (low.isEmpty()) return st.mapIndexed { i, s -> stationItem(i, s) }.take(1)
        st.indexOfFirst { it.name.lowercase().contains(low) || low.contains(it.name.lowercase()) }.let {
            if (it >= 0) return listOf(stationItem(it, st[it]))
        }
        for (p in Podcast.subs(prefs)) {
            if (p.title.lowercase().contains(low) || low.contains(p.title.lowercase())) {
                val f = Podcast.cachedFeed(this, p.feed) ?: continue
                val ep = f.second.firstOrNull { !Podcast.done(prefs, it) } ?: f.second.firstOrNull() ?: continue
                return listOf(epItem("e:${p.feed}\n${ep.url}", p, ep))
            }
        }
        return emptyList()
    }

    private fun <T> async(work: () -> T): ListenableFuture<T> {
        val f = SettableFuture.create<T>()
        bg.execute { try { f.set(work()) } catch (e: Throwable) { f.setException(e) } }
        return f
    }

    private inner class Callback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                                      params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> {
            val extras = Bundle().apply {
                putInt(STYLE_BROWSABLE, GRID)
                putInt(STYLE_PLAYABLE, LIST)
            }
            val root = MediaItem.Builder().setMediaId(ROOT).setMediaMetadata(
                MediaMetadata.Builder().setTitle("DC Radiouhr").setIsBrowsable(true).setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, LibraryParams.Builder().setExtras(extras).build()))
        }

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
                                   page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            async {
                val all = children(parentId)
                val from = (page * pageSize).coerceAtMost(all.size)
                val to = (from + pageSize).coerceAtMost(all.size)
                LibraryResult.ofItemList(ImmutableList.copyOf(if (pageSize > 0) all.subList(from, to) else all), params)
            }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
                               mediaId: String): ListenableFuture<LibraryResult<MediaItem>> = async {
            resolve(mediaId)?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onSearch(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
                              params: LibraryParams?): ListenableFuture<LibraryResult<Void>> {
            bg.execute { session.notifySearchResultChanged(browser, query, search(query).size, params) }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, query: String,
                                       page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            async { LibraryResult.ofItemList(ImmutableList.copyOf(search(query)), params) }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                                     mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> = async {
            val out = ArrayList<MediaItem>()
            for (m in mediaItems) {
                val q = m.requestMetadata.searchQuery
                if (q != null) out.addAll(search(q))
                else resolve(m.mediaId)?.let { out.add(it) }
            }
            out as MutableList<MediaItem>
        }

        override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                                     mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = async {
            val out = ArrayList<MediaItem>()
            for (m in mediaItems) {
                val q = m.requestMetadata.searchQuery
                if (q != null) out.addAll(search(q)) else resolve(m.mediaId)?.let { out.add(it) }
            }
            val idx = startIndex.coerceIn(0, (out.size - 1).coerceAtLeast(0))
            // Podcast-Folgen dort fortsetzen, wo man aufgehört hat
            var pos = startPositionMs
            if (pos == C.TIME_UNSET && out.isNotEmpty()) {
                epOf(out[idx].mediaId)?.let { (_, ep) -> if (!Podcast.done(prefs, ep)) pos = Podcast.pos(prefs, ep) }
            }
            MediaSession.MediaItemsWithStartPosition(out, idx, if (pos == C.TIME_UNSET) 0 else pos)
        }
    }
}
