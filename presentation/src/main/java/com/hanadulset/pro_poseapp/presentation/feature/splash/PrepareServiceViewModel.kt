package com.hanadulset.pro_poseapp.presentation.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hanadulset.pro_poseapp.domain.usecase.CheckUserSuccessToUseUseCase
import com.hanadulset.pro_poseapp.domain.usecase.PreLoadModelUseCase
import com.hanadulset.pro_poseapp.domain.usecase.SaveUserSuccessToUseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrepareServiceViewModel @Inject constructor(
    private val preLoadModelUseCase: PreLoadModelUseCase,
    private val saveUserSuccessToUseUseCase: SaveUserSuccessToUseUseCase,
    private val checkUserSuccessToUseUseCase: CheckUserSuccessToUseUseCase,

    ) : ViewModel() {


    private val _totalLoadedState = MutableStateFlow(false)
    private val _modelLoadedState = MutableStateFlow(false)
    val totalLoadedState = _totalLoadedState.asStateFlow()


    private val _checkUserSuccess = MutableStateFlow<Boolean?>(null)
    val checkUserSuccess = _checkUserSuccess.asStateFlow()


    fun preLoadModel() {
        _modelLoadedState.value = false
        viewModelScope.launch {
            _modelLoadedState.value = preLoadModelUseCase()
            checkLoadAllPreRunMethod()
        }

    }

    fun successToUse() {
        viewModelScope.launch {
            saveUserSuccessToUseUseCase()
        }
    }

    fun checkToUse() {
        viewModelScope.launch {
            _checkUserSuccess.value = checkUserSuccessToUseUseCase()
        }
    }


    private fun checkLoadAllPreRunMethod() {
        if (_modelLoadedState.value) _totalLoadedState.value = true
    }


}