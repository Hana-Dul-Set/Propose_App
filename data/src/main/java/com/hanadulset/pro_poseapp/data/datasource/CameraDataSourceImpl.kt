package com.hanadulset.pro_poseapp.data.datasource

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.Analyzer
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.hanadulset.pro_poseapp.data.datasource.interfaces.CameraDataSource
import com.hanadulset.pro_poseapp.utils.camera.CameraState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.android.OpenCVLoader
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class CameraDataSourceImpl(private val context: Context) : CameraDataSource {
    private lateinit var preview: Preview
    private var imageCapture: ImageCapture? = null
    private lateinit var imageAnalysis: ImageAnalysis
    private val executor by lazy { ContextCompat.getMainExecutor(context) }

    private var isOPENCVInit: Boolean = false

    private lateinit var camera: Camera
    private lateinit var cameraProvider: ProcessCameraProvider
    private val cameraProviderFuture by lazy {
        ProcessCameraProvider.getInstance(context)
    }

    override suspend fun initCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        aspectRatio: Int,
        previewRotation: Int,
        analyzer: Analyzer
    ): CameraState = suspendCoroutine { cont ->
        if (!isOPENCVInit) isOPENCVInit = OpenCVLoader.initLocal()

        prepareCamera(
            lifecycleOwner,
            surfaceProvider,
            aspectRatio,
            previewRotation,
            analyzer
        ).onSuccess {
            cont.resume(
                CameraState(
                    CAMERA_INIT_COMPLETE,
                    imageAnalyzerResolution = imageAnalysis.resolutionInfo?.resolution
                )
            )
        }.onFailure {
            cont.resume(
                CameraState(CAMERA_INIT_ERROR, it.cause as? Exception, it.message)
            )
        }

    }

    private fun prepareCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        aspectRatio: Int,
        previewRotation: Int,
        analyzer: Analyzer
    ) = runCatching {
        cameraProvider = cameraProviderFuture.get()
        bindCameraUseCases(
            lifecycleOwner,
            surfaceProvider,
            aspectRatio,
            previewRotation,
            analyzer
        )
    }


    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        previewRotation: Int,
        aspectRatio: Int,
        analyzer: Analyzer
    ) = runCatching {
        val cameraSelector = CameraSelector.Builder().requireLensFacing(
            CameraSelector.LENS_FACING_BACK
        ).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(aspectRatio)
            .setTargetRotation(previewRotation)
            .build()


        imageCapture =
            ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(aspectRatio)
                .setTargetRotation(previewRotation)
                .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setTargetAspectRatio(aspectRatio)
            .setTargetRotation(previewRotation)
            .build().apply { setAnalyzer(executor, analyzer) }

        cameraProvider.unbindAll()
        camera = cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            imageAnalysis
        )
        preview.setSurfaceProvider(surfaceProvider)
    }


    override suspend fun takePhoto() = suspendCancellableCoroutine { cont ->

        CoroutineScope(Dispatchers.IO).launch {
            imageCapture!!.takePicture(executor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        // 원본 이미지를 획득함
                        cont.resume(image)
                        super.onCaptureSuccess(image)
                    }

                    //에러가 나는 경우 에러를 반환한다.
                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                        super.onError(exception)
                    }

                })


        }


    }

    fun unbindCameraResources(): Boolean {
        return try {
            cameraProvider!!.unbindAll()
            true
        } catch (exc: Exception) {
            false
        }
    }


    override fun setZoomLevel(zoomLevel: Float) {
//        val minValue = camera.cameraInfo.zoomState.value!!.minZoomRatio
//        val maxValue = camera.cameraInfo.zoomState.value!!.maxZoomRatio
//        Log.d("MIN/MAX ZoomRatio: ","$minValue/$maxValue")
        camera!!.cameraControl.setZoomRatio(zoomLevel)
    }

    override fun setFocus(meteringPoint: MeteringPoint, durationMilliSeconds: Long) {
//        camera!!.cameraControl.startFocusAndMetering()
        camera!!.cameraControl.startFocusAndMetering(
            FocusMeteringAction.Builder(meteringPoint)
                .setAutoCancelDuration(durationMilliSeconds, TimeUnit.MILLISECONDS)
                .build()
        )


    }


    companion object {
        const val CAMERA_INIT_COMPLETE = 0
        const val CAMERA_INIT_ERROR = 1
    }
}