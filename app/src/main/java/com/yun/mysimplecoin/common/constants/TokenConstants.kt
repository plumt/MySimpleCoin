package com.yun.mysimplecoin.common.constants

class TokenConstants {

    object Jwt {
        // create token
        const val CLAIM_ACCESS_KEY_NM = "access_key"
        const val CLAIM_NONCE_KEY_NM = "nonce"
        const val QUERY_HASH = "query_hash"
        const val QUERY_HASH_ALG = "query_hash_alg"

        const val SHA_512 = "SHA-512"
        const val SHA512 = "SHA512"
        const val QUERY_HASH_FORMAT = "%0128x"
    }
}