package com.yun.mysimplecoin.data.model

class MyCoins {
    data class RS(
        val currency: String,
        val balance: String,
        var locked: String,
        val avg_buy_price: String,
        val avg_buy_price_modified: Boolean,
        val unit_currency: String,
        var rate: String? = ""
    )
}