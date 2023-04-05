package com.yun.mysimplecoin.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.yun.mysimplecoin.data.model.AllCoinsNmModel
import com.yun.mysimplecoin.data.model.CandlesMinutesModel
import com.yun.mysimplecoin.data.model.FearGreedModel
import com.yun.mysimplecoin.data.repository.ApiRepository
import com.yun.mysimplecoin.util.JwtUtil
import com.yun.mysimplecoin.util.PreferenceUtil
import com.yun.mysimplecoin.util.Util
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Named

/**
 * foreground service에 비해 실행시간이 보장되지 않는다.
 * 지우지는 않지만 당장 사용하진 않는것으로.
 * 2023.3.30
 */

@HiltWorker
class CoinWorker @AssistedInject constructor(
    @Assisted mContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sharedPreferences: PreferenceUtil,
    @Named("upbit") upbitapi: ApiRepository,
    @Named("crawling") crawlingapi: ApiRepository
) : Worker(mContext, workerParams) {

    private val upbit_api = upbitapi
    private val crawling_api =crawlingapi

    private val accessToken = "mrPq8Ia8riJv5YQZYRQ5DhF42BYp9EXxr1ZzOhOI"
    private var allCoinsNmList = arrayListOf<AllCoinsNmModel.RS>()
    private var candlesMinutesList = arrayListOf<CandlesMinutesModel.RS>()
    private lateinit var fearGreedList: FearGreedModel.RS

    private val context = mContext

    var isRunning = false

    override fun doWork(): Result {
        Log.d("lys","doWork")
        if(!isRunning){
            myCoinsApi()
        }
        return Result.success()
    }

    private fun allCoinsNmCall(){
        allCoinsNmApi {
            if (it) {
                var cnt = 0
                allCoinsNmList.forEach {
                    candlesMinutesApi("5", it.market) {
                        if (it) cnt++
                        if (cnt == allCoinsNmList.size) calRsiMinuteCall()
                    }
                }
            }
        }
    }

    private fun calRsiMinuteCall() {
        Log.d("lys", "--------------------------------")
        val result = Util.calRsiMinute(candlesMinutesList)

        if (result.size == 0) {
//            title.value = ""
            Log.d("lys", "calRsiMinute is empty")
            myCoinsApi()
        }
        else {
            // 매수 및 매도 해야할 코인이 있다
            result.forEach {
                Log.d("lys", "calRsiMinute > $it")
            }
            crawlingApi {
                if (it) {
                    val data = arrayListOf<Triple<String,String,String>>()
                    result.forEach { r ->
                        fearGreedList.pairs.forEach {  p ->
                            if(p.code.contains(r.first)){
                                if(p.score <= "30" && r.second == "매수"){
                                    // 매수 코인
                                    data.add(r)
                                } else if(p.score >= "70" && r.second == "매도"){
                                    // 매도 코인
                                    data.add(r)
                                }
                            }
                        }
                    }
                    Log.d("lys","data > $data")
                    myCoinsApi()
//                    title.value = ""
                }
            }
        }
    }


    private fun allCoinsNmApi(callBack: (Boolean) -> Unit) {
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
                Log.e("lys", "allCoinsNm fail > $it")
                allCoinsNmApi(callBack)
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
                Log.d("lys", "candlesMinutes success > $it")
                candlesMinutesList.addAll(it)
                callBack(true)
            }, {
//                Log.e("lys", "candlesMinutes fail > $it")
                candlesMinutesApi(unit, market, callBack)
            })
    }

    private fun crawlingApi(callBack: (Boolean) -> Unit) {
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
                Log.e("lys", "crawling fail > $it")
                crawlingApi(callBack)
            })
    }

    fun myCoinsApi(isFail: Boolean = false) {
        if(sharedPreferences.getString(context,"isRunning") == "stop") {
            isRunning = false
            return
        }
        isRunning = true
//        if(title.value != "" && !isFail) return
//        title.value = "api 가져오는 중..."
        upbit_api.myCoins(JwtUtil.newToken(context, accessToken)).observeOn(Schedulers.io())
            .subscribeOn(Schedulers.io())
            .flatMap { Observable.just(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .map { it }.subscribe({
                Log.d("lys", "myCoins success > $it")
                allCoinsNmCall()
            }, {
                Log.e("lys", "myCoins fail > $it")
                myCoinsApi(true)
            })
    }

}


/**
 *
 * 아래 코드는 해당 work 작업을 실행 및 중지 하는 코드 > viewModel이나 fragment 에서 호출할 떄 쓰임
 *
 */

//fun startWork(){
//    sharedPreferences.setString(mContext,"isRunning","start")
//    val constraints = Constraints.Builder()
//        .setRequiredNetworkType(NetworkType.CONNECTED)
//        .build()
//
//    val myWork = PeriodicWorkRequestBuilder<CoinWorker>(
//        1, TimeUnit.MINUTES
//    )
//        .setConstraints(constraints)
//        .build()
//
//    WorkManager.getInstance(mContext).enqueueUniquePeriodicWork(
//        "MyWorker",
//        ExistingPeriodicWorkPolicy.UPDATE,
//        myWork
//    )
//}
//
//fun stopWork(){
//    sharedPreferences.setString(mContext,"isRunning","stop")
//    WorkManager.getInstance(mContext).cancelUniqueWork("MyWorker")
//}