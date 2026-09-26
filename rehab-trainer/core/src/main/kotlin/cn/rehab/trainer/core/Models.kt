package cn.rehab.trainer.core

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
const val DAY = 86_400_000L
const val CONTENT_VERSION = "demo-2026-09-26-v1"

enum class Skill(val title: String) {
    GOALS("目标与优先级"), INFORMATION("获取与理解信息"), HYPOTHESES("比较解释与不确定性"),
    PLANNING("可执行的训练计划"), REASSESSMENT("复评与调整"), COMMUNICATION("沟通与共同决策"),
    SAFETY("安全与工作边界"), EVIDENCE("核对依据与反思")
}
enum class Mode { EXPLORE, PRACTICE, REVIEW }
enum class Outcome { MET, PARTIAL, NOT_MET, UNCERTAIN, NOT_ASSESSED }
enum class EvaluationStatus { ACTIVE, NEEDS_REVIEW, DISPUTED, REVOKED, CANCELLED }
enum class Channel { DEMO_CHOICE, AI_PRACTICE }
enum class Role { USER, ASSISTANT }

data class Option(val id: String, val text: String)
data class Criterion(val id: String, val description: String)
data class Blueprint(
    val id: String, val skill: Skill, val family: String, val scenario: String,
    val question: String, val facts: Map<String, String>, val options: List<Option>,
    val correctOption: String, val criteria: List<Criterion>, val hint: String, val explanation: String
)

data class Exposure(val kind: String, val at: Long, val sourceId: String = kind)
data class Session(
    val id: String, val blueprintId: String, val contentVersion: String, val skill: Skill,
    val family: String, val mode: Mode, val reason: String, val definition: Blueprint, val createdAt: Long,
    val priorFamilyExposure: Boolean, val scenario: String, val question: String,
    val draft: String = "", val selectedOption: String? = null,
    val visibleFacts: List<String> = emptyList(), val exposures: List<Exposure> = emptyList(),
    val finishedAt: Long? = null, val generated: Boolean = false
)
data class Attempt(
    val id: String, val sessionId: String, val at: Long, val answer: String,
    val choice: String?, val assisted: Boolean, val repeatedFamily: Boolean,
    val visibleFacts: List<String>, val contentVersion: String
)
data class CriterionResult(val criterionId: String, val outcome: Outcome, val quotes: List<String>, val reason: String)
data class Evaluation(
    val id: String, val attemptId: String, val at: Long, val channel: Channel,
    val results: List<CriterionResult>, val feedback: String, val status: EvaluationStatus,
    val model: String, val requestId: String
) {
    val outcome: Outcome get() = when {
        results.any { it.outcome == Outcome.UNCERTAIN } -> Outcome.UNCERTAIN
        results.all { it.outcome == Outcome.NOT_ASSESSED } -> Outcome.NOT_ASSESSED
        results.any { it.outcome == Outcome.NOT_MET } -> Outcome.NOT_MET
        results.any { it.outcome == Outcome.PARTIAL } -> Outcome.PARTIAL
        else -> Outcome.MET
    }
}
data class Message(val id: String, val sessionId: String, val role: Role, val text: String, val at: Long)
data class Clue(val skill: Skill, val messageId: String, val quote: String, val description: String)
data class MemoryBatch(val id: String, val sessionId: String, val messageIds: List<String>, val summary: String, val clues: List<Clue>, val at: Long)
data class ApiCall(val id: String, val purpose: String, val at: Long, val tokens: Long? = null)
data class Learner(
    val id: String, val name: String, val sessions: List<Session> = emptyList(),
    val attempts: List<Attempt> = emptyList(), val evaluations: List<Evaluation> = emptyList(),
    val messages: List<Message> = emptyList(), val memories: List<MemoryBatch> = emptyList()
)
data class Snapshot(val schemaVersion: Int = 1, val revision: Long = 0, val activeLearnerId: String,
    val learners: List<Learner>, val apiCalls: List<ApiCall> = emptyList())

data class SkillProfile(
    val skill: Skill, val submitted: Int, val observations: Int, val unaidedPracticeMet: Int,
    val guidedPracticeMet: Int, val missingOrPartial: Int, val pendingReview: Int,
    val families: Int, val repeatedAttempts: Int, val latest: Outcome?, val dueAt: Long?,
    val verifiedIndependent: Int = 0
) {
    val stateLabel: String get() = when {
        pendingReview > 0 -> "含待核查记录"
        observations == 0 -> "尚未观察 / 证据不足"
        unaidedPracticeMet > 0 && missingOrPartial > 0 -> "表现不一致，待验证"
        unaidedPracticeMet > 0 -> "出现无提示练习表现（暂定）"
        guidedPracticeMet > 0 -> "提示后有进展（暂定）"
        else -> "出现待练线索（暂定）"
    }
}
data class Selection(val blueprint: Blueprint, val mode: Mode, val reason: String)

/** Rebuild projections from source records; neither chats nor summaries can mint ability evidence. */
object Profiles {
    fun build(l: Learner): List<SkillProfile> = Skill.values().map { skill ->
        val sessions = l.sessions.filter { it.skill == skill }
        val sessionIds = sessions.map { it.id }.toSet()
        val attempts = l.attempts.filter { it.sessionId in sessionIds }
        val ids = attempts.map { it.id }.toSet()
        val active = l.evaluations.filter { it.attemptId in ids && it.status == EvaluationStatus.ACTIVE }
        val meaningful = active.filter { it.outcome !in setOf(Outcome.UNCERTAIN, Outcome.NOT_ASSESSED) }
        val byId = attempts.associateBy { it.id }
        val independentMet = meaningful.filter { it.outcome == Outcome.MET && byId.getValue(it.attemptId).let { a -> !a.assisted && !a.repeatedFamily } }
            .map { byId.getValue(it.attemptId).sessionId }.distinct().size
        val guidedMet = meaningful.filter { it.outcome == Outcome.MET && byId.getValue(it.attemptId).assisted }.map { byId.getValue(it.attemptId).sessionId }.distinct().size
        val missing = meaningful.filter { it.outcome == Outcome.NOT_MET || it.outcome == Outcome.PARTIAL }.map { byId.getValue(it.attemptId).sessionId }.distinct().size
        val lastHelp = sessions.flatMap { it.exposures }.maxOfOrNull { it.at }
        val lastMet = meaningful.filter { it.outcome == Outcome.MET && byId.getValue(it.attemptId).let { a -> !a.assisted && !a.repeatedFamily } }.maxOfOrNull { byId.getValue(it.attemptId).at }
        // Trial intervals only. They schedule another observation; they never mark mastery.
        val due = when { lastHelp != null && (lastMet == null || lastHelp >= lastMet) -> lastHelp + 3 * DAY; lastMet != null -> lastMet + 7 * DAY; else -> null }
        SkillProfile(skill, attempts.size, meaningful.map { byId.getValue(it.attemptId).sessionId }.distinct().size,
            independentMet, guidedMet, missing,
            l.evaluations.count { it.attemptId in ids && it.status in setOf(EvaluationStatus.NEEDS_REVIEW, EvaluationStatus.DISPUTED) },
            meaningful.map { e -> sessions.first { it.id == byId.getValue(e.attemptId).sessionId }.family }.distinct().size,
            attempts.count { it.repeatedFamily },
            meaningful.maxByOrNull { byId.getValue(it.attemptId).at }?.outcome, due)
    }
}
