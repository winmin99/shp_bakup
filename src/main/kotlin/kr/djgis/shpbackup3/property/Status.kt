package kr.djgis.shpbackup3.property

import java.util.*
import kotlin.collections.HashSet

object Status {

    var tableCodeSet: MutableSet<String> = Collections.synchronizedSet(HashSet<String>())
}
