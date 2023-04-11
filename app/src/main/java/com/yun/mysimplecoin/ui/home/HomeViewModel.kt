package com.yun.mysimplecoin.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.work.*
import com.yun.mysimplecoin.base.BaseViewModel
import com.yun.mysimplecoin.data.model.*
import com.yun.mysimplecoin.data.repository.ApiRepository
import com.yun.mysimplecoin.util.JwtUtil.newToken
import com.yun.mysimplecoin.util.PreferenceUtil
import com.yun.mysimplecoin.util.Util.calRsiMinute
import com.yun.mysimplecoin.util.Util.getStandardDeviation
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val sPrefs: PreferenceUtil,
    @Named("upbit") private val upbit_api: ApiRepository,
    @Named("crawling") private val crawling_api: ApiRepository
) : BaseViewModel(application) {

    private val accessToken = "mrPq8Ia8riJv5YQZYRQ5DhF42BYp9EXxr1ZzOhOI"

    val title = MutableLiveData("")

    var isRunning = false

    /**
     * 보유 코인 리스트
     */
    private var myCoins = ArrayList<MyCoins.RS>()

    /**
     * 모든 코인 이름
     */
    private var allCoinsNmList = ArrayList<AllCoinsNmModel.RS>()

    /**
     * 코인들의 공포 탐욕 지수
     */
    private lateinit var fearGreedList: FearGreedModel.RS

    /**
     * 매수 및 매도 대기중인 코인 목록
     */
    private var waitCoins = ArrayList<OrderModel.RS>()

    private var askCoins = ArrayList<Triple<String, String, String>>()

    /**
     * 코인들의 rsi, 이동 평균선, 볼린저 밴드, MACD
     * 프로세스 시작점에서 초기화 해줘야 함
     */
    private var coinIndexList = ArrayList<CoinIndexModel.data>()

    /**
     * 코인 현재가
     */
    private var tickers = ArrayList<TickerModel.RS>()

    init {

        startWork()
    }

    /**
     * 프로세스 시작
     * 1. 보유 중인 코인 목록을 가져오는 api 호출
     * 2. 성공적으로 가져오면 모든 코인들에 대한 정보를 가져오는 로직 시작
     * 3. 이후 모든 로직이 성공하면 isSuccess 가 true, 중간에 한 번이라도 오류가 발생하면 false
     */
    fun startWork() {
        title.postValue("api 가져오는 중...")
        isRunning = true
        coinIndexList = arrayListOf()
        myCoinsApi {
            if (!isRunning) {
                Log.d("lys-test", "running stop")
            } else if (it) allCoinsNmCall { isSuccess ->
                title.postValue("")

                if (!isRunning) {
                    Log.d("lys-test", "running stop")
                } else if (isSuccess) {
                    // 모든 계산 완료 > 이후 매수 및 매도 관리
                    //TODO 매도 및 매수 로직 시작
                    Log.d("lys-test", "모든 로직 완료. 이후 매수 및 매도 작업 진행")
                    coinIndexList.forEachIndexed { index, it ->
                        fearGreedList.pairs.forEachIndexed { _, data ->
                            if (data.code.contains(it.market)) {
                                coinIndexList[index].score = data.score
                                return@forEachIndexed
                            }
                        }
                    }

                    checkAskBid()


                    Log.d("lys-test", "result > ${coinIndexList[0]}")

                    Log.d("lys-test", "size : ${allCoinsNmList.size}  size: ${coinIndexList.size}")
                } else {
                    Log.e("lys-test", "중간에 뭔가 오류가 있었음. 초기화 후 재진행")
                    // 중간에 뭔가 오류가 발생함 > 초기화하고 다시 작업
                }
                isRunning = false
            }
            else {
            } // network error
        }
    }

    /**
     * 프로세스 종료
     */
    fun stopWork() {
        isRunning = false
    }

    /**
     * 모든 코인들의 정보를 가져오는 로직
     * 1. 모든 코인의 정보를 가져오는 api 호출 > 거래 재화가 KRW 이며, 주의가 아닌 항목에 대해서만
     * 2. 성공적으로 가져오면 공포/탐욕 지수 정보 가져오는 로직 시작
     */
    private fun allCoinsNmCall(callBack: (Boolean) -> Unit) {
        allCoinsNmApi {
            if (!isRunning) {
                Log.d("lys", "running stop")
                callBack(false)
            } else if (it) crawlingCall(callBack)
            else {
                // network error
                Log.e("lys", "allCoinsNmApi fail")
                callBack(false)
            }
        }
    }

    /**
     * 공포/탐욕 지수 정보 가져오는 로직
     * 1. 공포/탐욕 지수 정보를 가져오는 api 호출
     * 2. 성공적으로 가져오면 업비트 코인 목록들에 대해 각각 5분 간격의 캔들 데이터를 조회하는 로직 시작
     */
    private fun crawlingCall(callBack: (Boolean) -> Unit) {
        crawlingApi {
            if (!isRunning) {
                Log.d("lys", "running stop")
                callBack(false)
            } else if (it) candleMinutesCall(callBack = callBack)
            else {
                // network error
                Log.e("lys", "crawlingApi fail")
                callBack(false)
            }
        }
    }

    /**
     * 코인들의 5분 간격 캔들 데이터를 조회하는 로직
     * 1. 코인들의 5분 간격 캔들 데이터를 조회하는 api 호출
     * 2. 성공적으로 모두 가져오면, 1일 간격 캔들 데이터를 조회하는 로직 시작
     */
    private fun candleMinutesCall(index: Int = 0, callBack: (Boolean) -> Unit) {
        if (index == allCoinsNmList.size) candleDaysCall(callBack = callBack) // 마지막 코인까지 api 전송했으면 다음 로직
        else if (!isRunning) callBack(false) // 일시 정지 눌렀을 경우 로직 정지
        else {
            candlesMinutesApi("5", allCoinsNmList[index].market) {
                if (it) candleMinutesCall(index + 1, callBack)
                else callBack(false)
            }
        }
    }

    /**
     * 코인들의 1일 간격 캔들 데이터 조회하는 로직
     * 1. 코인들의 1일 간격 캔들 데이터를 조회하는 api 호출
     * 2. 성공적으로 모두 가져오면,
     */
    private fun candleDaysCall(index: Int = 0, callBack: (Boolean) -> Unit) {
        if (index == allCoinsNmList.size) callBack(true) // 마지막 코인까지 api 전송했으면 callBack
        else if (!isRunning) callBack(false) //
        else {
            candlesDaysApi(allCoinsNmList[index].market, "100") {
                if (it) candleDaysCall(index + 1, callBack)
                else callBack(false)
            }
        }
    }

    /**
     * 지수를 기반으로 매수 및 매도 계산
     */
    private fun checkAskBid() {
        // case 1. 이동 평균선이 상승 추세, MACD 가 0을 상향 돌파, 볼린저 밴드의 상한선을 돌파 "RSI 70 이상" > 매도
        // case 2. 이동 평균선이 하락 추세, MACD 가 0을 하향 돌파, 볼린저 밴드의 하한선을 돌파 "RSI 30 이하" > 매수


        // case 3. 이동 평균선 상향 돌파, MACD 신호선 상향, 볼린저 밴드 상한선 > 매수
        // case 4. 이동 평균선 하향 돌파, MACD 신호선 하향, 불린저 밴드 하한선, RSI 30 이하 > 매도

        // case 5. RSI 70이상, 탐욕 지수 > 매도
        // case 6. RSI 30이하, 공포 지수 > 매수

        ticker(tickerParams()) {
            if (it) {
                Log.d("lys","#####################")
                coinIndexList.forEachIndexed { index, coin ->
                    var log = "${coin.market}(${allCoinsNmList[index].korean_name})"
                    log += " > 현재가 ${tickers[index].trade_price}"

//                    if (coin.rsi!!.rsi.toDouble() >= 70.0) log += " > rsi 70 이상(과매수)"
//                    else if (coin.rsi!!.rsi.toDouble() <= 30.0) Log.d("lys", "> rsi 30 이하(과매도)")
//
//                    if (coin.score != null && coin.score!!.toDouble() >= 70.0) log += " > 탐욕"
//                    else if (coin.score != null && coin.score!!.toDouble() <= 30.0) log += " > 공포"



                    if(tickers[index].trade_price > coin.mv!!.middle
                        && coin.macd!!.fast.toDouble() - coin.macd!!.slow.toDouble() > 0.0
                        && tickers[index].trade_price > coin.bb!!.upper){
                        if (coin.rsi!!.rsi.toDouble() >= 70.0){
                            // case 3
                            // TODO 매수
                            log += " > 매수(1)"
                        } else {
                            // case 1
                            // TODO 매도
                            log += " > 매도(1)"
                        }
                    }
                    else if(tickers[index].trade_price < coin.mv!!.middle
                        && coin.macd!!.fast.toDouble() - coin.macd!!.slow.toDouble() < 0.0
                        && tickers[index].trade_price < coin.bb!!.lower){
                        if (coin.rsi!!.rsi.toDouble() <= 30.0){
                            // case 4
                            // TODO 매도
                            log += " > 매도(2)"
                        } else {
                            // case 2
                            // TODO 매수
                            log += " > 매수(2)"
                        }
                    }
                    else if(coin.rsi!!.rsi.toDouble() >= 70.0
                        && coin.score != null && coin.score!!.toDouble() >= 70.0){
                        // TODO 매도
                        log += " > 매도(3)"
                    }
                    else if(coin.rsi!!.rsi.toDouble() <= 30.0
                        && coin.score != null && coin.score!!.toDouble() <= 30.0){
                        // TODO 매수
                        log += " > 매수(3)"
                    }

                    if(log.contains("매도")){
                        myCoins.forEach {
                            if(("KRW-"+it.currency) == coin.market){
                                // 내가 보유하고 있다
                                //TODO 매도 목록에 추가
                                log += " > 보유"
                            }
                        }
                    }

                    if(log.contains("매수") || log.contains("매도")){
                        Log.d("lys", log)
                    }
                }
            } else {
                //TODO network error
            }
        }
    }

    /**
     * ticker parameter
     * 검색 가능한 모든 코인들의 이름을 리턴
     * ex) KRW-BTC,KRW-WAXP,KRW-STPT ...
     */
    private fun tickerParams() : String {
        var coinNm = ""
        coinIndexList.forEachIndexed { index, coin ->
            if (index != 0) coinNm += ","
            coinNm += coin.market
        }
        return coinNm
    }

    /**
     * 보유 중인 코인을 가져오는 api
     */
    private fun myCoinsApi(failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        if (title.value != "" && failCnt == 0) return
        upbit_api.myCoins(newToken(mContext, accessToken))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }.subscribe({
                Log.d("lys", "myCoins success > $it")
                myCoins = arrayListOf()
                myCoins.addAll(it)
                callBack(true)
            }, {
                if (failCnt < 100) myCoinsApi(failCnt + 1, callBack)
                else callBack(false); Log.e("lys", "myCoins fail > $it")
            })
    }

    private fun allCoinsNmApi(failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        upbit_api.allCoinsNm()
            .subscribeOn(Schedulers.io())
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
        failCnt: Int = 0,
        callBack: (Boolean) -> Unit
    ) {
        upbit_api.candlesMinutes(unit, market)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
                coinIndexList.add(
                    CoinIndexModel.data(
                        market,
                        rsi = CoinIndexModel.data.Rsi(calRsiMinute(it as ArrayList<CandlesModel.RS>).toString())
                    )
                )
                callBack(true)
            }, {
                if (failCnt < 100) candlesMinutesApi(unit, market, failCnt + 1, callBack)
                else {
                    Log.e("lys", "candlesMinutes fail($failCnt) > $it")
                    callBack(false)
                }
            })
    }

    /**
     * 지정한 일수만큼 코인 종가를 가져온다
     */
    private fun candlesDaysApi(
        market: String,
        count: String,
        failCnt: Int = 0,
        callBack: (Boolean) -> Unit
    ) {
        upbit_api.candlesDays(market, count)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
//                Log.d("lys", "candlesDays success > $it")


                var slow = 0.0 // 장기
                var fast = 0.0 // 단기
                var middle = 0.0 // 중심

                var slowCnt = 0
                var fastCnt = 0
                var middleCnt = 0

                val middleList = ArrayList<Double>()

                it.forEachIndexed { index, coin ->
                    if (index < 10) {
                        fast += coin.trade_price.toDouble()
                        fastCnt++
                    }
                    if (index < 20) {
                        middle += coin.trade_price.toDouble()
                        middleCnt++
                        middleList.add(coin.trade_price.toDouble())
                    }
                    if (index < 50) {
                        slow += coin.trade_price.toDouble()
                        slowCnt++
                    }
                }

                fast /= fastCnt
                middle /= middleCnt
                slow /= slowCnt

                val standardDeviation = getStandardDeviation(middleList)
                val upper = middle + (standardDeviation * 2)
                val lower = middle - (standardDeviation * 2)
                coinIndexList.forEachIndexed { index, coinIndex ->
                    if (coinIndex.market == market) {
                        coinIndexList[index].mv =
                            CoinIndexModel.data.Mv(DecimalFormat("#.###").format(middle))
                        coinIndexList[index].macd = CoinIndexModel.data.Macd(
                            DecimalFormat("#.###").format(fast),
                            DecimalFormat("#.###").format(slow),
                        )
                        coinIndexList[index].bb = CoinIndexModel.data.Bb(
                            DecimalFormat("#.###").format(upper),
                            DecimalFormat("#.###").format(lower)
                        )
                        return@forEachIndexed
                    }
                }
                callBack(true)
            }, {
                if (failCnt < 100) candlesDaysApi(market, count, failCnt + 1, callBack)
                else {
                    Log.e("lys", "candlesDays fail($failCnt) > $it")
                    callBack(false)
                }
            })
    }

    private fun calRsiMinuteCall(list: ArrayList<CandlesModel.RS>, callBack: (Boolean) -> Unit) {
        val rsiResult = calRsiMinute(list)

//        if (rsiResult.size == 0) {
//            title.value = ""
//            Log.d("lys", "calRsiMinute is empty")
//        } else {
        // 매수 및 매도 해야할 코인이 있다
//            crawlingApi {
//                if (it) {
        val askCoins = arrayListOf<Pair<String, String>>()
        val bidCoins = arrayListOf<Pair<String, String>>()
//            rsiResult.forEach { r ->
//                fearGreedList.pairs.forEach { p ->
//                    if (p.code.contains(r.first)) {
//                        if (p.score <= "30" && r.second == ASK) {
//                            // 매수 코인
//                            askCoins.add(Pair(r.first, r.second))
//                        } else if (p.score >= "70" && r.second == BID) {
//                            // 매도 코인
//                            bidCoins.add(Pair(r.first, r.second))
//                        }
//                    }
//                }
//            }
        if (askCoins.size == 0 && bidCoins.size == 0) {
            title.value = ""
        } else {
            Log.d("lys", "askCoin > $askCoins  bidCoin > $bidCoins")
            sellCoinCheck(askCoins) {
                if (it) {
                    //구매 ㄲ
                }
            }

//                    }

//                } else {
//                    // network error
//                }
//            }
        }
    }

    private fun crawlingApi(failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        crawling_api.crawling()
            .subscribeOn(Schedulers.io())
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

    private fun ticker(markets: String, failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        upbit_api.ticker(markets)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
                Log.d("lys", "ticker success > $it")
                tickers = it as ArrayList<TickerModel.RS>
                callBack(true)
            }, {
                Log.e("lys", "ticker fail > $it")
                if (failCnt < 100) ticker(markets, failCnt + 1, callBack)
                else callBack(false)
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
            }?.subscribeOn(Schedulers.io())
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
            .subscribeOn(Schedulers.io())
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