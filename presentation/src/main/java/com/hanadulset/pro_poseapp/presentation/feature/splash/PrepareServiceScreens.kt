package com.hanadulset.pro_poseapp.presentation.feature.splash

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.hanadulset.pro_poseapp.presentation.R
import com.hanadulset.pro_poseapp.presentation.component.LocalColors
import com.hanadulset.pro_poseapp.presentation.component.LocalTypography
import com.hanadulset.pro_poseapp.presentation.component.UIComponents
import com.hanadulset.pro_poseapp.presentation.core.CustomDialog
import com.hanadulset.pro_poseapp.utils.camera.CameraState
import kotlinx.coroutines.delay

object PrepareServiceScreens {
    private const val APP_NAME = "프로_포즈"
    private const val CATCH_PRAISE = "포즈, 이제 고민하지마."

    @Composable
    fun SplashScreen(
        onCheckForMoveToNext: () -> Unit
    ) {

        val localContext = LocalContext.current
        val showUpdateDialog = remember {
            mutableStateOf<Map<String, String>?>(null)
        }
        val appIconSize = 200.dp
        val boxSize = remember {
            mutableStateOf(DpSize.Zero)
        }
        val localDensity = LocalDensity.current
        val activityResultLauncher =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
            }

        suspend fun checkForAppUpdate(
            localContext: Context
        ) {
            val appVersion = "updated_app_version"
            val currentVersionCode = localContext.packageManager.getPackageInfo(
                localContext.packageName,
                0
            ).run {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) versionCode.toLong()
                else longVersionCode
            }
//            val remoteConfig = Firebase.remoteConfig.apply {
//                setConfigSettingsAsync(
//                    remoteConfigSettings { minimumFetchIntervalInSeconds = 0 }
//                )
//                setDefaultsAsync(mapOf(appVersion to "0.0.0"))
//            }
//            try {
//                remoteConfig.fetchAndActivate().await()
//                val appInfo =
//                    JSONTokener(remoteConfig[appVersion].asString()).nextValue() as JSONObject
//                if ((appInfo["version_code"] as Int).toLong() > currentVersionCode) { // 업데이트가 필요한 경우
//                    showUpdateDialog.value =
//                        mapOf(
//                            Pair("mustToUpdate", (appInfo["mustToUpdate"] as Boolean).toString()),
//                            Pair("updateRequestText", appInfo["updateRequestText"] as String),
//                            Pair("version_name", appInfo["version_name"] as String)
//                        )
//                }
//            } catch (e: Exception) {
//                Log.e("Error: ", "Cannot load appInfo data. ${e.message}")
//            }
        }

        LaunchedEffect(key1 = Unit) {
            checkForAppUpdate(localContext = localContext)
            if (showUpdateDialog.value == null) onCheckForMoveToNext()
        }



        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.White)
                .onSizeChanged {
                    localDensity.run {
                        boxSize.value = DpSize(
                            it.width.toDp(),
                            it.height.toDp()
                        )
                    }
                }
        ) {
            val resource = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(R.drawable.app_icon_rounded_without_background)
                    .size(with(LocalDensity.current) {
                        appIconSize.toPx().toInt()
                    }) //현재 버튼의 크기만큼 리사이징한다.
                    .placeholder(R.drawable.app_icon_rounded_without_background)
                    .build()
            )
            val style = LocalTypography.current
            val color = LocalColors.current
            val positionOfTitle = remember { mutableStateOf(Offset.Zero) }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(1F),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(150.dp, Alignment.CenterVertically)
            ) {
                Text(
                    text = CATCH_PRAISE,
                    style = style.heading01,
                    fontSize = 20.sp
                )

                Image(
                    modifier = Modifier
                        .size(appIconSize),
                    painter = resource,
                    contentDescription = "앱 아이콘",
                )

                Text(
                    modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                        positionOfTitle.value = layoutCoordinates.boundsInRoot().center
                    },
                    text = APP_NAME,
                    style = style.heading01,
                    fontSize = 20.sp
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = color.primaryGreen100,
                    radius = localDensity.run { 320.dp.toPx() },
                    center = localDensity.run {
                        positionOfTitle.value.let {
                            Offset(it.x, it.y + 50.dp.toPx())
                        }
                    }
                )
            }
            if (showUpdateDialog.value != null)
                Box(
                    modifier = Modifier
                        .zIndex(2F)
                        .background(LocalColors.current.secondaryWhite100.copy(alpha = 0.5F))
                        .fillMaxSize()
                )
                {
                    CustomDialog.AppUpdateDialog(
                        modifier = Modifier
                            .wrapContentSize()
                            .align(Alignment.BottomCenter),
                        noticeText = showUpdateDialog.value!!["updateRequestText"]!!,
                        mustUpdate = showUpdateDialog.value!!["mustToUpdate"]!!.toBoolean(),
                        versionName = showUpdateDialog.value!!["version_name"]!!,
                        onConfirmRequest = {
                            try {
                                activityResultLauncher.launch(Intent(Intent.ACTION_VIEW).apply {
                                    data =
                                        Uri.parse("market://details?id=" + localContext.packageName)
                                })
                            } catch (e: ActivityNotFoundException) {
                                activityResultLauncher.launch(Intent(Intent.ACTION_VIEW).apply {
                                    data =
                                        Uri.parse("http://play.google.com/store/apps/details?id=" + localContext.packageName)
                                })
                            }
                        },
                        onDismissRequest = {
                            if (showUpdateDialog.value!!["mustToUpdate"]!!.toBoolean().not()) onCheckForMoveToNext()
                            else (localContext as Activity).finishAffinity()
                        })
                }
        }


    }

    @Composable
    fun AppLoadingScreen(
        previewState: State<CameraState>,
        onAfterLoadedEvent: () -> Unit,
//        onMoveToDownload: () -> Unit,
        onPrepareToLoadCamera: () -> Unit = {},
//        onRequestCheckForDownload: () -> Unit,
//        checkNeedToDownloadState: CheckResponse?,
        totalLoadedState: () -> Boolean
    ) {


        val isInitiated = remember {
            mutableStateOf(false)
        }
        LaunchedEffect(Unit) {
            delay(1000)
            onPrepareToLoadCamera()
//            onRequestCheckForDownload()
        }

//        LaunchedEffect(checkNeedToDownloadState) {
//            //만약 다운로드 상태 파악이 완료된 경우
//            if (checkNeedToDownloadState != null) {
//                if (checkNeedToDownloadState.needToDownload.not()) onPrepareToLoadCamera()
//                else onMoveToDownload()
//            }
//        }


        //여기는 카메라 로딩 준비
        if (totalLoadedState() &&
            previewState.value.cameraStateId == CameraState.CAMERA_INIT_COMPLETE && isInitiated.value.not()
        ) {
            onAfterLoadedEvent()//카메라 화면으로 이동하는 거임.
            isInitiated.value = true
        }

        AnimatedVisibility(visible = isInitiated.value.not()) {
            InnerAppLoadingScreen()
        }


    }

    //로딩 중일 때 보여주는 화면
    @Composable
    fun InnerAppLoadingScreen() {
        val appIconSize = 200.dp
        val localDensity = LocalDensity.current
        val boxSize = remember {
            mutableStateOf(DpSize.Zero)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.White)
                .onSizeChanged {
                    localDensity.run {
                        boxSize.value = DpSize(
                            it.width.toDp(),
                            it.height.toDp()
                        )
                    }
                }
        ) {
            val resource = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(R.drawable.app_icon_rounded_without_background)
                    .size(with(LocalDensity.current) {
                        appIconSize.toPx().toInt()
                    }) //현재 버튼의 크기만큼 리사이징한다.
                    .placeholder(R.drawable.app_icon_rounded_without_background)
                    .build()
            )
            val style = LocalTypography.current
            val color = LocalColors.current

            val positionOfTitle = remember {
                mutableStateOf(Offset.Zero)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .zIndex(1F)
                    .padding(top = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(150.dp, Alignment.CenterVertically)
            ) {
                Text(
                    text = CATCH_PRAISE, style = style.heading01, fontSize = 20.sp
                )

                Image(
                    modifier = Modifier
                        .size(appIconSize),
                    painter = resource,
                    contentDescription = "앱 아이콘",
                )

                Column(
                    modifier = Modifier.onGloballyPositioned { layoutCoordinates ->
                        positionOfTitle.value = layoutCoordinates.boundsInRoot().center
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        20.dp,
                        Alignment.CenterVertically
                    )
                ) {
                    UIComponents.CircularWaitingBar(
                        barColor = LocalColors.current.secondaryWhite100,
                        backgroundColor = LocalColors.current.subSecondaryGray100
                    )
                    Text(
                        text = "$APP_NAME 로딩중...",
                        style = style.heading02
                    )

                }
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = color.primaryGreen100,
                    radius = localDensity.run { 320.dp.toPx() },
                    center = localDensity.run {
                        positionOfTitle.value.let {
                            Offset(it.x, it.y + 10.dp.toPx())
                        }
                    }

                )
            }

        }


    }


}


