package com.hanadulset.pro_poseapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.Image
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.SizeF
import androidx.camera.core.ImageProxy
import com.hanadulset.pro_poseapp.data.datasource.DownloadResourcesDataSourceImpl
import com.hanadulset.pro_poseapp.data.datasource.FileHandleDataSourceImpl
import com.hanadulset.pro_poseapp.data.datasource.ImageProcessDataSourceImpl
import com.hanadulset.pro_poseapp.data.datasource.ModelRunnerImpl
import com.hanadulset.pro_poseapp.data.datasource.feature.CompDataSourceImpl
import com.hanadulset.pro_poseapp.data.datasource.feature.PoseDataSourceImpl
import com.hanadulset.pro_poseapp.domain.repository.ImageRepository
import com.hanadulset.pro_poseapp.utils.camera.ImageResult
import com.hanadulset.pro_poseapp.utils.pose.PoseData
import com.hanadulset.pro_poseapp.utils.pose.PoseDataResult

class ImageRepositoryImpl(private val context: Context) : ImageRepository {
    private val modelRunnerImpl by lazy {
        ModelRunnerImpl(context)
    }

    private val poseDataSourceImpl by lazy {
        PoseDataSourceImpl(context)
    }

    private val imageProcessDataSource by lazy {
        ImageProcessDataSourceImpl()
    }

    private val fileHandleDataSource by lazy {
        FileHandleDataSourceImpl(context)
    }

    private val compDataSource by lazy {
        CompDataSourceImpl(modelRunnerImpl)
    }
    private val downloadResourcesDataSource by lazy {
        DownloadResourcesDataSourceImpl(context)
    }


    override suspend fun getRecommendCompInfo(backgroundBitmap: Bitmap) =
        compDataSource.recommendCompData(backgroundBitmap)


    override suspend fun getRecommendPose(
        backgroundBitmap: Bitmap
    ): PoseDataResult =
        poseDataSourceImpl.recommendPose(backgroundBitmap)


    override fun getFixedScreen(backgroundBitmap: Bitmap): Bitmap =
        imageProcessDataSource.getFixedImage(bitmap = backgroundBitmap).apply {
        }



    override suspend fun getLatestImage(): Uri? {
        val data = fileHandleDataSource.loadCapturedImages(false)
        return if (data.isEmpty()) null
        else data[0].dataUri
    }

    override suspend fun downloadResources() = downloadResourcesDataSource.startToDownload()


    override suspend fun checkForDownloadResources() =
        downloadResourcesDataSource.checkForDownload()


    override suspend fun preRunModel(): Boolean {
        return modelRunnerImpl.preRun()
    }

    //이미지에서 포즈를 가져오기
    override fun getPoseFromImage(uri: Uri?): Bitmap? = if (uri != null) {
        val backgroundBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri);
        }
        val softwareBitmap = backgroundBitmap.copy(Bitmap.Config.ARGB_8888, false)
        getFixedScreen(softwareBitmap)
    } else null

    override suspend fun loadAllCapturedImages(): List<ImageResult> =
        fileHandleDataSource.loadCapturedImages(true)


    override suspend fun deleteCapturedImage(uri: Uri): Boolean =
        fileHandleDataSource.deleteCapturedImage(uri)

    override suspend fun updateOffsetPoint(
        backgroundBitmap: Bitmap,
        targetOffset: SizeF
    ): SizeF? =
        imageProcessDataSource.useOpticalFlow(
            targetOffset = targetOffset,
            bitmap = backgroundBitmap
        )

    override fun stopPointOffset() {
        imageProcessDataSource.stopToUseOpticalFlow()
    }


}