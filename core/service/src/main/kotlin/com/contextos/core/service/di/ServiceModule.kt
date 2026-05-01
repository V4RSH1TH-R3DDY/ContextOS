package com.contextos.core.service.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the `:core:service` module.
 *
 * Concrete @Provides / @Binds declarations will be added here in later phases
 * as the service layer grows (e.g., providing the SituationModelBuilder, ActionDispatcher, etc.).
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule
