plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
tasks.register<JavaExec>("regressionTest") {
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("cn.rehab.trainer.core.CoreTestsKt")
}
tasks.named("check") { dependsOn("regressionTest") }
