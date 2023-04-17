package com.yun.mysimplecoin.data.api

import com.yun.mysimplecoin.data.model.*
import io.reactivex.rxjava3.core.Observable
import retrofit2.Response
import retrofit2.http.*

interface Api {

    // 내가 보유한 자산 리스트
    @GET("accounts")
    suspend fun myCoins(
        @Header("Authorization") Authorization: String
    ) : Response<List<MyCoins.RS>>

    // 모든 코인 이름
    @GET("market/all")
    suspend fun allCoinsNm(
        @Query("isDetails") isDetails: Boolean = true
    ) : Response<List<AllCoinsNmModel.RS>>

    // 시세 캔들 조회 - 분 캔들
    @GET("candles/minutes/{unit}")
    suspend fun candlesMinutes(
        @Path("unit") unit: String,
        @Query("market") markets: String,
        @Query("count") count: String = "200"
    ) : Response<List<CandlesModel.RS>>

    // 시세 캔들 조회 - 일 캔들
    @GET("candles/days")
    suspend fun candlesDays(
        @Query("market") markets: String,
        @Query("count") count: String
    ) : Response<List<CandlesModel.RS>>


    // 공포 탐욕 지수 크롤링
    @GET("feargreed")
//    @GET("fearindex")
    suspend fun crawling() : Response<FearGreedModel.RS>

    // 주문 대기 리스트 조회
    @GET("orders")
    suspend fun orders(
        @Header("Authorization") Authorization: String,
        @Query("state") state: String
    ) : Response<List<OrderModel.RS>>


    @GET("orders")
    suspend fun orders(
        @Header("Authorization") Authorization: String,
        @Query("market") market: String,
        @Query("state") state: String
    ) : Response<List<OrderModel.RS>>

    // 주문 취소
    @DELETE("order")
    suspend fun order(
        @Header("Authorization") Authorization: String,
        @Query("uuid") uuid: String
    ) : Response<OrderModel.RS>

    // 주문요청 - 매수
    @POST("orders")
    suspend fun orders(
        @Header("Authorization") Authorization: String,
        @Body rq: OrderModel.BID
    ) : Response<OrderModel.RS>

    // 주문요청 - 매도
    @POST("orders")
    suspend fun orders(
        @Header("Authorization") Authorization: String,
        @Body rq: OrderModel.ASK
    ) : Response<OrderModel.RS>

//    // 거래내역
//    @GET("orders")
//    fun orders(
//        @Header("Authorization") Authorization: String,
//        @Body rq: OrderModel.ORDERS
//    ) : Observable<OrderModel.RS>


    // 호가 정보
    @GET("orderbook")
    suspend fun orderBook(
        @Query("markets") markets: String
    ) : Response<List<OrderBookModel.RS?>>

    // 현재가
    @GET("ticker")
    suspend fun ticker(
        @Query("markets") markets: String
    ) : Response<List<TickerModel.RS>>
}