@Preview
@Composable
fun TestPrepareServiceScreen() {

}


//@Preview(name = "NEXUS_5", device = Devices.NEXUS_5)
//@Preview(name = "NEXUS_6", device = Devices.NEXUS_6)
//@Preview(name = "NEXUS_5X", device = Devices.NEXUS_5X)
//@Preview(name = "NEXUS_6P", device = Devices.NEXUS_6P)
//@Preview(name = "PIXEL", device = Devices.PIXEL)
//@Preview(name = "PIXEL_2", device = Devices.PIXEL_2)
//@Preview(name = "PIXEL_3", device = Devices.PIXEL_3)
//@Preview(name = "PIXEL_3_XL", device = Devices.PIXEL_3_XL)
//@Preview(name = "PIXEL_3A", device = Devices.PIXEL_3A)
//@Preview(name = "PIXEL_3A_XL", device = Devices.PIXEL_3A_XL)
//@Preview(name = "PIXEL_4", device = Devices.PIXEL_4)
//@Preview(name = "PIXEL_4_XL", device = Devices.PIXEL_4_XL)
//@Composable
//fun PreViewSplash() {
//    SplashScreen()
//}

//
//@Preview(name = "NEXUS_5", device = Devices.NEXUS_5)
//@Preview(name = "NEXUS_6", device = Devices.NEXUS_6)
//@Preview(name = "NEXUS_5X", device = Devices.NEXUS_5X)
//@Preview(name = "NEXUS_6P", device = Devices.NEXUS_6P)
//@Preview(name = "PIXEL", device = Devices.PIXEL)
//@Preview(name = "PIXEL_2", device = Devices.PIXEL_2)
//@Preview(name = "PIXEL_3", device = Devices.PIXEL_3)
//@Preview(name = "PIXEL_3_XL", device = Devices.PIXEL_3_XL)
//@Preview(name = "PIXEL_3A", device = Devices.PIXEL_3A)
//@Preview(name = "PIXEL_3A_XL", device = Devices.PIXEL_3A_XL)
//@Preview(name = "PIXEL_4", device = Devices.PIXEL_4)
//@Preview(name = "PIXEL_4_XL", device = Devices.PIXEL_4_XL)
@Composable
fun PreviewInnerAppLoadingScreen() {
    PrepareServiceScreens.InnerAppLoadingScreen()
}

