package com.hanadulset.pro_poseapp.presentation.feature.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.hanadulset.pro_poseapp.presentation.feature.ArScreen
import com.hanadulset.pro_poseapp.presentation.feature.camera.CameraModules.LowerButtons
import com.hanadulset.pro_poseapp.presentation.feature.camera.CameraModules.UpperButtons
import com.hanadulset.pro_poseapp.presentation.feature.camera.PoseScreen.PoseResultScreen
import com.hanadulset.pro_poseapp.utils.eventlog.EventLog
import com.hanadulset.pro_poseapp.utils.pose.PoseData
import io.github.sceneview.ar.ARCore
import kotlinx.coroutines.delay


//리컴포지션시 데이터가 손실되는 문제를 해결하기 위한, 전역변수
var savedData = 0
var saveRecentlyPoses = arrayListOf<Int>()

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("SuspiciousIndentation")
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun Screen(
    cameraViewModel: CameraViewModel,
    previewView: PreviewView,
    onClickRecentlyImages: () -> Unit,
    showBackContinueDialog: () -> Unit,
    onClickSettingBtnEvent: () -> Unit,
    arSession: Session?,
    cameraInit: () -> Unit
) {
    val context = LocalContext.current
    val localDensity = LocalDensity.current
    val arConfig: Config? = if (arSession == null) null else Config(arSession)

    //요청된 고정 화면에 대한 결과값을 가지고 있는 State 변수
    val resFixedScreenState by cameraViewModel.fixedScreenState.collectAsState()
    val isUpdated = remember {
        mutableStateOf(false)
    }
    val isPressedFixedBtn = remember {
        mutableStateOf(false)
    }
    val selectedModeIdxState = remember {
        mutableIntStateOf(1)
    }
    val poseScreenVisibleState = remember {
        mutableStateOf(false)
    }
    val selectedPoseId = remember {
        mutableStateOf<Int?>(null)
    }
    val isFirstLaunch = remember {
        mutableStateOf(true)
    }
    val ddaogiOnState = remember {
        mutableStateOf(false)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val capturedThumbnailBitmap: Uri? by cameraViewModel.capturedBitmapState.collectAsState() //업데이트된 캡쳐화면을 가지고 있는 변수
    val viewRateIdxState by cameraViewModel.viewRateIdxState.collectAsState()
    val aspectRatioState by cameraViewModel.aspectRatioState.collectAsState()
    val viewRateState by cameraViewModel.viewRateState.collectAsState()


    val cropImageLauncher =
        rememberLauncherForActivityResult(contract = CropImageContract()) { result ->
            if (result.isSuccessful) {
                // Use the returned uri.
                val uriContent = result.uriContent
                cameraViewModel.getPoseFromImage(uriContent)
            } else {
                // An error occurred.
                val exception = result.error
            }

        }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                val cropOptions = CropImageContractOptions(
                    uri, CropImageOptions(
                        aspectRatioX = aspectRatioState.width.toInt(),
                        aspectRatioY = aspectRatioState.height.toInt(),
                        fixAspectRatio = true,

                        )
                )
                cropImageLauncher.launch(cropOptions)
            }
        }
    val recentlyChoosePoses = remember { arrayListOf<Int>() } //최근에 추천받은 포즈 데이터 목록

    val poseRecPair: Pair<DoubleArray?, List<PoseData>?>? by cameraViewModel.poseResultState.collectAsState()

