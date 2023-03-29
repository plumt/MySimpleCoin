package com.yun.mysimplecoin.di

import com.yun.mysimplecoin.util.PreferenceUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SharedPreferencesModule {

    @Singleton
    @Provides
    fun provideSharedPref(): PreferenceUtil {
        return PreferenceUtil
    }
}