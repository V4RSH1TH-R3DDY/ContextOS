package com.contextos.core.service.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the `:core:service` module.
 *
 * ## Phase 1 — Core Infrastructure
 *
 * All Phase 1 service-layer classes are annotated with `@Singleton @Inject constructor(...)`,
 * so Hilt binds them automatically without explicit `@Provides` declarations:
 *
 *  - [com.contextos.core.service.SensorDataCollector]
 *  - [com.contextos.core.service.SituationModelBuilder]
 *  - [com.contextos.core.service.ServiceHealthMonitor]
 *  - [com.contextos.core.service.agent.ActionDispatcher]
 *  - [com.contextos.core.service.agent.ContextAgent]
 *
 * [com.contextos.core.network.CalendarSyncWorker] and
 * [com.contextos.core.service.AgentCycleWorker] are `@HiltWorker` and are
 * handled automatically by the Hilt-Work integration.
 *
 * Future phases may add explicit `@Provides` / `@Binds` declarations here as the service
 * layer grows (e.g. providing platform-specific implementations via interfaces).
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule
