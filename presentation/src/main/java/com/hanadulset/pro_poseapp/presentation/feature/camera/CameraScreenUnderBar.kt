package com.hanadulset.pro_poseapp.presentation.feature.camera

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.hanadulset.pro_poseapp.presentation.R
import com.hanadulset.pro_poseapp.presentation.feature.camera.CameraScreenButtons.ParticularZoomButton
import com.hanadulset.pro_poseapp.presentation.feature.camera.CameraScreenButtons.ToggledButton
import com.hanadulset.pro_poseapp.utils.pose.PoseData
import dev.chrisbanes.snapper.ExperimentalSnapperApi
import dev.chrisbanes.snapper.rememberLazyListSnapperLayoutInfo
import dev.chrisbanes.snapper.rememberSnapperFlingBehavior
import kotlinx.coroutines.launch

object CameraScreenUnderBar {

    //하단바 구성
    //총 세 개의 층이고, 추천 층을 제외한 두 층 각각은 별도의 패딩이 필요하다.

    @Composable
    fun UnderBar(
        modifier: Modifier = Modifier,
        galleryImageUri: Uri?,
        onPoseRecommendEvent: () -> Unit,
        onEdgeDetectEvent: (Boolean) -> Unit,
        onShutterClickEvent: () -> Unit,
        onGalleryButtonClickEvent: () -> Unit,
        onZoomLevelChangeEvent: (Float) -> Unit,
        onFixedButtonClickEvent: (Boolean) -> Unit,
        lowerLayerPaddingBottom: Dp = 0.dp,
        aboveSize: Dp,
    ) {
        //필요할 때만 리컴포지션을 진행함.
        //https://kotlinworld.com/256 참고
        val disturbFixedButtonState = remember {
            mutableStateOf(false)
        }
        val disturbEdgeDetectorButtonState = remember {
            mutableStateOf(false)
        }

        val fixedBtnClickEvent = remember<(Boolean) -> Unit> {
            {
                onFixedButtonClickEvent(it)
                disturbEdgeDetectorButtonState.value = true
                disturbFixedButtonState.value = false
            }
        }

        val edgeDetectBtnClickEvent = remember<(Boolean) -> Unit> {
            {
                onEdgeDetectEvent(it)
                disturbFixedButtonState.value = true
                disturbEdgeDetectorButtonState.value = false
            }
        }

        val galleryThumbUri by rememberUpdatedState(newValue = galleryImageUri)

        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            UpperLayer(
                modifier = Modifier.fillMaxWidth(),
                onEdgeDetectEvent = edgeDetectBtnClickEvent,
                onZoomLevelChangeEvent = onZoomLevelChangeEvent,
                disturbFromFixedButton = disturbEdgeDetectorButtonState.value,
                onRecommendPoseEvent = onPoseRecommendEvent
            )
            LowerLayer(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = lowerLayerPaddingBottom),
                onShutterClickEvent = onShutterClickEvent,
                onGalleryButtonClickEvent = onGalleryButtonClickEvent,
                onFixedButtonClickEvent = fixedBtnClickEvent,
                galleryImageUri = galleryThumbUri,
                disturbFromEdgeDetector = disturbFixedButtonState.value,
            )
        }

    }

}


// 포즈 추천 , 따오기 -> On/Off , 줌레벨은 선택된 버튼이 돋보이도록 사이즈 조절
// 포즈 추천 버튼, 줌레벨 설정 버튼 , 따오기 버튼이 존재

