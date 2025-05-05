package com.example.jc_camerax

import android.content.Context
import android.util.Log
import androidx.camera.core.SurfaceRequest
import androidx.camera.video.Quality
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val TAG = "CameraPreviewViewModel"

data class CameraPreviewState(
    val surfaceRequest: SurfaceRequest? = null,
    val isRecorder: Boolean = false,
)

// https://developer.android.google.cn/media/camera/camerax/video-capture?hl=zh-cn

class CameraPreviewViewModel(
    private val cameraController: CameraController
) : ViewModel() {
    private val _cameraPreviewState = MutableStateFlow(CameraPreviewState())
    val cameraPreviewState: StateFlow<CameraPreviewState> = _cameraPreviewState.asStateFlow()

    init {
        Log.i(TAG, "????")
        cameraController.setSurfaceProvider { newSurfaceProvider ->
            _cameraPreviewState.update {
                it.copy(
                    surfaceRequest = newSurfaceProvider
                )
            }
        }
    }


    // 绑定摄像头
    suspend fun bindToCamera(context: Context, lifecycleCamera: LifecycleOwner) {
        cameraController.bindToCamera(context, lifecycleCamera)
    }

    // 视频存储的文件夹
    private var dirPath = ""

    private var recordingJob: Job? = null

    // 开始录像
    fun startRecorderJob(context: Context,lifecycleCamera: LifecycleOwner) {
        if (cameraPreviewState.value.isRecorder) {
            return
        }

        val currentZonedDateTime = ZonedDateTime.now()
        dirPath = currentZonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

        recordingJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                while (true) {
                    cameraController.startRecorder(context, dirPath)
                    delay(10.seconds)
                    cameraController.stopRecorder(lifecycleCamera)
                }
            } catch (e: CancellationException) {
                // 取消协程后取消录制
                cameraController.stopRecorder(lifecycleCamera)
            } catch (e: Exception) {
                Log.e(TAG, "录制视频还能出错?")
                e.printStackTrace()
            }
        }


        _cameraPreviewState.update {
            it.copy(
                isRecorder = true
            )
        }
    }

    // 停止录像
    fun stopRecorderJob(lifecycleCamera: LifecycleOwner) {
        if (!cameraPreviewState.value.isRecorder) {
            return
        }

        recordingJob?.cancel()
        recordingJob = null

        cameraController.stopRecorder(lifecycleCamera)

        _cameraPreviewState.update {
            it.copy(
                isRecorder = false
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                CameraPreviewViewModel(CameraController())
            }
        }
    }

    // 设置分辨率
    fun setQualitySelector(
        lifecycleCamera: LifecycleOwner,
        quality: Quality
    ) {
        cameraController.setQualitySelector(lifecycleCamera, quality)
    }

}

