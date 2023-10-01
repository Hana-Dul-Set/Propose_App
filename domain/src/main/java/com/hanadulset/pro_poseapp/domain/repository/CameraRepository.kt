package com.hanadulset.pro_poseapp.domain.repository

import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.hanadulset.pro_poseapp.utils.camera.CameraState
import com.hanadulset.pro_poseapp.utils.eventlog.EventLog


//카메라 기능을 담당하는 레포지토리
interface CameraRepository {
    suspend fun initCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        aspectRatio: Int,
        previewRotation: Int,
        analyzer: ImageAnalysis.Analyzer,
    ): CameraState

    suspend fun takePhoto(): Uri
    fun setZoomRatio(zoomLevel: Float)
    fun sendCameraSound()
    fun setFocus(meteringPoint: MeteringPoint, durationMilliSeconds: Long)
    suspend fun trackingXYPoint(
        inputFrame: ImageProxy,
        inputOffset: Pair<Float, Float>,
        radius: Int
    ): Pair<Float, Float>
    fun stopTracking()

    fun sendUserFeedBackData(eventLogs: ArrayList<EventLog>)
}