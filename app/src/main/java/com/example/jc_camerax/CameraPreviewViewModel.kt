package com.example.jc_camerax

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.ImageWriter
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

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

    // 开始录像
    fun startRecorder(context: Context) {
        if (cameraPreviewState.value.isRecorder) {
            return
        }
        cameraController.startRecorder(context)
        _cameraPreviewState.update {
            it.copy(
                isRecorder = true
            )
        }
    }

    // 停止录像
    fun stopRecorder() {
        if (!cameraPreviewState.value.isRecorder) {
            return
        }
        cameraController.stopRecorder()
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

