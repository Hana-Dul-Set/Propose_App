package com.hanadulset.pro_poseapp.data.datasource

//import org.opencv.core.Size
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.util.Log
import android.util.SizeF
import com.hanadulset.pro_poseapp.data.datasource.interfaces.ImageProcessDataSource
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import java.io.ByteArrayOutputStream
import kotlin.math.pow
import kotlin.math.sqrt


//이미지 처리
class ImageProcessDataSourceImpl : ImageProcessDataSource {

    private var prevFrame: Mat? = null
    private var prevPoint: SizeF? = null
    private var prevCornerPoint: MatOfPoint2f? = null

    override fun getFixedImage(bitmap: Bitmap): Bitmap {
        // No implementation found ~ 에러 해결
        OpenCVLoader.initDebug()
        val input = Mat()

        Utils.bitmapToMat(bitmap, input) // bitmap을 매트릭스로 변환
        Imgproc.cvtColor(input, input, Imgproc.COLOR_RGB2GRAY) //흑백으로 변경
        val hierarchy = Mat.zeros(input.size(),input.type())
        val output =Mat.zeros(input.size(),input.type())
        //Canny Edge Detection을 이용하여, 엣지를 추출함.

//        Imgproc.threshold(input,input,127.0,255.0,Imgproc.THRESH_BINARY)
//        Core.bitwise_not(input,input)
        Imgproc.Canny(input, output, 50.0, 150.0)
        val points = mutableListOf<MatOfPoint>()
        Imgproc.findContours(input,points, hierarchy, Imgproc.RETR_CCOMP,Imgproc.CHAIN_APPROX_NONE)
        for(i in points.indices){
            Imgproc.drawContours(output,points,i, Scalar(255.0,255.0,255.0))
        }

        return bitmap.copy(bitmap.config, true).apply {
            Utils.matToBitmap(output, this)
        }
    }


    private fun bitmapToMatWithOpenCV(bitmap: Bitmap): Mat {
        val resMat = Mat(bitmap.width, bitmap.height, CvType.CV_8UC3)
        Utils.bitmapToMat(bitmap, resMat)
        Imgproc.cvtColor(resMat, resMat, Imgproc.COLOR_RGB2GRAY)
        return resMat
    }

    override fun resizeBitmapWithOpenCV(bitmap: Bitmap, size: org.opencv.core.Size): Bitmap {
        val inputImageMat = Mat(bitmap.width, bitmap.height, CvType.CV_8UC3)
        val outputResizeBitmap =
            Bitmap.createBitmap(size.width.toInt(), size.height.toInt(), Bitmap.Config.ARGB_8888)
        Utils.bitmapToMat(bitmap, inputImageMat)
        Imgproc.cvtColor(inputImageMat, inputImageMat, Imgproc.COLOR_RGBA2RGB) //알파값을 빼고 저장
        Imgproc.resize(inputImageMat, inputImageMat, size)
        Utils.matToBitmap(inputImageMat, outputResizeBitmap)
        return outputResizeBitmap
    }


    override suspend fun useOpticalFlow(bitmap: Bitmap, targetOffset: SizeF): SizeF? {

        //이전 프레임이 없는 경우, 트래킹을 하지 않는다.
        try {
            if (prevFrame == null) {
                prevFrame =
                    bitmapToMatWithOpenCV(bitmap)
                prevPoint = targetOffset

                prevCornerPoint = MatOfPoint().apply {
                    Imgproc.goodFeaturesToTrack(prevFrame, this, 1000, 0.01, 10.0)
                }.let { goodCorner -> MatOfPoint2f().apply { fromList(goodCorner.toList()) } }

                return targetOffset
            } //흑백이미지 Matrix

            else {
                val outputFrame =
                    bitmapToMatWithOpenCV(bitmap) //흑백이미지 Matrix
                val outputState = MatOfByte()
                val outputErr = MatOfFloat()
                val outputCornerPoint = MatOfPoint().apply {
                    Imgproc.goodFeaturesToTrack(outputFrame, this, 1000, 0.01, 10.0)
                }.let { goodCorner -> MatOfPoint2f().apply { fromList(goodCorner.toList()) } }

                Video.calcOpticalFlowPyrLK(
                    prevFrame,
                    outputFrame,
                    prevCornerPoint,
                    outputCornerPoint,
                    outputState,
                    outputErr,
                )

                //트래킹에 실패하면, outputState.toList().map { it.toInt() }.toSet() <-  이 값이 [0]이 된다.
                val isFailToTrack = (outputState.toList().map { it.toInt() }.toSet() == setOf(0))


                return if (isFailToTrack.not()) {
                    val outputPoint =
                        calculateOffsetDiff(prevCornerPoint!!, outputCornerPoint, targetOffset)
                    prevFrame = outputFrame //업데이트
                    prevPoint = outputPoint
                    prevCornerPoint = outputCornerPoint
                    outputPoint
                } else null
            }
        } catch (ex: CvException) {
            Log.e("Error:", ex.message ?: "CVException Occurred")
            return null
        }
    }


    private fun calculateOffsetDiff(
        prevCornerPoint: MatOfPoint2f,
        outputCornerPoint: MatOfPoint2f,
        targetOffset: SizeF
    ): SizeF {
        val distanceArr = FloatArray(outputCornerPoint.toList().size)

        for (i in 0 until outputCornerPoint.toList().size) {
            val outputPoint = outputCornerPoint.toList()[i]
            distanceArr[i] = sqrt(
                (outputPoint.x - targetOffset.width).pow(2) + (outputPoint.y - targetOffset.height).pow(
                    2
                )
            ).toFloat()
        }
        val index = distanceArr.indices.minBy { distanceArr[it] }

        val prevX = prevCornerPoint.toList()[index].x
        val prevY = prevCornerPoint.toList()[index].y

        val outPutX = outputCornerPoint.toList()[index].x
        val outPutY = outputCornerPoint.toList()[index].y

        return targetOffset.let {
            SizeF(
                it.width + (outPutX - prevX).toFloat(),
                it.height + (outPutY - prevY).toFloat()
            )
        }
    }


    override fun stopToUseOpticalFlow() {
        prevFrame = null
        prevPoint = null
        prevCornerPoint = null
    }


}