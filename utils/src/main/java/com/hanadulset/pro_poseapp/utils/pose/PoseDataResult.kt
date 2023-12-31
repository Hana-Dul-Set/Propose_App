package com.hanadulset.pro_poseapp.utils.pose

data class PoseDataResult(
    val poseDataList: MutableList<PoseData>, //포즈 결과 목록
    val backgroundAngleList: List<Double>, //백그라운드 이미지 앵글값 목록
    val backgroundId: Int, //백그라운드 이미지 클러스터링 아이디
)