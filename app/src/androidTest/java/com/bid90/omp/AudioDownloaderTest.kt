package com.bid90.omp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bid90.omp.download.AudioDownloadService
import com.bid90.omp.download.SharedAudioLink
import android.media.MediaMetadataRetriever
import android.graphics.BitmapFactory
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Intent
import android.provider.MediaStore
import android.content.ContentUris
import android.content.ContentValues
import com.bid90.omp.playback.LocalMusic
import androidx.test.core.app.ActivityScenario
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class AudioDownloaderTest {
    @Test fun receivesYouTubeAndYouTubeMusicShares() {
        fun share(text: String) = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        assertEquals("https://youtu.be/BX0lKSa_PTk?si=test",
            SharedAudioLink.extract(share("Ascultă: https://youtu.be/BX0lKSa_PTk?si=test")))
        assertEquals("https://music.youtube.com/watch?v=BX0lKSa_PTk",
            SharedAudioLink.extract(share("Miss You\nhttps://music.youtube.com/watch?v=BX0lKSa_PTk")))
        assertNull(SharedAudioLink.extract(share("Fără link")))
        assertNull(SharedAudioLink.extract(share("file:///sdcard/song.mp3")))
        assertNull(SharedAudioLink.extract(Intent(Intent.ACTION_VIEW).setType("text/plain")))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolved = share("https://youtu.be/BX0lKSa_PTk").setPackage(context.packageName)
            .resolveActivity(context.packageManager)
        assertEquals(MainActivity::class.java.name, resolved?.className)
    }

    @Test fun createsDedicatedFolderBeforeAnyDownload() {
        assertTrue(LocalMusic.ensureFolder().isDirectory)
    }

    /** Opt-in live test: the requested audio is kept in the user's music folder. */
    @Test fun downloadsRequestedOnlineSource() {
        val url = InstrumentationRegistry.getArguments().getString("sourceUrl").orEmpty()
        if (url.isBlank()) return // Opt-in only; normal test runs never contact YouTube.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val before = AudioDownloadService.state.value.completed
        val share = Intent(Intent.ACTION_SEND).setClass(context, MainActivity::class.java)
            .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Ascultă această piesă: $url")
        ActivityScenario.launch<MainActivity>(share).use {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val deadline = System.currentTimeMillis() + 240000
            var started = false
            while (System.currentTimeMillis() < deadline) {
                val state = AudioDownloadService.state.value
                if (state.running) started = true
                if (state.completed > before || (started && !state.running)) break
                Thread.sleep(500)
            }
            val result = AudioDownloadService.state.value
            assertTrue("${result.message}\n${result.details}", result.completed > before)
            val downloaded = LocalMusic.ensureFolder().listFiles().orEmpty()
                .filter { it.extension == "mp3" && it.length() > 100 }.maxByOrNull { it.lastModified() }
            assertNotNull(downloaded)
            MediaMetadataRetriever().use { metadata ->
                metadata.setDataSource(downloaded!!.absolutePath)
                val cover = metadata.embeddedPicture
                assertNotNull("The downloaded MP3 must contain its cover image", cover)
                assertNotNull(BitmapFactory.decodeByteArray(cover!!, 0, cover.size))
            }
        }
    }

    @Test fun acceptsWebLinksAndRejectsLocalOrMalformedInputs() {
        assertTrue(AudioDownloadService.validUrl(" https://example.com/audio?id=1 "))
        assertTrue(AudioDownloadService.validUrl("http://example.com/audio"))
        listOf("", "example.com", "--exec=command", "file:///sdcard/song.mp3",
            "content://media/audio/1", "https://", "https://user:password@example.com").forEach {
            assertFalse(it, AudioDownloadService.validUrl(it))
        }
    }

    @Test fun bundledNativeRuntimeCanExecuteYtDlp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        YoutubeDL.init(context)
        FFmpeg.init(context)
        val request = YoutubeDLRequest("https://example.com").apply { addOption("--version") }
        assertTrue(YoutubeDL.execute(request).out.trim().isNotEmpty())
    }

    @Test fun downloadsAudioConvertsToMp3AndPublishesInMusic() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "omp-test-${UUID.randomUUID()}"
        // Serve a one-second PCM WAV locally: no external site or account is needed for this test.
        val pcmBytes = 16000
        val wav = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcmBytes); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(pcmBytes)
            repeat(pcmBytes / 2) { putShort((kotlin.math.sin(it * 2.0 * Math.PI * 440 / 8000) * 8000).toInt().toShort()) }
        }.array()
        val server = ServerSocket(0)
        val serverThread = thread(isDaemon = true) {
            try {
                while (!server.isClosed) server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    val first = reader.readLine().orEmpty()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${wav.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        if (!first.startsWith("HEAD")) write(wav)
                        flush()
                    }
                }
            } catch (_: java.io.IOException) { }
        }
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("$name%")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        try {
            automation.grantRuntimePermission(context.packageName, LocalMusic.permission)
            val initialShare = Intent(Intent.ACTION_SEND).setClass(context, MainActivity::class.java)
                .setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Fără link")
            ActivityScenario.launch<MainActivity>(initialShare).use { scenario ->
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val completedBefore = AudioDownloadService.state.value.completed
                scenario.onActivity { activity ->
                    activity.startActivity(Intent(Intent.ACTION_SEND).setClass(activity, MainActivity::class.java)
                        .setType("text/plain").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(Intent.EXTRA_TEXT, "Piesa de test: http://127.0.0.1:${server.localPort}/$name.wav"))
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.recreate()
                val deadline = System.currentTimeMillis() + 90000
                while (AudioDownloadService.state.value.completed == completedBefore && System.currentTimeMillis() < deadline) {
                    Thread.sleep(250)
                }
                assertTrue(AudioDownloadService.state.value.message,
                    AudioDownloadService.state.value.completed > completedBefore)
                assertEquals("Rotation must not download a consumed share twice", 0, AudioDownloadService.state.value.queued)
                context.contentResolver.query(collection, arrayOf(MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.RELATIVE_PATH,
                    MediaStore.Audio.Media.IS_MUSIC, MediaStore.Audio.Media.IS_PENDING), selection, args, null)!!.use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("audio/mpeg", cursor.getString(1))
                    assertEquals("Music/OpenMusic/", cursor.getString(2))
                    assertEquals(1, cursor.getInt(3))
                    assertEquals(0, cursor.getInt(4))
                    context.contentResolver.openInputStream(ContentUris.withAppendedId(collection, cursor.getLong(0)))!!.use { input ->
                        val audio = input.readBytes()
                        assertTrue(audio.size > 100)
                        val otherUri = context.contentResolver.insert(collection, ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name-outside.mp3")
                            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/OpenMusicOther/")
                            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                            put(MediaStore.Audio.Media.IS_MUSIC, 1)
                            put(MediaStore.Audio.Media.IS_PENDING, 1)
                        })!!
                        context.contentResolver.openOutputStream(otherUri)!!.use { it.write(audio) }
                        context.contentResolver.update(otherUri, ContentValues().apply {
                            put(MediaStore.Audio.Media.IS_PENDING, 0)
                        }, null, null)
                        val library = LocalMusic(context).tracks()
                        assertTrue(library.any { it.mediaId == cursor.getLong(0).toString() })
                        assertFalse(library.any { it.mediaId == ContentUris.parseId(otherUri).toString() })
                    }
                }
            }
        } finally {
            server.close()
            serverThread.join(1000)
            context.contentResolver.delete(collection, selection, args)
        }
    }
}
