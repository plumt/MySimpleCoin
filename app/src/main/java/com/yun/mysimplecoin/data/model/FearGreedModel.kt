package com.yun.mysimplecoin.data.model

class FearGreedModel {
    data class RS(
        val pairs: ArrayList<Pairs>
    ) {
        data class Pairs(
            val code: String,
            val date: String,
            val change_rate: String,
            val updated_at: String,
            val cls_prc: String,
            val score: String,
            val currency: String,
            val stage: String,
            val css: String,
            val korean_name: String
        )
    }
}