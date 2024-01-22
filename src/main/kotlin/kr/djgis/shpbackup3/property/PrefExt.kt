package kr.djgis.shpbackup3.property

import kr.djgis.shpbackup3.logger
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*

private val properties = Properties()

fun initPropertyFile(filePath: String): Properties {
    try {
        FileInputStream(filePath).use {
            properties.load(BufferedReader(InputStreamReader(it, "UTF-8")))
        }
    } catch (e: Exception) {
        logger.error(e.message)
    }
    return properties
}

infix fun Properties.of(key: String): String = this[key.toUpperCase()] as String

infix fun String.at(properties: Properties): String = properties[this] as String

infix fun Properties.numberOf(key: String): Int = (this[key.toUpperCase()] as String).toInt()

infix fun Properties.ask(key: String): Boolean = when (this[key.toUpperCase()] as String) {
    "TRUE", "true" -> true
    else -> false
}
