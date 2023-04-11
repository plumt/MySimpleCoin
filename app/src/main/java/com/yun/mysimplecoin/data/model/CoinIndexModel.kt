package com.yun.mysimplecoin.data.model

class CoinIndexModel {
    data class data(
        val market: String,
        var score: String? = null,
        var rsi: Rsi? = null,
        var mv: Mv? = null,
        var bb: Bb? = null,
        var macd: Macd? = null
    ) {

        data class Rsi(
            var rsi: String
        )

        data class Mv(
            var middle: String
        )

        data class Bb(
            var upper: String,
            var lower: String
        )

        data class Macd(
            var fast: String,
            var slow: String
        )
    }
}