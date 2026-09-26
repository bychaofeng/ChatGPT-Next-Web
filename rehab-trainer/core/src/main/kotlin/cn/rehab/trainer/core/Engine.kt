package cn.rehab.trainer.core

/** Domain coordinator. Models propose content; only this layer commits learner records. */
class Engine(val repository: Repository) {
    fun snapshot(): Snapshot = repository.read()
    fun learner(id: String = snapshot().activeLearnerId): Learner = snapshot().learners.firstOrNull { it.id == id } ?: error("Unknown learner")
    fun active(l: Learner = learner()): Session? = l.sessions.lastOrNull { it.finishedAt == null }
    private fun change(id: String, action: (Learner) -> Learner) = repository.transaction { s ->
        require(s.learners.any { it.id == id })
        s.copy(learners = s.learners.map { if (it.id == id) action(it) else it })
    }
    fun createLearner(): String {
        val id = newId()
        repository.transaction { it.copy(activeLearnerId = id, learners = it.learners + Learner(id, "学习档案 ${it.learners.size + 1}")) }
        return id
    }
    fun selectLearner(id: String) { repository.transaction { s -> require(s.learners.any { it.id == id }); s.copy(activeLearnerId = id) } }
    fun deleteLearner(id: String) { repository.transaction { s ->
        val rest = s.learners.filter { it.id != id }
        if (rest.isEmpty()) freshSnapshot().copy(revision = s.revision, apiCalls = s.apiCalls)
        else s.copy(learners = rest, activeLearnerId = if (s.activeLearnerId == id) rest.first().id else s.activeLearnerId)
    } }
    fun start(learnerId: String, now: Long, preferred: Skill? = null): Session {
        var output: Session? = null
        change(learnerId) { l ->
            val current = active(l)
            if (current != null) { output = current; l } else {
                val choice = Scheduler.select(l, now, preferred); val b = choice.blueprint
                val s = Session(newId(), b.id, CONTENT_VERSION, b.skill, b.family, choice.mode, choice.reason, b, now,
                    l.sessions.any { it.family == b.family }, b.scenario, b.question)
                output = s; l.copy(sessions = l.sessions + s)
            }
        }
        return output!!
    }
    fun session(l: Learner, id: String): Session = l.sessions.firstOrNull { it.id == id } ?: error("Unknown session")
    fun draft(lid: String, sid: String, text: String, choice: String?) {
        require(text.length <= 6000)
        change(lid) { l -> val s = session(l, sid); require(s.finishedAt == null)
            require(choice == null || s.definition.options.any { it.id == choice })
            l.copy(sessions = l.sessions.map { if (it.id == sid) it.copy(draft = text, selectedOption = choice) else it }) }
    }
    fun revealFact(lid: String, sid: String, factId: String): String {
        var fact = ""
        change(lid) { l -> val s = session(l, sid); require(s.finishedAt == null)
            fact = s.definition.facts[factId] ?: error("Unknown fact")
            l.copy(sessions = l.sessions.map { if (it.id == sid) it.copy(visibleFacts = (it.visibleFacts + factId).distinct()) else it }) }
        return fact
    }
    /** Called when a UI is about to display teaching. No model output can undo this exposure. */
    fun expose(lid: String, sid: String, kind: String, now: Long, sourceId: String = kind) {
        require(kind in setOf("hint", "explanation", "feedback", "coach"))
        change(lid) { l -> val s = session(l, sid)
            l.copy(sessions = l.sessions.map { if (it.id == s.id && it.exposures.none { e -> e.sourceId == sourceId && e.kind == kind }) it.copy(exposures = it.exposures + Exposure(kind, now, sourceId)) else it }) }
    }
    fun submit(lid: String, sid: String, openText: Boolean, now: Long): Attempt {
        var result: Attempt? = null
        change(lid) { l ->
            val s = session(l, sid); require(s.finishedAt == null)
            val answer = if (openText) s.draft.trim() else s.definition.options.firstOrNull { it.id == s.selectedOption }?.text ?: error("先选择一个选项。")
            require(answer.isNotBlank() && answer.length <= 6000) { "先写下你的答案。" }
            val choice = if (openText) null else s.selectedOption
            // Same draft with identical conditions reuses its logical operation, including network retries.
            val previous = l.attempts.lastOrNull { it.sessionId == sid }
            if (previous != null && previous.answer == answer && previous.choice == choice && previous.assisted == s.exposures.isNotEmpty()
                && previous.visibleFacts == s.visibleFacts) { result = previous; l }
            else {
                val a = Attempt(newId(), sid, now, answer, choice, s.exposures.isNotEmpty(), s.priorFamilyExposure, s.visibleFacts.toList(), s.contentVersion)
                result = a; l.copy(attempts = l.attempts + a)
            }
        }
        return result!!
    }
    fun gradeDemo(lid: String, attemptId: String, now: Long): Evaluation {
        val l = learner(lid); val a = l.attempts.first { it.id == attemptId }; require(a.choice != null)
        val s = session(l, a.sessionId); val correct = a.choice == s.definition.correctOption
        val e = Evaluation(newId(), a.id, now, Channel.DEMO_CHOICE,
            listOf(CriterionResult("decision", if (correct) Outcome.MET else Outcome.NOT_MET, listOf(a.answer), "仅核对演示选项，不评价开放式解释或真实操作。"),
                CriterionResult("reason", Outcome.NOT_ASSESSED, emptyList(), "离线模式未评价开放式理由。")),
            (if (correct) "本次选择符合演示参考。" else "本次选择与演示参考不同。") + "\n" + s.definition.explanation,
            EvaluationStatus.ACTIVE, "offline-demo-not-clinically-reviewed", "demo:${a.id}")
        accept(lid, e)
        return learner(lid).evaluations.first { it.attemptId == a.id }
    }
    fun accept(lid: String, e: Evaluation) {
        change(lid) { l ->
            val a = l.attempts.firstOrNull { it.id == e.attemptId } ?: error("Unknown attempt")
            val s = session(l, a.sessionId)
            require(e.results.map { it.criterionId }.toSet() == s.definition.criteria.map { it.id }.toSet())
            require(e.results.size == s.definition.criteria.size)
            require(e.results.all { r -> r.quotes.all { it.isNotBlank() && a.answer.contains(it) } }) { "评价引用并非来自这次作答。" }
            // First accepted response wins. Corrections require an explicit review path, not a retry.
            if (l.evaluations.any { it.attemptId == a.id }) l else l.copy(evaluations = l.evaluations + e)
        }
    }
    fun dispute(lid: String, eid: String) { change(lid) { l -> require(l.evaluations.any { it.id == eid })
        l.copy(evaluations = l.evaluations.map { if (it.id == eid) it.copy(status = EvaluationStatus.DISPUTED) else it }) } }
    fun revoke(lid: String, eid: String) { change(lid) { l -> require(l.evaluations.any { it.id == eid })
        l.copy(evaluations = l.evaluations.map { if (it.id == eid) it.copy(status = EvaluationStatus.REVOKED) else it }) } }
    fun withdrawContent(blueprintId: String) { repository.transaction { state -> state.copy(learners = state.learners.map { l ->
        val sids = l.sessions.filter { it.blueprintId == blueprintId }.map { it.id }.toSet()
        val aids = l.attempts.filter { it.sessionId in sids }.map { it.id }.toSet()
        l.copy(evaluations = l.evaluations.map { if (it.attemptId in aids) it.copy(status = EvaluationStatus.REVOKED) else it })
    }) } }
    fun finish(lid: String, sid: String, now: Long, leavePending: Boolean = false) { change(lid) { l ->
        val pending = l.attempts.filter { it.sessionId == sid && l.evaluations.none { e -> e.attemptId == it.id } }
        require(pending.isEmpty() || leavePending) { "尚有待评价答案，可以暂停或明确选择保留未评价记录并结束。" }
        val definition = session(l, sid).definition
        val cancelled = pending.map { a -> Evaluation(newId(), a.id, now, Channel.AI_PRACTICE,
            definition.criteria.map { CriterionResult(it.id, Outcome.NOT_ASSESSED, emptyList(), "用户结束本轮，未评价。") },
            "未评价；不会视为错误。", EvaluationStatus.CANCELLED, "application", "cancel:${a.id}") }
        l.copy(sessions = l.sessions.map { if (it.id == sid) it.copy(finishedAt = it.finishedAt ?: now) else it },
            evaluations = l.evaluations + cancelled)
    } }
    fun replaceQuestion(lid: String, sid: String, question: String) { change(lid) { l ->
        val s = session(l, sid)
        require(s.finishedAt == null && l.attempts.none { it.sessionId == sid } && s.exposures.isEmpty() && s.draft.isBlank() && s.selectedOption == null && l.messages.none { it.sessionId == sid })
        require(question.length in 10..800)
        l.copy(sessions = l.sessions.map { if (it.id == sid) it.copy(question = question, generated = true) else it }) } }
    fun message(lid: String, sid: String, role: Role, text: String, now: Long): Message {
        require(text.isNotBlank() && text.length <= 6000)
        val m = Message(newId(), sid, role, text.trim(), now)
        change(lid) { l -> require(session(l, sid).finishedAt == null); l.copy(messages = l.messages + m) }
        return m
    }
    fun unabsorbed(l: Learner, sid: String): List<Message> {
        val seen = l.memories.flatMap { it.messageIds }.toSet()
        return l.messages.filter { it.sessionId == sid && it.id !in seen }
    }
    fun shouldAbsorb(lid: String, sid: String, ending: Boolean = false): Boolean {
        val m = unabsorbed(learner(lid), sid)
        val pairs = m.count { it.role == Role.ASSISTANT }.coerceAtMost(m.count { it.role == Role.USER })
        return m.isNotEmpty() && (ending || pairs >= 6 || m.sumOf { it.text.length } >= 16_000)
    }
    fun absorb(lid: String, b: MemoryBatch) { change(lid) { l ->
        if (l.memories.any { it.id == b.id }) l else {
            require(b.messageIds.isNotEmpty() && b.messageIds.distinct().size == b.messageIds.size)
            val newMessages = unabsorbed(l, b.sessionId).associateBy { it.id }
            require(b.messageIds.all { it in newMessages }) { "摘要包含已处理或不存在的消息。" }
            require(b.clues.all { c -> val m = newMessages[c.messageId]; m != null && m.id in b.messageIds && m.role == Role.USER && c.quote.isNotBlank() && m.text.contains(c.quote) })
            l.copy(memories = l.memories + b)
        }
    } }
    fun reserveApi(purpose: String, now: Long, dailyLimit: Int): String {
        require(dailyLimit in 1..200)
        val id = newId()
        repository.transaction { s ->
            // Rolling 24 h, global per installation. Switching/deleting profiles cannot reset this cap.
            require(s.apiCalls.count { it.at > now - DAY } < dailyLimit) { "已达到滚动24小时调用上限；失败和重试也计入次数。" }
            s.copy(apiCalls = (s.apiCalls.filter { it.at > now - 31 * DAY } + ApiCall(id, purpose, now)))
        }
        return id
    }
    fun tokenUsage(id: String, tokens: Long?) { if (tokens != null) require(tokens >= 0)
        repository.transaction { s -> s.copy(apiCalls = s.apiCalls.map { if (it.id == id) it.copy(tokens = tokens) else it }) } }
}
