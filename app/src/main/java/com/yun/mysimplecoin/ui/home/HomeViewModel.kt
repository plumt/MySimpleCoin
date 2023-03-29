package com.yun.mysimplecoin.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import com.yun.mysimplecoin.base.BaseViewModel
import com.yun.mysimplecoin.common.constants.SharedPreferencesConstants.Key.ACCESS_KEY
import com.yun.mysimplecoin.common.constants.SharedPreferencesConstants.Key.AUTHORIZATION_TOKEN
import com.yun.mysimplecoin.data.repository.ApiRepository
import com.yun.mysimplecoin.util.JwtUtil.newToken
import com.yun.mysimplecoin.util.PreferenceUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val sharedPreferences: PreferenceUtil,
    private val api: ApiRepository
) : BaseViewModel(application) {

    private val accessToken = "mrPq8Ia8riJv5YQZYRQ5DhF42BYp9EXxr1ZzOhOI"

    init {
        callApi()
    }
    
    private fun callApi(){
        api.myCoins(newToken(mContext,accessToken)).observeOn(Schedulers.io()).subscribeOn(Schedulers.io())
            .flatMap { Observable.just(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .map {
                Log.d("lys","myCoins result > $it")
            }.subscribe({
                Log.d("lys","myCoins success")
            },{
                Log.e("lys","myCoins fail > ${it.message}")
            })
    }
}