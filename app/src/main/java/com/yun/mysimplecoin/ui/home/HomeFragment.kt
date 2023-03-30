package com.yun.mysimplecoin.ui.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import com.yun.mysimplecoin.R
import com.yun.mysimplecoin.BR
import com.yun.mysimplecoin.base.BaseFragment
import com.yun.mysimplecoin.databinding.FragmentHomeBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : BaseFragment<FragmentHomeBinding, HomeViewModel>() {
    override val viewModel: HomeViewModel by viewModels()
    override fun getResourceId(): Int = R.layout.fragment_home
    override fun isOnBackEvent(): Boolean = false
    override fun setVariable(): Int = BR.home
    override fun onBackEvent() { }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnApiStart.setOnClickListener {
            viewModel.startWork()
        }

        binding.btnApiStop.setOnClickListener {
            viewModel.stopWork()
        }
    }


}