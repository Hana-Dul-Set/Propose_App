package com.hanadulset.pro_poseapp.data.datasource

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
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
import java.text.SimpleDateFormat
import java.util.EnumSet
import java.util.Locale
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
                    Log.e(this::class.java.name, "Error starting camera")
                } catch (e: ExecutionException) {
                    cont.resume(CameraState(CAMERA_INIT_ERROR, e, e.message))
                    Log.e(this::class.java.name, "Error starting camera")
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
            Log.e("Error on Init Camera", "에러입니다", exc)
            return false

        }
    }

    fun unbindCameraResources(): Boolean {
        return try {
            cameraProvider!!.unbindAll()
            Log.d("available cameras: ", cameraProvider!!.availableCameraInfos.map { it.cameraState.value }.toString())
            true
        } catch (exc: Exception) {
            false
        }
    }

    private suspend fun saveImageAndSendUri(): Uri = suspendCoroutine { cont ->
        val sdf = SimpleDateFormat(
            DATE_FORMAT,
            Locale.KOREA
        ).format(System.currentTimeMillis())
        val name = "IMG_${sdf}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, PHOTO_TYPE)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${APP_NAME}")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture!!.takePicture(
            outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri!!
                    Log.d(TAG, "Photo capture succeeded: $savedUri")

                    cont.resume(savedUri)
                }
            })
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
        private const val PHOTO_TYPE = "image/jpeg"
        private const val DATE_FORMAT = "yyyy_MM_dd_HH_mm_ss_SSS"
        private const val APP_NAME = "Pro_Pose"
        private val TAG = this::class.simpleName
        const val CAMERA_INIT_COMPLETE = 0
        const val CAMERA_INIT_ERROR = 1
    }
}