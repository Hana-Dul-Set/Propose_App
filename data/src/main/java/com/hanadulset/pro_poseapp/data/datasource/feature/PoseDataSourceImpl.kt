package com.hanadulset.pro_poseapp.data.datasource.feature

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.SizeF
import androidx.core.net.toUri
import com.hanadulset.pro_poseapp.data.datasource.interfaces.PoseDataSource
import com.hanadulset.pro_poseapp.utils.ImageUtils
import com.hanadulset.pro_poseapp.utils.pose.PoseData
import com.hanadulset.pro_poseapp.utils.pose.PoseDataResult
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.ExperimentalTime

class PoseDataSourceImpl(private val applicationContext: Context) : PoseDataSource {

    private lateinit var centroid: MutableList<List<Double>>
    private lateinit var poseRanks:List<List<PoseData>>

    private fun initPoseRankList(): List<List<PoseData>> {
        val poseDataList = mutableListOf<List<PoseData>>()
        val rankList = applicationContext.assets.open("pose_ranks.csv").use { stream ->
            val resMutableList = mutableListOf<List<Double>>()
            CSVReader(InputStreamReader(stream)).forEach { strings ->
                if (strings[1].equals("pose_ids").not()) {
                    val listStrData = strings[1].substring(1, strings[1].length - 1)
                    listStrData.split(',').map { it.toDouble() }.let {
                        resMutableList.add(it)
                    }
                }
            }
            resMutableList
        }

        //저장된 이미지와 데이터를 매핑시킨다.
        val imageDataList: MutableList<PoseData> = applicationContext.assets.open("image_datas.csv").use { stream ->
            val resultList = mutableListOf<PoseData>()
            val imageRes = loadPoseImages()
            CSVReaderBuilder(InputStreamReader(stream)).withCSVParser(
                CSVParserBuilder().withSeparator(',').build()
            ).build().readAll().run {
                //첫 줄은 생략하고 시작함.
                this.subList(1, this.size).forEach { strings ->
                    val center =
                        strings[1].replace("[", "").replace("]", "").split(",").map {
                            it.toFloat()
                        }
                    val size = strings[2].replace("[", "").replace("]", "").split(",")
                        .map { it.toFloat() }

                    resultList.add(
                        PoseData(
                            poseId = strings[0].toInt(),
                            bottomCenterRate = SizeF(center[0], center[1]),
                            sizeRate = SizeF(size[0], size[1])
                        )
                    )
                }
                resultList.sortBy { it.poseId }
                for (i in resultList.indices) {
                    resultList[i] = resultList[i].copy(imageUri = imageRes[i])
                }
            }
            resultList
        }


        for (idx in rankList.indices) {
            val tmp = mutableListOf<PoseData>()
            rankList[idx].forEach { poseId ->
                tmp.add(imageDataList[poseId.toInt()].copy(poseCat = idx))
            }
            poseDataList.add(tmp)
        }
        return poseDataList.toList()
    }


    override suspend fun recommendPose(backgroundBitmap: Bitmap): PoseDataResult {
        val histogramMap = withContext(Dispatchers.Default) {
            getHistogramMap(backgroundBitmap)
        }

        val angle = withContext(Dispatchers.Default) {
            getAngleFromHog(histogramMap)
        }

        return CoroutineScope(Dispatchers.Default).async {
            var res = Pair(-1, java.lang.Double.POSITIVE_INFINITY)
            for (i in 0 until centroid.size - 2) {
                val calculatedDistance = getDistance(angle, i)
                if (res.second > calculatedDistance) res =
                    res.copy(first = i, second = calculatedDistance)
            }
            val backgroundId = res.first //백그라운드 클러스터 ID
            val poseDataResult = PoseDataResult(
                poseDataList = poseRanks[backgroundId].toMutableList(),
                backgroundId = backgroundId,
                backgroundAngleList = angle
            )
            poseDataResult
        }.await()
    }

    override fun preparePoseData() {
        poseRanks = initPoseRankList()
        centroid = initCentroidValue()
    }

