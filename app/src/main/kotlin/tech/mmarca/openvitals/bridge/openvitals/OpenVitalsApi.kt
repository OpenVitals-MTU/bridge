package tech.mmarca.openvitals.bridge.openvitals

import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate

/**
 * The names of the OpenVitals data sharing API, copied from its contract in
 * the mobile-app repo (`sharing/contract/DataSharingContract.kt`). Keep the two in step.
 */
object OpenVitalsApi {
    /** The contract version this bridge was written against. `/groups` reports the app's. */
    const val API_VERSION = 1

    /** Opens OpenVitals' sharing settings, where the user turns sharing on. */
    const val ACTION_MANAGE_DATA_SHARING = "tech.mmarca.openvitals.action.MANAGE_DATA_SHARING"

    const val EXTRA_STATUS = "status"
    const val EXTRA_HISTORY_START = "history_start"

    /** The API's longest daily and session range, both days counted. */
    const val MAX_RANGE_DAYS = 366L

    /** The API's longest sample range. */
    const val MAX_SAMPLE_RANGE_HOURS = 48L

    fun authority(packageName: String): String = "$packageName.data"

    fun permission(packageName: String, group: OpenVitalsGroup): String =
        "$packageName.permission.READ_${group.name}"
}

/** The OpenVitals builds the bridge can read from, preferred first. */
enum class OpenVitalsBuild(val packageName: String) {
    RELEASE("tech.mmarca.openvitals"),
    DEBUG("tech.mmarca.openvitals.debug"),
    CI("tech.mmarca.openvitals.ci"),
}

/** One permission each. [path] is the first URI segment. */
enum class OpenVitalsGroup(val path: String, val hasSessions: Boolean = false) {
    ACTIVITY("activity"),
    WORKOUTS("workouts", hasSessions = true),
    ROUTES("routes"),
    SLEEP("sleep", hasSessions = true),
    HEART("heart"),
    VITALS("vitals"),
    BODY("body"),
    NUTRITION("nutrition"),
    RECOVERY("recovery", hasSessions = true),
    CYCLE("cycle"),
    SEXUAL_ACTIVITY("sexual_activity"),
    ;

    /** Routes have no daily or sample table; a route is read by workout. */
    val hasDailyAndSamples: Boolean get() = this != ROUTES

    companion object {
        fun fromPath(path: String): OpenVitalsGroup? = entries.firstOrNull { it.path == path }
    }
}

/** The API's paths, relative to its authority. */
object OpenVitalsPaths {
    const val GROUPS = "groups"
    const val METRICS = "metrics"

    fun daily(group: OpenVitalsGroup, start: LocalDate, end: LocalDate): String =
        "${group.path}/daily?start=$start&end=$end"

    fun samples(group: OpenVitalsGroup, start: Instant, end: Instant): String =
        "${group.path}/samples?start=${start.toEpochMilli()}&end=${end.toEpochMilli()}"

    fun sessions(group: OpenVitalsGroup, start: LocalDate, end: LocalDate): String =
        "${group.path}/sessions?start=$start&end=$end"

    fun session(group: OpenVitalsGroup, sessionId: String): String = "${group.path}/sessions/${segment(sessionId)}"

    fun splits(sessionId: String): String = "${OpenVitalsGroup.WORKOUTS.path}/sessions/${segment(sessionId)}/splits"

    fun route(sessionId: String): String = "${OpenVitalsGroup.ROUTES.path}/${segment(sessionId)}"

    /** A session id as one path segment: a slash in it must not start a new one. */
    private fun segment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
