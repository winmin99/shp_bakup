package kr.djgis.shpbackup3

import kr.djgis.shpbackup3.network.MysqlConnectionPool
import kr.djgis.shpbackup3.network.PostgresConnectionPool
import kr.djgis.shpbackup3.property.Config
import kr.djgis.shpbackup3.property.Status
import kr.djgis.shpbackup3.property.at
import org.opengis.feature.simple.SimpleFeature
import org.postgresql.util.PSQLException
import java.io.File
import java.sql.ResultSet
import java.sql.SQLSyntaxErrorException
import java.util.concurrent.Callable

class ExecuteFile(private val file: File) : Callable<Nothing> {

    private val errorList = mutableListOf<String>()
    private var errorCount = 0
    private val fileName = file.nameWithoutExtension
    private val tableCode = fileName at tableList
    private val shpOnlyTable =
        setOf(
            "geo_line_as", // 지적선
            "swl_cap_ps", // 하수관말
            "swl_hmpipe_ls", // 가정내오수관 (임의 테이블명)
            "swl_pipe_as", // 면형관
            "wtl_cap_ps", // 상수관말
            "wtl_pipe_dir_ps", // 물방향
            "wtl_prme_ps", // 수압측정
            "wtl_taper_ps", // 편락관
            "wtl_userlabel_as", // 다목적영역 (임의 테이블명)
            "wtl_userlabel_ps", // 사용자주기 (임의 테이블명)
            "wtl_wspipe_ls", // 수자원광역관 (임의 테이블명)
            "bml_badm_as", // 법정동경계
            "bml_hadm_as" // 행정동경계
        )

    @Throws(Throwable::class)
    override fun call(): Nothing? {
        if (shpOnlyTable.contains(tableCode)) {
            ExecuteShp(file).run()
            return null
        }
        val mConn = MysqlConnectionPool.getConnection()
        val pConn = PostgresConnectionPool.getConnection()
        mConn.createStatement().use { mStmt ->
            pConn.open(true) { pConnection ->
                pConnection.createStatement().use { pStmt ->
                    val featureCollection = getFeatureCollection(file)
                    val features = arrayOfNulls<SimpleFeature>(featureCollection.size())
                    featureCollection.toArray(features)
                    val metaData = mStmt.executeQuery("SELECT * FROM $tableCode LIMIT 0").metaData
                    val columnCount = metaData.columnCount
                    val columnNames = arrayOfNulls<String>(columnCount + 1)
                    columnNames[0] = "\"geom\""
                    for (i in 1..columnCount) {
                        columnNames[i] = "\"${metaData.getColumnName(i)}\""
                    }
                    val columnList = columnNames.joinToString(",").toLowerCase().trim()
//                    val columnList = columnNames.joinToString(",").toUpperCase().trim()
                    if (Status.tableCodeSet.add(tableCode)) {
                        pStmt.execute("TRUNCATE TABLE $tableCode")
//                        pStmt.execute("SELECT SETVAL('public.${tableCode}_id_seq',1,false)")
                    }
                    features.forEach feature@{ feature ->
                        val ftrIdn = setupFtrIdn(feature!!)
                        val resultSet: ResultSet
                        try {
                            val selectQuery = setupQuery(
                                fileName = fileName,
                                tableCode = tableCode,
                                ftrIdn = ftrIdn
                            )
                            resultSet = mStmt.executeQuery(selectQuery)
                            if (!resultSet.isBeforeFirst) {
                                errorList.add("$ftrIdn  MySQL 데이터베이스에서 데이터를 찾을 수 없습니다.")
                                return@feature
                            }
                        } catch (e: SQLSyntaxErrorException) {
                            errorList.add("$ftrIdn ${e.message}")
                            return@feature
                        }
                        val columnValues = arrayOfNulls<String>(columnCount + 1)
                        val coordinate = setupCoordinate(feature)
                        columnValues[0] = "ST_GeomFromText($coordinate, ${Config.origin})"
                        while (resultSet.next()) for (j in 1..columnCount) {
                            var resultString = resultSet.getString(j)
                            if (resultString != null) {
                                resultString = resultString.replace("'", "''")
                            }
                            val field = ValueField(metaData.getColumnType(j), resultString)
                            columnValues[j] = field.value
                        }
                        val valueList = columnValues.joinToString(",").trim()
                        resultSet.close()
                        val insertQuery = "INSERT INTO $tableCode ($columnList) VALUES ($valueList)"
                        if (fileName == "마을상수구역") insertQuery.replace("wsg_cde", "wsb_cde")
                        try {
                            pStmt.execute(insertQuery)
                        } catch (e: PSQLException) {
                            errorList.add(
                                "$ftrIdn ${e.message}"
                                    .replace("\n", " ")
                                    .replace("오류:", "")
                                    .replace("Detail:", "→")
                            )
                        } finally {
                            pConnection.commit()
                        }
                    }
                    if (errorList.size > 0) {
                        errorCount = errorList.size
                        errorList.sortBy { selectFtrIdn(it) }
                        errorList.add(0, "$fileName(${tableCode.toUpperCase()}): ${errorList.size} 건")
                        logger.error(errorList.joinToString("\n"))
                    }
                    pConnection.reportResults(
                        fileName = fileName,
                        rowCount = features.size,
                        errorCount = errorCount
                    )
                }
            }
        }
        return null
    }
}
