package com.yun.mysimplecoin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yun.mysimplecoin.R
import com.yun.mysimplecoin.data.model.*
import com.yun.mysimplecoin.data.repository.ApiRepository
import com.yun.mysimplecoin.ui.main.MainActivity
import com.yun.mysimplecoin.util.JwtUtil
import com.yun.mysimplecoin.util.Util
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import retrofit2.Response
import java.text.DecimalFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class CoinService : Service() {

    val mBinder = MyBinder()

    inner class MyBinder : Binder() {
        fun getService(): CoinService = this@CoinService
    }

    override fun onBind(p0: Intent?): IBinder = mBinder

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    @Inject
    @Named("upbit")
    lateinit var upbit_api: ApiRepository

    @Inject
    @Named("crawling")
    lateinit var crawling_api: ApiRepository

    private val CHANNEL_ID = "ForegroundServiceCoinChannel"
    private val NOTIFICATION_ID = 1703

    private var accessToken = "mrPq8Ia8riJv5YQZYRQ5DhF42BYp9EXxr1ZzOhOI"

    /**
     * 로직 실행중 여부
     */
    var isRunning = false

    var stop = false

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

    /**
     * 매도 코인
     */
    private var askCoins = ArrayList<String>()

    /**
     * 매수 코인
     */
    private var bidCoins = ArrayList<String>()

    /**
     * 코인들의 rsi, 이동 평균선, 볼린저 밴드, MACD
     * 프로세스 시작점에서 초기화 해줘야 함
     */
    private var coinIndexList = ArrayList<CoinIndexModel.data>()

    /**
     * 코인 현재가
     */
    private var tickers = ArrayList<TickerModel.RS>()

    /**
     * 코인 거래 내역 > done
     */
    private var doneCoins: ArrayList<OrderModel.RS>? = arrayListOf()

    /**
     * 코인 거래 내역 > cancel
     */
    private var cancelCoins: ArrayList<OrderModel.RS>? = arrayListOf()

    private fun createNotificationChannel(
        context: Context,
        importance: Int,
        showBadge: Boolean,
        name: String,
        description: String,
        _channel: String
    ) {
        val channel = NotificationChannel(_channel, name, importance)
        channel.description = description
        channel.setShowBadge(showBadge)
        notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun settingNotification() {
        createNotificationChannel(
            this, NotificationManager.IMPORTANCE_NONE, false,
            "Foreground Service", "위치 정보를 수집하고 있습니다", CHANNEL_ID
        )

        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("testTitle")
            .setContentText("testMessage")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setShowWhen(false) // 시간표시
            .setAutoCancel(false)
            .setGroup("gps")
            .setContentIntent(pendingIntent)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    override fun onDestroy() {
        notificationManager.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        settingNotification()
    }

    /**
     * 배열 데이터 초기화
     */
    private fun clearData() {
        tickers = arrayListOf()
        coinIndexList = arrayListOf()
        bidCoins = arrayListOf()
        askCoins = arrayListOf()
        waitCoins = arrayListOf()
        allCoinsNmList = arrayListOf()
        myCoins = arrayListOf()
        doneCoins = arrayListOf()
        cancelCoins = arrayListOf()
    }

    /**
     * 로직 종료 후 재시작
     */
    private fun reStartWork() {
        isRunning = false
        CoroutineScope(Dispatchers.IO).launch {
            delay(2000)
            runCoinTrading()
        }
    }

    fun start() {
        stop = false
        CoroutineScope(Dispatchers.IO).launch {
            runCoinTrading()
        }
    }

    /**
     * 프로세스 시작
     * 1. 매수 및 매도 대기중인 코인들의 정보를 가져오는 로직 시작
     * 2. 이후 모든 로직이 성공하면 isSuccess 가 true, 중간에 한 번이라도 오류가 발생하면 false
     * 3. isSuccess 가 true > 지수를 계산하여 매수 및 매도 로직 시작
     */
    private suspend fun runCoinTrading() {
        if (stop) return
        Log.d("lys", "ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ")
        clearData()
        isRunning = true
        if (myWaitCoins() && startCalculation()) {
            Log.d("lys", "success end")
            reStartWork()
        } else {
            if (isRunning) {
                Log.e("lys", "중간에 뭔가 오류가 있었음. 초기화 후 재진행")
                reStartWork()
            } else {
                Log.d("lys", "stop end")
            }
        }
    }

    /**
     * 프로세스 종료
     */
    fun stopWork() {
        isRunning = false
        stop = true
    }

    /**
     * 필요한 정보를 모두 가져왔으면 실제 지수를 바탕으로 매수 및 매도 로직 시작
     */
    private suspend fun startCalculation(): Boolean {
        coinIndexList.forEach { coin ->
            fearGreedList.pairs.find { it.code.contains(coin.market) }?.let { pair ->
                coin.score = pair.score
            }
        }
        return checkAskBid()
    }

    /**
     * 매수 및 매도 대기중인 코인들의 정보를 가져오는 로직
     * 1. 매수 및 매도 대기중인 코인의 정보를 가져오는 api 호출
     * 2. 성공적으로 가져오면 대기 중인 코인들의 주문 취소하는 로직 시작
     */
    private suspend fun myWaitCoins() = isRunning && withContext(Dispatchers.IO) {
        ordersWait() && cancelCoinsCall()
    }

    /**
     * 대기 중인 코인들을 주문 취소하는 로직
     * 1. 매수 및 매도 대기중인 코인의 주문 취소하는 api 호출
     * 2. 성공적으로 완수하면 보유 중인 코인들의 정보를 가져오는 로직 시작
     */
    private suspend fun cancelCoinsCall() = isRunning && withContext(Dispatchers.IO) {
        ordersCancel() && myCoinsCall()
    }

    /**
     * 보유 중인 코인들의 정보를 가져오는 로직
     * 1. 보유 중인 코인의 정보를 가져오는 api 호출
     * 2. 성공적으로 가져오면 모든 코인들의 정보를 가져오는 로직 시작
     */
    private suspend fun myCoinsCall() = isRunning && withContext(Dispatchers.IO) {
        myCoinsApi() && allCoinsNmCall()
    }

    /**
     * 모든 코인들의 정보를 가져오는 로직
     * 1. 모든 코인의 정보를 가져오는 api 호출 > 거래 재화가 KRW 이며, 주의가 아닌 항목에 대해서만
     * 2. 성공적으로 가져오면 공포/탐욕 지수 정보 가져오는 로직 시작
     */
    private suspend fun allCoinsNmCall() = isRunning && withContext(Dispatchers.IO) {
        allCoinsNmApi() && crawlingCall()
    }

    /**
     * 공포/탐욕 지수 정보 가져오는 로직
     * 1. 공포/탐욕 지수 정보를 가져오는 api 호출
     * 2. 성공적으로 가져오면 업비트 코인 목록들에 대해 각각 5분 간격의 캔들 데이터를 조회하는 로직 시작
     */
    private suspend fun crawlingCall() = isRunning && withContext(Dispatchers.IO) {
        crawlingApi() && candleMinutesCall()
    }

    /**
     * 코인들의 5분 간격 캔들 데이터를 조회하는 로직
     * 1. 코인들의 5분 간격 캔들 데이터를 조회하는 api 호출
     * 2. 성공적으로 모두 가져오면, 1일 간격 캔들 데이터를 조회하는 로직 시작
     */
    private suspend fun candleMinutesCall() = isRunning && withContext(Dispatchers.IO) {
        candlesMinutesApi("5") && candleDaysCall()
    }

    /**
     * 코인들의 1일 간격 캔들 데이터 조회하는 로직
     * 1. 코인들의 1일 간격 캔들 데이터를 조회하는 api 호출
     * 2. 성공적으로 모두 가져오면,
     */
    private suspend fun candleDaysCall() = isRunning && withContext(Dispatchers.IO) {
        candlesDaysApi("100")
    }

    /**
     * 매수 및 매도 케이스 리턴
     * @return 0 > 통과
     * @return 2, 3, 6 > 매수
     * @return 1, 4, 5 > 매도
     */
    private fun calculationAskBid(index: Int, coin: CoinIndexModel.data): Int =
        if (tickers[index].trade_price > coin.mv!!.middle
            && coin.macd!!.fast.toDouble() - coin.macd!!.slow.toDouble() > 0.0
            && tickers[index].trade_price > coin.bb!!.upper
        ) if (coin.rsi!!.rsi.toDouble() >= 70.0) 3 else 1
        else if (tickers[index].trade_price < coin.mv!!.middle
            && coin.macd!!.fast.toDouble() - coin.macd!!.slow.toDouble() < 0.0
            && tickers[index].trade_price < coin.bb!!.lower
        ) if (coin.rsi!!.rsi.toDouble() <= 30.0) 4 else 2
        else if (coin.rsi!!.rsi.toDouble() >= 70.0
            && coin.score != null && coin.score!!.toDouble() >= 70.0
        ) 5
        else if (coin.rsi!!.rsi.toDouble() <= 30.0
            && coin.score != null && coin.score!!.toDouble() <= 30.0
        ) 6
        else 0

    /**
     * 지수를 기반으로 매수 및 매도 계산
     */
    private suspend fun checkAskBid(): Boolean = withContext(Dispatchers.IO) {
        // case 1. 이동 평균선이 상승 추세, MACD 가 0을 상향 돌파, 볼린저 밴드의 상한선을 돌파 "RSI 70 이상" > 매도
        // case 2. 이동 평균선이 하락 추세, MACD 가 0을 하향 돌파, 볼린저 밴드의 하한선을 돌파 "RSI 30 이하" > 매수
        // case 3. 이동 평균선 상향 돌파, MACD 신호선 상향, 볼린저 밴드 상한선 > 매수
        // case 4. 이동 평균선 하향 돌파, MACD 신호선 하향, 불린저 밴드 하한선, RSI 30 이하 > 매도
        // case 5. RSI 70이상, 탐욕 지수 > 매도
        // case 6. RSI 30이하, 공포 지수 > 매수
        //TODO 보유한 코인의 손익을 계산해서 매도 영역에 넣어야 함 > 수치는 추후에 생각
        if (!ticker(tickerParams())) return@withContext false
        val r = coinIndexList.withIndex().all { (index, coin) ->
            if (!isRunning) return@withContext false
            when (val case = calculationAskBid(index, coin)) {
                2, 3, 6 -> Pair("매수", case)
                1, 4, 5 -> Pair("매도", case)
                else -> null
            }?.let { (askBid, case) ->
                when (askBid) {
                    "매수" -> {
                        Log.d(
                            "lys",
                            "${coin.market}(${allCoinsNmList[index].korean_name}) > 현재가 ${tickers[index].trade_price} > 매수($case)"
                        )
                        bidCoins.add(coin.market)
                    }
                    "매도" -> {
                        askCheck(coin.market)
                        val myCoin = myCoins.find { ("KRW-" + it.currency) == coin.market }
                        var holdings = ""
                        if (myCoin != null) {
                            val nowValue =
                                myCoin.balance.toDouble() * tickers[index].trade_price.toDouble()
                            val myValue =
                                myCoin.balance.toDouble() * myCoin.avg_buy_price.toDouble()
                            val profitLoss = ((nowValue - myValue) / myValue) * 100
                            holdings += "> 보유금액(${
                                String.format(
                                    "%.3f",
                                    nowValue
                                )
                            } 원) 손익(${String.format("%.3f", profitLoss)} %)"
                        }
                        Log.d(
                            "lys",
                            "${coin.market}(${allCoinsNmList[index].korean_name}) > 현재가 ${tickers[index].trade_price} > 매도($case) $holdings"
                        )

                    }
                    else -> {}
                }
            }
            true
        }
        Log.d("lys", "askCoins > ${askCoins}")
        r && sellCoins() && buyCoins()
    }

    /**
     * 매도 권장 코인이 내가 보유중인 코인인지 체크
     */
    private fun askCheck(market: String) {
        if (myCoins.any { ("KRW-" + it.currency) == market }) {
            askCoins.add(market)
        }
    }

    /**
     * 코인 매도 로직
     */
    private suspend fun sellCoins(): Boolean = withContext(Dispatchers.IO) {
        askCoins.all { coin ->
            if (!isRunning) return@withContext false
            val balance =
                myCoins.find { ("KRW-" + it.currency) == coin }?.balance ?: return@withContext true
            val price =
                tickers.find { (it.market == coin) }?.trade_price?.toDoubleOrNull()
                    ?: return@withContext true
            val money = String.format("%.2f", price * balance.toDouble()).toDouble()
            if (money < 5000.0) {
                Log.d("lys", "최소 판매 금액이 안되서 매수 목록에 최소 금액으로 추가해야 할듯.. > ${coin}")
                bidCoins.add(coin)
                true
            } else {
                Log.d("lys", "balance > $balance")
                ordersAsk(coin, balance)
            }
        }
    }

    /**
     * 코인 매수 로직
     */
    private suspend fun buyCoins(): Boolean = withContext(Dispatchers.IO) {
        bidCoins.all { coin ->
            if (!isRunning) return@withContext false
            val money = myCoins.firstOrNull { it.currency == "KRW" }?.balance?.toDouble()
                ?: return@all true
            if (money < 5000.0) return@all true
            when (bidCheck(coin)) {
                -1 -> return@all false
                0 -> {
                    Log.d("lys", "최근 12시간 이내 매수 이력이 있어서 통과")
                    true
                }
                1 -> orderBid(coin, if (money > 10000.0) "10000" else "5000")
                else -> false
            }
        }
    }

    /**
     * 최근에 거래한 코인 목록 Call
     */
    private suspend fun bidCheck(market: String): Int {
        return if (ordersDone(market)) {
            // 최근 12시간 이내에 거래를 함
            if (checkCoins(doneCoins!!) || checkCoins(cancelCoins!!)) 0
            else 1
        } else -1
    }

    /**
     * 과거 거래한 코인 체크
     * 최근 12시간 이내에 거래를 했다면 해당 코인은 추가 매수 X
     */
    private fun checkCoins(coins: ArrayList<OrderModel.RS>): Boolean {
        val nowDate = LocalDateTime.now()
        var isAlready = false
        coins.forEachIndexed { _, coin ->
            val orderDate = LocalDateTime.parse(
                coin.created_at.substring(0, 19),
                DateTimeFormatter.ISO_DATE_TIME
            )
            val duration = Duration.between(nowDate, orderDate)
            if (duration.toHours() > -12 && coin.executed_volume > "0.0") {
                isAlready = true
                return@forEachIndexed
            }
        }
        return isAlready
    }

    /**
     * ticker parameter
     * 검색 가능한 모든 코인들의 이름을 리턴
     * ex) KRW-BTC,KRW-WAXP,KRW-STPT ...
     */
    private fun tickerParams() = coinIndexList.joinToString(",") { it.market }

    /**
     * 보유 중인 코인을 가져오는 api
     */
    private suspend fun myCoinsApi(): Boolean {
        return callApi(upbit_api.myCoins(token()))?.run {
            myCoins = this as ArrayList<MyCoins.RS>
            true
        } ?: false
    }

    /**
     * 업비트 코인 이름들 가져오는 api
     */
    private suspend fun allCoinsNmApi(): Boolean {
        allCoinsNmList = (callApi(upbit_api.allCoinsNm())?.filter {
            it.market_warning == "NONE" && it.market.contains("KRW-")
        }?.toMutableList() as? ArrayList<AllCoinsNmModel.RS>) ?: return false
        return true
    }

    /**
     * 5분 간격으로 코인의 호가를 불러오는 api
     */
    private suspend fun candlesMinutesApi(unit: String): Boolean = withContext(Dispatchers.IO) {
        allCoinsNmList.all {
            if (!isRunning) return@withContext false
            val result =
                callApi(upbit_api.candlesMinutes(unit, it.market)) ?: return@all false
            coinIndexList.add(
                CoinIndexModel.data(
                    it.market,
                    rsi = CoinIndexModel.data.Rsi(
                        Util.calRsiMinute(result as ArrayList<CandlesModel.RS>).toString()
                    )
                )
            )
            delay(100L) // 0.1초 딜레이
            true
        }
    }

    /**
     * 지정한 일수만큼 코인 종가를 가져온다
     */
    private suspend fun candlesDaysApi(count: String): Boolean = withContext(Dispatchers.IO) {
        allCoinsNmList.all { coin ->
            if (!isRunning) return@withContext false
            val result =
                callApi(upbit_api.candlesDays(coin.market, count)) ?: return@all false
            val middleList = result.take(20).map { it.trade_price.toDouble() }
            val fastAvg = result.take(10).map { it.trade_price.toDouble() }.average() // 단기
            val middleAvg = result.take(20).map { it.trade_price.toDouble() }.average() // 중심
            val slowAvg = result.take(50).map { it.trade_price.toDouble() }.average() // 장기
            val standardDeviation = Util.getStandardDeviation(middleList)
            val upper = middleAvg + (standardDeviation * 2)
            val lower = middleAvg - (standardDeviation * 2)
            val coinIndex =
                coinIndexList.find { coin.market == it.market } ?: return@all false
            coinIndex.mv = CoinIndexModel.data.Mv(DecimalFormat("#.###").format(middleAvg))
            coinIndex.macd = CoinIndexModel.data.Macd(
                DecimalFormat("#.###").format(fastAvg),
                DecimalFormat("#.###").format(slowAvg)
            )
            coinIndex.bb = CoinIndexModel.data.Bb(
                DecimalFormat("#.###").format(upper),
                DecimalFormat("#.###").format(lower)
            )
            delay(100L) // 0.1초 딜레이
            true

        }
    }

    /**
     * 공포 / 탐욕 지수 api
     */
    private suspend fun crawlingApi(): Boolean =
        callApi(crawling_api.crawling())?.run {
            fearGreedList = this
            true
        } ?: false

    /**
     * 코인들의 현재 시세 조회 api
     */
    private suspend fun ticker(market: String): Boolean =
        callApi(upbit_api.ticker(market))?.run {
            tickers = this as ArrayList<TickerModel.RS>
            true
        } ?: false

    /**
     * 주문 조회 api > wait
     */
    private suspend fun ordersWait(): Boolean =
        callApi(upbit_api.orders(token(Pair("state", "wait")), "wait"))?.run {
            Log.d("lys", "ordersWait >> $this")
            waitCoins = this as ArrayList<OrderModel.RS>
            true
        } ?: false

    /**
     * 주문 취소 api > cancel
     */
    private suspend fun ordersCancel() = withContext(Dispatchers.IO) {
        waitCoins.all { coin ->
            if (!isRunning) return@withContext false
            delay(100L)
            callApi(upbit_api.orders((token(Pair("uuid", coin.uuid))), coin.uuid)) != null
        }
    }

    private suspend fun ordersDone(market: String): Boolean {
        doneCoins = callApi(
            upbit_api.orders(
                token(Pair("market", market), Pair("state", "done")), market, "done"
            )
        ) as? ArrayList<OrderModel.RS>
        cancelCoins = callApi(
            upbit_api.orders(
                token(Pair("market", market), Pair("state", "cancel")), market, "cancel"
            )
        ) as? ArrayList<OrderModel.RS>
        return doneCoins != null && cancelCoins != null
    }

    /**
     * 매도 신청 api > ask
     */
    private suspend fun ordersAsk(market: String, volume: String): Boolean =
        callApi(
            upbit_api.orders(
                token(
                    Pair("market", market), Pair("ord_type", "market"),
                    Pair("side", "ask"), Pair("volume", volume)
                ), OrderModel.ASK(market, "ask", volume, "market")
            )
        ) != null

    /**
     * 매수 신청 api > bid
     */
    private suspend fun orderBid(market: String, price: String): Boolean =
        callApi(
            upbit_api.orders(
                token(
                    Pair("market", market), Pair("ord_type", "price"),
                    Pair("price", price), Pair("side", "bid")
                ), OrderModel.BID(market, "bid", price, "price")
            )
        ) != null

    private fun token(vararg value: Pair<String, Any?>) =
        JwtUtil.newToken(applicationContext, accessToken, *value)

    private fun token() = JwtUtil.newToken(applicationContext, accessToken)

    private fun <T : Response<R>, R> callApi(api: T): R? =
        if (api.isSuccessful) {
//            Log.d("lys","callApi success > ${api.body()}")
            api.body()
        } else {
            Log.e("lys", "callApi fail > ${api.errorBody()}")
            null
        }
}