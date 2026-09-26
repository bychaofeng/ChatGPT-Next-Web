package cn.rehab.trainer.core

import java.net.URI
import javax.net.ssl.HttpsURLConnection

/** Secrets are not part of this data class, snapshots, prompts, diagnostics or exports. */
data class ApiConfig(val baseUrl: String, val model: String, val jsonMode: Boolean = true, val dailyLimit: Int = 30) {
    fun endpoint(): String {
        require(model.isNotBlank() && model.length <= 160) { "请填写服务商提供的模型名称。" }
        require(dailyLimit in 1..200)
        val u = URI(baseUrl.trim())
        require(u.scheme == "https" && !u.host.isNullOrBlank() && u.userInfo == null && u.query == null && u.fragment == null) { "API地址必须是无凭证、无查询参数的HTTPS地址。" }
        val base = u.toString().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }
}
data class WireResponse(val status: Int, val body: String)
fun interface Transport { fun post(endpoint: String, key: String, body: String): WireResponse }
class HttpsTransport : Transport {
    override fun post(endpoint: String, key: String, body: String): WireResponse {
        require(key.isNotBlank() && key.length <= 4096 && !key.contains('\n') && !key.contains('\r')) { "无效API Key。" }
        val c = URI(endpoint).toURL().openConnection() as HttpsURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 15_000; c.readTimeout = 60_000
            c.instanceFollowRedirects = false; c.doOutput = true
            c.setRequestProperty("Authorization", "Bearer $key")
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = c.responseCode
            // Never follow redirects with a credential, and never put raw provider bodies in diagnostics.
            if (status !in 200..299) return WireResponse(status, "")
            val bytes = c.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) { val count = input.read(buffer); if (count < 0) break; require(out.size() + count <= 1_048_576) { "响应超过大小限制。" }; out.write(buffer, 0, count) }; out.toByteArray()
            }
            return WireResponse(status, String(bytes, Charsets.UTF_8))
        } finally { c.disconnect() }
    }
}

