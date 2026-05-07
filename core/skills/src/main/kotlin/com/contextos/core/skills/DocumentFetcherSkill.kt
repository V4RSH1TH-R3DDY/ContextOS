package com.contextos.core.skills

import com.contextos.core.data.model.ActionOutcome
import com.contextos.core.data.model.SituationModel
import com.contextos.core.network.DriveApiClient
import com.contextos.core.network.GmailApiClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentFetcherSkill @Inject constructor(
    private val gmailApiClient: GmailApiClient,
    private val driveApiClient: DriveApiClient
) : Skill {

    override val id: String = "document_fetcher"
    override val name: String = "Document Fetcher"
    override val description: String = "Surfaces relevant Drive and Gmail documents before meetings."

    override fun shouldTrigger(model: SituationModel): Boolean {
        val event = model.nextCalendarEvent ?: return false

        val nowMs = model.currentTime
        val minutesUntilStart = TimeUnit.MILLISECONDS.toMinutes(event.startTime - nowMs)
        if (minutesUntilStart !in 0..20) return false

        val titleLower = event.title.lowercase()
        val regex = "review|presentation|demo|sync|q[1-4]".toRegex()
        
        return regex.containsMatchIn(titleLower)
    }

    override suspend fun execute(model: SituationModel): SkillResult {
        val event = model.nextCalendarEvent ?: return SkillResult.Skipped("No event")

        val keywords = listOf("review", "presentation", "demo", "sync", "q1", "q2", "q3", "q4")
        val titleLower = event.title.lowercase()
        val foundKeywords = keywords.filter { titleLower.contains(it) }
        
        if (foundKeywords.isEmpty()) return SkillResult.Skipped("No keywords matched")
        val keywordQuery = foundKeywords.joinToString(" OR ")

        val gmailQuery = "subject:($keywordQuery) newer_than:7d"
        val driveQuery = "name contains '${foundKeywords.first()}'" 

        val emails = gmailApiClient.searchMessages(gmailQuery, 3)
        val files = driveApiClient.searchFiles(driveQuery, 3)

        val links = mutableListOf<String>()
        files.forEach { file ->
            links.add("[Open Drive File](${file.webViewLink})")
        }
        emails.forEach { msg ->
            links.add("[Open Email](https://mail.google.com/mail/u/0/#inbox/${msg.id})")
        }

        if (links.isEmpty()) {
            return SkillResult.Skipped("No matching documents found")
        }

        return SkillResult.Success(
            description = "Found ${files.size + emails.size} files for your ${event.title} meeting. ${links.joinToString(" ")}",
            outcome = ActionOutcome.SUCCESS
        )
    }
}
