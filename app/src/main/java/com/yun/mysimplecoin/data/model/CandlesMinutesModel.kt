package com.yun.mysimplecoin.data.model

/**
    market - 마켓명
    candle_date_time_utc - 캔들 기준 시각(UTC 기준)
    candle_date_time_kst - 캔들 기준 시각(KST 기준)
    opening_price - 시가
    high_price - 고가
    low_price - 저가
    trade_price - 종가
    timestamp - 해당 캔들에서 마지막 틱이 저장된 시각
    candle_acc_trade_price - 누적 거래 금액
    candle_acc_trade_volume - 누적 거래량
    unit - 분 단위(유닛)
 */

class CandlesMinutesModel {
    data class RS(
        val market: String,
        val candle_date_time_utc: String,
        val candle_date_time_kst: String,
        val opening_price: String,
        val high_price: String,
        val low_price: String,
        val trade_price: String,
        val timestamp: String,
        val candle_acc_trade_price: String,
        val candle_acc_trade_volume: String,
        val unit: String
    )
}