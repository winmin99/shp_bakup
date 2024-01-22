package kr.djgis.shpbackup3

import kr.djgis.shpbackup3.network.PostgresConnectionPool
import kr.djgis.shpbackup3.property.Config
import kr.djgis.shpbackup3.property.Status
import kr.djgis.shpbackup3.property.at
import org.opengis.feature.simple.SimpleFeature
import org.postgresql.util.PSQLException
import java.io.File

class ExecuteShp(private val file: File) {

    private val errorList = mutableListOf<String>()
    private var errorCount = 0
    private val fileName = file.nameWithoutExtension
    private val tableCode = fileName at tableList

    @Throws(Throwable::class)
    fun run() {
        val pConn = PostgresConnectionPool.getConnection()
        pConn.open(true) { pConnection ->
            pConnection.createStatement().use { pStmt ->
                val featureCollection = getFeatureCollection(file)
                val features = arrayOfNulls<SimpleFeature>(featureCollection.size())
                featureCollection.toArray(features)
                val metaData = featureCollection.schema.attributeDescriptors
                val columnCount = metaData.size
                val columnNames = arrayOfNulls<String>(columnCount)
                columnNames[0] = "\"geom\""
                for (i in 1 until columnCount) {
                    columnNames[i] = "\"${metaData[i].localName}\""
                }
                val columnList = columnNames.joinToString(",").toLowerCase().trim()
                if (Status.tableCodeSet.add(tableCode)) {
                    pStmt.execute("TRUNCATE TABLE $tableCode")
//                    pStmt.execute("SELECT SETVAL('public.${tableCode}_id_seq',1,false)")
                }
                features.forEach feature@{ feature ->
                    val columnValues = arrayOfNulls<String>(columnCount)
                    val coordinate = setupCoordinate(feature!!)
                    columnValues[0] = "ST_GeomFromText($coordinate, ${Config.origin})"
                    for (j in 1 until columnCount) {
                        var attribute = feature.getAttribute(j)
                        if (attribute != null) {
                            attribute = attribute.toString().replace("'", "''")
                        } else {
                            attribute = attribute.toString()
                        }
                        val field = ValueField(columnNames[j], attribute)
                        columnValues[j] = field.value
                    }
                    val valueList = columnValues.joinToString(",").trim()
                    val insertQuery = "INSERT INTO $tableCode ($columnList) VALUES ($valueList)"
                    try {
                        pStmt.execute(insertQuery)
                    } catch (e: PSQLException) {
                        errorList.add(
                            "${e.message}"
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
}
