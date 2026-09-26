package cn.rehab.trainer.core

/** Fictional, non-clinically-reviewed process exercises. No diagnosis, prescription or certification. */
object Content {
    private fun item(
        id: String, skill: Skill, scenario: String, question: String, options: List<String>,
        correct: Int, hint: String, explanation: String, facts: Map<String, String> = emptyMap()
    ) = Blueprint(id, skill, id, scenario, question, facts,
        options.mapIndexed { n, text -> Option(('A'.code + n).toChar().toString(), text) },
        ('A'.code + correct).toChar().toString(),
        listOf(Criterion("decision", "在当前信息范围内提出有理由的下一步，不把未知当成已知。"),
            Criterion("reason", "说明为何选择该步骤，以及目前还不能得出的结论。")), hint, explanation)

    val bank: List<Blueprint> = listOf(
        item("goals-function", Skill.GOALS,
            "虚构学习情境：一位练习者说，希望‘恢复得好一点’。目前没有进一步的目标描述。",
            "先做什么能让后续计划更有针对性？选择或写出下一步，并说明理由。",
            listOf("立刻安排统一动作清单", "了解他希望重新完成的具体活动，以及当前限制", "直接把目标设为每天训练一小时"), 1,
            "目前的目标能不能被双方清楚地理解和观察？",
            "本题练习先把宽泛愿望转成可讨论的活动目标。缺少目标信息时，不应替对方决定活动或训练量。",
            mapOf("goal" to "希望能重新参加周末的休闲活动，具体活动和要求仍待确认。")),
        item("goals-tradeoff", Skill.GOALS,
            "虚构学习情境：一位练习者提出两个不同的活动目标，但本周可投入的时间有限。尚不知道哪个更重要。",
            "如何确定当前优先级？",
            listOf("共同澄清优先目标和现实约束，再讨论取舍", "直接选难度最高的目标", "既然不能全部完成，就停止讨论"), 0,
            "优先级是否需要结合这个人的选择，而不是替他作决定？",
            "本题没有预先指定哪个活动更重要。应先澄清偏好和约束；不能从有限信息推出唯一训练安排。"),
        item("information-request", Skill.INFORMATION,
            "虚构学习情境：对方说‘练完不太好’，没有进一步描述。",
            "在提出调整前，最有价值的下一步是什么？",
            listOf("把所有练习都换掉", "假定练习量不足并加量", "了解‘不太好’指什么、何时出现，以及实际做了什么"), 2,
            "这句话包含了足够清楚、可解释的信息吗？",
            "这里先练习澄清含糊表述。没有核实的描述不能自动变成某种病因，也不足以支持增加或更换训练。",
            mapOf("record" to "目前没有完整的训练执行记录；这条未知需要保留。")),
        item("information-observation", Skill.INFORMATION,
            "虚构学习情境：视频里看到一个动作与示范不同。目前只有一次录像，没有其他评估资料。",
            "怎样表述当前观察比较合适？",
            listOf("动作不同就证明某个组织受伤", "描述可见差异，说明原因尚不确定并考虑补充信息", "从一次视频确定所有训练禁忌"), 1,
            "观察到的现象与对原因的解释，是同一层信息吗？",
            "题目只提供了可观察的差异，不能把差异直接当成结构损伤。这里练习区分事实、假设与未知。"),
        item("hypothesis-disconfirm", Skill.HYPOTHESES,
            "虚构学习情境：你对练习者的困难提出了一个初步解释，但信息尚不充分。",
            "下一步怎样检查这个解释，而不是只找支持它的信息？",
            listOf("列出支持和反对的现有信息，并说明什么新证据会改变判断", "只保留符合初步解释的描述", "为了完整，编造一个确定病因"), 0,
            "什么信息可能让你改变当前看法？",
            "初步解释是待检验的假设。本题看重能否提出验证或反驳的条件，并保留信息不足，而非要求猜出病名。"),
        item("hypothesis-new-evidence", Skill.HYPOTHESES,
            "虚构学习情境：出现一条与原解释不一致的新信息。尚未核实新信息的来源和测量条件。",
            "你会怎样处理？",
            listOf("忽略不一致信息", "立即认定所有原判断都错了", "先核对新信息质量，再比较它对不同解释的影响"), 2,
            "既不要固守，也不要没有核查就全盘推翻。",
            "本题练习对证据质量和解释进行分开核查。新信息不自动证明任何一种解释，需要说明它能改变什么。"),
        item("planning-feasible", Skill.PLANNING,
            "虚构学习情境：一个计划依赖专门设备，但练习者在家没有该设备。具体替代安排尚未评估。",
            "如何让计划变得可执行？",
            listOf("保持原计划并责怪对方不配合", "核实可用条件，围绕原目标讨论适当替代，并设定后续观察", "随便找一个动作替代，认为效果必然相同"), 1,
            "需要保留的是训练目标，还是某一件设备？",
            "这里练习核实执行条件、解释替代方案与后续观察。题目未提供足够信息来指定实际动作或剂量。"),
        item("planning-monitor", Skill.PLANNING,
            "虚构学习情境：一份计划只有动作名称，没有说明执行方式、观察项目或何时复评。",
            "最需要补充什么？",
            listOf("围绕目标澄清执行安排、需观察的反应与复评条件", "只增加更多动作名称", "承诺不论反应如何都一直执行"), 0,
            "除了‘做什么’，对方是否知道怎样执行、观察什么、何时重新讨论？",
            "计划的可执行性需要明确安排与反馈。具体剂量和安全边界要依据适当评估，本题不生成通用处方。"),
        item("reassess-adherence", Skill.REASSESSMENT,
            "虚构学习情境：对方说‘没效果’，要求换练习。实际执行情况和目标记录尚未核实。",
            "在评价整个计划是否无效前，你会先核实什么？",
            listOf("先认定计划完全无效", "先认定是对方没有认真练", "核实计划、实际执行和目标结果，区分未知与已知"), 2,
            "计划写了什么、实际做了什么、结果怎么判断，是否都清楚？",
            "不能从一句‘没效果’直接推定方案无效，也不能推定执行问题。先补齐能够支持判断的信息。",
            mapOf("execution" to "实际只完成计划中的一部分，原因尚未核实。", "outcome" to "目前没有按约定记录目标活动；还不能完成可靠比较。")),
        item("reassess-verified", Skill.REASSESSMENT,
            "虚构学习情境：执行情况已核实，结果记录条件可比较，但预先约定的目标变化没有出现。",
            "此时为什么不能只重复说‘先查执行’？下一步怎样重新考虑？",
            listOf("不管资料怎样，都归因于没有执行", "承认已核实的信息，重新检查目标、假设和方案是否需要调整", "立即编造一个隐藏病因解释结果"), 1,
            "哪些环节已经核实了？下一步应针对尚未解决的问题。",
            "一个原则不能机械套用。执行和记录已核实后，应说明还有哪些假设或安排需要重新评估，而不是凭空添加病因。"),
        item("communication-barrier", Skill.COMMUNICATION,
            "虚构学习情境：对方说‘我做不到这个安排’。还不知道具体障碍。",
            "你会怎样回应？",
            listOf("先了解时间、理解或其他障碍，再共同调整可行安排", "告诉对方必须无条件服从", "默认对方懒惰"), 0,
            "‘做不到’背后可能是什么？目前是否有依据给人贴标签？",
            "先澄清障碍、共同讨论安排。这里不评价对方人格，也不把解释困难自动当作不配合。"),
        item("communication-understanding", Skill.COMMUNICATION,
            "虚构学习情境：解释完计划后，对方说‘懂了’。你尚未确认双方理解是否一致。",
            "怎样进一步确认而不把沟通变成刁难？",
            listOf("把‘懂了’自动当作独立操作合格", "要求背诵全部专业术语", "邀请对方用自己的话说说下一步，温和核对关键点"), 2,
            "重复专业术语与理解执行安排，有什么不同？",
            "可以核对关键理解并继续澄清。一次口头回应仍不能证明现场操作或长期执行合格。"),
        item("safety-scope", Skill.SAFETY,
            "虚构学习情境：问题超出当前学习者能够可靠判断的范围，是否适合继续训练尚不清楚。",
            "此时怎样处理未知和自身边界？",
            listOf("为了显得专业，编造确定结论", "明确限制，考虑需要的适当专业评估，不贸然给执行指令", "不论情况如何都继续加量"), 1,
            "承认未知与停止帮助并不是同一件事。",
            "本题仅练习识别工作边界。它没有提供真实症状或具体分诊标准，不能用于判断真实患者的紧急程度。"),
        item("safety-unexpected", Skill.SAFETY,
            "虚构学习情境：模拟记录出现了原计划没有预料的信息，是否影响继续执行尚未核实。",
            "怎样避免在未知条件下机械执行？",
            listOf("不管信息是什么都照旧", "直接断定必然是严重疾病", "先确认信息及其相关性，必要时暂停原决定并寻求适当评估"), 2,
            "既不能忽视变化，也不能在没有依据时断言严重结论。",
            "这里练习在新信息下重新核对条件，不提供实际诊断、急救或个体化训练指令。"),
        item("evidence-check", Skill.EVIDENCE,
            "虚构学习情境：AI给出一段说得很肯定的建议，但没有提供可核对依据。",
            "怎样使用这段建议？",
            listOf("核查依据、适用范围和未知，不因语气肯定就直接执行", "把自信语气当成证据", "把AI文字自动写入已验证知识库"), 0,
            "回答听起来确定，是否等于依据可以核对？",
            "本题关注可核对依据与适用范围。AI输出、用户经验和本演示题都不因文字流畅而自动变成可靠临床结论。"),
        item("evidence-conflict", Skill.EVIDENCE,
            "虚构学习情境：两份资料看似给出不同建议，但适用对象和条件还未核对。",
            "如何开始比较？",
            listOf("任意选择符合自己看法的一份", "比较适用人群、问题、方法和限制，保留无法确定之处", "直接认为所有建议都无效"), 1,
            "表面相反的建议，是否可能回答了不同问题？",
            "先比较资料回答的问题和适用条件。不能在没有核对原文时宣称哪一份必然正确。")
    )
    fun get(id: String): Blueprint = bank.firstOrNull { it.id == id } ?: error("Unknown content ID")
}

