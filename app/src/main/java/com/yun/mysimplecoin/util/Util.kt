package com.yun.mysimplecoin.util

import com.yun.mysimplecoin.common.constants.OrderConstants.SIDE.ASK
import com.yun.mysimplecoin.common.constants.OrderConstants.SIDE.BID
import com.yun.mysimplecoin.data.model.CandlesMinutesModel

object Util {
    fun calRsiMinute(candles: ArrayList<CandlesMinutesModel.RS>): ArrayList<Triple<String, String, String>> {
        var U_cnt = 0
        var U_BEFORE = 0.0
        var U = 0.0
        var D_cnt = 0
        var D_BEFORE = 0.0
        var D = 0.0
        val RSI_14 = 2 / (1.0 + 14.0)
        val RSI_RESULT = 1.0 - RSI_14
        val result = arrayListOf<Triple<String, String, String>>()

        candles.forEachIndexed { index, minuteModel ->
            if (index > 0) {
                val before = candles[index - 1].trade_price.toDouble()
                if (before > minuteModel.trade_price.toDouble()) {
                    // 이전 기준 하락
                    val cal = before - minuteModel.trade_price.toDouble()
                    D_cnt++
                    if (D_cnt == 1) {
                        D_BEFORE = cal
                        D = cal
                    } else {
                        val temp = D
                        D = (cal * RSI_14) + (D_BEFORE * RSI_RESULT)
                        D_BEFORE = temp
                    }
                } else if (before < minuteModel.trade_price.toDouble()) {
                    // 이전 기준 상승
                    val cal = minuteModel.trade_price.toDouble() - before
                    U_cnt++
                    if (U_cnt == 1) {
                        U_BEFORE = cal
                        U = cal
                    } else {
                        val temp = U
                        U = (cal * RSI_14) + (U_BEFORE * RSI_RESULT)
                        U_BEFORE = temp
                    }
                }
            }
        }
        val RS = U / D
        val RSI = RS / (1 + RS)
        val RSI100 = (RSI * 100).toInt()
        if (RSI100 >= getRsiMax().toInt()) {
            result.add(Triple(candles[0].market, BID, RSI100.toString()))
        } else if (RSI100 <= getRsiMin().toInt()) {
            result.add(Triple(candles[0].market, ASK, RSI100.toString()))
        }
        return result
    }

    private fun getRsiMin(): String {
        // rsi min - 매수 시점
        return "30"
    }

    private fun getRsiMax(): String {
        // rsi max - 매도 시점
        return "70"
    }

    fun getStandardDeviation(numbers: List<Double>): Double {
        val size = numbers.size
        val mean = numbers.average()
        val variance = numbers.map { (it - mean) * (it - mean) }.sum() / (size - 1)
        return Math.sqrt(variance)
    }
}