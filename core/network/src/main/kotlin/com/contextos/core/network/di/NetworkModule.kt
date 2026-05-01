package com.contextos.core.network.di

import com.contextos.core.network.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

/**
 * Hilt module for the :core:network library.
 *
 * Provides the shared [OkHttpClient] and the stub [Retrofit] instance pointed
 * at the Google Maps APIs base URL. Individual API clients (Calendar, Gmail,
 * Drive) use the Google API Client library directly via [GoogleAuthManager]
 * rather than Retrofit, so no additional Retrofit service interfaces are
 * declared here yet.
 *
 * [GoogleAuthManager], [CalendarApiClient], [GmailApiClient], [DriveApiClient],
 * and [MapsDistanceMatrixClient] are all @Singleton @Inject and do not require
 * explicit @Provides bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Provides an [HttpLoggingInterceptor] configured with:
     *  - BODY level in debug builds (full request/response logging)
     *  - NONE level in release builds (no logging overhead or data leakage)
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    /**
     * Provides the shared [OkHttpClient] used by Retrofit.
     * Additional interceptors (auth headers, retry logic) will be added in Phase 0.2.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

    /**
     * Provides a stub [Retrofit] instance pointed at the Google Maps APIs.
     * Additional service interfaces (e.g., DistanceMatrix) will be bound here
     * as Phase 2.4 progresses.
     */
    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
