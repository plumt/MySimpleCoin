package com.yun.mysimplecoin.ui.main

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.viewModels
import androidx.core.app.ServiceCompat.stopForeground
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.navigation.Navigation
import com.yun.mysimplecoin.R
import com.yun.mysimplecoin.databinding.ActivityMainBinding
import com.yun.mysimplecoin.service.CoinService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    lateinit var navController: NavController

    private val mainViewModel: MainViewModel by viewModels()
    lateinit var binding: ActivityMainBinding

    var mService: CoinService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        binding.run {
            lifecycleOwner = this@MainActivity
            main = mainViewModel
        }

        navController = Navigation.findNavController(this, R.id.nav_host_fragment)
        startService()
        mainViewModel.let { it ->
            it.isCoinService.observe(this@MainActivity){
                if(it) startTrading()
                else stopTrading()
            }
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val myBinder: CoinService.MyBinder = p1 as CoinService.MyBinder
            mService = myBinder.getService()

        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            Log.d("lys", "onServiceDisconnected")
        }
    }

    private fun startService(){
        val serviceIntent = Intent(this, CoinService::class.java)
        startForegroundService(serviceIntent)
        bindService(serviceIntent, conn, Context.BIND_ADJUST_WITH_ACTIVITY)

    }

    private fun stopService(){

//        stopService(Intent(this, CoinService::class.java))
//        stopSelf()
    }

    private fun startTrading(){
        mService?.start()?:Log.w("lys","mService is null")
    }

    private fun stopTrading(){
        mService?.stopWork()?:Log.w("lys","mService is null")
    }
}