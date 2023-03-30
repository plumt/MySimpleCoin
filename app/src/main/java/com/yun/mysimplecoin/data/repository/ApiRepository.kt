package com.yun.mysimplecoin.data.repository

import com.yun.mysimplecoin.data.api.Api
import javax.inject.Inject

class ApiRepository @Inject constructor(private val api: Api) {

    fun myCoins(Authorization: String) = api.myCoins(Authorization)

    fun allCoinsNm() = api.allCoinsNm()

    fun candlesMinutes(unit: String, markets: String) = api.candlesMinutes(unit, markets)

    fun crawling() = api.crawling()
}