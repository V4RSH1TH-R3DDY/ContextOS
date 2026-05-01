package com.contextos.core.memory.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the :core:memory library.
 *
 * [RoutineMemoryManager], [PreferenceMemoryManager], and [LocationMemoryManager]
 * are all annotated with @Singleton and @Inject, so Hilt constructs and provides
 * them automatically via constructor injection — no explicit @Provides bindings
 * are required here.
 *
 * This module exists as the conventional entry point for future manual bindings
 * (e.g., interface-to-implementation mappings) that may be added in Phase 3.x.
 */
@Module
@InstallIn(SingletonComponent::class)
object MemoryModule
