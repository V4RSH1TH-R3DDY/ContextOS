package com.contextos.core.skills.di

import com.contextos.core.skills.BatteryWarnerSkill
import com.contextos.core.skills.DndSetterSkill
import com.contextos.core.skills.LocationIntelligenceSkill
import com.contextos.core.skills.NavigationLauncherSkill
import com.contextos.core.skills.PhaseOneHeartbeatSkill
import com.contextos.core.skills.PersonalNudgeSkill
import com.contextos.core.skills.DocumentFetcherSkill
import com.contextos.core.skills.MessageDrafterSkill
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

    @Binds
    @IntoSet
    abstract fun bindDndSetterSkill(skill: DndSetterSkill): Skill

    @Binds
    @IntoSet
    abstract fun bindBatteryWarnerSkill(skill: BatteryWarnerSkill): Skill

    @Binds
    @IntoSet
    abstract fun bindNavigationLauncherSkill(skill: NavigationLauncherSkill): Skill

    @Binds
    @IntoSet
    abstract fun bindLocationIntelligenceSkill(skill: LocationIntelligenceSkill): Skill

    @Binds
    @IntoSet
    abstract fun bindPersonalNudgeSkill(skill: PersonalNudgeSkill): Skill

    @Binds
    @IntoSet
    abstract fun bindDocumentFetcherSkill(skill: DocumentFetcherSkill): Skill

    @Binds
    @IntoSet
    abstract fun bindMessageDrafterSkill(skill: MessageDrafterSkill): Skill
}