class AiGateway(private val engine: Engine, private val config: ApiConfig, private val apiKey: String,
    private val transport: Transport = HttpsTransport(), private val clock: () -> Long = System::currentTimeMillis) {
    private fun request(purpose: String, system: String, input: Map<String, Any?>): Pair<String, Map<String, Any?>> {
        val endpoint = config.endpoint()
        require(apiKey.isNotBlank()) { "请先在本机设置API Key。" }
        val id = engine.reserveApi(purpose, clock(), config.dailyLimit)
        val payload = linkedMapOf<String, Any?>("model" to config.model, "stream" to false, "max_tokens" to 2400,
            "messages" to listOf(mapOf("role" to "system", "content" to system), mapOf("role" to "user", "content" to Json.stringify(input))))
        if (config.jsonMode) payload["response_format"] = mapOf("type" to "json_object")
        val response = try { transport.post(endpoint, apiKey, Json.stringify(payload)) }
            catch (_: java.io.IOException) { error("网络请求未完成；已保留原答案，可以手动重试。") }
        require(response.status in 200..299) { "模型服务返回HTTP ${response.status}；未更新评价。请核对连接设置或稍后重试。" }
        val envelope = Json.parse(response.body).obj()
        val usage = envelope["usage"] as? Map<*, *>
        val tokens = (usage?.get("total_tokens") as? Number)?.toLong()
        engine.tokenUsage(id, tokens)
        val choice = envelope.items("choices").firstOrNull()?.obj() ?: error("模型未返回答案；记录保持待评价。")
        require(choice.text("finish_reason") == "stop") { "模型输出未正常结束（可能截断）；记录保持待评价。" }
        val content = choice["message"].obj()["content"] as? String
        require(!content.isNullOrBlank()) { "模型返回空内容；记录保持待评价。" }
        return id to Json.parse(content).obj()
    }
    fun evaluate(lid: String, attemptId: String): Evaluation {
        val l = engine.learner(lid)
        l.evaluations.firstOrNull { it.attemptId == attemptId }?.let { return it }
        val a = l.attempts.first { it.id == attemptId }; require(a.choice == null)
        val s = engine.session(l, a.sessionId)
        val (requestId, m) = request("evaluate", Prompts.evaluate, Prompts.evaluationInput(l, a))
        m.keysExactly("task_id", "attempt_id", "content_version", "results", "feedback", "needs_review")
        require(m.text("task_id") == s.id && m.text("attempt_id") == a.id && m.text("content_version") == a.contentVersion) { "评价版本或引用对象不匹配。" }
        val results = m.items("results").map { raw ->
            val r = raw.obj(); r.keysExactly("criterion_id", "outcome", "quotes", "reason")
            val outcome = Outcome.valueOf(r.text("outcome"))
            val quotes = r.items("quotes").map { it as? String ?: error("引文格式错误。") }
            require(quotes.size <= 6 && quotes.all { it.isNotBlank() && a.answer.contains(it) }) { "模型引用了用户没有说过的话；未接受评价。" }
            require(outcome !in setOf(Outcome.MET, Outcome.PARTIAL) || quotes.isNotEmpty()) { "正面评价缺少本次作答证据。" }
            CriterionResult(r.limitedText("criterion_id", 80), outcome, quotes, r.limitedText("reason", 1600))
        }
        val review = m.bool("needs_review") || results.any { it.outcome == Outcome.UNCERTAIN }
        val e = Evaluation(newId(), a.id, clock(), Channel.AI_PRACTICE, results, m.limitedText("feedback"),
            if (review) EvaluationStatus.NEEDS_REVIEW else EvaluationStatus.ACTIVE, config.model + "/" + Prompts.VERSION, requestId)
        engine.accept(lid, e)
        return engine.learner(lid).evaluations.first { it.attemptId == a.id }
    }
    fun generate(lid: String, sid: String): String {
        val l = engine.learner(lid); val s = engine.session(l, sid)
        require(l.attempts.none { it.sessionId == sid } && s.exposures.isEmpty()) { "开始作答后不可改题。" }
        val (_, m) = request("generate", Prompts.generate, Prompts.generationInput(l, s))
        m.keysExactly("task_id", "content_version", "question", "needs_review")
        require(m.text("task_id") == s.id && m.text("content_version") == s.contentVersion)
        require(!m.bool("needs_review")) { "模型提出内容疑点，保留原题。" }
        val q = m.limitedText("question", 800)
        engine.replaceQuestion(lid, sid, q)
        return q
    }
    fun coach(lid: String, sid: String, mid: String): Message {
        val l = engine.learner(lid); val s = engine.session(l, sid)
        val user = l.messages.first { it.id == mid }; require(user.role == Role.USER)
        val sameSession = l.messages.filter { it.sessionId == sid }
        val after = sameSession.drop(sameSession.indexOfFirst { it.id == mid } + 1)
        after.takeWhile { it.role != Role.USER }.firstOrNull { it.role == Role.ASSISTANT }?.let { return it }
        val (_, m) = request("coach", Prompts.coach, Prompts.coachInput(l, s, user))
        m.keysExactly("task_id", "reply", "needs_review"); require(m.text("task_id") == sid)
        val reply = (if (m.bool("needs_review")) "【内容待核查，不作为执行依据】\n" else "") + m.limitedText("reply")
        return engine.message(lid, sid, Role.ASSISTANT, reply, clock())
    }
    fun absorb(lid: String, sid: String, ending: Boolean = false): MemoryBatch? {
        if (!engine.shouldAbsorb(lid, sid, ending)) return null
        val l = engine.learner(lid)
        val messages = engine.unabsorbed(l, sid).take(24)
        val batchId = "$sid:${messages.first().id}:${messages.last().id}"
        val (_, m) = request("absorb", Prompts.absorb, Prompts.absorbInput(batchId, messages, l.memories.lastOrNull { it.sessionId == sid }?.summary ?: ""))
        m.keysExactly("batch_id", "processed_message_ids", "summary", "clues")
        require(m.text("batch_id") == batchId)
        require(m.items("processed_message_ids") == messages.map { it.id }) { "摘要消息范围不匹配。" }
        val clues = m.items("clues").map { raw -> val c = raw.obj(); c.keysExactly("skill_id", "message_id", "quote", "description")
            Clue(Skill.valueOf(c.text("skill_id")), c.limitedText("message_id", 80), c.limitedText("quote", 6000), c.limitedText("description", 1200)) }
        require(clues.size <= 8)
        val batch = MemoryBatch(batchId, sid, messages.map { it.id }, m.limitedText("summary", 2000), clues, clock())
        engine.absorb(lid, batch)
        return batch
    }
}
