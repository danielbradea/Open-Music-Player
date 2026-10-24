package com.bid90.omp

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val PlayerWhite = Color(0xFFF9F6FF)
private val PlayerMuted = Color(0xFFB7ACC9)
private val PlayerAccent = Color(0xFFE8A4FF)

@Composable
internal fun FullPlayer(item: MediaItem, controller: MediaController?, onClose: () -> Unit, onDelete: () -> Unit,
                        playlists: List<MusicPlaylist>, selectedPlaylistId: String?,
                        onPlaylistSelected: (String?) -> Unit, playlistTracks: List<MediaItem>, onNewPlaylist: () -> Unit, onDownload: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var position by remember(item.mediaId) { mutableLongStateOf(0L) }
    var duration by remember(item.mediaId) { mutableLongStateOf(0L) }
    var dragging by remember(item.mediaId) { mutableStateOf<Float?>(null) }
    var previous by remember { mutableStateOf(false) }
    var next by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    DisposableEffect(controller) {
        fun sync() {
            playing = controller?.isPlaying == true
            shuffle = controller?.shuffleModeEnabled == true
            repeat = controller?.repeatMode ?: Player.REPEAT_MODE_OFF
            previous = controller?.hasPreviousMediaItem() == true
            next = controller?.hasNextMediaItem() == true
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        controller?.addListener(listener)
        sync()
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller, item.mediaId) {
        while (true) {
            position = controller?.currentPosition?.coerceAtLeast(0) ?: 0
            duration = controller?.duration?.coerceAtLeast(0) ?: 0
            delay(250)
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(onBack = onClose)
        Surface(Modifier.fillMaxSize(), color = Color(0xFF29243E)) {
            Box(Modifier.fillMaxSize().background(com.bid90.omp.ui.theme.MusicBackground)) {
                BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                    val landscape = maxWidth > maxHeight
                    val landscapeCoverSize = (maxHeight - 250.dp).coerceIn(80.dp, 240.dp)
                    val playerContent: @Composable () -> Unit = {
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(if (landscape) 4.dp else 64.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val coverSize = if (landscape) minOf(maxWidth * 0.65f, landscapeCoverSize) else minOf(maxWidth * 0.74f, 360.dp)
                        TrackCover(item, Modifier.size(coverSize).clip(RoundedCornerShape(if (landscape) 20.dp else 40.dp)))
                    }
                    Spacer(Modifier.height(if (landscape) 8.dp else 26.dp))
                    Text(item.mediaMetadata.title?.toString() ?: resources.getString(R.string.untitled), color = PlayerWhite,
                        style = if (landscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center, maxLines = if (landscape) 1 else 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(item.mediaMetadata.artist?.toString() ?: resources.getString(R.string.unknown_artist), color = PlayerMuted,
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(if (landscape) 4.dp else 40.dp))
                    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(horizontal = 32.dp)) {
                        Slider(value = dragging ?: position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                            onValueChange = { dragging = it },
                            onValueChangeFinished = { dragging?.let { controller?.seekTo(it.toLong()) }; dragging = null },
                            valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = duration > 0,
                            colors = SliderDefaults.colors(thumbColor = PlayerWhite, activeTrackColor = PlayerWhite,
                                inactiveTrackColor = PlayerWhite.copy(alpha = 0.22f)))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(playerTime(dragging?.toLong() ?: position), color = PlayerMuted, style = MaterialTheme.typography.labelSmall)
                            Text(playerTime(duration), color = PlayerMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(if (landscape) 4.dp else 20.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly) {
                            PlayerButton("shuffle", if (shuffle) resources.getString(R.string.shuffle_off) else resources.getString(R.string.shuffle_on),
                                tint = if (shuffle) PlayerAccent else PlayerMuted, selected = shuffle,
                                onClick = { controller?.shuffleModeEnabled = !shuffle })
                            PlayerButton("previous", resources.getString(R.string.previous_song), enabled = previous,
                                onClick = { controller?.seekToPreviousMediaItem() })
                            FilledIconButton(onClick = { controller?.let { if (it.isPlaying) it.pause() else { it.prepare(); it.play() } } },
                                enabled = controller != null, modifier = Modifier.size(if (landscape) 52.dp else 76.dp), shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PlayerWhite, contentColor = Color(0xFF29243E))) {
                                PlayerGlyph(if (playing) "pause" else "play", if (playing) resources.getString(R.string.pause) else resources.getString(R.string.play), Color(0xFF29243E))
                            }
                            PlayerButton("next", resources.getString(R.string.next_song), enabled = next, onClick = { controller?.seekToNextMediaItem() })
                            PlayerButton(if (repeat == Player.REPEAT_MODE_ONE) "repeatOne" else "repeat",
                                when (repeat) { Player.REPEAT_MODE_ALL -> resources.getString(R.string.repeat_all_on)
                                    Player.REPEAT_MODE_ONE -> resources.getString(R.string.repeat_one_on)
                                    else -> resources.getString(R.string.repeat_on) },
                                tint = if (repeat != Player.REPEAT_MODE_OFF) PlayerAccent else PlayerMuted,
                                selected = repeat != Player.REPEAT_MODE_OFF,
                                onClick = { controller?.repeatMode = when (repeat) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF } })
                        }
                    }
                            Spacer(Modifier.height(if (landscape) 8.dp else 48.dp))
                        }
                    }
                    Column(Modifier.fillMaxSize().padding(top = if (landscape) 0.dp else 10.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        PlayerButton("back", resources.getString(R.string.back_library), onClick = onClose)
                        Box(Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterEnd) {
                            Surface(onClick = { menu = true }, shape = RoundedCornerShape(28.dp),
                                color = PlayerWhite.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, PlayerWhite.copy(alpha = 0.1f))) {
                                Row(Modifier.widthIn(max = 200.dp).padding(horizontal = 16.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(playlists.firstOrNull { it.id == selectedPlaylistId }?.name ?: resources.getString(R.string.all_tracks),
                                        fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.sp, color = PlayerWhite, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    PlayerGlyph("chevronDown", resources.getString(R.string.choose_playlist), PlayerWhite)
                                }
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false },
                                modifier = Modifier.width(250.dp), shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xFF242039),
                                border = BorderStroke(1.dp, PlayerWhite.copy(alpha = 0.1f)),
                                tonalElevation = 0.dp, shadowElevation = 12.dp) {
                                DropdownMenuItem(
                                    modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp))
                                        .background(if (selectedPlaylistId == null) Color(0xFF51408C) else Color.Transparent),
                                    text = { Text(resources.getString(R.string.all_tracks), fontFamily = FontFamily.SansSerif,
                                        fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp, color = PlayerWhite) },
                                    leadingIcon = { PlayerGlyph("playlist", "", PlayerWhite) },
                                    trailingIcon = { if (selectedPlaylistId == null) PlayerGlyph("check", resources.getString(R.string.selected), PlayerWhite) },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    onClick = { menu = false; onPlaylistSelected(null) })
                                playlists.forEach { playlist ->
                                    val chosen = playlist.id == selectedPlaylistId
                                    DropdownMenuItem(
                                        modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(12.dp))
                                            .background(if (chosen) Color(0xFF51408C) else Color.Transparent),
                                        text = { Text(playlist.name, fontFamily = FontFamily.SansSerif,
                                            fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
                                            color = PlayerWhite, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        leadingIcon = { PlayerGlyph("playlist", "", PlayerWhite) },
                                        trailingIcon = { if (chosen) PlayerGlyph("check", resources.getString(R.string.selected), PlayerWhite) },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        onClick = { menu = false; onPlaylistSelected(playlist.id) })
                                }
                            }
                        }

                    }
                        if (landscape) {
                            Row(Modifier.weight(1f).fillMaxWidth()) {
                                Box(Modifier.weight(1f).fillMaxHeight()) { playerContent() }
                                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 20.dp)) {
                                    Text(playlists.firstOrNull { it.id == selectedPlaylistId }?.name ?: resources.getString(R.string.all_tracks),
                                        color = PlayerWhite, style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(bottom = 8.dp))
                                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(5.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (playlistTracks.isEmpty()) item { Text(resources.getString(R.string.empty_playlist), color = PlayerMuted) }
                                        items(playlistTracks, key = { it.mediaId }) { track ->
                                            Surface(shape = RoundedCornerShape(12.dp), color = if (track.mediaId == item.mediaId)
                                                Color(0xFF51408C) else PlayerWhite.copy(alpha = 0.05f)) {
                                                Row(Modifier.fillMaxWidth().clickable {
                                                    controller?.let { player ->
                                                        val index = (0 until player.mediaItemCount).firstOrNull {
                                                            player.getMediaItemAt(it).mediaId == track.mediaId
                                                        }
                                                        if (index != null) { player.seekTo(index, 0L); player.prepare(); player.play() }
                                                        else {
                                                            player.setMediaItems(playlistTracks, playlistTracks.indexOf(track), 0L)
                                                            player.prepare(); player.play()
                                                        }
                                                    }
                                                }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    TrackCover(track, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)))
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(track.mediaMetadata.title?.toString() ?: resources.getString(R.string.untitled), color = PlayerWhite,
                                                            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(track.mediaMetadata.artist?.toString() ?: resources.getString(R.string.unknown_artist), color = PlayerMuted,
                                                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(Modifier.weight(1f).fillMaxWidth()) { playerContent() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCover(item: MediaItem, modifier: Modifier) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    val cover by produceState<ImageBitmap?>(null, item.mediaId) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val data = item.mediaMetadata.artworkData ?: MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, item.localConfiguration?.uri
                        ?: android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, item.mediaId.toLong()))
                    retriever.embeddedPicture
                }
                data?.let {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(it, 0, it.size, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (bounds.outWidth / inSampleSize > 1024 || bounds.outHeight / inSampleSize > 1024) inSampleSize *= 2
                    }
                    BitmapFactory.decodeByteArray(it, 0, it.size, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFFCF7DA8), Color(0xFF654CAB), Color(0xFF253C67)))),
        contentAlignment = Alignment.Center) {
        if (cover != null) Image(cover!!, resources.getString(R.string.cover), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Canvas(Modifier.fillMaxSize()) {
            val edge = minOf(size.width, size.height)
            val origin = Offset((size.width - edge) / 2f, (size.height - edge) / 2f)
            val ink = PlayerWhite.copy(alpha = 0.85f)
            fun point(x: Float, y: Float) = origin + Offset(edge * x, edge * y)
            drawOval(ink, topLeft = point(.28f, .62f), size = androidx.compose.ui.geometry.Size(edge * .28f, edge * .18f))
            drawLine(ink, point(.54f, .2f), point(.54f, .7f), strokeWidth = edge * .045f, cap = StrokeCap.Round)
            val flag = androidx.compose.ui.graphics.Path().apply {
                val start = point(.54f, .2f)
                moveTo(start.x, start.y)
                val bend = point(.54f, .34f)
                val end = point(.72f, .36f)
                quadraticTo(bend.x, bend.y, end.x, end.y)
            }
            drawPath(flag, ink, style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = edge * .045f, cap = StrokeCap.Round))
        }
    }
}

private fun playerTime(ms: Long): String = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

@Composable
private fun PlayerButton(kind: String, label: String, tint: Color = PlayerWhite, enabled: Boolean = true,
                         selected: Boolean = false, onClick: () -> Unit) {
    IconToggleButton(checked = selected, onCheckedChange = { onClick() }, enabled = enabled,
        modifier = Modifier.size(48.dp), colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = tint, checkedContentColor = tint, disabledContentColor = tint.copy(alpha = 0.25f))) {
        PlayerGlyph(kind, label, if (enabled) tint else tint.copy(alpha = 0.25f))
    }
}

@Composable
private fun PlayerGlyph(kind: String, label: String, color: Color) {
    // Draw the controls as vectors so they stay crisp without a font or extra icon dependency.
    androidx.compose.foundation.layout.Box(Modifier.size(26.dp).then(Modifier
        .semanticsLabel(label))) {
        Canvas(Modifier.fillMaxSize()) {
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color,
                Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            fun triangle(a: Float, b: Float, c: Float) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * a, size.height * .24f)
                    lineTo(size.width * b, size.height * .5f)
                    lineTo(size.width * a, size.height * .76f); close()
                }
                drawPath(path, color)
                if (c >= 0) line(c, .24f, c, .76f)
            }
            when (kind) {
                "play" -> triangle(.32f, .77f, -1f)
                "pause" -> { line(.38f, .28f, .38f, .72f); line(.62f, .28f, .62f, .72f) }
                "next" -> triangle(.22f, .7f, .78f)
                "previous" -> triangle(.78f, .3f, .22f)
                "chevronDown" -> { line(.2f, .36f, .5f, .65f); line(.5f, .65f, .8f, .36f) }
                "check" -> { line(.18f, .52f, .4f, .73f); line(.4f, .73f, .84f, .26f) }
                "playlist" -> {
                    line(.12f, .23f, .6f, .23f); line(.12f, .42f, .6f, .42f); line(.12f, .61f, .4f, .61f)
                    line(.72f, .28f, .72f, .76f); line(.72f, .28f, .9f, .36f)
                    drawCircle(color, 3.dp.toPx(), Offset(size.width * .61f, size.height * .78f))
                }
                "download" -> { line(.5f, .12f, .5f, .66f); line(.29f, .45f, .5f, .66f); line(.71f, .45f, .5f, .66f); line(.18f, .85f, .82f, .85f) }
                "back" -> { line(.62f, .24f, .35f, .5f); line(.35f, .5f, .62f, .76f) }
                "moreVertical" -> listOf(.2f, .5f, .8f).forEach { drawCircle(color, 1.8.dp.toPx(), Offset(size.width * .5f, size.height * it)) }
                "more" -> listOf(.2f, .5f, .8f).forEach { drawCircle(color, 1.8.dp.toPx(), Offset(size.width * it, size.height * .5f)) }
                "shuffle" -> {
                    line(.12f, .27f, .3f, .27f); line(.3f, .27f, .7f, .73f); line(.7f, .73f, .9f, .73f)
                    line(.12f, .73f, .3f, .73f); line(.3f, .73f, .43f, .58f)
                    line(.57f, .42f, .7f, .27f); line(.7f, .27f, .9f, .27f)
                    line(.77f, .14f, .9f, .27f); line(.77f, .4f, .9f, .27f)
                    line(.77f, .6f, .9f, .73f); line(.77f, .86f, .9f, .73f)
                }
                else -> {
                    line(.2f, .32f, .8f, .32f); line(.8f, .32f, .8f, .48f)
                    line(.66f, .18f, .8f, .32f); line(.66f, .46f, .8f, .32f)
                    line(.8f, .68f, .2f, .68f); line(.2f, .68f, .2f, .52f)
                    line(.34f, .54f, .2f, .68f); line(.34f, .82f, .2f, .68f)
                    if (kind == "repeatOne") line(.5f, .44f, .5f, .56f)
                }
            }
        }
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier = this.then(Modifier.semantics {
    contentDescription = label
})

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactPlayer(item: MediaItem, controller: MediaController?, onExpand: () -> Unit) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var position by remember(item.mediaId) { mutableLongStateOf(0) }
    var duration by remember(item.mediaId) { mutableLongStateOf(0) }
    var dragging by remember(item.mediaId) { mutableStateOf<Float?>(null) }
    DisposableEffect(controller) {
        fun sync() {
            playing = controller?.isPlaying == true
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        controller?.addListener(listener)
        sync()
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller, item.mediaId) {
        while (true) {
            position = controller?.currentPosition?.coerceAtLeast(0) ?: 0
            duration = controller?.duration?.coerceAtLeast(0) ?: 0
            delay(250)
        }
    }
    Surface(Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TrackCover(item, Modifier.offset(x = (-3).dp).size(48.dp).clip(RoundedCornerShape(10.dp)).clickable(
                    onClickLabel = resources.getString(R.string.open_player), onClick = onExpand))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).clickable(onClickLabel = resources.getString(R.string.open_player), onClick = onExpand)) {
                    Text(item.mediaMetadata.title?.toString() ?: resources.getString(R.string.untitled), color = PlayerWhite,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${item.mediaMetadata.artist ?: resources.getString(R.string.unknown_artist)} · ${playerTime(duration)}",
                        color = PlayerMuted, style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledIconButton(onClick = { controller?.let {
                    if (it.isPlaying) it.pause() else { it.prepare(); it.play() }
                } }, enabled = controller != null, modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = PlayerWhite)) {
                    PlayerGlyph(if (playing) "pause" else "play", if (playing) resources.getString(R.string.pause) else resources.getString(R.string.play), Color(0xFF29243E))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 60.dp, end = 56.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val progress = dragging ?: position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat())
                Slider(value = progress, onValueChange = { dragging = it },
                    onValueChangeFinished = { dragging?.let { controller?.seekTo(it.toLong()) }; dragging = null },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = duration > 0,
                    modifier = Modifier.weight(1f).height(24.dp).semanticsLabel(resources.getString(R.string.song_position)),
                    thumb = { Box(Modifier.size(8.dp).background(PlayerWhite, CircleShape)) },
                    track = {
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(PlayerWhite.copy(alpha = 0.22f))) {
                            Box(Modifier.fillMaxWidth((progress / duration.coerceAtLeast(1)).coerceIn(0f, 1f))
                                .fillMaxHeight().background(PlayerWhite))
                        }
                    })
            }
        }
    }
}
@Composable
internal fun LibraryTrackRow(item: MediaItem, selected: Boolean, playlists: List<MusicPlaylist>,
                             onPlay: () -> Unit, onDelete: () -> Unit,
                             onAddToPlaylist: (MusicPlaylist) -> Unit, onRemoveFromPlaylist: (() -> Unit)?) {
    val resources = androidx.compose.ui.platform.LocalResources.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var choosingPlaylist by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = if (selected)
        MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().combinedClickable(onClick = onPlay,
            onLongClickLabel = resources.getString(R.string.song_options), onLongClick = { choosingPlaylist = false; menu = true })
            .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackCover(item, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(item.mediaMetadata.title?.toString() ?: resources.getString(R.string.untitled), color = PlayerWhite,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.mediaMetadata.artist?.toString() ?: resources.getString(R.string.unknown_artist), color = PlayerMuted,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { choosingPlaylist = false; menu = true }, modifier = Modifier.size(44.dp)) {
                    PlayerGlyph("moreVertical", resources.getString(R.string.song_options), PlayerWhite)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (choosingPlaylist) {
                        DropdownMenuItem(text = { Text(resources.getString(R.string.back)) }, onClick = { choosingPlaylist = false })
                        if (playlists.isEmpty()) DropdownMenuItem(text = { Text(resources.getString(R.string.create_playlist_first)) },
                            enabled = false, onClick = {})
                        playlists.forEach { playlist ->
                            DropdownMenuItem(text = { Text(playlist.name) }, enabled = item.mediaId !in playlist.trackIds,
                                onClick = { onAddToPlaylist(playlist); menu = false })
                        }
                    } else {
                        DropdownMenuItem(text = { Text(resources.getString(R.string.play_song)) }, onClick = { menu = false; onPlay() })
                        DropdownMenuItem(text = { Text(resources.getString(R.string.add_to_playlist)) }, onClick = { choosingPlaylist = true })
                        if (onRemoveFromPlaylist != null) DropdownMenuItem(text = { Text(resources.getString(R.string.remove_from_playlist)) },
                            onClick = { menu = false; onRemoveFromPlaylist() })
                        DropdownMenuItem(text = { Text(resources.getString(R.string.delete_from_phone)) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}
