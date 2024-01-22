package kr.djgis.shpbackup3.network

import kr.djgis.shpbackup3.property.Config
import org.apache.commons.dbcp2.BasicDataSource
import java.sql.Connection
import kotlin.system.exitProcess

object PostgresConnectionPool {

    private val source = BasicDataSource()

    init {
        source.apply {
            driverClassName = "org.postgresql.Driver"
            url = "jdbc:postgresql://${Config.pHost}:${Config.pPort}/${Config.dbCode}"
            username = Config.pUserName
            password = Config.pKey
            maxIdle = Config.maxIdle
            maxTotal = Config.maxTotal
            minEvictableIdleTimeMillis = -1
            validationQuery = "SELECT 1"
            validationQueryTimeout = 10
        }
    }

    @Throws(Throwable::class)
    fun getConnection(): Connection {
        return try {
            source.connection
        } catch (e: Exception) {
            e.printStackTrace()
            exitProcess(401)
        }
    }
}
