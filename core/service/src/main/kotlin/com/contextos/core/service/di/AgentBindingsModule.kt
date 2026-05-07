package com.contextos.core.service.di

import com.contextos.core.data.model.MessageDrafter
import com.contextos.core.service.agent.MessageDraftingEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AgentBindingsModule {

    @Binds
    abstract fun bindMessageDrafter(engine: MessageDraftingEngine): MessageDrafter
}
