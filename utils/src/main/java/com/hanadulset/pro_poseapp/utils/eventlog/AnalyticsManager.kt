package com.hanadulset.pro_poseapp.utils.eventlog

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.provider.Settings

@SuppressLint("HardwareIds")
class AnalyticsManager(contentResolver: ContentResolver) {
//        private val analytics = Firebase.analytics
    private val deviceID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    private val isOnDebug = true


    private fun makeLogEvent(eventName: String, params: HashMap<String, Any>?) {
//        analytics.run {
//            if (isOnDebug.not()) {
//                setUserId(deviceID)
//                logEvent(eventName) {
//                    param("deviceID", deviceID)
//                    param("timestamp", System.currentTimeMillis())
//                    params?.let{
//                        makeParameterBuilder(it)()
//                    }
//                }
//            }
//        }
    }

//    private fun makeParameterBuilder(data: HashMap<String, Any>): ParametersBuilder.() -> Unit = {
//        data.forEach {
//            when (it.value) {
//                is Double -> param(it.key, it.value as Double)
//                is Int -> param(it.key, (it.value as Int).toDouble())
//                is String -> param(it.key, it.value as String)
//                else -> {
//                    /*Pass*/
//                }
//            }
//        }
//    }


    fun saveUserAgreeToUseEvent() = makeLogEvent(
        eventName = EVENT_SUCCESS_TO_USE, params = hashMapOf
            ("user_answer" to true.toString())
    )

    fun saveAppOpenEvent() = makeLogEvent(EVENT_APP_OPEN, null)
    fun saveAppClosedEvent() = makeLogEvent(EVENT_APP_CLOSE, null)

    fun saveCapturedEvent(
        captureEventData: CaptureEventData,
        estimationResult: List<Triple<Float, Float, Float>?>
    ) {
        val encryptData = HashMap<String, Any>()
        captureEventData.run {
            val hog = backgroundHog.toString()
            val prev = prevRecommendPoses.toString()
            val poseEstimationResult = estimationResult.toString()
            encryptData["poseID"] = poseID.toDouble()
            encryptData["backgroundId"] = backgroundId?.toDouble() ?: -1.0
            if (prev.length > 99) {
                prev.chunked(99).forEachIndexed { index, s ->
                    encryptData["prevRecommendPoses_$index"] = s
                }
            } else {
                encryptData["prevRecommendPoses"] = prev
            }
            if (hog.length > 99) {
                hog.chunked(99).forEachIndexed { index, s ->
                    encryptData["backgroundHog_$index"] = s
                }
            } else {
                encryptData["backgroundHog"] = hog
            }
            if (poseEstimationResult.length > 99) {
                poseEstimationResult.chunked(99).forEachIndexed { index, s ->
                    encryptData["human_pose_estimation_$index"] = s
                }
            } else {
                encryptData["human_pose_estimation"] = poseEstimationResult
            }
            makeLogEvent(EVENT_CAPTURE, encryptData)
        }

    }


    companion object {
        private const val EVENT_CAPTURE = "EVENT_CAPTURE"
        private const val EVENT_APP_CLOSE = "EVENT_APP_CLOSE"
        private const val EVENT_SUCCESS_TO_USE = "EVENT_SUCCESS_TO_USE"
        private const val EVENT_APP_OPEN = "EVENT_APP_OPEN"
    }
}