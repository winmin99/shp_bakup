package kr.djgis.shpbackup3

import kr.djgis.shpbackup3.property.Config
import kr.djgis.shpbackup3.property.Status
import kr.djgis.shpbackup3.property.at
import kr.djgis.shpbackup3.property.initPropertyFile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

val logger: Logger = LoggerFactory.getLogger("kr.djgis.shpbackup3.MainKt")
lateinit var shpFiles: Array<File>
lateinit var tableList: Properties

fun main() {
    println("${Config.local} 작업 시작: ${Date()}")
    try {
        setupProperties()
        runExecutor()
    } catch (e: Exception) {
        logger.error(e.message)
    }
}

private fun setupProperties() {
    tableList = initPropertyFile("./table.properties")
    shpFiles = File(Config.filePath).listFiles { file ->
        file.name.endsWith("shp") && (file.nameWithoutExtension at tableList != "") && (file.length() > 100L)
    }!!
    if (shpFiles.isNullOrEmpty()) {
        throw FileNotFoundException("${Config.local} 백업 대상 .shp 파일이 없음 (지정 폴더위치: ${Config.filePath})")
    }
}

@Throws(Throwable::class)
private fun runExecutor() {
    val tasks: MutableCollection<Callable<Nothing>> = ArrayList(shpFiles.size)
    shpFiles.forEach { file ->
        tasks.add(ExecuteFile(file))
    }
    val executor = Executors.newFixedThreadPool(shpFiles.size)
    executor.invokeAll(tasks)
    try {
        if (!executor.awaitTermination(2500, TimeUnit.MILLISECONDS)) {
            executor.shutdownNow()
        }
    } catch (e: InterruptedException) {
        executor.shutdownNow()
    } finally {
        println("${Config.local} 백업 종료")
        logger.info("——————END——————\n")
        logger.error("——————END——————\n")
        Status.tableCodeSet.clear()
    }
}
