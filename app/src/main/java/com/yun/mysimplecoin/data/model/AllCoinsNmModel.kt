package com.yun.mysimplecoin.data.model

/**
    market - 업비트에서 제공중인 시장 정보
    korean_name - 거래 대상 암호화폐 한글명
    english_name - 거래 대상 암호화폐 영문명
    market_warning - 유의 종목 여부 -> NONE (해당 사항 없음), CAUTION(투자유의)
 */

class AllCoinsNmModel {
    data class RS(
        val market: String,
        val korean_name: String,
        val english_name: String,
        val market_warning: String
    )
}