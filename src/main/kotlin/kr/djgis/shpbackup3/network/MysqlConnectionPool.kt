package kr.djgis.shpbackup3.network

import kr.djgis.shpbackup3.property.Config
import org.apache.commons.dbcp2.BasicDataSource
import java.sql.Connection
import kotlin.system.exitProcess

object MysqlConnectionPool {

    private val source = BasicDataSource()

    init {
        source.apply {
            driverClassName = "com.mysql.cj.jdbc.Driver"
            url = "jdbc:mysql://${Config.mHost}:${Config.mPort}/${Config.dbCode}?autoReconnect=true&serverTimezone=UTC"
            username = Config.mUserName
            password = Config.mKey
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