//따오기를 할 때, 고정을 할 수 있음.. -> 이문제는 어떻게 해결해야 할까?
// 고정을 할 때, 따오기를 이전에 했다면, 해제해줘야하는데 어떻게 해야할 까?
@Composable
fun UpperLayer(
    modifier: Modifier = Modifier,
    buttonSize: Dp = 44.dp,
    onEdgeDetectEvent: (Boolean) -> Unit,
    onZoomLevelChangeEvent: (Float) -> Unit,
    onRecommendPoseEvent: () -> Unit,
    disturbFromFixedButton: Boolean,
) {
    val initEdgeValue = false
    val edgeDetectorState = remember { mutableStateOf(initEdgeValue) }
    val edgeDetectEvent by rememberUpdatedState<(Boolean) -> Unit>(newValue = { selected ->
        edgeDetectorState.value = selected
        onEdgeDetectEvent(selected) //선택된 경우에만 호출
    })
    val isDisturbed by rememberUpdatedState(newValue = disturbFromFixedButton)


    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        //포즈 추천 버튼 -> 토글 형식이 아니었던가..?
        CameraScreenButtons.NormalButton(
            buttonSize = buttonSize,
            buttonName = "포즈 추천 버튼",
            buttonText = "포즈\n추천",
            colorTint = Color.Black,
            onClick = onRecommendPoseEvent
        )

        //줌 레벨 설정 버튼
        ZoomButtonRow(
            modifier = Modifier.wrapContentSize(),
            defaultButtonSize = buttonSize,
            onClickEvent = onZoomLevelChangeEvent
        )

        //따오기 버튼
        ToggledButton(
            buttonSize = buttonSize,
            buttonText = "따오기",
            initState = initEdgeValue,
            onClickEvent = edgeDetectEvent,
            customEventValue = isDisturbed,
        )
    }


}

// 갤러리 버튼, 셔터 버튼, 고정 버튼이 위치
@Composable
fun LowerLayer(
    modifier: Modifier = Modifier,
    galleryImageUri: Uri?,
    disturbFromEdgeDetector: Boolean,
    onShutterClickEvent: () -> Unit = {},
    onGalleryButtonClickEvent: () -> Unit = {},
    onFixedButtonClickEvent: (Boolean) -> Unit = {}
) {

    //개별 상태변수를 가지고 있으므로써, 의도치 않은 리컴포지션을 방지
    val galleryImageState by rememberUpdatedState(newValue = galleryImageUri)
    val isFixedDisturbed by rememberUpdatedState(newValue = disturbFromEdgeDetector)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        //갤러리 이미지 버튼
        GalleryImageButton(
            galleryImageUri = galleryImageState,
            buttonSize = 60.dp,
            onClickEvent = onGalleryButtonClickEvent
        )

        //셔터
        CameraScreenButtons.ShutterButton(
            buttonSize = 100.dp
        ) { onShutterClickEvent() }

        //고정
        CameraScreenButtons.FixedButton(
            modifier = Modifier,
            buttonSize = 60.dp,
            disturbFromEdgeDetector = isFixedDisturbed,
            fixedButtonPressedEvent = onFixedButtonClickEvent

        )
    }
}


@Composable
private fun GalleryImageButton(
    modifier: Modifier = Modifier,
    galleryImageUri: Uri?,
    buttonSize: Dp,
    defaultBackgroundColor: Color = Color(0x80FAFAFA),
    onClickEvent: () -> Unit,
) {
    val imagePainter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(galleryImageUri)
            .size(with(LocalDensity.current) { buttonSize.toPx().toInt() }) //현재 버튼의 크기만큼 리사이징한다.
            .build()
    )


    Image(
        painter = imagePainter,
        contentDescription = "갤러리 이미지",
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(color = defaultBackgroundColor)
            .clickable(
                interactionSource = MutableInteractionSource(),
                indication = CameraScreenButtons.CustomIndication,
                onClick = onClickEvent
            ),
        contentScale = ContentScale.Crop,
    )
}


@Composable
fun ZoomButtonRow(
    modifier: Modifier,
    selectedButtonScale: Float = 1.2F,
    defaultButtonSize: Dp = 20.dp,
    spaceByEachItems: Dp = 8.dp,
    onClickEvent: (Float) -> Unit,
) {
    val selectedButtonIndexState = rememberSaveable { mutableIntStateOf(0) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            spaceByEachItems, Alignment.CenterHorizontally
        )
    ) {
        listOf(1, 2).forEachIndexed { index, value ->
            ParticularZoomButton(
                selected = selectedButtonIndexState.intValue == index,
                defaultButtonSize = defaultButtonSize / selectedButtonScale,
                buttonValue = value,
                selectedButtonScale = selectedButtonScale
            ) {
                selectedButtonIndexState.intValue = index
                onClickEvent(value.toFloat())
            }
        }
    }
}


