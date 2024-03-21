package com.hanadulset.pro_poseapp.data.datasource

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalZeroShutterLag
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
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class CameraDataSourceImpl(private val context: Context) : CameraDataSource {

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val executor by lazy {
        ContextCompat.getMainExecutor(context)
    }

    private var isOPENCVInit: Boolean = false

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null


    override suspend fun initCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        aspectRatio: Int,
        previewRotation: Int,
        analyzer: Analyzer
    ): CameraState = suspendCoroutine { cont ->
        if (!isOPENCVInit) isOPENCVInit = OpenCVLoader.initDebug()
        if (cameraProviderFuture == null && cameraProvider == null) {
            cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture!!.addListener({
                try {
                    cameraProvider = cameraProviderFuture!!.get()
                } catch (e: InterruptedException) {
                    cont.resume(CameraState(CAMERA_INIT_ERROR, e, e.message))
                } catch (e: ExecutionException) {
                    cont.resume(CameraState(CAMERA_INIT_ERROR, e, e.message))
                }
                if (bindCameraUseCases(
                        surfaceProvider = surfaceProvider,
                        lifecycleOwner = lifecycleOwner,
                        analyzer = analyzer,
                        aspectRatio = previewRotation,
                        previewRotation = aspectRatio,
                    )
                ) cont.resume(
                    CameraState(
                        CAMERA_INIT_COMPLETE,
                        imageAnalyzerResolution = imageAnalysis!!.resolutionInfo!!.resolution,
                    )
                )
            }, executor)
        } else {
            if (bindCameraUseCases(
                    surfaceProvider = surfaceProvider,
                    previewRotation = previewRotation,
                    lifecycleOwner = lifecycleOwner,
                    analyzer = analyzer,
                    aspectRatio = aspectRatio
                )
            )
                cont.resume(
                    CameraState(
                        CAMERA_INIT_COMPLETE,
                        imageAnalyzerResolution = imageAnalysis!!.resolutionInfo!!.resolution,

                        )
                )
        }

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


    @OptIn(ExperimentalZeroShutterLag::class)
    @SuppressLint("RestrictedApi")
    private fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        previewRotation: Int,
        aspectRatio: Int,
        analyzer: Analyzer
    ): Boolean {
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

        imageAnalysis =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio)
                .setTargetRotation(previewRotation)
                .build().apply {
                    setAnalyzer(
                        executor, analyzer
                    )
                }


        try {
            cameraProvider!!.unbindAll()
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider!!.bindToLifecycle(
                lifecycleOwner, // Use the provided lifecycle owner
                cameraSelector, preview, imageCapture, imageAnalysis
            )
            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(surfaceProvider)

            return true
            // Now you have the CameraControl instance if you need it
        } catch (exc: Exception) {
            // Handle camera initialization error
//            Log.e("Error on Init Camera", "에러입니다", exc)
            return false

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