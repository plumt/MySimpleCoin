package com.yun.mysimplecoin.data.api

import io.reactivex.rxjava3.core.Observable
import retrofit2.http.GET
import retrofit2.http.Header

interface Api {

    // 내가 보유한 자산 리스트
    @GET("accounts")
    fun myCoins(@Header("Authorization") Authorization: String) : Observable<Any>
}