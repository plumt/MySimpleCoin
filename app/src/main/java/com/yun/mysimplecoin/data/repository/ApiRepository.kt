package com.yun.mysimplecoin.data.repository

import com.yun.mysimplecoin.data.api.Api
import javax.inject.Inject

class ApiRepository @Inject constructor(private val api: Api) {

    fun myCoins(Authorization: String) = api.myCoins(Authorization)
}