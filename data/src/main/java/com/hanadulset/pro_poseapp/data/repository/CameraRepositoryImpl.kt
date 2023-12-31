package com.hanadulset.pro_poseapp.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaActionSound
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
import com.hanadulset.pro_poseapp.utils.eventlog.AnalyticsManager
import com.hanadulset.pro_poseapp.utils.eventlog.CaptureEventData
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraRepositoryImpl @Inject constructor(private val applicationContext: Context) :
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


    override suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        aspectRatio: Int,
        previewRotation: Int,
        analyzer: ImageAnalysis.Analyzer,
    ) = cameraDataSource.initCamera(
        lifecycleOwner, surfaceProvider, aspectRatio, previewRotation, analyzer
    )

    @SuppressLint("HardwareIds")
    @OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override suspend fun takePhoto(eventData: CaptureEventData) =
        cameraDataSource.takePhoto()
            .let { data ->
                val capturedImageBitmap = data.use {
                    ImageUtils.imageToBitmap(
                        it.image!!,
                        it.imageInfo.rotationDegrees
                    )
                }
                val res = fileHandleDataSourceImpl.saveImageToGallery(capturedImageBitmap)
                val poseEstimationResult = estimatePose(capturedImageBitmap)
                analyticsManager.saveCapturedEvent(
                    captureEventData = eventData,
                    poseEstimationResult
                )
                res
            }


    override fun setZoomRatio(zoomLevel: Float) =
        cameraDataSource.setZoomLevel(zoomLevel)

    //카메라 소리
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

    override fun setFocus(meteringPoint: MeteringPoint, durationMilliSeconds: Long) {
        cameraDataSource.setFocus(meteringPoint, durationMilliSeconds)
    }

    override fun sendUserFeedBackData(eventLogs: ArrayList<CaptureEventData>) {

    }

    override fun unbindCameraResource() =
        cameraDataSource.unbindCameraResources()

    private suspend fun estimatePose(bitmap: Bitmap): List<Triple<Float, Float, Float>?> {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()
        val poseDetector = PoseDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)
        val processTask = poseDetector.process(image)
        val resultPoseData = processTask.await()
        val returnList = MutableList<Triple<Float, Float, Float>?>(33) { null }
        resultPoseData.allPoseLandmarks.forEach {
            returnList[it.landmarkType] = it.position3D.run { Triple(x, y, z) }
        }
        return returnList.toList()
    }
}
