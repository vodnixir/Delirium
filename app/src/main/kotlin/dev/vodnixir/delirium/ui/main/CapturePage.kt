package dev.vodnixir.delirium.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.vodnixir.delirium.R
import dev.vodnixir.delirium.ui.camera.compressUriToJpeg
import dev.vodnixir.delirium.ui.camera.toCompressedJpeg
import dev.vodnixir.delirium.ui.components.EmptyState
import dev.vodnixir.delirium.ui.theme.LocketShape
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Center zone — the live capture surface. Binds CameraX only while this page is
 * [active] (the pager keeps neighbours composed). On shutter, hands the
 * compressed JPEG to [onCapture] for the preview/recipient step.
 */
@Composable
fun CapturePage(
    active: Boolean,
    onCapture: (ByteArray) -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier,
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

    LaunchedEffect(active) {
        if (active && !hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (hasPermission) {
            CameraCaptureContent(active = active, onCapture = onCapture, onDraw = onDraw)
        } else {
            EmptyState(
                emoji = "📷",
                title = stringResource(R.string.capture_permission_title),
                subtitle = stringResource(R.string.capture_permission_subtitle),
                actionText = stringResource(R.string.camera_grant),
                onAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}

@Composable
private fun CameraCaptureContent(
    active: Boolean,
    onCapture: (ByteArray) -> Unit,
    onDraw: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember { ImageCapture.Builder().build() }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_FRONT) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var capturing by remember { mutableStateOf(false) }

    // Gallery upload routes through the same capture preview/recipient flow as the
    // camera, so an uploaded photo is sent exactly like a snapped one.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { compressUriToJpeg(context, uri) }.onSuccess(onCapture)
        }
    }

    LaunchedEffect(Unit) {
        provider = ProcessCameraProvider.getInstance(context).await()
    }

    LaunchedEffect(provider, lensFacing, active) {
        val p = provider ?: return@LaunchedEffect
        p.unbindAll()
        if (!active) return@LaunchedEffect
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        runCatching { p.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture) }
    }

    DisposableEffect(Unit) {
        onDispose { provider?.unbindAll() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(LocketShape)
                .background(Color.Black),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        Spacer(Modifier.height(36.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = stringResource(R.string.feed_gallery),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onDraw, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Default.Brush,
                        contentDescription = stringResource(R.string.capture_draw),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            ShutterButton(
                enabled = !capturing,
                onClick = {
                    capturing = true
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
                                    capturing = false
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                            }
                        },
                    )
                },
            )

            IconButton(
                onClick = {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    Icons.Default.Cameraswitch,
                    contentDescription = stringResource(R.string.camera_switch),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .padding(6.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            )
            .clickable(enabled = enabled, onClick = onClick),
    )
}
