package kr.djgis.shpbackup3.property

object Config {

    lateinit var local: String
    lateinit var dbCode: String
    lateinit var filePath: String
    lateinit var mHost: String
    lateinit var mPort: String
    lateinit var mUserName: String
    lateinit var mKey: String
    lateinit var pHost: String
    lateinit var pPort: String
    lateinit var pUserName: String
    lateinit var pKey: String
    lateinit var origin: String
    var isPostQuery: Boolean = false
    var isPreQuery: Boolean = false
    var maxTotal: Int = -1
    var maxIdle: Int = -1

    private val property = initPropertyFile("./config.properties")

    init {
        this.apply {
            local = property of "local"
            dbCode = property of "dbCode"
            filePath = property of "shp_path"
            mHost = property of "mysql_host"
            mPort = property of "mysql_port"
            mUserName = property of "mysql_username"
            mKey = property of "mysql_key"
            pHost = property of "postgres_host"
            pPort = property of "postgres_port"
            pUserName = property of "postgres_username"
            pKey = property of "postgres_key"
            origin = property of "geo_origin"
            isPostQuery = property ask "use_post_query"
            isPreQuery = property ask "use_pre_query"
            maxTotal = property numberOf "max_total"
            maxIdle = property numberOf "max_idle"
        }
    }
}
