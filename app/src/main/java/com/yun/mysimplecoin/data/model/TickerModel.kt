package com.yun.mysimplecoin.data.model

class TickerModel {
    data class RS(
        val market: String,
        val trade_date: String,
        val trade_time: String,
        val trade_date_kst: String,
        val trade_time_kst: String,
        val trade_timestamp: String,
        val opening_price: String,
        val high_price: String,
        val low_price: String,
        val trade_price: String,
        val prev_closing_price: String,
        val change: String,
        val change_price: String,
        val change_rate: String,
        val signed_change_price: String,
        val signed_change_rate: String,
        val trade_volume: String,
        val acc_trade_price: String,
        val acc_trade_price_24h: String,
        val acc_trade_volume: String,
        val acc_trade_volume_24h: String,
        val highest_52_week_price: String,
        val highest_52_week_date: String,
        val lowest_52_week_price: String,
        val lowest_52_week_date: String,
        val timestamp: String
    )
}