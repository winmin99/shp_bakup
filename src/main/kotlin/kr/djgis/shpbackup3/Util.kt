package kr.djgis.shpbackup3

import kr.djgis.shpbackup3.network.PostgresConnectionPool
import kr.djgis.shpbackup3.property.Config
import org.geotools.data.shapefile.ShapefileDataStore
import org.geotools.data.simple.SimpleFeatureCollection
import org.opengis.feature.simple.SimpleFeature
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.charset.Charset
import java.sql.Connection
import java.sql.Types
import java.text.ParseException
import java.text.SimpleDateFormat

val digitMatchPattern = "[0-9]+".toRegex()

@Throws(Exception::class)
inline fun <R> Connection.open(rollback: Boolean = false, block: (Connection) -> R): R {
    try {
        if (rollback) {
            this.autoCommit = false
            this.setSavepoint()
        }
        return block(this)
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    } finally {
        close()
    }
}

fun getFeatureCollection(file: File): SimpleFeatureCollection {
    val featureCollection: SimpleFeatureCollection
    val store = ShapefileDataStore(file.toURI().toURL())
    try {
        store.charset = Charset.forName("MS949")
        featureCollection = store.featureSource.features
    } finally {
        store.featureSource?.unLockFeatures()
        store.dispose()
    }
    return featureCollection
}

fun setupFtrIdn(feature: SimpleFeature): String {
    return when (feature.getProperty("관리번호")) {
        null -> feature.getProperty("FTR_IDN").value.toString()
        else -> feature.getProperty("관리번호").value.toString()
    }
}

fun setupCoordinate(feature: SimpleFeature): String {
    return when {
        "MULTI" in "${feature.getAttribute(0)}" -> {
            when {
                "MULTILINESTRING" in "${feature.getAttribute(0)}" -> {
                    "ST_AsText(ST_LineMerge(ST_GeomFromText('${feature.getAttribute(0)}')))"
                }
                else -> "'${feature.getAttribute(0)}'"
            }
        }
        else -> "'MULTI${feature.getAttribute(0)}'"
    }
}

fun setupQuery(fileName: String, tableCode: String, ftrIdn: String): String {
    return when (tableCode) {
        "wtl_pipe_lm", "wtl_sply_ls", "wtl_stpi_ps",
        "wtl_valv_ps", "wtl_fire_ps", "wtl_spcnt_as", "wtl_scvst_ps" -> {
            "SELECT * FROM $tableCode WHERE ftr_idn=$ftrIdn AND LAYER='$fileName'"
        }
        else -> {
            "SELECT * FROM $tableCode WHERE ftr_idn=$ftrIdn"
        }
    }
}

fun selectFtrIdn(message: String): Int {
    val matchResult = digitMatchPattern.find(input = message)?.value
    return if (matchResult !== null) matchResult.toInt() else 0
}

fun Connection.reportResults(fileName: String, rowCount: Int, errorCount: Int) {
    val paddedFileName = fileName.padEnd(8 - fileName.length)
    val validCount = (rowCount - errorCount).toString().padEnd(4)
    val invalidCount = errorCount.toString().padEnd(2)
    when (errorCount) {
        0 -> {
            println("${Config.local} 정상 완료: $paddedFileName")
            logger.info("$paddedFileName $validCount 건")
        }
        in 1 until rowCount -> {
            println("${Config.local} 일부 에러: $paddedFileName (오류 $errorCount 건)")
            logger.info("$paddedFileName $validCount 건 → 오류 $invalidCount 건")
        }
        else -> {
            this.rollback()
            println("${Config.local} 전체 에러: $paddedFileName ($errorCount 건 오류)...백업 취소 및 롤백 실행")
        }
    }
}

fun executePostQuery() {
    val pConn1 = PostgresConnectionPool.getConnection()
    pConn1.open {
        try {
            it.createStatement().execute("VACUUM verbose analyze")
        } catch (e: Exception) {
            logger.error(e.message)
        }
    }.also {
        val pConn2 = PostgresConnectionPool.getConnection()
        pConn2.open(true) { postgres ->
            postgres.createStatement().use { pStmt ->
                BufferedReader(FileReader("./postquery.txt")).use {
                    try {
                        var line: String? = it.readLine()
                        while (line != null) {
                            println(line)
                            line = it.readLine()
                            pStmt.execute(line)
                        }
                    } catch (e: Exception) {
                        logger.error(e.message)
                        postgres.rollback()
                        println("${Config.local} 업데이트 롤백")
                        return@executePostQuery
                    } finally {
                        postgres.commit()
                    }
                }
                println("${Config.local} 업데이트 완료")
            }
        }
    }
}

class ValueField(private val columnType: Any?, private val columnValue: String?) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    /**
     * @return columnType 을 받아 SQL query syntax 에 맞게 가공
     */
    val value: String
        @Throws(ParseException::class)
        get() {
            return when {
                columnValue != null -> {
                    val dataFormat: String
                    var dataValue: String = columnValue
                    when (columnType) {
                        Types.TIMESTAMP, Types.DATE -> {
                            dataFormat = "'%s'"
                            dataValue = dateFormat.format(dateFormat.parse(columnValue))
                        }
                        Types.INTEGER, Types.DECIMAL, Types.DOUBLE -> {
                            dataFormat = "%s"
                        }
                        Types.CHAR, Types.VARCHAR, Types.TIME -> {
                            dataFormat = "'%s'"
                        }
                        "\"EDDATE\"" -> {
                            dataFormat = if (columnValue == "") {
                                return "null"
                            } else "'%s'"
                        }
                        else -> {
                            dataFormat = "'%s'"
                        }
                    }
                    return String.format(dataFormat, dataValue)
                }
                else -> {
                    "null"
                }
            }
        }
}
