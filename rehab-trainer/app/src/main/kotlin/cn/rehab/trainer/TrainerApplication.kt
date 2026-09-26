package cn.rehab.trainer

import android.app.Application
import cn.rehab.trainer.core.Engine
import cn.rehab.trainer.core.FileRepository
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TrainerApplication : Application() {
    val executor = Executors.newSingleThreadExecutor()
    val busy = AtomicBoolean(false)
    // The lazy initialization can fail visibly without overwriting a corrupt/future-version snapshot.
    val engine: Engine by lazy { Engine(FileRepository(File(filesDir, "learning-v1.json"))) }
    val vault: KeyVault by lazy { KeyVault(this) }
    var lastError: String? = null
}