//        뷰를 계속 업데이트 하면서 생겼던 오류
    if (isUpdated.value.not()) {
        isUpdated.value = true
    }




    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val upperButtonsRowSize = remember {
            mutableStateOf(IntSize(0, 0))
        }
        val cameraDisplaySize = remember {
            mutableStateOf(IntSize(0, 0))
        }
        val cameraDisplayPxSize = remember {
            mutableStateOf(IntSize(0, 0))
        }
        val lowerBarDisplaySize = remember {
            mutableStateOf(IntSize(0, 0))
        }
        val lowerBarDisplayPxSize = remember {
            mutableStateOf(IntSize(0, 0))
        }

        val shutterAnimationState = remember {
            mutableStateOf(false)
        }

        val focusRingState = remember {
            mutableStateOf<Offset?>(null)
        }

        val poseClickIndexState = remember {
            mutableIntStateOf(0)
        }



        LaunchedEffect(viewRateState) {
            if (isFirstLaunch.value) isFirstLaunch.value = false
            else cameraViewModel.showPreview(
                lifecycleOwner,
                previewView.surfaceProvider,
                viewRateState,
                previewView.rotation.toInt()
            )
        }

        LaunchedEffect(key1 = focusRingState.value) {
            if (focusRingState.value != null) {
                delay(400)
                focusRingState.value = null
            }
        }

        AndroidView(factory = {
            if (previewView.isActivated.not()) {
                cameraInit()
                if (poseRecPair != null) {
                    poseClickIndexState.intValue = savedData
                    recentlyChoosePoses.addAll(saveRecentlyPoses)
                    poseScreenVisibleState.value = true
                }
            } //설정화면에서 다시 돌아올 때, 뷰가 보이지 않는 문제 해결
            cameraViewModel.getLastImage()
            Log.d("arConfig status: ", (arConfig != null).toString())

            previewView
        },
            modifier = Modifier
                .animateContentSize { _, _ -> }
                .onGloballyPositioned { coordinates ->
                    with(localDensity) {
                        cameraDisplaySize.value = coordinates.size.let {
                            IntSize(
                                it.width.toDp().value.toInt(), it.height.toDp().value.toInt()
                            )
                        }
                        cameraDisplayPxSize.value = coordinates.size.let {
                            IntSize(it.width, it.height)
                        }
                    }
                }
                .pointerInteropFilter {
                    when (it.action) {
                        MotionEvent.ACTION_DOWN -> {
                            focusRingState.value = null
                            val untouchableArea =
                                with(localDensity) { upperButtonsRowSize.value.height.dp.toPx() }
                            if (it.y > untouchableArea) {
                                val pointer = Offset(it.x, it.y)
                                focusRingState.value = pointer.copy()
                                cameraViewModel.setFocus(
                                    previewView.meteringPointFactory.createPoint(
                                        it.x, it.y
                                    ), 2000L
                                )
                            }
                            return@pointerInteropFilter true
                        }

                        else -> {
                            false
                        }
                    }
                }
                .padding(top = if (viewRateIdxState == 0) upperButtonsRowSize.value.height.dp else 0.dp)
                .aspectRatio(aspectRatioState.width / aspectRatioState.height.toFloat())

                .align(Alignment.TopCenter)) {

        }