    private fun initCentroidValue(): MutableList<List<Double>> =
        applicationContext.assets.open("centroids.csv").use { stream ->
            val resMutableList = mutableListOf<List<Double>>()
            CSVReader(InputStreamReader(stream)).forEach {
                //앞에 라벨 번호가 있는 것들만 데이터를 가져와보자
                if (!it.contains("label")) {
                    val tmpList = mutableListOf<Double>()
                    val length = it.size
                    tmpList.add(it[1].substring(1).toDouble())
                    for (i in 2 until length - 1) tmpList.add(it[i].toDouble())
                    tmpList.add(
                        it[length - 1].substring(0 until it[length - 1].length - 1).toDouble()
                    )
                    resMutableList.add(tmpList)
                }
            }
            resMutableList
        }


    override suspend fun preProcessing(image: Bitmap): Mat = withContext(Dispatchers.Default) {
        val resizedImageMat = Mat(image.width, image.height, CvType.CV_8UC3)
        Utils.bitmapToMat(image, resizedImageMat)
        Imgproc.cvtColor(
            resizedImageMat,
            resizedImageMat,
            Imgproc.COLOR_RGBA2RGB
        ) //알파값을 빼고 저장
        Imgproc.resize(resizedImageMat, resizedImageMat, HogConfig.imageResize)
        //10.01 추가
        Imgproc.medianBlur(
            resizedImageMat,
            resizedImageMat,
            HogConfig.blurSize.width.toInt()
        )
        resizedImageMat
    }

    override fun getDistance(angle: List<Double>, centroidIdx: Int): Double {
        val weight = 50.0
        val centroidGHog = centroid[centroidIdx]
        val distanceHog = distanceAngle(angle, centroidGHog)
        return weight * distanceHog
    }


    override fun distanceAngle(aAngle: List<Double>, bAngle: List<Double>): Double {
        var distance = 0.0
        for (idx in aAngle.indices) {
            distance += if ((aAngle[idx] == -1.0 && bAngle[idx] != -1.0) || (aAngle[idx] != -1.0 && bAngle[idx] == -1.0)) 1.0
            else 1 - abs(cos(aAngle[idx] - bAngle[idx]))
        }
        return distance
    }

    override fun distanceHog(aHog: List<Double>, bHog: List<Double>): Double {
        val imageResizeConfig = 128.0
        val cellSizeConfig = 16.0
        val histCnt = (imageResizeConfig / cellSizeConfig).pow(2).toInt()
        val binCnt = 9
        val reshapedAHog =
            reshapeList(aHog, listOf(histCnt, binCnt)).toMutableList()
                .apply { addZDimension(this) }
        val reshapedBHog =
            reshapeList(bHog, listOf(histCnt, binCnt)).toMutableList().apply {
                addZDimension(this)
            }
        return calculateDistance(reshapedAHog, reshapedBHog)
    }

    override fun addZDimension(arr: MutableList<List<Double>>) {
        val arrShape = arr.size
        val zeroArr = List(arrShape) { 0.0 }
        val normArr = MutableList(arrShape) { 0.0 }
        normArr[0] = 1.0
        normArr[1] = 1.0

        val arrTrue = arr.map { sublist ->
            sublist.all { value -> abs(value) < 1e-6 } // Adjust tolerance as needed
        }
        val arrZDim = arrTrue.mapIndexed { index, value ->
            if (value) normArr else zeroArr
        }

        val arrZDimReshaped = arrZDim.flatten()
        val arrConcat = arr.mapIndexed { index, sublist ->
            sublist + arrZDimReshaped[index]
        }
        arr.clear()
        arr.addAll(arrConcat)
    }

    override fun calculateDistance(a: List<List<Double>>, b: List<List<Double>>): Double {
        require(a.size == b.size && a.isNotEmpty()) { "Input lists must have the same non-empty size." }

        val numElements = a.size * a[0].size

        val diffSquaredSum = a.zip(b).sumOf { (aSublist, bSublist) ->
            require(aSublist.size == bSublist.size) { "Sublists must have the same size." }
            aSublist.zip(bSublist).sumOf { (aVal, bVal) ->
                val diff = aVal - bVal
                diff * diff
            }
        }

        return sqrt(diffSquaredSum) / numElements
    }

