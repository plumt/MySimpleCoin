package com.yun.mysimplecoin.data.repository

import com.yun.mysimplecoin.data.api.Api
import com.yun.mysimplecoin.data.model.OrderModel
import javax.inject.Inject

class ApiRepository @Inject constructor(private val api: Api) {

    /**
     * 나의 코인 목록
     */
    suspend fun myCoins(authorization: String) = api.myCoins(authorization)

    /**
     * 모든 코인 목록
     */
    suspend fun allCoinsNm() = api.allCoinsNm()

    /**
     * 5분 간격 지수
     */
    suspend fun candlesMinutes(unit: String, markets: String) = api.candlesMinutes(unit, markets)

    /**
     * 1일 간격 지수
     */
    suspend fun candlesDays(markets: String, count: String) = api.candlesDays(markets, count)

    /**
     * 공포 탐욕
     */
    suspend fun crawling() = api.crawling()

    /**
     * 대기 목록
     */
    suspend fun orders(authorization: String, state: String) = api.orders(authorization,state)

    /**
     * 예약 취소
     */
    suspend fun order(authorization: String, uuid: String) = api.order(authorization, uuid)

    /**
     * 매도
     */
    suspend fun orders(authorization: String, ask: OrderModel.ASK) = api.orders(authorization, ask)

    /**
     * 매수
     */
    suspend fun orders(authorization: String, bid: OrderModel.BID) = api.orders(authorization, bid)

    /**
     * 코인 호가
     */
    suspend fun orderBook(markets: String) = api.orderBook(markets)

    /**
     * 코인 현재가
     */
    suspend fun ticker(markets: String) = api.ticker(markets)

    /**
     * 코인 거래 내역
     */
    suspend fun orders(authorization: String, market: String, state: String) = api.orders(authorization, market, state)
}