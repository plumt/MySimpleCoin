package com.yun.mysimplecoin.data.model

class OrderBookModel {
    data class RS(
        val market: String,
        val timestamp: String,
        val total_ask_size: String,
        val total_bid_size: String,
        val orderbook_units: ArrayList<OrderbookUnits>
    ){
        data class OrderbookUnits(
            val ask_price: String,
            val bid_price: String,
            val ask_size: String,
            val bid_size: String
        )
    }
}