package com.yun.mysimplecoin.data.model

/*
    uuid - 주문의 고유 아이디
    side - 주문 종류
    ord_type - 주문 방식
    price - 주문 당시 화폐 가격
    state - 주문 상태
    market - 마켓의 유일키
    created_at - 주문 생성 시간
    volume - 사용자가 입력한 주문 양
    remaining_volume - 체결 후 남은 주문 양
    reserved_fee - 수수료로 예약된 비용
    remaining_fee - 남은 수수료
    paid_fee - 사용된 수수료
    locked - 거래에 사용중인 비용
    executed_volume - 체결된 양
    trade_count - 해당 주문에 걸린 체결 수
    trades - 체결
    trades.market - 마켓의 유일 키
    trades.uuid - 체결의 고유 아이디
    trades.price - 체결 가격
    trades.volume - 체결 양
    trades.funds - 체결된 총 가격
    trades.side - 체결 종류
    trades.created_at - 체결 시각
 */

class OrderModel {

    data class ASK(
        val market: String,
        val side: String,
        val volume: String,
        val ord_type: String
    )

    data class BID(
        val market: String,
        val side: String,
        val price: String,
        val ord_type: String
    )

    data class RS(
        val uuid: String,
        val side: String,
        val ord_type: String,
        val price: String,
        val state: String,
        val market: String,
        val created_at: String,
        val volume: String,
        val remaining_volume: String,
        val reserved_fee: String,
        val remaining_fee: String,
        val paid_fee: String,
        val locked: String,
        val executed_volume: String,
        val trades_count: String,
        val trades: ArrayList<Trades>? = null
    ) {
        data class Trades(
            val market: String,
            val uuid: String,
            val price: String,
            val volume: String,
            val funds: String,
            val side: String
        )
    }
}