//        ArScreen.ArScreen(
//
//        )

        //엣지 화면
        ShowEdgeImage(
            capturedEdgesBitmap = resFixedScreenState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(cameraDisplaySize.value.width.dp, cameraDisplaySize.value.height.dp)
                .padding(top = if (viewRateIdxState == 0) upperButtonsRowSize.value.height.dp else 0.dp)
        )

        if (poseScreenVisibleState.value) {
            PoseResultScreen(cameraDisplaySize = cameraDisplaySize,
                cameraDisplayPxSize = cameraDisplayPxSize,
                lowerBarDisplayPxSize = lowerBarDisplayPxSize,
                upperButtonsRowSize = upperButtonsRowSize,
                modifier = Modifier
                    .size(
                        cameraDisplaySize.value.width.dp, cameraDisplaySize.value.height.dp
                    )
                    .align(Alignment.TopCenter),
                poseResultData = poseRecPair,
                onDownActionEvent = {
                    focusRingState.value = null
                    val pointer = Offset(it.x, it.y)
                    focusRingState.value = pointer.copy()
                    cameraViewModel.setFocus(
                        previewView.meteringPointFactory.createPoint(
                            it.x, it.y
                        ), 2000L
                    )
                },
                onPoseChangeEvent = {
                    selectedPoseId.value = it.poseId //포즈 변경 시, 해당 데이터를 트레킹 함.
                },
                rememberClickIndexState = poseClickIndexState.intValue,
                onVisibilityEvent = {
                    poseScreenVisibleState.value = false
                },
                onPageChangeEvent = {
                    poseClickIndexState.intValue = it
                })

        }


        //상단 버튼들
        UpperButtons(modifier = Modifier
            .align(Alignment.TopCenter)
            .heightIn(80.dp)
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                with(localDensity) {
                    upperButtonsRowSize.value = coordinates.size.let {
                        IntSize(
                            it.width.toDp().value.toInt(),
                            it.height.toDp().value.toInt()
                        )
                    }
                }
            }
            .padding(top = 30.dp),
            viewRateIdx = viewRateIdxState,
            mainColor = MaterialTheme.colors.primary,
            selectedModeIdxState = selectedModeIdxState,
            onSettingEvent = {
                savedData = poseClickIndexState.intValue
                saveRecentlyPoses = recentlyChoosePoses
                onClickSettingBtnEvent()
            },
            viewRateClickEvent = { idx ->
                cameraViewModel.changeViewRate(idx)
            })

        //구도 추천 모듈
        if (selectedModeIdxState.intValue in 1..2) {
            val pointerState by cameraViewModel.compResultState.collectAsState()

//            CameraModules.CompositionScreen(modifier = Modifier
//                .align(Alignment.TopCenter)
//                .size(cameraDisplaySize.value.let { DpSize(it.width.dp, it.height.dp) }),
//                centroid = cameraDisplayPxSize.value.let {
//                    Offset(
//                        it.width.toFloat() / 2, it.height.toFloat() / 2
//                    )
//                },
//                screenSize = Size(
//                    cameraDisplayPxSize.value.width.toFloat(),
//                    cameraDisplayPxSize.value.height.toFloat()
//                ),
//                pointerState = pointerState,
//                trackerPoint = cameraViewModel.trackerDataState.collectAsState(),
//                onStartToTracking = { offset ->
//                    cameraViewModel.attachTracker(
//                        offset,
//                        cameraDisplayPxSize.value.let { android.util.Size(it.width, it.height) })
//                },
//                onSetNewPoint = {
//                    cameraViewModel.reqCompRecommend()
//                },
//                onCancelPoint = {
//
//                })
        }


        //포즈 추천 스크롤과 하단 버튼 메뉴 정리
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .heightIn(150.dp)
                .padding(bottom = (upperButtonsRowSize.value.height).dp)
                .onGloballyPositioned { coordinates ->
                    with(localDensity) {
                        lowerBarDisplaySize.value = coordinates.size.let {
                            IntSize(
                                it.width.toDp().value.toInt(), it.height.toDp().value.toInt()
                            )
                        }
                        lowerBarDisplayPxSize.value = coordinates.size.let {
                            IntSize(it.width, it.height)
                        }
                    }
                }
                .offset(y = 25.dp),
            verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically)
        ) {
            if (poseScreenVisibleState.value && poseRecPair != null && selectedModeIdxState.intValue in 0..1) {
                val tmp = poseClickIndexState.intValue
                poseClickIndexState.intValue = -1
                poseClickIndexState.intValue = tmp
                PoseScreen.RecommendedPoseSelectMenu(
                    poseDataList = poseRecPair!!.second!!,
                    modifier = Modifier.animateContentSize { initialValue, targetValue -> },
                    poseCnt = poseRecPair!!.second!!.size,
                    clickedItemIndexState = poseClickIndexState.intValue,
                    onItemClickEvent = {
                        poseClickIndexState.intValue = it
                        recentlyChoosePoses.add(poseRecPair!!.second!![it].poseId)
                    })
            }

            LowerButtons(
                modifier = Modifier,
                poseScreenVisibleState = poseScreenVisibleState.value,
                selectedModeIdxState = selectedModeIdxState,
                capturedImageBitmap = capturedThumbnailBitmap,
                captureImageEvent = {
                    if (poseRecPair != null) {
                        Log.d(
                            "Recently Choose Poses ",
                            recentlyChoosePoses.toString()
                        )
                    }
                    shutterAnimationState.value = true
                    cameraViewModel.getPhoto(
                        //
                        EventLog(
                            poseID = if (recentlyChoosePoses.isEmpty()) -1 else recentlyChoosePoses.last(),
                            eventId = EventLog.EVENT_CAPTURE,
                            prevRecommendPoses = recentlyChoosePoses,
                            backgroundHog = null,
                            backgroundId = null,
                            timestamp = System.currentTimeMillis().toString()
                        )
                    ) //이곳에 인자로 이벤트로그를 보내면 딱이다.
                    shutterAnimationState.value = false
                },
                poseBtnClickEvent = {
                    if (poseScreenVisibleState.value.not()) {
                        cameraViewModel.reqPoseRecommend()
                        poseClickIndexState.intValue = 1 //0번째 원소는 해제 버튼이기 때문에
                        poseScreenVisibleState.value = true
                    } else {
                        poseScreenVisibleState.value = false
                    }
                },
                fixedButtonPressedEvent = {
                    isPressedFixedBtn.value = !isPressedFixedBtn.value
                    cameraViewModel.controlFixedScreen(isPressedFixedBtn.value)
                },
                zoomInOutEvent = {
                    cameraViewModel.reqCompRecommend()
                    cameraViewModel.setZoomLevel(it)
                },
                ddaogiFeatureEvent = {
                    if (ddaogiOnState.value.not())
                        launcher.launch(
                            PickVisualMediaRequest(
                                mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    else ddaogiOnState.value = false
                },
                onClickGalleyBtnEvent = onClickRecentlyImages
            )

        }
        CameraModules.FocusRing(
            modifier = Modifier.align(Alignment.TopStart),
            color = Color.White,
            pointer = focusRingState.value,
            duration = 2000L
        )

    }

}

//고정된 화면을 보여주는 컴포저블
@Composable
fun ShowEdgeImage(
    capturedEdgesBitmap: Bitmap?, // 고정된 이미지
    modifier: Modifier = Modifier, //수정자

) {
    if (capturedEdgesBitmap != null) {
        Image(
            modifier = modifier.alpha(0.5F),
            bitmap = capturedEdgesBitmap.asImageBitmap(),
            contentDescription = "Edge Image"
        )
    }

}