    override fun reshapeList(
        inputList: List<Double>,
        newShape: List<Int>
    ): List<List<Double>> {
        val totalElements = inputList.size
        val newTotalElements = newShape.reduce { acc, i -> acc * i }

        require(totalElements == newTotalElements) { "Total elements in input list must match new shape, total: $totalElements / new:$newTotalElements" }

        val result = mutableListOf<List<Double>>()
        var currentIndex = 0

        for (dimension in newShape) {
            val sublist = inputList.subList(currentIndex, currentIndex + dimension)
            result.add(sublist)
            currentIndex += dimension
        }
        return result
    }

    override suspend fun getGradient(targetImage: Mat): Pair<Mat, Mat> {
        var gradientX = Mat(targetImage.size(), targetImage.type())
        var gradientY = Mat(targetImage.size(), targetImage.type())

        Imgproc.Sobel(targetImage, gradientX, CvType.CV_64F, 1, 0, 3)
        Imgproc.Sobel(targetImage, gradientY, CvType.CV_64F, 0, 1, 3)

        gradientX = gradientX.apply {
            val zeroMat = Mat.zeros(this.size(), this.type())
            val absMat = Mat(this.size(), this.type())
            Core.absdiff(this, zeroMat, absMat)//abs값으로 변환
            val std = Core.minMaxLoc(absMat)
            val dv = if (std.maxVal != 0.0) std.maxVal else 1.0
            Core.divide(this, Scalar(dv), this)
            Core.multiply(this, Scalar(255.0), this)
        }
        gradientY = gradientY.apply {
            val zeroMat = Mat.zeros(this.size(), this.type())
            val absMat = Mat(this.size(), this.type())
            Core.absdiff(this, zeroMat, absMat)//abs값으로 변환
            val std = Core.minMaxLoc(absMat)
            val dv = if (std.maxVal != 0.0) std.maxVal else 1.0
            Core.divide(this, Scalar(dv), this)
            Core.multiply(this, Scalar(255.0), this)
        }


        val gradientMagnitude = Mat(gradientX.size(), gradientX.type()).apply {
            val tmpY = Mat(gradientY.size(), gradientY.type())
            Core.pow(gradientX, 2.0, this)
            Core.pow(gradientY, 2.0, tmpY)
            Core.add(this, tmpY, this)
            Core.pow(this, 0.5, this)
        }

        // Calculate gradient orientation using OpenCV
        val gradientOrientation = Mat.zeros(gradientX.size(), gradientX.type()).apply {
            Core.phase(gradientX, gradientY, this, true)
        }

        // Adjust gradient orientation to [-π, π] range -> 이부분만 살짝 수정하면 Hog값은 100퍼센트 나옴.
        for (row in 0 until gradientOrientation.rows()) {
            for (col in 0 until gradientOrientation.cols()) {
                var adjustedPhase = gradientOrientation[row, col][0]
                adjustedPhase %= 180
                gradientOrientation.put(row, col, adjustedPhase)
            }
        }

        return Pair(gradientMagnitude, gradientOrientation)
    }

    override suspend fun getHistogram(magnitude: Mat, orientation: Mat): DoubleArray {
        val maxDegree = 180.0
        val diff = maxDegree / HogConfig.nBins
        var histogram = DoubleArray(HogConfig.nBins)
        val cellSize = magnitude.size()

        for (x in 0 until cellSize.width.toInt()) {
            for (y in 0 until cellSize.height.toInt()) {
                val magValue = magnitude[x, y][0]
                val orientationValue = orientation[x, y][0]
                val index = (orientationValue / diff).toInt()
                val deg = index * diff
                histogram[index] += magValue * (1 - (orientationValue - deg) / diff)

                val nextIndex = (index + 1) % HogConfig.nBins
                histogram[nextIndex] += magValue * ((orientationValue - deg) / diff)
            }
        }

        val maxVal = histogram.max()

        histogram = histogram.map {
            if (it == maxVal && maxVal != 0.0) 1.0
            else 0.0
        }.toDoubleArray()

        return histogram
    }


