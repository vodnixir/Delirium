package dev.vodnixir.delirium.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vodnixir.delirium.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onClose: () -> Unit,
    onPhotoSent: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CameraEvent.PhotoSent -> onPhotoSent()
            }
        }
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasPermission) {
            CameraContent(
                onCapture = viewModel::sendPhoto,
                onVideo = viewModel::sendVideo,
                onClose = onClose,
                sending = state is CameraUploadState.Sending,
            )
        } else {
            PermissionContent(
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }

        if (state is CameraUploadState.Sending) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Color.White) }
        }

        (state as? CameraUploadState.Error)?.let { err ->
            ErrorBanner(
                message = err.message,
                onDismiss = viewModel::clearError,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
            )
        }
    }
}

@Composable
private fun CameraContent(
    onCapture: (ByteArray) -> Unit,
    onVideo: (File) -> Unit,
    onClose: () -> Unit,
    sending: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val videoCapture = remember {
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build()
        VideoCapture.withOutput(recorder)
    }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var recording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }

    LaunchedEffect(Unit) {
        provider = ProcessCameraProvider.getInstance(context).await()
    }

    LaunchedEffect(provider, lensFacing) {
        val p = provider ?: return@LaunchedEffect
        p.unbindAll()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        // Preview + photo + video together. On the rare device that can't bind all
        // three, fall back to photo-only so capture still works.
        runCatching {
            p.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, videoCapture)
        }.onFailure {
            runCatching { p.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            provider?.unbindAll()
        }
    }

    fun takePhoto() {
        val executor = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val front = lensFacing == CameraSelector.LENS_FACING_FRONT
                    try {
                        onCapture(image.toCompressedJpeg(flipHorizontal = front))
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) = Unit
            },
        )
    }

    fun startRecording() {
        if (activeRecording != null) return
        val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        // No audio: silent clips avoid needing the RECORD_AUDIO permission.
        runCatching {
            videoCapture.output
                .prepareRecording(context, options)
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> recording = true
                        is VideoRecordEvent.Finalize -> {
                            recording = false
                            activeRecording = null
                            if (!event.hasError() && file.exists() && file.length() > 0L) {
                                onVideo(file)
                            } else {
                                file.delete()
                            }
                        }
                    }
                }
        }.onSuccess { rec ->
            activeRecording = rec
            // Cap clip length so files stay small and uploads stay quick.
            scope.launch {
                delay(MAX_VIDEO_MS)
                activeRecording?.stop()
            }
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )

        if (!recording) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close)) }

            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT
                    else CameraSelector.LENS_FACING_BACK
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = stringResource(R.string.camera_switch),
                )
            }
        }

        Text(
            text = stringResource(
                if (recording) R.string.camera_recording else R.string.camera_hold_for_video,
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (recording) Color(0xFFFF5252) else Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp),
        )

        ShutterButton(
            enabled = !sending,
            recording = recording,
            onTap = ::takePhoto,
            onHoldStart = ::startRecording,
            onRelease = ::stopRecording,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        )
    }
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    recording: Boolean,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(if (recording) 92.dp else 80.dp)
            .clip(CircleShape)
            .then(
                if (recording) Modifier.border(5.dp, Color(0xFFFF5252), CircleShape)
                else Modifier,
            )
            .background(
                if (recording) Color(0xFFFF5252).copy(alpha = 0.9f)
                else Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            )
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onHoldStart() },
                    onPress = {
                        tryAwaitRelease()
                        // Stops only if a hold actually started a recording.
                        onRelease()
                    },
                )
            },
    )
}

private const val MAX_VIDEO_MS = 15_000L

@Composable
private fun PermissionContent(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.camera_permission_required),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.camera_grant)) }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
    ) {
        Column {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDismiss) { Text(stringResource(R.string.common_retry)) }
        }
    }
}
