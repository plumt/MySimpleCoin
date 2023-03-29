package com.yun.mysimplecoin.di

import com.yun.mysimplecoin.data.api.Api
import com.yun.mysimplecoin.data.repository.ApiRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {

    @Singleton
    @Provides
    @Named("upbit")
    fun providerUpBitApi(@Named("upbit") retrofit: Retrofit): Api {
        return retrofit.create(Api::class.java)
    }

    @Singleton
    @Provides
    @Named("upbit")
    fun providerUpBitRepository(@Named("upbit") api: Api) = ApiRepository(api)

    @Singleton
    @Provides
    @Named("crawling")
    fun providerCrawlingApi(@Named("crawling") retrofit: Retrofit): Api {
        return retrofit.create(Api::class.java)
    }

    @Singleton
    @Provides
    @Named("crawling")
    fun providerCrawlingRepository(@Named("crawling") api: Api) = ApiRepository(api)
}