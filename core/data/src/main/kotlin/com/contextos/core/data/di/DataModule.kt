package com.contextos.core.data.di

import android.content.Context
import com.contextos.core.data.db.dao.ActionLogDao
import com.contextos.core.data.db.dao.UserPreferenceDao
import com.contextos.core.data.preferences.PreferencesManager
import com.contextos.core.data.repository.ActionLogRepository
import com.contextos.core.data.repository.UserPreferenceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideActionLogRepository(dao: ActionLogDao): ActionLogRepository =
        ActionLogRepository(dao)

    @Provides
    @Singleton
    fun provideUserPreferenceRepository(dao: UserPreferenceDao): UserPreferenceRepository =
        UserPreferenceRepository(dao)

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context,
    ): PreferencesManager = PreferencesManager(context)
}
