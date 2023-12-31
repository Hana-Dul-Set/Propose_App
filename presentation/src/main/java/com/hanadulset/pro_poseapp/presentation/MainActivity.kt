package com.hanadulset.pro_poseapp.presentation

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import com.hanadulset.pro_poseapp.presentation.core.MainScreen
import com.hanadulset.pro_poseapp.presentation.feature.camera.CameraViewModel
import com.hanadulset.pro_poseapp.presentation.feature.gallery.GalleryViewModel
import com.hanadulset.pro_poseapp.presentation.feature.splash.PrepareServiceViewModel
import com.hanadulset.pro_poseapp.presentation.component.ProPoseTheme
import com.hanadulset.pro_poseapp.utils.eventlog.AnalyticsManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val cameraViewModel: CameraViewModel by viewModels()
    private val prepareServiceViewModel: PrepareServiceViewModel by viewModels()
    private val galleryViewModel: GalleryViewModel by viewModels()
    private val analyticsManager by lazy { AnalyticsManager(this.contentResolver) }

    //전체화면 적용
    private fun setFullScreen(context: Context) {
        (context as AppCompatActivity).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                supportActionBar?.hide()
                window.insetsController?.apply {
                    hide(WindowInsets.Type.statusBars())
                    systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            //R버전 이하
            else {
                supportActionBar?.hide()
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                        // 컨텐츠를 시스템바 밑에 보이도록한다.
                        // 시스템바가 숨겨지거나 보여질 때 컨텐츠 부분이 리사이징 되는 것을 막기 위함
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        // 상태바를 사라지게하기
                        or View.SYSTEM_UI_FLAG_FULLSCREEN)
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setFullScreen(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED //회전 고정
        analyticsManager.saveAppOpenEvent()

        setContent {
            val navController = rememberNavController()  //화면 네비게이션 기능을 관리하는 컨트롤러

            ProPoseTheme {
                MainScreen.MainScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding() //시스템의 네비게이션 높이에 맞게 패딩을 적용할 수 있게 함.
                    , navController,
                    cameraViewModel = cameraViewModel,
                    prepareServiceViewModel = prepareServiceViewModel,
                    galleryViewModel = galleryViewModel
                )
            }
        }

    }

    @SuppressLint("HardwareIds")
    override fun onStop() {
        super.onStop()
        //앱 종료 이벤트 발생
        analyticsManager.saveAppClosedEvent()
    }


}