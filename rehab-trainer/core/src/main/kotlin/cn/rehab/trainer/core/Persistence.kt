package cn.rehab.trainer.core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One repository instance per app process. Commit disk first, then publish in-memory state. */
interface Repository {
    fun read(): Snapshot
    fun transaction(change: (Snapshot) -> Snapshot): Snapshot
}
fun freshSnapshot(): Snapshot {
    val l = Learner(newId(), "学习档案 1")
    return Snapshot(activeLearnerId = l.id, learners = listOf(l))
}
class MemoryRepository(initial: Snapshot = freshSnapshot()) : Repository {
    private var state = initial
    @Synchronized override fun read(): Snapshot = state
    @Synchronized override fun transaction(change: (Snapshot) -> Snapshot): Snapshot {
        val proposed = change(state)
        if (proposed != state) state = proposed.copy(revision = state.revision + 1)
        return state
    }
}
class FileRepository(private val file: File) : Repository {
    private var state: Snapshot
    init {
        file.parentFile?.mkdirs()
        // A corrupt/future-version file is an explicit error; never erase a learner's history silently.
        state = if (file.exists()) { require(file.length() <= 8_000_000L); Codec.decode(file.readText()) } else freshSnapshot().also { persist(it) }
    }
    @Synchronized override fun read(): Snapshot = state
    @Synchronized override fun transaction(change: (Snapshot) -> Snapshot): Snapshot {
        val proposed = change(state)
        if (proposed == state) return state
        val next = proposed.copy(revision = state.revision + 1)
        persist(next)
        state = next
        return next
    }
    private fun persist(s: Snapshot) {
        val encoded = Codec.encode(s).toByteArray(Charsets.UTF_8)
        require(encoded.size <= 8_000_000) { "本地记录已超过原型容量；请先导出，不会覆盖现有文件。" }
        val tmp = File(file.parentFile, file.name + "." + newId() + ".tmp")
        try {
            FileOutputStream(tmp).use { it.write(encoded); it.fd.sync() }
            // Same-directory atomic rename; unsupported platforms fail rather than use a lossy fallback.
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { tmp.delete() }
    }
}

object Codec {
    private fun option(o: Option) = mapOf("id" to o.id, "text" to o.text)
    private fun criterion(c: Criterion) = mapOf("id" to c.id, "description" to c.description)
    fun blueprint(b: Blueprint): Map<String, Any?> = mapOf("id" to b.id, "skill" to b.skill.name, "family" to b.family,
        "scenario" to b.scenario, "question" to b.question, "facts" to b.facts, "options" to b.options.map(::option),
        "correctOption" to b.correctOption, "criteria" to b.criteria.map(::criterion), "hint" to b.hint, "explanation" to b.explanation)
    private fun blueprint(m: Map<String, Any?>) = Blueprint(m.text("id"), Skill.valueOf(m.text("skill")), m.text("family"),
        m.text("scenario"), m.text("question"), m["facts"].obj().mapValues { it.value as String },
        m.items("options").map { it.obj().let { x -> Option(x.text("id"), x.text("text")) } }, m.text("correctOption"),
        m.items("criteria").map { it.obj().let { x -> Criterion(x.text("id"), x.text("description")) } }, m.text("hint"), m.text("explanation"))
    fun encode(s: Snapshot): String = Json.stringify(mapOf(
        "schemaVersion" to s.schemaVersion, "revision" to s.revision, "activeLearnerId" to s.activeLearnerId,
        "apiCalls" to s.apiCalls.map { mapOf("id" to it.id, "purpose" to it.purpose, "at" to it.at, "tokens" to it.tokens) },
        "learners" to s.learners.map { l -> mapOf(
            "id" to l.id, "name" to l.name,
            "sessions" to l.sessions.map { x -> mapOf(
                "id" to x.id, "blueprintId" to x.blueprintId, "contentVersion" to x.contentVersion, "skill" to x.skill.name,
                "family" to x.family, "mode" to x.mode.name, "reason" to x.reason, "definition" to blueprint(x.definition),
                "createdAt" to x.createdAt, "priorFamilyExposure" to x.priorFamilyExposure, "scenario" to x.scenario, "question" to x.question,
                "draft" to x.draft, "selectedOption" to x.selectedOption, "visibleFacts" to x.visibleFacts,
                "exposures" to x.exposures.map { mapOf("kind" to it.kind, "at" to it.at, "sourceId" to it.sourceId) }, "finishedAt" to x.finishedAt, "generated" to x.generated
            ) },
            "attempts" to l.attempts.map { a -> mapOf("id" to a.id, "sessionId" to a.sessionId, "at" to a.at, "answer" to a.answer,
                "choice" to a.choice, "assisted" to a.assisted, "repeatedFamily" to a.repeatedFamily, "visibleFacts" to a.visibleFacts, "contentVersion" to a.contentVersion) },
            "evaluations" to l.evaluations.map { e -> mapOf("id" to e.id, "attemptId" to e.attemptId, "at" to e.at, "channel" to e.channel.name,
                "results" to e.results.map { r -> mapOf("criterionId" to r.criterionId, "outcome" to r.outcome.name, "quotes" to r.quotes, "reason" to r.reason) },
                "feedback" to e.feedback, "status" to e.status.name, "model" to e.model, "requestId" to e.requestId) },
            "messages" to l.messages.map { m -> mapOf("id" to m.id, "sessionId" to m.sessionId, "role" to m.role.name, "text" to m.text, "at" to m.at) },
            "memories" to l.memories.map { b -> mapOf("id" to b.id, "sessionId" to b.sessionId, "messageIds" to b.messageIds, "summary" to b.summary,
                "clues" to b.clues.map { c -> mapOf("skill" to c.skill.name, "messageId" to c.messageId, "quote" to c.quote, "description" to c.description) }, "at" to b.at) }
        ) }
    ))
    fun decode(text: String): Snapshot {
        val m = Json.parse(text).obj()
        require(m.long("schemaVersion") == 1L) { "不支持的本地数据版本，原文件保留。" }
        val learners = m.items("learners").map { value ->
            val l = value.obj()
            Learner(l.text("id"), l.text("name"),
                l.items("sessions").map { v -> val x = v.obj(); Session(x.text("id"), x.text("blueprintId"), x.text("contentVersion"), Skill.valueOf(x.text("skill")),
                    x.text("family"), Mode.valueOf(x.text("mode")), x.text("reason"), blueprint(x["definition"].obj()), x.long("createdAt"), x.bool("priorFamilyExposure"),
                    x.text("scenario"), x.text("question"), x.text("draft"), x["selectedOption"] as? String,
                    x.items("visibleFacts").map { it as String }, x.items("exposures").map { it.obj().let { e -> Exposure(e.text("kind"), e.long("at"), e.text("sourceId")) } }, x["finishedAt"] as? Long, x.bool("generated")) },
                l.items("attempts").map { v -> val a = v.obj(); Attempt(a.text("id"), a.text("sessionId"), a.long("at"), a.text("answer"), a["choice"] as? String,
                    a.bool("assisted"), a.bool("repeatedFamily"), a.items("visibleFacts").map { it as String }, a.text("contentVersion")) },
                l.items("evaluations").map { v -> val e = v.obj(); Evaluation(e.text("id"), e.text("attemptId"), e.long("at"), Channel.valueOf(e.text("channel")),
                    e.items("results").map { it.obj().let { r -> CriterionResult(r.text("criterionId"), Outcome.valueOf(r.text("outcome")), r.items("quotes").map { q -> q as String }, r.text("reason")) } },
                    e.text("feedback"), EvaluationStatus.valueOf(e.text("status")), e.text("model"), e.text("requestId")) },
                l.items("messages").map { it.obj().let { x -> Message(x.text("id"), x.text("sessionId"), Role.valueOf(x.text("role")), x.text("text"), x.long("at")) } },
                l.items("memories").map { v -> val b = v.obj(); MemoryBatch(b.text("id"), b.text("sessionId"), b.items("messageIds").map { it as String }, b.text("summary"),
                    b.items("clues").map { it.obj().let { c -> Clue(Skill.valueOf(c.text("skill")), c.text("messageId"), c.text("quote"), c.text("description")) } }, b.long("at")) }
            )
        }
        require(learners.isNotEmpty() && learners.map { it.id }.distinct().size == learners.size)
        require(learners.any { it.id == m.text("activeLearnerId") })
        learners.forEach { l ->
            require(l.sessions.map { it.id }.distinct().size == l.sessions.size)
            require(l.attempts.map { it.id }.distinct().size == l.attempts.size)
            require(l.evaluations.map { it.attemptId }.distinct().size == l.evaluations.size)
            require(l.attempts.all { a -> l.sessions.any { it.id == a.sessionId } })
            require(l.evaluations.all { e -> l.attempts.any { it.id == e.attemptId } })
        }
        return Snapshot(1, m.long("revision"), m.text("activeLearnerId"), learners,
            m.items("apiCalls").map { it.obj().let { c -> ApiCall(c.text("id"), c.text("purpose"), c.long("at"), c["tokens"] as? Long) } })
    }
}
