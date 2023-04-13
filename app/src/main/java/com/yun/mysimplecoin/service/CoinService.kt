package com.yun.mysimplecoin.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.yun.mysimplecoin.R
import com.yun.mysimplecoin.data.model.*
import com.yun.mysimplecoin.data.repository.ApiRepository
import com.yun.mysimplecoin.ui.main.MainActivity
import com.yun.mysimplecoin.util.JwtUtil
import com.yun.mysimplecoin.util.Util
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
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
    private var doneCoins = ArrayList<OrderModel.RS>()

    /**
     * 코인 거래 내역 > cancel
     */
    private var cancelCoins = ArrayList<OrderModel.RS>()

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
        Handler(Looper.myLooper()!!).postDelayed({
            isRunning = false
            startWork()
        }, 2000)
    }

    fun start(){
        stop = false
        startWork()
    }

    /**
     * 프로세스 시작
     * 1. 매수 및 매도 대기중인 코인들의 정보를 가져오는 로직 시작
     * 2. 이후 모든 로직이 성공하면 isSuccess 가 true, 중간에 한 번이라도 오류가 발생하면 false
     * 3. isSuccess 가 true > 지수를 계산하여 매수 및 매도 로직 시작
     */
    private fun startWork() {
        if (stop) return
        Log.d("lys", "ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ")
        clearData()
        isRunning = true
        myWaitCoins { success ->
            if (success && isRunning) {
                // 성공
                startCalculation()
            } else {
                // 중간에 뭔가 오류가 발생함 > 초기화하고 다시 작업
                if (isRunning) {
                    Log.e("lys", "중간에 뭔가 오류가 있었음. 초기화 후 재진행")
                    reStartWork()
                } else {
                    Log.d("lys", "stop end")
                }
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
    private fun startCalculation() {
        coinIndexList.forEach { coin ->
            fearGreedList.pairs.find { it.code.contains(coin.market) }?.let { pair ->
                coin.score = pair.score
            }
        }
        checkAskBid { success ->
            if (success) Log.d("lys", "success end")
            else Log.e("lys", "fail end")
            reStartWork()
        }
    }

    /**
     * 매수 및 매도 대기중인 코인들의 정보를 가져오는 로직
     * 1. 매수 및 매도 대기중인 코인의 정보를 가져오는 api 호출
     * 2. 성공적으로 가져오면 대기 중인 코인들의 주문 취소하는 로직 시작
     */
    private fun myWaitCoins(callBack: (Boolean) -> Unit) {
        orders("wait", "wait") { success ->
            Log.d("lys", "myWaitCoins is done")
            if (success && isRunning) cancelCoinsCall(callBack = callBack)
            else callBack(false)
        }
    }

    /**
     * 대기 중인 코인들을 주문 취소하는 로직
     * 1. 매수 및 매도 대기중인 코인의 주문 취소하는 api 호출
     * 2. 성공적으로 완수하면 보유 중인 코인들의 정보를 가져오는 로직 시작
     */
    private fun cancelCoinsCall(index: Int = 0, callBack: (Boolean) -> Unit) {
        if (index == waitCoins.size) myCoinsCall(callBack)
        else {
            orders("cancel", waitCoins[index].uuid) { success ->
                Log.d("lys", "cancelCoinsCall is done")
                if (success && isRunning) cancelCoinsCall(index + 1, callBack)
                else callBack(false)
            }
        }
    }

    /**
     * 보유 중인 코인들의 정보를 가져오는 로직
     * 1. 보유 중인 코인의 정보를 가져오는 api 호출
     * 2. 성공적으로 가져오면 모든 코인들의 정보를 가져오는 로직 시작
     */
    private fun myCoinsCall(callBack: (Boolean) -> Unit) {
        myCoinsApi { success ->
            Log.d("lys", "myCoinsCall is done")
            if (success && isRunning) allCoinsNmCall(callBack)
            else callBack(false)
        }
    }

    /**
     * 모든 코인들의 정보를 가져오는 로직
     * 1. 모든 코인의 정보를 가져오는 api 호출 > 거래 재화가 KRW 이며, 주의가 아닌 항목에 대해서만
     * 2. 성공적으로 가져오면 공포/탐욕 지수 정보 가져오는 로직 시작
     */
    private fun allCoinsNmCall(callBack: (Boolean) -> Unit) {
        allCoinsNmApi { success ->
            Log.d("lys", "allCoinsNmCall is done")
            if (success && isRunning) crawlingCall(callBack)
            else callBack(false)
        }
    }

    /**
     * 공포/탐욕 지수 정보 가져오는 로직
     * 1. 공포/탐욕 지수 정보를 가져오는 api 호출
     * 2. 성공적으로 가져오면 업비트 코인 목록들에 대해 각각 5분 간격의 캔들 데이터를 조회하는 로직 시작
     */
    private fun crawlingCall(callBack: (Boolean) -> Unit) {
        crawlingApi { success ->
            Log.d("lys", "crawlingCall is done")
            if (success && isRunning) candleMinutesCall(callBack = callBack)
            else callBack(false)
        }
    }

    /**
     * 코인들의 5분 간격 캔들 데이터를 조회하는 로직
     * 1. 코인들의 5분 간격 캔들 데이터를 조회하는 api 호출
     * 2. 성공적으로 모두 가져오면, 1일 간격 캔들 데이터를 조회하는 로직 시작
     */
    private fun candleMinutesCall(index: Int = 0, callBack: (Boolean) -> Unit) {
        if (index == allCoinsNmList.size) {
            Log.d("lys", "candleMinutesCall done")
            candleDaysCall(callBack = callBack) // 마지막 코인까지 api 전송했으면 다음 로직
        } else {
            candlesMinutesApi("5", allCoinsNmList[index].market) { success ->
                if (success && isRunning) candleMinutesCall(index + 1, callBack)
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
        if (index == allCoinsNmList.size) {
            Log.d("lys", "candleDaysCall done")
            callBack(true) // 마지막 코인까지 api 전송했으면 callBack
        } else {
            candlesDaysApi(allCoinsNmList[index].market, "100") { success ->
                if (success && isRunning) candleDaysCall(index + 1, callBack)
                else callBack(false)
            }
        }
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
    private fun checkAskBid(callBack: (Boolean) -> Unit) {
        // case 1. 이동 평균선이 상승 추세, MACD 가 0을 상향 돌파, 볼린저 밴드의 상한선을 돌파 "RSI 70 이상" > 매도
        // case 2. 이동 평균선이 하락 추세, MACD 가 0을 하향 돌파, 볼린저 밴드의 하한선을 돌파 "RSI 30 이하" > 매수
        // case 3. 이동 평균선 상향 돌파, MACD 신호선 상향, 볼린저 밴드 상한선 > 매수
        // case 4. 이동 평균선 하향 돌파, MACD 신호선 하향, 불린저 밴드 하한선, RSI 30 이하 > 매도
        // case 5. RSI 70이상, 탐욕 지수 > 매도
        // case 6. RSI 30이하, 공포 지수 > 매수
        //TODO 보유한 코인의 손익을 계산해서 매도 영역에 넣어야 함 > 수치는 추후에 생각
        ticker(tickerParams()) { success ->
            if (success) {
                Log.d("lys", "#####################")
                coinIndexList.forEachIndexed { index, coin ->
                    val result = when (val case = calculationAskBid(index, coin)) {
                        2, 3, 6 -> Pair("매수", case)
                        1, 4, 5 -> Pair("매도", case)
                        else -> null
                    }
                    result?.let { (askBid, case) ->
                        if (askBid == "매수") {
                            bidCoins.add(coin.market)
                            Log.d(
                                "lys",
                                "${coin.market}(${allCoinsNmList[index].korean_name}) > 현재가 ${tickers[index].trade_price} > 매수($case)"
                            )
                        } else if (askBid == "매도") {
                            askCheck(coin.market)
                            var holdings = ""
                            val myCoin = myCoins.find { ("KRW-" + it.currency) == coin.market }
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
                    }
                }
                Log.d("lys", "askCoins > ${askCoins}")
                sellCoins(callBack = callBack)
            } else callBack(false)
        }
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
    private fun sellCoins(index: Int = 0, callBack: (Boolean) -> Unit) {
        if (index == askCoins.size) buyCoins(callBack = callBack)
        else {
            val balance =
                myCoins.find { ("KRW-" + it.currency) == askCoins[index] }?.balance ?: "0.0"
            val price = tickers.find { (it.market == askCoins[index]) }?.trade_price ?: "0.0"
            val money = String.format("%.2f", price.toDouble() * balance.toDouble()).toDouble()
            if (money < 5000.0) {
                Log.d("lys", "최소 판매 금액이 안되서 매수 목록에 최소 금액으로 추가해야 할듯.. > ${askCoins[index]}")
                bidCoins.add(askCoins[index])
                sellCoins(index + 1, callBack)
            } else {
                Log.d("lys", "balance > ${balance}")
                orders("ask", askCoins[index], balance) { success ->
                    if (success && isRunning) sellCoins(index + 1, callBack)
                    else callBack(false)
                }
            }
        }
    }

    /**
     * 코인 매수 로직
     */
    private fun buyCoins(index: Int = 0, callBack: (Boolean) -> Unit) {
        if (index == bidCoins.size) callBack(true)
        else {
            val money = myCoins.find { it.currency == "KRW" }?.balance?.toDouble() ?: 0.0
            if (money < 5000.0) {
                Log.d("lys", "보유 금액 부족.. > ${askCoins[index]}")
                buyCoins(index + 1, callBack)
            } else {
                bidCheck(askCoins[index]) { result ->
                    when (result) {
                        -1 -> callBack(false)
                        0 -> {
                            Log.d("lys", "최근 12시간 이내 매수 이력이 있어서 통과")
                            buyCoins(index + 1, callBack)
                        }
                        1 -> {
                            orders(
                                "bid",
                                askCoins[index],
                                if (money > 10000.0) "10000" else "5000"
                            ) { success ->
                                if (success) buyCoins(index + 1, callBack)
                                else callBack(false)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 최근에 거래한 코인 목록 Call
     */
    private fun bidCheck(market: String, callBack: (Int) -> Unit) {
        orders("done", market, "done") { done ->
            if (done) {
                orders("done", market, "cancel") { cancel ->
                    if (cancel) {
                        // 최근 12시간 이내에 거래를 함
                        if (checkCoins(doneCoins) || checkCoins(cancelCoins)) callBack(0)
                        else callBack(1)
                    } else callBack(-1)
                }
            } else callBack(-1)
        }
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
    private fun myCoinsApi(failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        upbit_api.myCoins(JwtUtil.newToken(applicationContext, accessToken))
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

    /**
     * 업비트 코인 이름들 가져오는 api
     */
    private fun allCoinsNmApi(failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        upbit_api.allCoinsNm()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
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

    /**
     * 5분 간격으로 코인의 호가를 불러오는 api
     */
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
                        rsi = CoinIndexModel.data.Rsi(
                            Util.calRsiMinute(it as ArrayList<CandlesModel.RS>).toString())
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

                val standardDeviation = Util.getStandardDeviation(middleList)
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

    private fun crawlingApi(failCnt: Int = 0, callBack: (Boolean) -> Unit) {
        crawling_api.crawling()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }
            .subscribe({
                fearGreedList = it
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
     * 주문 조회 > wait
     * 주문 취소 > cancel
     * 최근 주문 > done
     * 매도 > ask
     * 매수 > bid
     */
    private fun orders(
        type: String,
        vararg params: String,
        failCnt: Int = 0,
        callBack: (Boolean) -> Unit
    ) {
        if ("ask" == type) {
            Log.d("lys", "params[0]>${params[0]}  params[1] > ${params[1]}")
        }
        upbit_api.run {
            when (type) {
                "wait" ->
                    orders(
                        JwtUtil.newToken(
                            applicationContext,
                            accessToken,
                            Pair("state", params[0])
                        ), params[0]
                    )
                "cancel" -> order(
                    JwtUtil.newToken(applicationContext, accessToken, Pair("uuid", params[0])),
                    params[0]
                )
                "ask" -> orders(
                    JwtUtil.newToken(
                        applicationContext, accessToken,
                        Pair("market", params[0]),
                        Pair("ord_type", "market"),
                        Pair("side", "ask"),
                        Pair("volume", params[1])
                    ), OrderModel.ASK(params[0], "ask", params[1], "market")
                )
                "bid" -> orders(
                    JwtUtil.newToken(
                        applicationContext, accessToken,
                        Pair("market", params[0]),
                        Pair("ord_type", "price"),
                        Pair("price", params[1]),
                        Pair("side", "bid")
                    ), OrderModel.BID(params[0],"bid",params[1],"price")
                )
                "done" -> orders(
                    JwtUtil.newToken(
                        applicationContext, accessToken,
                        Pair("market", params[0]),
                        Pair("state", params[1])
                    ), params[0], params[1]
                )
                else -> null
            }?.subscribeOn(Schedulers.io())
                ?.observeOn(AndroidSchedulers.mainThread())
                ?.map { it }
                ?.subscribe({
                    when (type) {
                        "wait" -> {
                            (it as? List<*>)?.let {
                                waitCoins =
                                    it.filterIsInstance<OrderModel.RS>() as ArrayList<OrderModel.RS>
                            }
                        }
                        "cancel" -> {

                        }
                        "ask" -> {

                        }
                        "bid" -> {

                        }
                        "done" -> {
                            (it as ArrayList<OrderModel.RS>).run {
                                if (params[1] == "done") doneCoins = this
                                else if (params[1] == "cancel") cancelCoins = this
                            }
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
}