    @OptIn(ExperimentalTime::class)
    override suspend fun getHistogramMap(backgroundBitmap: Bitmap): Mat {
        //이미지
//        Log.d("ASDF1",System.currentTimeMillis().toString())
        val resizedImage = preProcessing(backgroundBitmap)

//        Log.d("ASDF2",System.currentTimeMillis().toString())
        val resizedImageMats = arrayListOf(
            Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_8UC1),
            Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_8UC1),
            Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_8UC1)
        )

//        Log.d("ASDF3",System.currentTimeMillis().toString())
        Core.split(resizedImage, resizedImageMats)
        val resList = ArrayList<Pair<Mat, Mat>>()

//        Log.d("ASDF4",System.currentTimeMillis().toString())
        for (mat in resizedImageMats) {
            resList.add(getGradient(mat))
        }
//        Log.d("ASDF5",System.currentTimeMillis().toString())
        val resListMagnitudeDump = mutableListOf<List<List<Double>>>()
        val resListOrientationDump = mutableListOf<List<List<Double>>>()
        val resDump = mutableListOf<List<List<List<Double>>>>()
        for (pair in resList) {
            resListMagnitudeDump.add(pair.first.to3dList()[0])
            resListOrientationDump.add(pair.second.to3dList()[0])
        }
//        Log.d("ASDF6",System.currentTimeMillis().toString())

        //for 문 속도 개선
        for (i in resListMagnitudeDump.indices) {
            resDump.add(listOf(resListMagnitudeDump[i], resListOrientationDump[i]))
        }

//        Log.d("ASDF7",System.currentTimeMillis().toString())
        //변수 초기화 -> 모든 칸의 값을 0으로 초기화하여 진행한다.
        val resMagnitude =
            Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_64FC1)
        val resOrientation =
            Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_64FC1)
        val cnt = Mat.zeros(resizedImage.width(), resizedImage.height(), CvType.CV_8UC1)


//        Log.d("ASDF8",System.currentTimeMillis().toString())
        //for 문 속도 개선
        resList.forEach {
            val magnitude = it.first
            val orientation = it.second
            for (row in 0 until magnitude.rows()) {
                for (col in 0 until magnitude.cols()) {
                    if (magnitude[row, col][0] != 0.0) {
                        resMagnitude.put(
                            row,
                            col,
                            resMagnitude.get(row, col)[0] + magnitude[row, col][0]
                        )
                        resOrientation.put(
                            row,
                            col,
                            resOrientation[row, col][0] + orientation[row, col][0]
                        )
                        cnt.put(row, col, cnt[row, col][0] + 1)
                    }
                }
            }
        }
//        Log.d("ASDF9",System.currentTimeMillis().toString())


        //for 문 속도 개선
        for (row in 0 until cnt.rows()) {
            for (col in 0 until cnt.cols()) {
                val currentCnt = cnt[row, col][0]
                if (currentCnt != 0.0) {
                    resMagnitude.put(
                        row, col, resMagnitude[row, col][0] / currentCnt
                    )
                    resOrientation.put(
                        row, col, resOrientation[row, col][0] / currentCnt
                    )
                }
            }
        }
//        Log.d("ASDF10",System.currentTimeMillis().toString())

        val ave =
            Core.sumElems(resMagnitude).`val`[0] / (resMagnitude.width() * resMagnitude.height())
//        Log.d("ASDF11",System.currentTimeMillis().toString())
        for (row in 0 until cnt.rows()) {
            for (col in 0 until cnt.cols()) {
                resMagnitude.put(
                    row,
                    col,
                    if (ave * HogConfig.magnitudeThreshold / 10 > resMagnitude[row, col][0]) 0.0
                    else 1.0
                )
            }
        }
//        Log.d("ASDF12",System.currentTimeMillis().toString())
        val arrayList = ArrayList<Double>()
        for (row in 0 until cnt.rows()) {
            for (col in 0 until cnt.cols())
                arrayList.add(resMagnitude[row, col][0])
        }
//        Log.d("ASDF13",System.currentTimeMillis().toString())

        val histogramMap = Mat.zeros(
            Size(
                resizedImage.width() / HogConfig.cellSize.width,
                resizedImage.height() / HogConfig.cellSize.height
            ), CvType.CV_64FC(HogConfig.nBins)
        )
