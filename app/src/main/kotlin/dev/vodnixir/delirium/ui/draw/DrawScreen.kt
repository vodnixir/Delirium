package dev.vodnixir.delirium.ui.draw

import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vodnixir.delirium.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath

private data class DrawStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val eraser: Boolean,
)

private val PALETTE = listOf(
    Color.Black,
    Color.White,
    Color(0xFFE53935),
    Color(0xFF43A047),
    Color(0xFF1E88E5),
    Color(0xFFFDD835),
    Color(0xFFFB8C00),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawScreen(
    viewModel: DrawViewModel,
    backgroundUri: String?,
    onClose: () -> Unit,
    onSent: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val strokes = remember { mutableStateListOf<DrawStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableStateOf(Color.Black) }
    var width by remember { mutableStateOf(12f) }
    var eraser by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var background by remember { mutableStateOf<AndroidBitmap?>(null) }
    var backgroundImage by remember { mutableStateOf<ImageBitmap?>(null) }

    suspend fun loadBackground(uri: Uri) {
        val bmp = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        }
        background = bmp
        backgroundImage = bmp?.asImageBitmap()
    }

    LaunchedEffect(backgroundUri) {
        if (backgroundUri != null) loadBackground(Uri.parse(backgroundUri))
    }

    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) scope.launch { loadBackground(uri) } }

    LaunchedEffect(Unit) {
        viewModel.events.collect { if (it is DrawEvent.Sent) onSent() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.draw_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        enabled = (strokes.isNotEmpty() || background != null) &&
                            state !is DrawUploadState.Sending,
                        onClick = {
                            val bmp = rasterize(canvasSize, background, strokes.toList())
                            if (bmp != null) viewModel.send(bmp)
                        },
                    ) {
                        Icon(Icons.Default.Send, contentDescription = stringResource(R.string.draw_send))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White)
                    .onSizeChanged { canvasSize = it },
            ) {
                backgroundImage?.let { img ->
                    Image(
                        bitmap = img,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .pointerInput(color, width, eraser) {
                            detectDragGestures(
                                onDragStart = { offset -> currentPoints = listOf(offset) },
                                onDrag = { change, _ -> currentPoints = currentPoints + change.position },
                                onDragEnd = {
                                    strokes.add(DrawStroke(currentPoints, color, width, eraser))
                                    currentPoints = emptyList()
                                },
                                onDragCancel = { currentPoints = emptyList() },
                            )
                        },
                ) {
                    strokes.forEach { drawStroke(it) }
                    if (currentPoints.isNotEmpty()) {
                        drawStroke(DrawStroke(currentPoints, color, width, eraser))
                    }
                }

                if (state is DrawUploadState.Sending) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = Color.White) }
                }
            }

            DrawToolbar(
                selectedColor = color,
                eraser = eraser,
                width = width,
                onColor = { color = it; eraser = false },
                onEraser = { eraser = !eraser },
                onWidth = { width = it },
                onUndo = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                onClear = { strokes.clear() },
                onPickBackground = {
                    bgLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawToolbar(
    selectedColor: Color,
    eraser: Boolean,
    width: Float,
    onColor: (Color) -> Unit,
    onEraser: () -> Unit,
    onWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    onPickBackground: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PALETTE.forEach { swatch ->
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(swatch)
                        .border(
                            width = if (!eraser && swatch == selectedColor) 3.dp else 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .clickable { onColor(swatch) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Slider(
                value = width,
                onValueChange = onWidth,
                valueRange = 4f..60f,
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = eraser,
                onClick = onEraser,
                label = { Text(stringResource(R.string.draw_eraser)) },
            )
            IconButton(onClick = onUndo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.draw_undo))
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.draw_clear))
            }
            IconButton(onClick = onPickBackground) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.draw_background))
            }
        }
    }
}

private fun DrawScope.drawStroke(stroke: DrawStroke) {
    if (stroke.points.isEmpty()) return
    val path = Path().apply {
        moveTo(stroke.points.first().x, stroke.points.first().y)
        stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    drawPath(
        path = path,
        color = if (stroke.eraser) Color.Transparent else stroke.color,
        style = Stroke(width = stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (stroke.eraser) BlendMode.Clear else BlendMode.SrcOver,
    )
}

private fun rasterize(
    size: IntSize,
    background: AndroidBitmap?,
    strokes: List<DrawStroke>,
): AndroidBitmap? {
    if (size.width <= 0 || size.height <= 0) return null
    val w = size.width
    val h = size.height
    val result = AndroidBitmap.createBitmap(w, h, AndroidBitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(result)
    canvas.drawColor(AndroidColor.WHITE)
    if (background != null) {
        val scale = maxOf(w.toFloat() / background.width, h.toFloat() / background.height)
        val dw = background.width * scale
        val dh = background.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        canvas.drawBitmap(background, null, RectF(left, top, left + dw, top + dh), null)
    }

    val overlay = AndroidBitmap.createBitmap(w, h, AndroidBitmap.Config.ARGB_8888)
    val overlayCanvas = AndroidCanvas(overlay)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        val path = AndroidPath().apply {
            moveTo(stroke.points.first().x, stroke.points.first().y)
            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        paint.strokeWidth = stroke.width
        if (stroke.eraser) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        } else {
            paint.xfermode = null
            paint.color = stroke.color.toArgb()
        }
        overlayCanvas.drawPath(path, paint)
    }
    canvas.drawBitmap(overlay, 0f, 0f, null)
    overlay.recycle()
    return result
}
