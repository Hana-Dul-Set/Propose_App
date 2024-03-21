package com.hanadulset.pro_poseapp.domain.repository

import com.hanadulset.pro_poseapp.utils.UserSet
import com.hanadulset.pro_poseapp.utils.eventlog.CaptureEventData
import kotlinx.coroutines.flow.Flow

//사용자 흔적을 기록하는 역할을 담당하는 레포지토리
interface UserRepository {
    suspend fun loadUserSet(): UserSet
    suspend fun saveUserSet(userSet: UserSet)
    suspend fun saveUserSuccessToTermOfUse()
    suspend fun checkUserSuccessToTermOfUse(): Boolean

}