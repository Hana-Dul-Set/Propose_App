package com.hanadulset.pro_poseapp.data.datasource.feature

import android.graphics.Bitmap
import com.hanadulset.pro_poseapp.data.datasource.ModelRunnerDataSourceDataSourceImpl
import com.hanadulset.pro_poseapp.data.datasource.interfaces.CompDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CompDataSourceImpl(private val modelRunner: ModelRunnerDataSourceDataSourceImpl) :
    CompDataSource {
    override suspend fun recommendCompData(backgroundBitmap: Bitmap): Pair<Float,Float> =
        suspendCoroutine {
            CoroutineScope(Dispatchers.Default).launch {
                it.resume(modelRunner.runVapNet(backgroundBitmap))
            }
        }

}