//        Log.d("ASDF14",System.currentTimeMillis().toString())

        for (x in 0 until resizedImage.width() step HogConfig.cellSize.height.toInt()) {
            for (y in 0 until resizedImage.height() step HogConfig.cellSize.width.toInt()) {

                val xEnd = x + HogConfig.cellSize.width.toInt()
                val yEnd = y + HogConfig.cellSize.height.toInt()
                val cellMagnitude = resMagnitude.submat(x, xEnd, y, yEnd)
                val cellOrientation = resOrientation.submat(x, xEnd, y, yEnd)

                val histogram = getHistogram(
                    cellMagnitude, cellOrientation
                )



                histogramMap.put(
                    x / HogConfig.cellSize.width.toInt(),
                    y / HogConfig.cellSize.height.toInt(),
                    *histogram
                )
            }
        }
//        Log.d("ASDF15",System.currentTimeMillis().toString())


        return histogramMap
    }

    override fun getAngleFromHog(histogramMap: Mat): List<Double> {
        val angleMap = MutableList(histogramMap.rows() * histogramMap.cols()) { -1.0 }

        for (row in 0 until histogramMap.rows()) {
            for (col in 0 until histogramMap.cols()) {
                if (histogramMap[row, col].all { it == 0.0 }
                        .not()) for (index in histogramMap[row, col].indices) {
                    val value = histogramMap[row, col][index]
                    if (value == 1.0) {
                        //angleMap에 데이터가 저장됨
                        angleMap[row * histogramMap.rows() + col] =
                            index * 180.0 / HogConfig.nBins
                        break
                    }
                }
            }
        }

        return angleMap.toList()
    }

    private fun loadPoseImages(): List<Uri> {
        val file = File(applicationContext.dataDir, "/silhouettes")
//        if (file.exists().not()) {
        val tmpFile = File(applicationContext.dataDir, SILHOUETTE_IMAGE_ZIP)
        //데이터를 옮김
        applicationContext.assets.open(SILHOUETTE_IMAGE_ZIP).use { `is` ->
            FileOutputStream(tmpFile).use { os ->
                val buffer = ByteArray(4 * 1024)
                var read: Int
                while (`is`.read(buffer).also { read = it } != -1) {
                    os.write(buffer, 0, read)
                }
                os.flush()
            }
        }
        unzip(tmpFile, file.parentFile!!.absolutePath)
//        }

        return mutableListOf<Uri>().let { uriList ->
            mutableListOf<Pair<Int, Uri>>().run {
                file.listFiles()?.forEach { image ->
                    val name = image.name.replace(".png", "").toInt()
                    val uri = image.toUri()
                    add(Pair(name, uri))
                }
                sortBy { imageData -> imageData.first }
                forEach {
                    uriList.add(it.first, it.second)
                }
            }
            uriList
        }

    }

    private fun unzip(sourceZip: File, targetDir: String) {
        val dir = File(targetDir)
        if (dir.exists().not()) dir.mkdir()
        ImageUtils.unZip(sourceZip, targetDir)
    }


    companion object {

        object HogConfig {
            val imageResize: Size = Size(128.0, 128.0)
            const val imageConvert: Int =
                Imgproc.COLOR_BGR2GRAY  //cv2.COLOR_BGR2GRAY | cv2.COLOR_BGR2RGB | cv2.COLOR_BGR2HSV
            val cellSize: Size = Size(16.0, 16.0)
            val blurSize = Size(17.0, 17.0)
            const val magnitudeThreshold: Int = 15 //11.18 수정
            const val nBins: Int = 12 //11.18 수정
        }

        const val SILHOUETTE_IMAGE_ZIP = "silhouette_image.zip"

    }


    private fun Mat.to3dList(): List<List<List<Double>>> {
        val channels = this.channels()
        val height = this.height()
        val width = this.width()

        return List(channels) { channel ->
            List(height) { y ->
                List(width) { x ->
                    this.get(x, y)[channel]
                }
            }
        }
    }
}