//포즈 추천 버튼을 누르면 나오는 버튼 배열
@Composable
fun ClickPoseBtnUnderBar(
    modifier: Modifier = Modifier,
    poseList: List<PoseData>?,
    galleryImageUri: Uri?,
    currentSelectedIdx: Int,
    onRefreshPoseData: () -> Unit,
    onClickShutterBtn: () -> Unit,
    onGalleryButtonClickEvent: () -> Unit,
    onClickCloseBtn: () -> Unit,
    onSelectedPoseIndexEvent: (Int) -> Unit,
) {

    val galleryImageState by rememberUpdatedState(newValue = galleryImageUri)

    BackHandler(onBack = onClickCloseBtn) //뒤로가기 버튼을 누르면 이전 화면으로 돌아감.

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        //포즈 선택 할 수 있는 Row
        PoseSelectRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White),
            currentSelectedIdx = currentSelectedIdx,
            poseList = poseList,
            onSelectedPoseIndexEvent = onSelectedPoseIndexEvent
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            //창 닫는 버튼
            GalleryImageButton(
                galleryImageUri = galleryImageState,
                buttonSize = 60.dp,
                onClickEvent = onGalleryButtonClickEvent
            )


//            CameraScreenButtons.NormalButton(
//                buttonName = "닫기",
//                onClick = ,
//
//                buttonSize = 44.dp,
//                colorTint = Color.Black,
//                innerIconDrawableId = R.drawable.left_arrow,
//                innerIconDrawableSize = 20.dp
//            )

            //셔터 버튼
            CameraScreenButtons.ShutterButton(
                onClickEvent = onClickShutterBtn, buttonSize = 100.dp
            )

            //포즈 새로고침 버튼
            CameraScreenButtons.NormalButton(
                buttonName = "포즈 새로고침",
                innerIconDrawableSize = 20.dp,
                colorTint = Color.Black,
                innerIconDrawableId = R.drawable.refresh,
                onClick = onRefreshPoseData,
                buttonSize = 60.dp
            )
        }
    }


}


@OptIn(ExperimentalSnapperApi::class)
@Composable
fun PoseSelectRow(
    modifier: Modifier = Modifier,
    currentSelectedIdx: Int,
    poseList: List<PoseData>?,
    onSelectedPoseIndexEvent: (Int) -> Unit,
) {
    val scrollState = rememberLazyListState()
    val nowSelected by rememberUpdatedState(newValue = currentSelectedIdx)
    val rememberCoroutineScope = rememberCoroutineScope()
    val layoutInfo = rememberLazyListSnapperLayoutInfo(scrollState)
    val scrollBySnap = remember { mutableStateOf(false) }
    val scrollByClick = remember { mutableStateOf(false) }
    val localDensity = LocalDensity.current
    val rowWidth =
        remember { derivedStateOf { with(localDensity) { scrollState.layoutInfo.viewportSize.width.toDp() } } }




    LaunchedEffect(nowSelected) {
        if (scrollBySnap.value.not()) {
            scrollByClick.value = true
            val itemInfo =
                scrollState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == nowSelected }
            if (itemInfo != null) {
                val paddingValue = (rowWidth.value.div(2F).value)
                val center = (scrollState.layoutInfo.viewportEndOffset - paddingValue) / 2
                val childCenter = itemInfo.offset + itemInfo.size
                scrollState.animateScrollBy(childCenter - center)
            } else scrollState.scrollToItem(nowSelected)
        } else scrollBySnap.value = false
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (!scrollState.isScrollInProgress) {
            // The scroll (fling) has finished, get the current item and
            // do something with it!
            if (scrollByClick.value.not()) {
                val snappedItem = layoutInfo.currentItem
                snappedItem?.let {
                    scrollBySnap.value = true
                    onSelectedPoseIndexEvent(it.index)
                }
            } else scrollByClick.value = false
        }
    }





    LazyRow(
        modifier = modifier, state = scrollState,
        horizontalArrangement = Arrangement.Center,
        contentPadding = PaddingValues(
            horizontal = 10.dp,
            vertical = 10.dp
        ),
        flingBehavior = rememberSnapperFlingBehavior(layoutInfo),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (poseList != null) itemsIndexed(poseList) { idx, posedata ->
            PoseSelectionItem(
                modifier = Modifier.padding(horizontal = 10.dp),
                isSelected = idx == nowSelected,
                drawableId = posedata.poseDrawableId,
                poseIndex = idx,
                onClickEvent = {
                    onSelectedPoseIndexEvent(idx)
                    /*코루틴을 컴포저블에서 사용하기 위해서는 rememberCoroutineScope()를 사용해야 함.*/
                    rememberCoroutineScope.launch {
                        scrollState.animateScrollToItem(idx)
                    }
                }, poseSize = 70
            )
        }
        else { //아직 포즈를 찾고 있는 중 일 때
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF95FA99)
                    )
                    Text(

                        text = "포즈 추천 중..",
                        textAlign = TextAlign.Center,
                        fontFamily = CameraScreenButtons.pretendardFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .padding(top = 5.dp)
                    )
                }
            }
        }

    }
}

