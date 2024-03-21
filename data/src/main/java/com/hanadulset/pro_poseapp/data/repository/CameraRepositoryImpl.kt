package com.hanadulset.pro_poseapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaActionSound
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.hanadulset.pro_poseapp.data.datasource.CameraDataSourceImpl
import com.hanadulset.pro_poseapp.data.datasource.FileHandleDataSourceImpl
import com.hanadulset.pro_poseapp.domain.repository.CameraRepository
import com.hanadulset.pro_poseapp.utils.ImageUtils
import com.hanadulset.pro_poseapp.utils.camera.CameraState
import com.hanadulset.pro_poseapp.utils.eventlog.AnalyticsManager
import com.hanadulset.pro_poseapp.utils.eventlog.CaptureEventData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * This class provides the implementation of the camera-related business logic.
 * It interacts with the camera-related data sources and domain services to provide
 * the necessary functionality for the camera feature.
 */
@Singleton
class CameraRepositoryImpl @Inject constructor(@ApplicationContext private val applicationContext: Context) :
    CameraRepository {

    private val cameraDataSource by lazy {
        CameraDataSourceImpl(applicationContext)
    }

    private val shutterSoundManager by lazy {
        applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val fileHandleDataSourceImpl by lazy {
        FileHandleDataSourceImpl(context = applicationContext)
    }

    private val analyticsManager by lazy {
        AnalyticsManager(applicationContext.contentResolver)
    }

    /**
     * Binds the camera to the given [LifecycleOwner], [Preview.SurfaceProvider], [aspectRatio], [previewRotation], and [analyzer].
     *
     * @param lifecycleOwner the [LifecycleOwner] that controls the lifecycle of the camera
     * @param surfaceProvider the [Preview.SurfaceProvider] that provides the [Surface] for the camera preview
     * @param aspectRatio the aspect ratio of the camera preview
     * @param previewRotation the rotation of the camera preview
     * @param analyzer the [ImageAnalysis.Analyzer] that is used to analyze images captured by the camera
     * @return the [CameraState] indicating the status of the camera binding
     */
    override suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        aspectRatio: Int,
        previewRotation: Int,
        analyzer: ImageAnalysis.Analyzer,
    ): CameraState {
        return cameraDataSource.initCamera(
            lifecycleOwner,
            surfaceProvider,
            aspectRatio,
            previewRotation,
            analyzer
        )
    }

    @OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override suspend fun takePhoto(eventData: CaptureEventData): Uri {
        return cameraDataSource.takePhoto()
            .let { data ->
                val capturedImageBitmap = data.use {
                    ImageUtils.imageToBitmap(
                        it.image!!,
                        it.imageInfo.rotationDegrees
                    )
                }
                val resPhoto = fileHandleDataSourceImpl.saveImageToGallery(capturedImageBitmap)
                val poseEstimationResult = estimatePose(capturedImageBitmap)
                analyticsManager.saveCapturedEvent(
                    captureEventData = eventData,
                    poseEstimationResult
                )
                resPhoto
            }
    }

    /**
     * Sets the zoom ratio of the camera.
     *
     * @param zoomLevel the zoom level to set
     */
    override fun setZoomRatio(zoomLevel: Float) =
        cameraDataSource.setZoomLevel(zoomLevel)

    /**
     * Plays the shutter sound of the camera.
     */
    override fun sendCameraSound() {
        shutterSoundManager.setStreamVolume(
            AudioManager.STREAM_SYSTEM,
            1,
            AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
        )
        MediaActionSound().apply {
            play(MediaActionSound.SHUTTER_CLICK)
        }
    }

    /**
     * Sets the focus of the camera to the given [MeteringPoint] for the given duration in milliseconds.
     *
     * @param meteringPoint the [MeteringPoint] to set the focus to
     * @param durationMilliSeconds the duration in milliseconds for which to keep the focus set
     */
    override fun setFocus(meteringPoint: MeteringPoint, durationMilliSeconds: Long) {
        cameraDataSource.setFocus(meteringPoint, durationMilliSeconds)
    }

    /**
     * Unbinds the camera resources.
     */
    override fun unbindCameraResource() =
        cameraDataSource.unbindCameraResources()

    /**
     * Estimates the pose of the given [bitmap] using the Google ML Kit Pose Detection API.
     *
     * @param bitmap the [Bitmap] to estimate the pose of
     * @return the estimated pose as a list of [Triple]s, where each [Triple] represents the position of a specific pose landmark
     */
    private suspend fun estimatePose(bitmap: Bitmap): List<Triple<Float, Float, Float>?> =
        suspendCoroutine { cont ->
            val options = AccuratePoseDetectorOptions.Builder()
                .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
                .build()
            val poseDetector = PoseDetection.getClient(options)
            val image = InputImage.fromBitmap(bitmap, 0)
            val processTask = poseDetector.process(image)
            // Only proceed if the processing is successful
            processTask.addOnSuccessListener { resultPoseData ->
                val returnList = MutableList<Triple<Float, Float, Float>?>(33) { null }
                resultPoseData.allPoseLandmarks.forEach {
                    returnList[it.landmarkType] = it.position3D.run { Triple(x, y, z) }
                }
                cont.resume(returnList.toList())
            }.addOnFailureListener {
                cont.resumeWithException(it) // Return error
            }
        }
}

