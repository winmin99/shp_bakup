package kr.djgis.shpbackup3

import kr.djgis.shpbackup3.property.Config
import kr.djgis.shpbackup3.property.Status
import kr.djgis.shpbackup3.property.initPropertyFile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

val logger: Logger = LoggerFactory.getLogger("kr.djgis.shpbackup3.MainKt")
lateinit var shpFiles: Array<File>
lateinit var tableList: Properties

// 백업 대상에서 제외하는 shp 파일명 (테이블 매핑 없음)
private val excludedNames = setOf("급수구역", "급수분구", "급수블럭")

fun main() {
    println("${Config.local} 작업 시작: ${Date()}")
    var failed = false
    try {
        setupProperties()
        runExecutor()
    } catch (e: Exception) {
        failed = true
        logger.error(e.message)
        println("${Config.local} 작업 중단: ${e.message ?: e.javaClass.simpleName}")
    } finally {
        println("${Config.local} 백업 종료")
        logger.info("——————END——————\n")
        logger.error("——————END——————\n")
        Status.tableCodeSet.clear()
    }
    // exitProcess 는 finally 를 건너뛰므로 정리 작업 이후에 호출
    if (failed) exitProcess(1)
}

private fun setupProperties() {
    tableList = initPropertyFile("./table.properties")
    // 테이블 목록에 없는 shp 파일명을 모아서 한번에 보고
    val unmappedNames = mutableListOf<String>()
    shpFiles = File(Config.filePath).listFiles { file ->
        // 확장자 shp 인 파일만 선택, 대소문자 구분X
        //   (제외 대상은 테이블 매핑이 없어도 되므로 매핑 검증보다 먼저 걸러냄)
        val isShp = file.extension.equals("shp", ignoreCase = true) && file.nameWithoutExtension !in excludedNames
        // shp 파일명이 테이블 목록에 키로 등록되어 있는지 확인
        //   (매핑 누락은 설정 오류이므로 목록만 수집하고, 필터링 후 백업 시작 전에 예외로 중단)
        if (isShp && tableList.getProperty(file.nameWithoutExtension).isNullOrEmpty()) {
            unmappedNames.add(file.name)
        }
        // 조건 3: shp 파일 크기가 100 byte를 넘는지 확인
        //        (shp 파일 헤더 100byte, 헤더만 있는 파일은 도형 없는 파일이므로 제외)
        isShp && file.length() > 100L
    } ?: emptyArray()
    if (unmappedNames.isNotEmpty()) {
        throw IllegalStateException(
            "테이블 목록에 없는 shp 파일 ${unmappedNames.size}개:\n" +
                unmappedNames.sorted().joinToString("\n") { " - $it" }
        )
    }
    if (shpFiles.isEmpty()) {
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
    }
}
