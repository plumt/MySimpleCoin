package com.yun.mysimplecoin.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.yun.mysimplecoin.base.BaseViewModel
import com.yun.mysimplecoin.base.ListLiveData
import com.yun.mysimplecoin.data.model.AllCoinsNmModel
import com.yun.mysimplecoin.data.model.CandlesMinutesModel
import com.yun.mysimplecoin.data.model.FearGreedModel
import com.yun.mysimplecoin.data.repository.ApiRepository
import com.yun.mysimplecoin.util.JwtUtil.newToken
import com.yun.mysimplecoin.util.PreferenceUtil
import com.yun.mysimplecoin.util.RsiUtil.calRsiMinute
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

    private val allCoinsNmList = ListLiveData<AllCoinsNmModel.RS>()
    private val candlesMinutesList = ListLiveData<CandlesMinutesModel.RS>()
    private val fearGreedList = MutableLiveData<FearGreedModel.RS>()

    init {
        myCoinsApi()
    }

    private fun allCoinsNmCall(){
        allCoinsNmApi {
            if (it) {
                var cnt = 0
                allCoinsNmList.value!!.forEach {
                    candlesMinutesApi("5", it.market) {
                        if (it) cnt++
                        if (cnt == allCoinsNmList.sizes()) calRsiMinuteCall()
                    }
                }
            }
        }
    }

    private fun calRsiMinuteCall() {
        Log.d("lys", "--------------------------------")
        val result = calRsiMinute(candlesMinutesList.value!!)

        if (result.size == 0) {
            title.value = ""
            Log.d("lys", "calRsiMinute is empty")
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
                        fearGreedList.value!!.pairs.forEach {  p ->
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
                    title.value = ""
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
                allCoinsNmList.clear(true)
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
                candlesMinutesList.clear(true)
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
                fearGreedList.value = it
                Log.d("lys", "crawling success > $it")
                callBack(true)
            }, {
                Log.e("lys", "crawling fail > $it")
                crawlingApi(callBack)
            })
    }

    fun myCoinsApi(isFail: Boolean = false) {
        if(title.value != "" && !isFail) return
        title.value = "api 가져오는 중..."
        upbit_api.myCoins(newToken(mContext, accessToken)).observeOn(Schedulers.io())
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