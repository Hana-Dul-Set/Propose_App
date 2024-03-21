package com.hanadulset.pro_poseapp

import android.content.Context
import com.hanadulset.pro_poseapp.data.repository.CameraRepositoryImpl
import com.hanadulset.pro_poseapp.data.repository.ImageRepositoryImpl
import com.hanadulset.pro_poseapp.data.repository.UserRepositoryImpl
import com.hanadulset.pro_poseapp.domain.repository.CameraRepository
import com.hanadulset.pro_poseapp.domain.repository.ImageRepository
import com.hanadulset.pro_poseapp.domain.repository.UserRepository
import dagger.Binds

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
//Hilt 적용시 각 의존성을 주입하는 모듈
abstract class RepositoryModule {

    @Singleton
    @Binds
    abstract fun bindCameraRepository(cameraRepositoryImpl: CameraRepositoryImpl): CameraRepository

    @Singleton
    @Binds
    abstract fun bindImageRepository(imageRepositoryImpl: ImageRepositoryImpl): ImageRepository

    @Singleton
    @Binds
    abstract fun bindUserRepository(userRepositoryImpl: UserRepositoryImpl): UserRepository

}


