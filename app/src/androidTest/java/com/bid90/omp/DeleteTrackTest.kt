package com.bid90.omp

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bid90.omp.playback.LocalMusic
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DeleteTrackTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var fixture: Uri? = null

    @After fun cleanUp() {
        fixture?.let {
            compose.activity.contentResolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Audio.Media._ID} = ?", arrayOf(android.content.ContentUris.parseId(it).toString()))
        }
    }

    @Test fun longPressOffersDeletionCancelPreservesFileAndConfirmRemovesPlayingTrack() {
        val context = compose.activity
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, LocalMusic.permission)
        val title = "Test ștergere ${UUID.randomUUID()}"
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.wav")
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.RELATIVE_PATH, LocalMusic.FOLDER_PATH)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        })!!
        fixture = uri
        val pcmBytes = 16000
        val wav = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcmBytes); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(pcmBytes)
            put(ByteArray(pcmBytes))
        }.array()
        resolver.openOutputStream(uri)!!.use { it.write(wav) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performTouchInput { longClick() }
        compose.onNodeWithText("Ștergi melodia?").assertIsDisplayed()
        compose.onNodeWithText("Anulează").performClick()
        resolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)!!.use { assertTrue(it.moveToFirst()) }
        compose.onNodeWithText(title).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("ÎN REDARE").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText(title).onFirst().performTouchInput { longClick() }
        compose.onNodeWithText("Șterge").performClick()
        try {
            compose.waitUntil(15000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isEmpty() }
        } catch (e: Exception) {
            compose.onRoot(useUnmergedTree = true).printToLog("DeleteUI")
            throw e
        }
        resolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)!!.use { assertFalse(it.moveToFirst()) }
        fixture = null // The UI already removed it; deleting the now-invalid URI again can throw on Android 17.
        compose.onNodeWithText("ÎN REDARE").assertDoesNotExist()
    }
}
