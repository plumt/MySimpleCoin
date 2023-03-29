package com.yun.mysimplecoin.data.api

import com.yun.mysimplecoin.data.model.AllCoinsNmModel
import com.yun.mysimplecoin.data.model.CandlesMinutesModel
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface Api {

    // 내가 보유한 자산 리스트
    @GET("accounts")
    fun myCoins(@Header("Authorization") Authorization: String) : Observable<Any>

    // 모든 코인 이름
    @GET("market/all")
    fun allCoinsNm(
        @Query("isDetails") isDetails: Boolean = true
    ) : Observable<List<AllCoinsNmModel.RS>>

    // 시세 캔들 조회 - 분 캔들
    @GET("candles/minutes/{unit}")
    fun candlesMinutes(
        @Path("unit") unit: String,
        @Query("market") markets: String,
        @Query("count") count: String = "200"
    ) : Observable<List<CandlesMinutesModel.RS>>
}