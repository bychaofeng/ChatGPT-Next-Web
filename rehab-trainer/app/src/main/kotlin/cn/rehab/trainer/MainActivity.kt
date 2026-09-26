package cn.rehab.trainer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import cn.rehab.trainer.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Thin native prototype. The core owns progress; views never ask an LLM to rewrite a profile. */
class MainActivity : Activity() {
    private val app get() = application as TrainerApplication
    private val engine get() = app.engine
    private val prefs get() = getSharedPreferences("api_config", MODE_PRIVATE)
    private lateinit var page: LinearLayout
    private var route = "home"
    private var selectedSession: String? = null
    private val ui = Handler(Looper.getMainLooper())
    private var observedBusy = false
    private val poll = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            val busy = app.busy.get()
            if (observedBusy && !busy) {
                observedBusy = false
                if (route == "train") {
                    val session = engine.learner().sessions.firstOrNull { it.id == selectedSession }
                    if (session?.finishedAt != null) { route = "profile"; selectedSession = null }
                }
                render()
                app.lastError?.let { error -> app.lastError = null; showError(error) }
            }
            ui.postDelayed(this, 250)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route = savedInstanceState?.getString("route") ?: "home"
        selectedSession = savedInstanceState?.getString("session")
        observedBusy = app.busy.get()
        try { engine.snapshot(); render() } catch (_: Exception) {
            setContentView(TextView(this).apply { text = "本地档案无法读取，原文件已保留。请先备份应用私有数据并检查版本；不会自动清空。"; setPadding(32, 64, 32, 32) })
        }
    }
    override fun onResume() { super.onResume(); ui.post(poll) }
    override fun onPause() { ui.removeCallbacks(poll); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("route", route); outState.putString("session", selectedSession); super.onSaveInstanceState(outState) }
    @Deprecated("Prototype uses a single activity navigation stack")
    override fun onBackPressed() { if (route == "home") super.onBackPressed() else navigate("home") }
    private fun now() = System.currentTimeMillis()
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun showError(text: String) { AlertDialog.Builder(this).setTitle("操作未完成").setMessage(text).setPositiveButton("知道了", null).show() }
    private fun safe(action: () -> Unit) { try { action() } catch (e: Exception) { showError(e.message ?: "操作未完成，原记录已保留。") } }
    private fun navigate(next: String) { route = next; render() }
    private fun render() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(244, 247, 248)) }
        root.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION") v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        val scroll = ScrollView(this)
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(32)) }
        scroll.addView(page); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("home" to "训练", "profile" to "学习记录", "settings" to "设置").forEach { (r, label) ->
            nav.addView(Button(this).apply { text = label; isEnabled = !app.busy.get(); setOnClickListener { navigate(r) } }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav)
        setContentView(root); root.requestApplyInsets()
        when (route) { "train" -> training(); "profile" -> profile(); "settings" -> settings(); else -> home() }
        if (app.busy.get()) { label("正在处理模型请求。原始记录已保存；没有评价结果不会被判错。", 14); page.addView(ProgressBar(this)) }
    }
    private fun label(text: String, size: Int = 16, bold: Boolean = false) = TextView(this).also {
        it.text = text; it.textSize = size.toFloat(); it.setTextColor(Color.rgb(24, 43, 55))
        if (bold) it.setTypeface(null, android.graphics.Typeface.BOLD)
        it.setPadding(0, dp(7), 0, dp(7)); page.addView(it)
    }
    private fun button(text: String, action: () -> Unit): Button = Button(this).also {
        it.text = text; it.isAllCaps = false; it.isEnabled = !app.busy.get()
        page.addView(it, LinearLayout.LayoutParams(-1, -2)); it.setOnClickListener { safe(action) }
    }
    private fun input(hint: String, value: String = "", multiline: Boolean = false): EditText = EditText(this).also {
        it.hint = hint; it.setText(value); it.textSize = 16f; it.isEnabled = !app.busy.get()
        it.inputType = if (multiline) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE else InputType.TYPE_CLASS_TEXT
        it.minLines = if (multiline) 3 else 1; it.maxLines = if (multiline) 8 else 1
        it.filters = arrayOf(android.text.InputFilter.LengthFilter(if (multiline) 6000 else 4096))
        page.addView(it, LinearLayout.LayoutParams(-1, -2))
    }
    private fun home() {
        label("康复决策练习", 28, true)
        label("不填背景表，直接开始。系统从未知状态逐步观察，不把你预先分成初级或高级。")
        label("开发原型 · 16道未医学审核的虚构流程题 · 不是能力认证或患者治疗工具", 13)
        label("当前：${engine.learner().name}", 16, true)
        val current = engine.active()
        button(if (current == null) "开始训练" else "继续上次训练") {
            val s = engine.start(engine.learner().id, now()); selectedSession = s.id; navigate("train")
        }
        button("自己选择一个学习主题（可选）") {
            if (engine.active() != null) error("请先完成或暂停处理当前任务；默认会保留原题。")
            AlertDialog.Builder(this).setTitle("选择主题").setItems(Skill.values().map { it.title }.toTypedArray()) { _, n -> safe {
                val s = engine.start(engine.learner().id, now(), Skill.values()[n]); selectedSession = s.id; navigate("train")
            } }.show()
        }
        label("每项能力独立积累证据；会的部分继续探索，尚不清楚的部分再验证。", 14)
        label("离线可以使用固定选项练习。填写自己的API连接后，才能使用开放回答评价、题面改写和讨论。", 14)
    }
    private fun training() {
        val l = engine.learner()
        val s = l.sessions.firstOrNull { it.id == selectedSession } ?: engine.active(l)
        if (s == null) { label("没有进行中的训练。"); button("返回") { navigate("home") }; return }
        selectedSession = s.id
        val sid = s.id; val lid = l.id
        label(if (s.mode == Mode.PRACTICE) "有目的地练一轮" else "情境决策练习", 24, true)
        label("虚构演示 / ${if (s.generated) "AI改写，未医学审核" else "固定题面，未医学审核"}", 13)
        if (s.priorFamilyExposure) label("你已见过这个题目家族，本次只作重复练习。", 13)
        label(s.scenario, 17)
        label(s.question, 19, true)
        if (s.definition.facts.isNotEmpty()) {
            label("可主动获取的病例资料（不算提示）", 14, true)
            s.definition.facts.keys.forEach { fid ->
                if (fid in s.visibleFacts) label("$fid：${s.definition.facts.getValue(fid)}", 14)
                else button("查看资料：$fid") { engine.revealFact(lid, sid, fid); render() }
            }
        }
        if (s.finishedAt != null) { label("本轮已结束。下面的历史答案会保留。"); history(l, s); return }
        val answers = l.attempts.filter { it.sessionId == sid }
        if (answers.isEmpty() && s.exposures.isEmpty() && s.draft.isBlank() && s.selectedOption == null)
            button("AI改写这一题的问法") { launchApi { it.generate(lid, sid) } }
        label("方式一：离线选项练习", 16, true)
        var selected = s.selectedOption
        val radios = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL; isEnabled = !app.busy.get() }
        s.definition.options.forEach { o -> radios.addView(RadioButton(this).apply {
            id = View.generateViewId(); text = "${o.id}  ${o.text}"; tag = o.id; isChecked = o.id == selected; isEnabled = !app.busy.get()
        }) }
        page.addView(radios)
        val draft = input("方式二：写出你的判断与依据（用AI评价）", s.draft, true)
        radios.setOnCheckedChangeListener { group, checked ->
            selected = group.findViewById<RadioButton>(checked)?.tag as? String
            safe { engine.draft(lid, sid, draft.text.toString(), selected) }
        }
        draft.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { safe { engine.draft(lid, sid, s?.toString() ?: "", selected) } }
            override fun afterTextChanged(s: Editable?) {}
        })
        button("提交选项 · 无API费用") {
            val a = engine.submit(lid, sid, false, now()); engine.gradeDemo(lid, a.id, now()); render()
        }
        button("提交文字 · AI评价") {
            requireConnection()
            val a = engine.submit(lid, sid, true, now()); launchApi { it.evaluate(lid, a.id) }
        }
        button("给一个提示") {
            engine.expose(lid, sid, "hint", now())
            AlertDialog.Builder(this).setTitle("本题后续将记录为获得提示").setMessage(s.definition.hint).setPositiveButton("继续尝试", null).show()
        }
        button("查看完整演示讲解") {
            engine.expose(lid, sid, "explanation", now())
            AlertDialog.Builder(this).setTitle("演示讲解 · 未医学审核").setMessage(s.definition.explanation).setPositiveButton("知道了", null).show()
        }
        history(l, s)
        label("继续讨论这道题", 20, true)
        label("这里是教学，不是考试。不要输入患者姓名、联系方式、病历等真实身份资料。", 13)
        val messages = l.messages.filter { it.sessionId == sid }
        messages.takeLast(12).forEach { m ->
            if (m.role == Role.ASSISTANT) engine.expose(lid, sid, "coach", now(), "coach:${m.id}")
            label("${if (m.role == Role.USER) "你" else "AI"}：${m.text}", 15)
        }
        val followup = input("只讨论当前虚构题目", multiline = true)
        button("发送讨论 · AI") {
            requireConnection()
            val text = followup.text.toString().trim(); require(text.isNotEmpty()) { "先写下问题。" }
            val previous = engine.learner(lid).messages.lastOrNull { it.sessionId == sid }
            val m = if (previous?.role == Role.USER && previous.text == text) previous else engine.message(lid, sid, Role.USER, text, now())
            launchApi { gateway -> gateway.coach(lid, sid, m.id); gateway.absorb(lid, sid) }
        }
        if (messages.lastOrNull()?.role == Role.USER) button("重试最后一条讨论") {
            val m = messages.last(); launchApi { gateway -> gateway.coach(lid, sid, m.id); gateway.absorb(lid, sid) }
        }
        button("结束这一轮") {
            val current = engine.learner(lid)
            val pending = current.attempts.any { a -> a.sessionId == sid && current.evaluations.none { it.attemptId == a.id } }
            fun complete() {
                if (hasConnection() && engine.shouldAbsorb(lid, sid, true)) {
                    launchApi { gateway ->
                        try { gateway.absorb(lid, sid, true) } finally { engine.finish(lid, sid, now(), leavePending = pending) }
                    }
                } else { engine.finish(lid, sid, now(), leavePending = pending); selectedSession = null; navigate("profile") }
            }
            if (pending) AlertDialog.Builder(this).setTitle("保留未评价答案并结束？")
                .setMessage("这些答案会保留为未评价，不会视为错误。晚到的模型结果不会覆盖它们。")
                .setPositiveButton("保留并结束") { _, _ -> safe { complete() } }.setNegativeButton("继续本轮", null).show()
            else complete()
        }
    }
    private fun history(l: Learner, s: Session) {
        val attempts = l.attempts.filter { it.sessionId == s.id }
        if (attempts.isEmpty()) return
        label("本轮作答记录", 19, true)
        attempts.forEachIndexed { i, a ->
            label("第${i + 1}次 · ${if (a.assisted) "获得帮助后" else "本次未用提示"}${if (a.repeatedFamily) " · 见过同家族" else ""}\n${a.answer}", 14)
            val evaluation = l.evaluations.firstOrNull { it.attemptId == a.id }
            if (evaluation == null) {
                label("待评价：网络失败不算答错。", 13)
                if (s.finishedAt == null) button("重试这次评价") { launchApi { it.evaluate(l.id, a.id) } }
            } else {
                label("${evaluation.channel} / ${evaluation.status} · 暂定练习记录", 12)
                button("查看这次反馈") {
                    engine.expose(l.id, s.id, "feedback", now(), "feedback:${evaluation.id}")
                    val detail = evaluation.feedback + "\n\n" + evaluation.results.joinToString("\n\n") { r -> "${r.criterionId}: ${r.outcome}\n${r.reason}\n引用：${r.quotes.joinToString(" / ")}" }
                    AlertDialog.Builder(this).setTitle("可核对的练习反馈").setMessage(detail).setPositiveButton("关闭", null).show()
                }
                if (evaluation.status == EvaluationStatus.ACTIVE || evaluation.status == EvaluationStatus.NEEDS_REVIEW)
                    button("我认为这次评价有问题") { engine.dispute(l.id, evaluation.id); render() }
            }
        }
        label("为什么安排这道题：${s.reason}", 13)
    }
    private fun profile() {
        val l = engine.learner()
        label("${l.name}的学习记录", 25, true)
        label("以下只是原型练习观察，不是整体水平分数。正式独立能力认证始终为0；聊天摘要不能增加能力计数。", 14)
        Profiles.build(l).forEach { p ->
            label(p.skill.title, 18, true)
            label("${p.stateLabel}\n提交 ${p.submitted} · 有效观察轮次 ${p.observations} · 未用提示的练习达标 ${p.unaidedPracticeMet} · 提示后达标 ${p.guidedPracticeMet}\n待核查 ${p.pendingReview} · 已见家族 ${p.families}", 14)
            p.dueAt?.let { label("建议再观察：${SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(it))}（试用间隔，可不按日历执行）", 12) }
        }
        val clues = l.memories.flatMap { it.clues }
        if (clues.isNotEmpty()) { label("讨论中的待验证线索", 19, true); clues.takeLast(8).forEach { label("${it.skill.title}：${it.description}\n原话：${it.quote}", 14) } }
        val last = l.sessions.lastOrNull { it.finishedAt != null }
        if (last != null) button("查看最近一轮原始记录") { selectedSession = last.id; navigate("train") }
        button("继续下一轮") { val s = engine.start(l.id, now()); selectedSession = s.id; navigate("train") }
        button("导出当前学习档案（不含API Key）") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "rehab-learning-export.json")
            }, 401)
        }
    }
    private fun settings() {
        label("连接与本地档案", 26, true)
        label("默认完全离线。填写Key不会自动发送记录；只有主动使用AI功能才调用配置的服务。", 14)
        val base = input("HTTPS API 基础地址", prefs.getString("base", "https://api.deepseek.com") ?: "")
        val model = input("服务商提供的模型名称", prefs.getString("model", "") ?: "")
        val secret = input(if (app.vault.exists()) "已保存Key；留空保留原Key" else "只在本机输入API Key")
        secret.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        secret.isSaveEnabled = false
        secret.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        val json = CheckBox(this).apply { text = "请求JSON模式（服务商需支持）"; isChecked = prefs.getBoolean("json", true); isEnabled = !app.busy.get() }; page.addView(json)
        val limit = input("滚动24小时请求上限（1–200）", prefs.getInt("limit", 30).toString()).apply { inputType = InputType.TYPE_CLASS_NUMBER }
        val consent = CheckBox(this).apply { text = "我了解：AI功能会把本次模拟作答、相关学习摘要和必要对话发给上述服务商；其保存政策需自行核对。"; isChecked = false; isEnabled = !app.busy.get() }; page.addView(consent)
        button("保存连接设置") {
            require(consent.isChecked) { "请先确认发送范围和服务地址。" }
            val config = ApiConfig(base.text.toString().trim(), model.text.toString().trim(), json.isChecked, limit.text.toString().toIntOrNull() ?: 0)
            config.endpoint()
            val key = secret.text.toString().trim()
            require(key.isNotBlank() || app.vault.exists()) { "请在本机填写API Key。" }
            val oldBase = prefs.getString("base", null)
            if (oldBase != null && java.net.URI(oldBase).authority != java.net.URI(config.baseUrl).authority)
                require(key.isNotEmpty()) { "服务域名或端口变化时，请重新输入适用于新服务的Key；不会自动发送原Key。" }
            if (key.isNotEmpty()) app.vault.write(key)
            check(prefs.edit().putString("base", config.baseUrl).putString("model", config.model).putBoolean("json", config.jsonMode)
                .putInt("limit", config.dailyLimit).putBoolean("consent", true).commit())
            secret.setText(""); Toast.makeText(this, "设置已保存；尚未发起模型请求。", Toast.LENGTH_LONG).show(); render()
        }
        button("删除本机Key / 回到离线模式") {
            app.vault.clear(); prefs.edit().putBoolean("consent", false).apply(); render()
        }
        label("调用记录：最近24小时 ${engine.snapshot().apiCalls.count { it.at > now() - DAY }} 次尝试。失败和重试也可能收费；这个上限不是精确费用预算。", 13)
        label("本地学习档案", 20, true)
        engine.snapshot().learners.forEach { l -> button("切换：${l.name}${if (l.id == engine.learner().id) "（当前）" else ""}") {
            engine.selectLearner(l.id); selectedSession = null; navigate("home")
        } }
        button("新建一个空白学习档案") { engine.createLearner(); selectedSession = null; navigate("home") }
        button("删除当前学习档案") {
            val id = engine.learner().id
            AlertDialog.Builder(this).setTitle("删除当前档案？").setMessage("原始作答、讨论和派生画像都会删除。API连接和全局调用计数不受影响。")
                .setPositiveButton("删除") { _, _ -> safe { engine.deleteLearner(id); selectedSession = null; navigate("home") } }
                .setNegativeButton("取消", null).show()
        }
        label("原型限制：仅模拟训练，无真实病例入口、3D、跨设备同步和医学能力认证。密钥本地加密，学习记录位于应用私有目录；不承诺被攻破设备上的绝对安全。", 13)
    }
    @Deprecated("Uses the platform document picker for this dependency-light prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 401 && resultCode == RESULT_OK) safe {
            val uri = data?.data ?: return@safe
            val l = engine.learner()
            val export = Snapshot(activeLearnerId = l.id, learners = listOf(l))
            contentResolver.openOutputStream(uri)?.use { it.write(Codec.encode(export).toByteArray(Charsets.UTF_8)) } ?: error("无法写入导出文件。")
            Toast.makeText(this, "已导出当前档案；不含Key和其他学习档案。", Toast.LENGTH_LONG).show()
        }
    }
    private fun hasConnection() = prefs.getBoolean("consent", false) && app.vault.exists() && !prefs.getString("model", "").isNullOrBlank()
    private fun requireConnection() { require(hasConnection()) { "请先到设置填写自己的API连接并确认数据发送范围；无需把Key发给任何人。" } }
    private fun launchApi(action: (AiGateway) -> Unit) {
        requireConnection()
        val config = ApiConfig(prefs.getString("base", "") ?: "", prefs.getString("model", "") ?: "", prefs.getBoolean("json", true), prefs.getInt("limit", 30))
        config.endpoint()
        val key = app.vault.read() ?: error("没有可用Key。")
        require(app.busy.compareAndSet(false, true)) { "已有请求正在执行；答案已经保存。" }
        observedBusy = true; render()
        // A single application-level worker survives activity recreation; no Activity writes the records.
        app.executor.execute {
            try { action(AiGateway(engine, config, key)) }
            catch (_: Exception) {
                // Do not surface arbitrary provider strings/exception payloads that may contain secrets.
                app.lastError = "请求或返回内容未通过校验，原始记录已保留。请检查地址、模型、Key及调用上限；可手动重试。"
            }
            finally { app.busy.set(false) }
        }
    }
}
