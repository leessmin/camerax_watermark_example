package com.example.jc_camerax

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.video.Quality
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.jc_camerax.ui.theme.Jc_cameraxTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Jc_cameraxTheme {
                CameraPreviewScreen()
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreviewScreen(modifier: Modifier = Modifier) {
    // 获取权限
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val recordAudioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    if (cameraPermissionState.status.isGranted && recordAudioPermissionState.status.isGranted) {
        CameraPreviewContent()
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .wrapContentSize()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val textToShow =
                if (recordAudioPermissionState.status.shouldShowRationale || cameraPermissionState.status.shouldShowRationale) {
                    "Whoops! Looks like we need your camera to work our magic!" + "Don't worry, we just wanna see your pretty face (and maybe some cats).  " + "Grant us permission and let's get this party started!"
                } else {
                    "Hi there! We need your camera to work our magic! ✨\n" + "Grant us permission and let's get this party started! \uD83C\uDF89"
                }

            Text(textToShow, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    cameraPermissionState.launchPermissionRequest()
                    recordAudioPermissionState.launchPermissionRequest()
                }) {
                Text("Request permission")
            }
        }
    }
}

@Composable
private fun CameraPreviewContent(
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    context: Context = LocalContext.current,
    viewModel: CameraPreviewViewModel = viewModel(factory = CameraPreviewViewModel.Factory),
) {

    val cameraPreviewState by viewModel.cameraPreviewState.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleOwner) {
        viewModel.bindToCamera(context, lifecycleOwner)
    }

    Box(modifier) {
        cameraPreviewState.surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
            )
        }
        Row(modifier = Modifier.align(Alignment.BottomCenter)) {
            Button(
                enabled = !cameraPreviewState.isRecorder,
                onClick = {
                    viewModel.setQualitySelector(lifecycleOwner, Quality.FHD)
                }
            ) { Text("1080p") }

            Button(
                enabled = !cameraPreviewState.isRecorder,
                onClick = {
                    viewModel.setQualitySelector(lifecycleOwner, Quality.UHD)
                }
            ) { Text("2160p") }

            Button(
                enabled = !cameraPreviewState.isRecorder,
                onClick = {
                    viewModel.setQualitySelector(lifecycleOwner, Quality.SD)
                }
            ) { Text("480P") }

            Button(
                onClick = {
                    if (cameraPreviewState.isRecorder) {
                        viewModel.stopRecorderJob(lifecycleOwner)
                    } else {
                        viewModel.startRecorderJob(context, lifecycleOwner)
                    }
                }) {
                if (cameraPreviewState.isRecorder) {
                    Text("停止录制")
                } else {
                    Text("开始录制")
                }
            }
        }
    }
}