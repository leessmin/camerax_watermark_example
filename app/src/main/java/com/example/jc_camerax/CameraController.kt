package com.example.jc_camerax

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "CameraController"

class CameraController(
    // 分辨率，默认为1920*1080
    private var quality: Quality = Quality.FHD,
    // 旋转角度 默认90度。 使用Surface可以获得常量
    private var rotation: Int = Surface.ROTATION_90
) {
    // 摄像头实例
    private var _processCameraProvider: ProcessCameraProvider? = null

    // 摄像头预览
    private val _cameraPreview = Preview.Builder().build()

    // 视频录制器
    private var _record: Recording? = null

    // 视频截取器
    private var _videoCapture: VideoCapture<Recorder> = createVideoCapture()

    // 视频帧叠加效果
    private val overlayEffect = OverlayEffect(
        CameraEffect.VIDEO_CAPTURE,
        0,
        Handler(Looper.getMainLooper())
    ) {
        Log.e(TAG, "overlayEffect error")
    }.apply {

        // 填充色
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 80f
            isAntiAlias = true // 抗锯齿
        }

        // 描边
        val strokePaint = Paint().apply {
            color = Color.BLACK
            textSize = 80f
            isAntiAlias = true
            style = Paint.Style.STROKE // 描边样式
            strokeWidth = 10f
        }

        // 清除setOnDrawListener的监听
        clearOnDrawListener()

        // 在每一帧上绘制水印
        setOnDrawListener {

            // 保存当前 Canvas 状态
            it.overlayCanvas.save()

            it.overlayCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) // 清除上一帧
            it.overlayCanvas.setMatrix(it.sensorToBufferTransform)  // 应用传感器到缓冲区的转换矩阵

            val currentZonedDateTime = ZonedDateTime.now()
            val text = "当前位置: 昆仑大道 | 当前车速: 100Km/h | ${
                currentZonedDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }"


            // 获取可绘制区域
            val rect = it.overlayCanvas.clipBounds

            // 计算文本宽度和高度
            val textBounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            val textWidth = textPaint.measureText(text)

            // 文本绘制坐标
            val x = rect.right - textWidth - 50f
            val y = rect.bottom - 50f

            // 从中心点旋转canvas画布，使绘制方向变成水平
//            it.overlayCanvas.rotate(-90f, centerX, centerY)
            // 添加文字路径
            val path = Path()
            textPaint.getTextPath(text, 0, text.length, x, y, path)
//            it.overlayCanvas.drawText(
//                text,
//                rect.right - textWidth,
//                rect.bottom - textHeight,
//                textPaint
//            ) // 绘制文本
            // 绘制描边
            it.overlayCanvas.drawPath(path, strokePaint)
            // 绘制填充
            it.overlayCanvas.drawPath(path, textPaint)


            true
        }
    }

    // 参数整合
    private var useCaseGroup = createUseCaseGroup()

    // 创建VideoCapture
    private fun createVideoCapture(): VideoCapture<Recorder> {
        return VideoCapture.withOutput(
            Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build()
        ).apply {
            targetRotation = rotation
        }
    }

    // 创建useCaseGroup
    private fun createUseCaseGroup(): UseCaseGroup {
        return UseCaseGroup.Builder()
            .addEffect(overlayEffect)
            .addUseCase(_videoCapture)
            .addUseCase(_cameraPreview)
            .build()
    }

    // 将useCaseGroup（这个是类的成员变量，只要他更改了就一起更改） 重新应用到相机
    private fun reapplyBindToLifecycle(lifecycleCamera: LifecycleOwner) {
        // 重新应用到相机
        _processCameraProvider.apply {
            this!!.unbindAll()

            bindToLifecycle(
                lifecycleCamera,
                CameraSelector.DEFAULT_BACK_CAMERA,
                useCaseGroup,
            )
        }
    }

    // 绑定摄像头
    suspend fun bindToCamera(context: Context, lifecycleCamera: LifecycleOwner) {
        _processCameraProvider = ProcessCameraProvider.awaitInstance(context)
        val camera = _processCameraProvider?.bindToLifecycle(
            lifecycleCamera,
            CameraSelector.DEFAULT_BACK_CAMERA,
            useCaseGroup,
        )

        // 完成释放摄像头资源
        try {
            awaitCancellation()
        } finally {
            Log.i(TAG, "摄像头资源释放")
            _processCameraProvider?.unbindAll()
        }
    }


    // 为摄像头添加预览
    fun setSurfaceProvider(surfaceProvider: Preview.SurfaceProvider) {
        _cameraPreview.surfaceProvider = surfaceProvider
    }

    // 开始录像
    fun startRecorder(context: Context) {
        if (_record != null) {
            // _record不为空说明已经在录制了
            Log.e(TAG, "_record!=null，不能重复录制")
            return
        }


        val name = "CameraX-recording-" + SimpleDateFormat(
            "yyyy-MM-DD HH:mm:ss", Locale.US
        ).format(System.currentTimeMillis()) + ".mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "不存在录音权限")
            return
        }
        _record =
            _videoCapture.output.prepareRecording(context, mediaStoreOutput).withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { videoRecordEvent ->
                    when (videoRecordEvent) {
                        is VideoRecordEvent.Start -> {
                            // 开始录制
                        }

                        is VideoRecordEvent.Pause -> {
                            // 录制暂停
                        }

                        is VideoRecordEvent.Resume -> {
                            // 录制恢复
                        }

                        is VideoRecordEvent.Finalize -> {
                            // 录制完成
                        }
                    }
                }
    }

    // 停止录像
    fun stopRecorder() {
        if (_record == null) {
            Log.e(TAG, "_record==null, 录制未开启")
            return
        }

        _record?.stop()

        _record = null
    }

    // 修改视频质量
    fun setQualitySelector(
        lifecycleCamera: LifecycleOwner,
        funQuality: Quality
    ) {
        if (_record != null) {
            Log.e(TAG, "录制视频时不能修改视频质量")
            return
        }

        quality = funQuality

        _videoCapture = createVideoCapture()

        useCaseGroup = createUseCaseGroup()

        reapplyBindToLifecycle(lifecycleCamera)
    }
}