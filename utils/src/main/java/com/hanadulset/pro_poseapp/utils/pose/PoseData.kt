package com.hanadulset.pro_poseapp.utils.pose

import android.net.Uri
import android.util.SizeF
import kotlinx.coroutines.flow.MutableStateFlow

//포즈 데이터 클래스
data class PoseData(
    val poseId: Int = 0,
    val poseCat: Int = 0,
    val bottomCenterRate: SizeF = SizeF(0F, 0F), //중심점 비율
    val sizeRate: SizeF = SizeF(0F, 0F),
    val imageUri: Uri? = null,
    val imageScale: Float = 1F,
)
