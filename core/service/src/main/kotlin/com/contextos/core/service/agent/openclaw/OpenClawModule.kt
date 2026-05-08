package com.contextos.core.service.agent.openclaw

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OpenClawModule {
    companion object {
        /**
         * Provides the appropriate OpenClawAgent implementation.
         * Uses MockOpenClawAgent if OPENCLAW_USE_MOCK is true or API key is not set.
         */
        @Provides
        @Singleton
        fun provideOpenClawAgent(
            realAgent: RealOpenClawAgent,
            mockAgent: MockOpenClawAgent,
        ): OpenClawAgent {
            val useMock = com.contextos.core.service.BuildConfig.OPENCLAW_USE_MOCK
            val apiKeySet = com.contextos.core.service.BuildConfig.OPENCLAW_API_KEY.isNotEmpty()
            
            return if (useMock || !apiKeySet) {
                android.util.Log.i(
                    "OpenClawModule",
                    "Using MockOpenClawAgent (mock=$useMock, apiKeySet=$apiKeySet)"
                )
                mockAgent
            } else {
                val provider = com.contextos.core.service.BuildConfig.OPENCLAW_API_PROVIDER
                android.util.Log.i("OpenClawModule", "Using RealOpenClawAgent with $provider API")
                realAgent
            }
        }
    }
}
