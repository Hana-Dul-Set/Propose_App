package com.hanadulset.pro_poseapp.presentation.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object UIComponents {

    // 원형으로 도는 대기 바
    @Composable
    fun CircularWaitingBar(
        modifier: Modifier = Modifier,
        barSize: Dp = 60.dp,
        barColor: Color = LocalColors.current.primaryGreen100,
        backgroundColor: Color = LocalColors.current.subSecondaryGray100
    ) {
        val colorTheme = LocalColors.current
        CircularProgressIndicator(
            modifier = modifier
                .size(barSize),
            color = colorTheme.primaryGreen100,
            backgroundColor = colorTheme.subSecondaryGray100,
            strokeCap = StrokeCap.Round,
            strokeWidth = 8.dp
        )
    }


    //구도 버튼 아이콘
    @Composable
    fun CompIconButton(

    ) {

    }


}

