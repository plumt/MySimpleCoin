package com.yun.mysimplecoin.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    private val _isCoinService = MutableLiveData(false)
    val isCoinService: LiveData<Boolean> get() = _isCoinService

    fun startService() = viewModelScope.launch {
        _isCoinService.value = true
//        _isCoinService.postValue(true)
    }

    fun stopService() = viewModelScope.launch {
        _isCoinService.value = false
//        _isCoinService.postValue(false)
    }
}