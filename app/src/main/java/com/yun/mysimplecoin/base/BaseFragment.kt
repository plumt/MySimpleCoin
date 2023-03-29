package com.yun.mysimplecoin.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.LayoutRes
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.yun.mysimplecoin.BR
import androidx.lifecycle.ViewModel
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import com.yun.mysimplecoin.ui.main.MainViewModel


abstract class BaseFragment<B : ViewDataBinding, M : ViewModel> : Fragment() {

    lateinit var binding: B

    abstract val viewModel: M

    abstract fun setVariable(): Int

    @LayoutRes
    abstract fun getResourceId(): Int

    abstract fun onBackEvent()

    abstract fun isOnBackEvent(): Boolean

    val sharedViewModel: MainViewModel by activityViewModels()

    fun navigate(resId: Int, bundle: Bundle? = null, options: NavOptions? = null) {
        try {
            view?.findNavController()?.navigate(resId, bundle, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, getResourceId(), container, false)
        binding.lifecycleOwner = activity
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.setVariable(setVariable(), viewModel)
        binding.setVariable(BR.main, sharedViewModel)

        if (isOnBackEvent()) {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                onBackEvent()
            }
        }
    }
}