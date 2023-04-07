package com.yun.mysimplecoin.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import com.yun.mysimplecoin.base.BaseViewModel
import com.yun.mysimplecoin.common.constants.OrderConstants.SIDE.ASK
import com.yun.mysimplecoin.common.constants.OrderConstants.SIDE.BID
import com.yun.mysimplecoin.data.model.*
import com.yun.mysimplecoin.data.repository.ApiRepository
import com.yun.mysimplecoin.util.JwtUtil.newToken
import com.yun.mysimplecoin.util.PreferenceUtil
import com.yun.mysimplecoin.util.Util.calRsiMinute
import com.yun.mysimplecoin.util.Util.getStandardDeviation
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val sharedPreferences: PreferenceUtil,
    @Named("upbit") private val upbit_api: ApiRepository,
    @Named("crawling") private val crawling_api: ApiRepository
) : BaseViewModel(application) {

    private val accessToken = "mrPq8Ia8riJv5YQZYRQ5DhF42BYp9EXxr1ZzOhOI"

    val title = MutableLiveData("")

    /**
     * 보유 코인 리스트
     */
    private var myCoins = ArrayList<MyCoins.RS>()

    /**
     * 모든 코인 이름
     */
    private var allCoinsNmList = ArrayList<AllCoinsNmModel.RS>()

    /**
     * 코인들의 5분간의 거래 동향
     */
    private var candlesMinutesList = ArrayList<CandlesMinutesModel.RS>()

    /**
     * 코인들의 공포 탐욕 지수
     */
    private lateinit var fearGreedList: FearGreedModel.RS

    /**
     * 매수 및 매도 대기중인 코인 목록
     */
    private var waitCoins = ArrayList<OrderModel.RS>()

    private var askCoins = ArrayList<Triple<String, String, String>>()

    init {

        testApi()
    }

    fun testApi() {

        var a = 0.0
        candlesDaysApi("KRW-BTC", "20") { s ->
            if (s != null) {
                candlesDaysApi("KRW-BTC", "100") { l ->
                    if (l != null) {
                        var short = 0.0
                        var long = 0.0
                        var priceList = arrayListOf<Double>()
                        s.forEach {
                            short += it.trade_price.toDouble()
                            priceList.add(it.trade_price.toDouble())
                        }
                        short /= s.size

                        l.forEach {
                            long += it.trade_price.toDouble()
                        }
                        long /= l.size
                        if (short > long) Log.d("lys", "$short $long 상향 돌파")
                        else if (short < long) Log.d("lys", "$short $long 하향 돌파")
                        else Log.d("lys", "$short $long 유지")

                        orderBookApi("KRW-BTC") { data ->
                            if (data != null) {
                                Log.d("lys", "매도 호가 > ${data.orderbook_units[0].ask_price}")
                            }
                        }


                        val standardDeviation = getStandardDeviation(priceList)
                        Log.d("lys", "standardDeviation > ${standardDeviation}")
//                        상한선 = 중심선 + (표준편차 계수(2) X 주가의 표준편차)
//                        하한선 = 중심선 - (표준편차 계수(2) X 주가의 표준편차)
                        val a = short + (standardDeviation * 2)
                        val b = short - (standardDeviation * 2)
                        Log.d("lys","상한선 > $a  하한선 > $b")


                        candlesMinutesApi("5", "KRW-BTC") {
//                            if (it) cnt++
//                            if (cnt == allCoinsNmList.size) calRsiMinuteCall()
                            if(it){
                                calRsiMinuteCall()
                            }
                        }

                    }
                }
            }
        }
    }

    fun startWork() {
        myCoinsApi {
            if (it) allCoinsNmCall()
            else {
                // network error
            }
        }
    }

    fun stopWork() {
    }

    fun myCoinsApi(failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        if (title.value != "") return
        title.value = "api 가져오는 중..."
        upbit_api.myCoins(newToken(mContext, accessToken)).observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .flatMap { Observable.just(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }.subscribe({
                myCoins = arrayListOf()
                myCoins.addAll(it)
                Log.d("lys", "myCoins success > $it")
                callBack(true)
//                allCoinsNmCall()
            }, {
                if (failCnt < 100) myCoinsApi(failCnt + 1, callBack)
                else {
                    Log.e("lys", "myCoins fail > $it")
                    callBack(false)
                }
            })
    }

    private fun allCoinsNmCall() {
        allCoinsNmApi {
            if (it) {
                var cnt = 0
                allCoinsNmList.forEach {
                    candlesMinutesApi("5", it.market) {
                        if (it) cnt++
                        if (cnt == allCoinsNmList.size) calRsiMinuteCall()
                    }
                }
            } else {
                // network error
            }
        }
    }

    private fun allCoinsNmApi(failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        upbit_api.allCoinsNm().observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .flatMap { Observable.just(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
                Log.d("lys", "allCoinsNm success $it")
                allCoinsNmList = arrayListOf()
                it.forEach { data ->
                    if (data.market_warning == "NONE" && data.market.contains("KRW-")) {
                        allCoinsNmList.add(data)
                    }
                }
                callBack(true)
            }, {

                if (failCnt < 100) allCoinsNmApi(failCnt + 1, callBack)
                else {
                    Log.e("lys", "allCoinsNm fail > $it")
                    callBack(false)
                }
            })
    }

    private fun candlesMinutesApi(
        unit: String,
        market: String,
        callBack: (Boolean) -> Unit
    ) {
        upbit_api.candlesMinutes(unit, market).observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .flatMap { Observable.just(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
                candlesMinutesList = arrayListOf()
//                Log.d("lys", "candlesMinutes success > $it")
                candlesMinutesList.addAll(it)
                callBack(true)
            }, {
//                Log.e("lys", "candlesMinutes fail > $it")
                candlesMinutesApi(unit, market, callBack)
            })
    }

    private fun calRsiMinuteCall() {
        Log.d("lys", "--------------------------------")
        val rsiResult = calRsiMinute(candlesMinutesList)

        if (rsiResult.size == 0) {
            title.value = ""
            Log.d("lys", "calRsiMinute is empty")
        } else {
            // 매수 및 매도 해야할 코인이 있다
            crawlingApi {
                if (it) {
                    val askCoins = arrayListOf<Pair<String, String>>()
                    val bidCoins = arrayListOf<Pair<String, String>>()
                    rsiResult.forEach { r ->
                        fearGreedList.pairs.forEach { p ->
                            if (p.code.contains(r.first)) {
                                if (p.score <= "30" && r.second == ASK) {
                                    // 매수 코인
                                    askCoins.add(Pair(r.first, r.second))
                                } else if (p.score >= "70" && r.second == BID) {
                                    // 매도 코인
                                    bidCoins.add(Pair(r.first, r.second))
                                }
                            }
                        }
                    }
                    if (askCoins.size == 0 && bidCoins.size == 0) {
                        title.value = ""
                    } else {
                        Log.d("lys", "askCoin > $askCoins  bidCoin > $bidCoins")
                        sellCoinCheck(askCoins) {
                            if (it) {
                                //구매 ㄲ
                            }
                        }

                    }

                } else {
                    // network error
                }
            }
        }
    }

    private fun crawlingApi(failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        crawling_api.crawling().observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .flatMap { Observable.just(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
                fearGreedList = it
                Log.d("lys", "crawling success > $it")
                callBack(true)
            }, {

                if (failCnt < 100) crawlingApi(failCnt + 1, callBack)
                else {
                    Log.e("lys", "crawling fail > $it")
                    callBack(false)
                }
            })
    }

    /**
     * @param coins > 매도하는게 좋은 코인 목록(market, bid or ask)
     */
    private fun sellCoinCheck(coins: ArrayList<Pair<String, String>>, callBack: (Boolean) -> Unit) {
        // 일단 팔거 팔고 남은 돈 조회해서, 살 목록 중에서 구매한다
        // 팔기 전에, 이미 가지고 있으면 추매 하자(과거에 구매한 이력 조회해서 최근 12시간 이내)
        if (coins.size == 0) {
            callBack(true)
            return
        }
        orders("wait", "wait") {
            if (it) {

                if (waitCoins.size == 0) {
                    // 매수매도 대기 목록이 없다면 바로 매수 매도
                    // TODO 매수
                } else {
                    coins.forEach { coin ->
                        var isOrder = false
                        waitCoins.forEach { wait ->
                            // 대기 목록에 있는 코인과 매도 조건에 있는 코인이 겹치는 경우
                            if (wait.market == coin.first && wait.side == coin.second) {
                                isOrder = true
                                // 매도 조건 코인이 대기 목록에 있다면 걸었던 걸 취소하고, 보유중인 모든 코인을 매도
//                                orders("cancel", wait.uuid) {
//                                    if (it) {
//                                        // 나의 코인 정보를 재검색 한 이후에 매도
//                                        coinAsk(coin)
//                                    }
//                                }
                                askCoins.add(Triple(coin.first, coin.second, wait.uuid))
                            }
                        }
                        if (!isOrder) {
                            // 대기 목록은 있지만, 매수 매도 조건 코인들과 겹치지 않았을 경우 > 위 forEach 에서 if에 걸리지 않았을 경우를 뜻함
//                            coinAsk(coin)
                            askCoins.add(Triple(coin.first, coin.second, ""))
                        }
                    }
                    coinCancel()
                }
            }
        }
    }

    /**
     * 주문 조회 > wait
     * 주문 취소 > cancel
     * 매도 > ask
     * 매수 > bid
     */
    private fun orders(
        type: String,
        vararg params: String,
        failCnt: Int = 0,
        callBack: (Boolean) -> Unit
    ) {

        upbit_api.run {
            when (type) {
                "wait" ->
                    orders(
                        newToken(
                            mContext,
                            accessToken,
                            Pair("state", params[0]),
                            Pair("page", "1")
                        ),
                        params[0], "1"
                    )
                "cancel" -> order(
                    newToken(mContext, accessToken, Pair("uuid", params[0])),
                    params[0]
                )
                "ask" -> orders(
                    newToken(
                        mContext, accessToken,
                        Pair("market", params[0]),
                        Pair("ord_type", "market"),
                        Pair("volume", params[1]),
                        Pair("side", "ask")
                    ), OrderModel.ASK(params[0], "ask", params[1], "market")
                )
                else -> null
            }?.observeOn(Schedulers.io())
                ?.subscribeOn(Schedulers.io())
                ?.flatMap { Observable.just(it) }
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.map { it }
                ?.subscribe({
                    Log.d("lys", "orders $type success > $it")
                    when (type) {
                        "wait" -> {
                            (it as? List<*>)?.let {
                                waitCoins = arrayListOf()
                                waitCoins.addAll(it.filterIsInstance<OrderModel.RS>())
                            }
                        }
                        "cancel" -> {

                        }
                        "ask" -> {

                        }
                        "bid" -> {

                        }
                    }
                    callBack(true)

                }, {
                    if (failCnt < 100) orders(
                        type = type,
                        params = params,
                        failCnt = +1,
                        callBack = callBack
                    )
                    else {
                        Log.e("lys", "orders $type fail > $it")
                        callBack(false)
                    }
                }) ?: callBack(false)
        }
    }

    private fun askCheck(market: String, callBack: (Boolean) -> Unit) {
        orderBookApi(market) {
            if (it != null) {
                myCoins.run {
                    forEachIndexed { index, rs ->
                        if ("${rs.unit_currency}-${rs.currency}" == market && it.orderbook_units[0].ask_price.toDouble() * rs.balance.toDouble() < 5000.0) {
                            callBack(true)
                        }
                    }
                }
            } else {
                // network error
            }
        }
    }

    /**
     * 코인 호가
     */
    private fun orderBookApi(
        market: String,
        failCnt: Int = 0,
        callBack: (OrderBookModel.RS?) -> Unit
    ) {
        upbit_api.orderBook(market)
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .flatMap { Observable.just(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
                Log.d("lys", "orderBook success > $it")
                callBack(it[0])
            }, {
                if (failCnt < 100) orderBookApi(market, failCnt + 1, callBack)
                else {
                    Log.e("lys,", "orderBook fail > $it")
                    callBack(null)
                }
            })
    }

    /**
     * 예약 중인 코인을 예약 취소
     */
    private fun coinCancel() {
        // 매도 조건 코인이 대기 목록에 있다면 걸었던 걸 취소하고, 보유중인 모든 코인을 매도
        askCoins.forEach { coin ->
            if (coin.third != "") {
                orders("cancel", coin.third) {
                    if (it) {
                        coinAsk(coin)
                    }
                }
            } else {
                coinAsk(coin)
            }
        }
    }

    /**
     * 코인 매도
     */
    private fun coinAsk(coin: Triple<String, String, String>) {
        // 나의 코인 정보를 재검색 한 이후에 매도
        myCoinsApi {
            if (it) {
                // 최소 주문 금액을 충족하는지 체크 > 5,000원 이상
                askCheck(coin.first) {
                    if (it) {
                        // 모든 절차를 통과했다면 실제로 매도 진행
                        orders("ask", coin.first) { }
                    }
                }
            }
        }
    }

    /**
     * 지정한 일수만큼 코인 종가를 가져온다
     */
    private fun candlesDaysApi(
        market: String,
        count: String,
        callBack: (List<CandlesMinutesModel.RS>?) -> Unit
    ) {
        upbit_api.candlesDays(market, count)
            .observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .flatMap { Observable.just(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
                Log.d("lys", "candlesDays success > $it")
                callBack(it)
//                orderBookApi(market){ data ->
//                    if(data != null){
//                        var temp = 0.0
//                        it.forEach {
//                            temp += it.trade_price.toDouble()
//                        }
//                        Log.d("lys","평균(${count}) > ${temp / it.size}  |  매도 호가 > ${data.orderbook_units[0].ask_price}")
////                        Log.d("lys","평균 > ${String.format("%.4f",temp / it.size)}  |  매도 호가 > ${String.format("%.4f",data.orderbook_units[0].ask_price)}")
//                        if(data.orderbook_units[0].ask_price.toDouble() > (temp / it.size)){
//                            Log.d("lys","상승")
//                        } else {
//                            Log.d("lys","하락")
//                        }
//                    }
//                }
//                callBack(it)
            }, {
                Log.e("lys", "candlesDays fail > $it")
                callBack(null)
            })
    }

//    fun testApi(){
//        upbit_api.orders(newToken(mContext, accessToken, Pair("state","done"),Pair("page","1")),"done","1").observeOn(Schedulers.io())
//            .subscribeOn(Schedulers.io())
//            .flatMap { Observable.just(it) }
//            .observeOn(AndroidSchedulers.mainThread())
//            .map { it }
//            .subscribe({
//                Log.d("lys","orders success > $it")
//            },{
//                Log.e("lys","orders fail > $it")
//            })
//    }


}


/**
 * RS(uuid=cc49ccc9-a049-439a-ac1a-b1db3e9990f7, side=ask, ord_type=limit, price=500, state=wait, market=KRW-DOGE, created_at=2023-03-31T11:06:16+09:00, volume=15, remaining_volume=15, reserved_fee=0, remaining_fee=0, paid_fee=0, locked=15, executed_volume=0, trades_count=0, trades=null),
 * RS(uuid=a535b82d-94e1-4401-b6b2-b3e304c059e3, side=bid, ord_type=limit, price=1500000, state=wait, market=KRW-ETH, created_at=2023-03-31T11:05:19+09:00, volume=0.004, remaining_volume=0.004, reserved_fee=3, remaining_fee=3, paid_fee=0, locked=6003, executed_volume=0, trades_count=0, trades=null)
 */


//이동 평균선, MACD, 볼린저 밴드, RSI, 공포탐욕지수

// 이동평균선 > 최근 5일의 종가를 더한 후 5로 나눈 값이 현재 주가와 비교해서 높낮이를 확인 > 현재 주가가 높으면 상승 추세, 낮으면 하락 추세
// 단기 이동 평균선과 장기 이동평균선이 교차하는 지점을 기반으로 매수 매도
// 단기 평균선이 장기 평균선을 아래에서 위로 돌파(상향돌파)하면 상승 추세 > 매수 타이밍 (MACD)
// 단기 평균선이 장기 평균선을 위에서 아래로 돌파(하향돌파)하면 하락 추세 > 매도 타이밍 (MACD)

// MACD > 빠른 지수와 느린 지슈의 차이를 계산
// 빠른 지수 이동평균(12일), 느린 지수이동편균(26일)
// MACD 선 = 빠른지수 - 느린지수 결과가 0보다 크면 상승 추세, 0보다 작으면 하락 추세
// 신호선(9일 이동평균) 과 MACD 선이 교차할 때 매수/매도
// MACD 선이 신호선을 상향 돌파하면 매수 신호
// MACD 선이 신호선을 하향 돌파하면 매도 신호

// 볼린저 밴드
// 1. 이동편균선(중심선)을 구함(20일)
// 2. 상한선 = 중심선 + (표준편차 계수(2) X 주가의 표준편차)
// 3. 하한선 = 중심선 - (표준편차 계수(2) X 주가의 표준편차)
// 표준편차 >  주가가 1일부터 5일까지 각각 10, 20, 30, 40, 50원이라면,
// 주가의 평균은 (10+20+30+40+50)/5 = 30원
// 이 때, 분산을 계산하기 위해 (10-30)² + (20-30)² + (30-30)² + (40-30)² + (50-30)²
// 400 + 100 + 0 + 100 + 400 = 1000
// 루트(1000 / (5 - 1)) = 루트(250)
// 상한선을 돌파하면 과대매수, 하한선을 돌파하면 과대매도 상태
// 현재 주가가 중심선 위쪽으로 상승하면서 상한선 돌파하면 상한 돌파 > 과대매수

// rsi
// 70 이상이면 과매수 > 지금 현재 코딩으론 매도하게 코딩
// 30 이하라면 과매도 > 지금 현재 코딩으론 매수하게 코딩

// 공포탐욕
// 탐욕이 높을수록 상승장 0.8 > 현재 시점으론 매도하게 코딩
// 공포가 높을수록 하락장 0.2 > 현재 시점으론 매수하게 코딩

/**
 *
 * 이동 평균선: 이동 평균선은 주가의 추세를 나타냅니다. 이동평균선이 상승하는 추세에서는 매수, 하락하는 추세에서는 매도를 하는 것이 적절합니다.
 * MACD: MACD는 단기 이동 평균과 장기 이동 평균의 차이를 보여주는 지표입니다. MACD가 0을 상향돌파하면 매수, 하향돌파하면 매도를 하는 것이 적절합니다.
 * 볼린저 밴드: 볼린저 밴드는 이동 평균선을 중심으로 상한선과 하한선을 그려 매수/매도 타이밍을 결정하는 지표입니다. 볼린저 밴드의 상한선을 돌파하면 매도, 하한선을 돌파하면 매수를 하는 것이 적절합니다.
 * RSI 값: RSI는 상승과 하락의 강도를 나타내는 지표입니다. 70 이상이면 과매수, 30 이하면 과매도라고 판단하여 매도/매수를 하는 것이 적절합니다.
 * 공포탐욕 지수: 공포탐욕 지수는 시장 참여자들의 감성을 반영한 지표로, 공포 지수가 높으면 매수하고, 탐욕 지수가 높으면 매도하는 것이 적절합니다.
 */

// case 1. 이동 평균선이 상승 추세, MACD 가 0을 상향 돌파, 볼린저 밴드의 상한선을 돌파 > 매도
// case 2. 이동 평균선이 하락 추세, MACD 가 0을 하향 돌파, 볼린저 밴드의 하한선을 돌파 > 매수

// case 3. 이동 평균선 상향 돌파, MACD 신호선 상향, 볼린저 밴드 상한선, RSI 70 이상 > 매수
// case 4. 이동 평균선 하향 돌파, MACD 신호선 하향, 불린저 밴드 하한선, RSI 30 이하 > 매도

// 좀 더 자세히 찾아봐야 할듯...

// 이동 평균선과 RSI가 계단식 하향에 더 민감하다고 함..
// 순간적인 하락과 상승에는 RSI 와 공포 탐욕 지수에 민감 > 단기적인 추세
// 이동 평균선과 MACD, 볼린저 밴드는 장기적인 추세 파악에 더 나음

// 보편적으로 10% 손실 보면 매도
// 상승 폭에 따른 매도는 고민 해 봐야 할듯