/** Explainable cold-start policy; no job title, demographics or self-rating required. */
object Scheduler {
    fun select(l: Learner, now: Long, preferred: Skill? = null): Selection {
        val profiles = Profiles.build(l).associateBy { it.skill }
        val last = l.sessions.lastOrNull()
        val unknown = Skill.values().filter { profiles.getValue(it).observations == 0 }
        val leastExplored = unknown.minByOrNull { s -> l.sessions.count { it.skill == s } }
        val recentTwoSame = l.sessions.takeLast(2).let { it.size == 2 && it[0].skill == it[1].skill }
        val lastSignal = last?.let { profiles.getValue(it.skill).latest }
        val due = profiles.values.filter { it.dueAt != null && it.dueAt <= now }
            .filter { p -> last?.skill != p.skill || !recentTwoSame }.minByOrNull { it.dueAt!! }
        val clue = l.memories.flatMap { it.clues }.lastOrNull { c ->
            val batchAt = l.memories.last { it.clues.contains(c) }.at
            l.sessions.none { it.skill == c.skill && it.createdAt >= batchAt }
        }
        val skill: Skill
        val mode: Mode
        val reason: String
        when {
            preferred != null -> { skill = preferred; mode = Mode.PRACTICE; reason = "你选择了这个主题；不把选择本身当成能力证据。" }
            l.sessions.isEmpty() -> { skill = Skill.GOALS; mode = Mode.EXPLORE; reason = "尚无作答记录，先从一个常见决策开始观察，不预设初级或高级。" }
            l.sessions.size % 3 == 0 && leastExplored != null -> { skill = leastExplored; mode = Mode.EXPLORE; reason = "保留探索机会，避免被早期判断限制。" }
            due != null -> { skill = due.skill; mode = Mode.REVIEW; reason = "到了试用复习间隔，收集新表现；到期不代表已掌握或退步。" }
            last != null && lastSignal in setOf(Outcome.NOT_MET, Outcome.PARTIAL) && !recentTwoSame -> { skill = last.skill; mode = Mode.PRACTICE; reason = "上一次练习出现待练线索；换一个情境，先检验而不是给整个人定级。" }
            clue != null -> { skill = clue.skill; mode = Mode.EXPLORE; reason = "讨论出现一条待验证线索；这次只是进一步观察，不先扣分。" }
            leastExplored != null -> { skill = leastExplored; mode = Mode.EXPLORE; reason = "这个能力证据仍不足，补充一次观察。" }
            else -> { skill = Skill.values().filter { it != last?.skill }.minByOrNull { s -> l.sessions.count { it.skill == s } } ?: Skill.GOALS; mode = Mode.REVIEW; reason = "交错练习，检查不同情境中的表现；不追求无限加难。" }
        }
        val candidates = Content.bank.filter { it.skill == skill }
        val blueprint = candidates.minWith(compareBy<Blueprint> { b -> l.sessions.count { it.family == b.family } }
            .thenBy { b -> if (last?.blueprintId == b.id) 1 else 0 })
        return Selection(blueprint, mode, reason)
    }
}
