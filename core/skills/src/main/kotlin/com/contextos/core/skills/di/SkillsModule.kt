package com.contextos.core.skills.di

import com.contextos.core.skills.PhaseOneHeartbeatSkill
import com.contextos.core.skills.Skill
import dagger.Module
import dagger.Binds
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds

/**
 * Hilt module that seeds the [Skill] multi-binding set.
 * Declaring an empty [Multibinds] set here ensures [SkillRegistry] compiles even when
 * no concrete skill implementations have been added yet.
 * Feature modules contribute skills by adding @IntoSet bindings in their own modules.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SkillsModule {

    @Multibinds
    abstract fun bindSkillSet(): Set<Skill>

    @Binds
    @IntoSet
    abstract fun bindPhaseOneHeartbeatSkill(skill: PhaseOneHeartbeatSkill): Skill
}
