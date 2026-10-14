package com.tonewerk.eq

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tonewerk.eq.audio.FxEngine
import com.tonewerk.eq.audio.GlobalFxService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Track(
    val id: Long, val title: String, val artist: String, val album: String,
    val albumId: Long, val durationMs: Long, val uri: Uri
)

class PlayerModel(app: Application) : AndroidViewModel(app) {

    var controller by mutableStateOf<MediaController?>(null)
        private set
    val tracks = mutableStateListOf<Track>()
    var libraryLoaded by mutableStateOf(false)
        private set

    init { connect() }

    private fun connect() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try { controller = future.get() } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(ctx))
    }

    fun loadLibrary() {
        if (libraryLoaded) return
        val ctx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val out = ArrayList<Track>()
            val proj = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION)
            ctx.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Audio.Media.IS_MUSIC}!=0", null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iAr = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAl = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDu = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    out += Track(id, c.getString(iTi) ?: "Unknown",
                        c.getString(iAr) ?: "Unknown artist", c.getString(iAl) ?: "",
                        c.getLong(iAi), c.getLong(iDu),
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id))
                }
            }
            withContext(Dispatchers.Main) { tracks.clear(); tracks.addAll(out); libraryLoaded = true }
        }
    }

    fun playFrom(index: Int) {
        val c = controller ?: return
        if (c.mediaItemCount != tracks.size) {
            c.clearMediaItems()
            c.addMediaItems(tracks.map { MediaItem.fromUri(it.uri) })
        }
        c.seekTo(index, 0); c.prepare(); c.play()
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Global EQ = effects on the output mix, so ALL apps are equalized. */
    fun setGlobalEq(on: Boolean) {
        val ctx = getApplication<Application>()
        if (on) {
            FxEngine.attach(0, isGlobal = true)
            GlobalFxService.start(ctx)
        } else {
            GlobalFxService.stop(ctx)
            val sid = FxEngine.playerSessionId
            if (sid != C.AUDIO_SESSION_ID_UNSET) FxEngine.attach(sid, isGlobal = false)
        }
    }

    fun sleepIn(minutes: Int) {
        viewModelScope.launch { delay(minutes * 60_000L); controller?.pause() }
    }
}
