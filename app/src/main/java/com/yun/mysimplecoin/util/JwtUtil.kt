package com.yun.mysimplecoin.util

import android.content.Context
import android.util.Log
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.yun.mysimplecoin.R
import com.yun.mysimplecoin.common.constants.TokenConstants.Jwt.CLAIM_ACCESS_KEY_NM
import com.yun.mysimplecoin.common.constants.TokenConstants.Jwt.CLAIM_NONCE_KEY_NM
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*

object JwtUtil {
    fun newToken(context: Context, accessKey: String) : String {
        // 파리미터가 없는 token 생성
        val jwt = JWT.create()
            .withClaim(CLAIM_ACCESS_KEY_NM,accessKey)
            .withClaim(CLAIM_NONCE_KEY_NM, UUID.randomUUID().toString())
            .sign(Algorithm.HMAC256(context.getString(R.string.SECRET_KEY)))
        return jwt
//        setToken(jwt)
//        return true
    }
//    fun newToken(vararg value: Pair<String, Any?>) : Boolean{
//        // 파리미터가 있는 token 생성
//        var result = ""
//        value.forEachIndexed { index, map ->
//            if(index > 0) result += "&"
//            result += "${map.first}=${map.second}"
//        }
//        val md = MessageDigest.getInstance(SHA_512)
//        md.update(result.toByteArray(StandardCharsets.UTF_8))
//        val jwt = JWT.create()
//            .withClaim(CLAIM_ACCESS_KEY_NM,getAccessKey())
//            .withClaim(CLAIM_NONCE_KEY_NM, UUID.randomUUID().toString())
//            .withClaim(QUERY_HASH, String.format(QUERY_HASH_FORMAT, BigInteger(1, md.digest())))
//            .withClaim(QUERY_HASH_ALG, SHA512)
//            .sign(Algorithm.HMAC256(applicationContext.getString(R.string.SECRET_KEY)))
//
//        setToken(jwt)
//        Log.d("lys","create Token : $jwt")
//        return true
//    }
}