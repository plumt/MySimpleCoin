package com.yun.mysimplecoin.ui.home

import android.app.Application
import android.util.Log
import com.yun.mysimplecoin.base.BaseViewModel
import com.yun.mysimplecoin.base.ListLiveData
import com.yun.mysimplecoin.data.model.AllCoinsNmModel
import com.yun.mysimplecoin.data.model.CandlesMinutesModel
import com.yun.mysimplecoin.data.repository.ApiRepository
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

    private val allCoinsNmList = ListLiveData<AllCoinsNmModel.RS>()
    private val candlesMinutesList = ListLiveData<CandlesMinutesModel.RS>()

    init {
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
        result.forEach {
            Log.d("lys", "calRsiMinute > $it")
        }
        if (result.size == 0) Log.d("lys", "calRsiMinute is empty")
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

//    private fun myCoinsApi(index: Int, callBack: (Boolean) -> Unit) {
//        api.myCoins(newToken(mContext, accessToken)).observeOn(Schedulers.io())
//            .subscribeOn(Schedulers.io())
//            .flatMap { Observable.just(it) }
//            .observeOn(AndroidSchedulers.mainThread())
//            .map {
//                Log.d("lys", "myCoins result($index) > $it")
//            }.subscribe({
//                callBack(true)
//                Log.d("lys", "myCoins success($index) > cnt : $myCoinsCnt")
//            }, {
//                myCoinsApi(index,callBack)
//                Log.e("lys", "myCoins fail($index) > ${it.message} ${it}")
//            })
//    }
}