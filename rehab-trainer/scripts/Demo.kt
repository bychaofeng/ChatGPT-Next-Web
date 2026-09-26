import cn.rehab.trainer.core.*
import java.io.File
import java.nio.file.Files

fun main(args: Array<String>) {
    if ("--smoke" in args) { smoke(); return }
    val file = if (args.size == 2 && args[0] == "--data") File(args[1]) else File(System.getProperty("user.home"), "rehab-trainer-demo.json")
    val engine = Engine(FileRepository(file))
    println("康复决策练习 v0.3 · 离线演示 · 不是医学评估\n记录文件：${file.absolutePath}\n无需填写学习背景。所有内容是虚构流程题。")
    while (true) {
        val learner = engine.learner()
        val s = engine.start(learner.id, System.currentTimeMillis())
        println("\n[${learner.name}] ${s.scenario}\n${s.question}")
        s.definition.options.forEach { println("${it.id}. ${it.text}") }
        println("输入 A/B/C；H=提示；F=已设定病例资料；P=画像；N=下一题；U=新档案；Q=退出并保存")
        when (val command = readlnOrNull()?.trim()?.uppercase() ?: "Q") {
            "Q" -> return
            "P" -> Profiles.build(learner).forEach { println("${it.skill.title}：${it.stateLabel} / 观察${it.observations} / 正式认证${it.verifiedIndependent}") }
            "U" -> { engine.createLearner(); println("新档案从未知状态开始，原档案保留。") }
            "H" -> { engine.expose(learner.id, s.id, "hint", System.currentTimeMillis()); println(s.definition.hint) }
            "F" -> if (s.definition.facts.isEmpty()) println("没有额外设定的信息。") else s.definition.facts.forEach { (id, _) -> println(engine.revealFact(learner.id, s.id, id)) }
            "N" -> engine.finish(learner.id, s.id, System.currentTimeMillis(), true)
            "A", "B", "C" -> {
                engine.draft(learner.id, s.id, "", command)
                val a = engine.submit(learner.id, s.id, false, System.currentTimeMillis())
                val e = engine.gradeDemo(learner.id, a.id, System.currentTimeMillis())
                engine.expose(learner.id, s.id, "feedback", System.currentTimeMillis(), "feedback:${e.id}")
                println("${e.feedback}\n条件：${if (a.assisted) "获得帮助后" else "本次未用提示"}；仅是演示选择，不评估理由和实际操作。\n下一题由你输入N继续。")
            }
            else -> println("请使用上方命令。")
        }
    }
}
private fun smoke() {
    val dir = Files.createTempDirectory("rehab-smoke").toFile()
    try {
        val file = File(dir, "state.json"); val engine = Engine(FileRepository(file)); var time = 1000L
        val aId = engine.learner().id; val first = engine.start(aId, time++)
        engine.draft(aId, first.id, "", first.definition.correctOption)
        val attempt = engine.submit(aId, first.id, false, time++); engine.gradeDemo(aId, attempt.id, time++); engine.finish(aId, first.id, time++)
        val strongNext = engine.start(aId, time++)
        val bId = engine.createLearner(); val second = engine.start(bId, time++)
        engine.draft(bId, second.id, "", second.definition.options.first { it.id != second.definition.correctOption }.id)
        val wrong = engine.submit(bId, second.id, false, time++); engine.gradeDemo(bId, wrong.id, time++); engine.finish(bId, second.id, time++)
        val supportNext = engine.start(bId, time)
        check(strongNext.skill != supportNext.skill)
        check(Engine(FileRepository(file)).snapshot() == engine.snapshot())
        println("PASS：两个新用户都未填写背景表。")
        println("用户A下一题：${strongNext.skill.title}；${strongNext.reason}")
        println("用户B下一题：${supportNext.skill.title}；${supportNext.reason}")
        println("PASS：同一套调度规则根据不同作答选择不同路径。")
        println("PASS：关闭并重新加载本地文件，任务、作答、画像依据完全一致。")
        println("PASS：演示观察没有被标成正式能力认证；全部模拟，不是学习效果验证。")
    } finally { dir.deleteRecursively() }
}