@Composable
fun PoseSelectionItem(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    drawableId: Int,
    poseIndex: Int,
    poseSize: Int,
    onClickEvent: () -> Unit
) {


    val stateColor = if (isSelected) Color(0x8095FFA7) else Color(0x80999999)
    Column(
        modifier = modifier
            .clickable {
                onClickEvent()
            }
            .wrapContentSize(),

        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {


        if (drawableId == -1) {
            Box(
                modifier = Modifier
                    .background(color = stateColor)
                    .size(poseSize.dp)
            )
        } else {
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(drawableId)
                    .size(with(LocalDensity.current) {
                        poseSize.dp.toPx().toInt()
                    }) //현재 버튼의 크기만큼 리사이징한다.
                    .build()
            )
            Image(
                modifier = Modifier
                    .background(color = stateColor)
                    .size(poseSize.dp),
                painter = painter,
                contentDescription = "이미지",
                contentScale = ContentScale.Fit
            )
        }
        Text(

            text = if (drawableId == -1) "없음" else "포즈 #$poseIndex",
            textAlign = TextAlign.Center,
            fontFamily = CameraScreenButtons.pretendardFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier
                .width(poseSize.dp)
                .padding(top = 5.dp)
        )


    }
}


@Composable
@Preview
fun PreviewLowerLayer() {
    LowerLayer(modifier = Modifier.fillMaxWidth(), onShutterClickEvent = {

    }, onGalleryButtonClickEvent = {

    }, galleryImageUri = null, disturbFromEdgeDetector = false
    )
}


@Composable
@Preview
fun PreviewUnderBar() {
    CameraScreenUnderBar.UnderBar(
        galleryImageUri = null,
        onPoseRecommendEvent = {},
        onEdgeDetectEvent = { /*TODO*/ },
        onShutterClickEvent = { /*TODO*/ },
        onGalleryButtonClickEvent = { /*TODO*/ },
        onZoomLevelChangeEvent = {},
        onFixedButtonClickEvent = {},
        aboveSize = 0.dp
    )
}


@Composable
@Preview
fun PrePoseSelector() {
    val poseList = listOf(
        PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_1,
            poseCat = 1
        ), PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_2,
            poseCat = 1
        ), PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_3,
            poseCat = 1
        ), PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_4,
            poseCat = 1
        ), PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_5,
            poseCat = 1
        )

    )

    PoseSelectRow(modifier = Modifier.fillMaxWidth(),
        poseList = poseList,
        currentSelectedIdx = 0,
        onSelectedPoseIndexEvent = {})
}


@Composable
@Preview
fun PreviewSelector() {
    val poseList = listOf(
        PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_1,
            poseCat = 1
        ), PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_2,
            poseCat = 1
        ), PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_3,
            poseCat = 1
        ), PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_4,
            poseCat = 1
        ), PoseData(
            poseId = 0,
            poseDrawableId = com.hanadulset.pro_poseapp.utils.R.drawable.key_image_5,
            poseCat = 1
        )

    )
    ClickPoseBtnUnderBar(
        Modifier.fillMaxWidth(),
        poseList = poseList,
        onSelectedPoseIndexEvent = {},
        onClickCloseBtn = {},
        onClickShutterBtn = {},
        onRefreshPoseData = {},
        currentSelectedIdx = 0,
        galleryImageUri = null,
        onGalleryButtonClickEvent = {

        }
    )
}