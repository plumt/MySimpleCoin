package com.yun.mysimplecoin.data.api

import com.yun.mysimplecoin.data.model.*
import io.reactivex.rxjava3.core.Observable
import retrofit2.http.*

interface Api {

    // 내가 보유한 자산 리스트
    @GET("accounts")
    fun myCoins(
        @Header("Authorization") Authorization: String
    ) : Observable<List<MyCoins.RS>>

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
    ) : Observable<List<CandlesModel.RS>>

    // 시세 캔들 조회 - 일 캔들
    @GET("candles/days")
    fun candlesDays(
        @Query("market") markets: String,
        @Query("count") count: String
    ) : Observable<List<CandlesModel.RS>>


    // 공포 탐욕 지수 크롤링
    @GET("feargreed")
//    @GET("fearindex")
    fun crawling() : Observable<FearGreedModel.RS>

    // 주문 대기 리스트 조회
    @GET("orders")
    fun orders(
        @Header("Authorization") Authorization: String,
        @Query("state") state: String
    ) : Observable<List<OrderModel.RS>>


    @GET("orders")
    fun orders(
        @Header("Authorization") Authorization: String,
        @Query("market") market: String,
        @Query("state") state: String
    ) : Observable<List<OrderModel.RS>>

    // 주문 취소
    @DELETE("order")
    fun order(
        @Header("Authorization") Authorization: String,
        @Query("uuid") uuid: String
    ) : Observable<OrderModel.RS>

    // 주문요청 - 매수
    @POST("orders")
    fun orders(
        @Header("Authorization") Authorization: String,
        @Body rq: OrderModel.BID
    ) : Observable<OrderModel.RS>

    // 주문요청 - 매도
    @POST("orders")
    fun orders(
        @Header("Authorization") Authorization: String,
        @Body rq: OrderModel.ASK
    ) : Observable<OrderModel.RS>

//    // 거래내역
//    @GET("orders")
//    fun orders(
//        @Header("Authorization") Authorization: String,
//        @Body rq: OrderModel.ORDERS
//    ) : Observable<OrderModel.RS>


    // 호가 정보
    @GET("orderbook")
    fun orderBook(
        @Query("markets") markets: String
    ) : Observable<List<OrderBookModel.RS?>>

    // 현재가
    @GET("ticker")
    fun ticker(
        @Query("markets") markets: String
    ) : Observable<List<TickerModel.RS>>
}