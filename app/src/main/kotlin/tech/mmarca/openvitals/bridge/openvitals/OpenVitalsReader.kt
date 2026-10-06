package tech.mmarca.openvitals.bridge.openvitals

import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One table as read: column names, rows, and what the API says about them. */
data class Table(
    val columns: List<String>,
    val rows: List<List<Any?>>,
    /** `ok`, `partial`, `sharing_off`, `background_read_missing`, … */
    val status: String?,
    /** Set when Health Connect served less history than asked for. */
    val historyStart: String?,
) {
    fun column(row: List<Any?>, name: String): Any? = columns.indexOf(name).takeIf { it >= 0 }?.let { row[it] }
}

/** How one query went. */
sealed interface ReadResult {
    data class Read(val table: Table) : ReadResult

    /** The user has not allowed this group to the bridge. */
    data class Denied(val message: String) : ReadResult

    /** OpenVitals refused the query or failed; [message] says why. */
    data class Failed(val message: String) : ReadResult

    /** No such provider: OpenVitals is missing, or it is a build without the sharing API. */
    data object Unavailable : ReadResult
}

/** Everything read for one group. [results] is keyed by a short label such as "daily". */
data class GroupReading(
    val group: OpenVitalsGroup,
    val granted: Boolean,
    val results: Map<String, ReadResult>,
)

/** Reads the OpenVitals data sharing API. Every query runs off the main thread. */
class OpenVitalsReader(private val context: Context) {

    /** The OpenVitals builds on this phone that offer the sharing API, preferred first. */
    fun installedBuilds(): List<OpenVitalsBuild> = OpenVitalsBuild.entries.filter { build ->
        context.packageManager.resolveContentProvider(OpenVitalsApi.authority(build.packageName), 0) != null
    }

    fun isGranted(build: OpenVitalsBuild, group: OpenVitalsGroup): Boolean =
        ContextCompat.checkSelfPermission(context, OpenVitalsApi.permission(build.packageName, group)) ==
            PackageManager.PERMISSION_GRANTED

    fun permissions(build: OpenVitalsBuild, groups: Collection<OpenVitalsGroup>): Array<String> =
        groups.map { OpenVitalsApi.permission(build.packageName, it) }.toTypedArray()

    suspend fun read(build: OpenVitalsBuild, path: String): ReadResult = withContext(Dispatchers.IO) {
        val uri = "content://${OpenVitalsApi.authority(build.packageName)}/$path".toUri()
        try {
            context.contentResolver.query(uri, null, null, null, null)
                ?.use { cursor -> ReadResult.Read(cursor.toTable()) }
                ?: ReadResult.Unavailable
        } catch (error: SecurityException) {
            ReadResult.Denied(error.message ?: "Not allowed")
        } catch (error: IllegalArgumentException) {
            ReadResult.Failed(error.message ?: "Bad query")
        } catch (error: Exception) {
            ReadResult.Failed(error.message ?: error.javaClass.simpleName)
        }
    }

    /**
     * One group's tables over a short window: a week of daily values, a day of
     * samples, a month of sessions. Routes are read for that month's workouts.
     */
    suspend fun readGroup(
        build: OpenVitalsBuild,
        group: OpenVitalsGroup,
        today: LocalDate = LocalDate.now(),
        now: Instant = Instant.now(),
    ): GroupReading {
        val results = linkedMapOf<String, ReadResult>()
        if (group.hasDailyAndSamples) {
            results["daily"] = read(build, OpenVitalsPaths.daily(group, today.minusDays(DailyDays - 1), today))
            results["samples"] = read(build, OpenVitalsPaths.samples(group, now.minus(SampleHours, ChronoUnit.HOURS), now))
        }
        if (group.hasSessions) {
            results["sessions"] = read(build, OpenVitalsPaths.sessions(group, today.minusDays(SessionDays - 1), today))
        }
        if (group == OpenVitalsGroup.ROUTES) results += routes(build, today)
        return GroupReading(group, isGranted(build, group), results)
    }

    suspend fun readAll(build: OpenVitalsBuild): List<GroupReading> =
        OpenVitalsGroup.entries.map { readGroup(build, it) }

    /** A route is read by workout id, so this needs the workouts group as well. */
    private suspend fun routes(build: OpenVitalsBuild, today: LocalDate): Map<String, ReadResult> {
        val workouts = read(
            build,
            OpenVitalsPaths.sessions(OpenVitalsGroup.WORKOUTS, today.minusDays(SessionDays - 1), today),
        )
        val table = (workouts as? ReadResult.Read)?.table
            ?: return mapOf("workouts" to workouts)
        val ids = table.rows
            .filter { table.column(it, "kind") == "recorded" }
            .mapNotNull { table.column(it, "session_id") as? String }
            .takeLast(MaxRoutes)
        if (ids.isEmpty()) return mapOf("routes" to ReadResult.Read(Table(emptyList(), emptyList(), table.status, null)))
        return ids.associate { id -> "route $id" to read(build, OpenVitalsPaths.route(id)) }
    }

    private fun Cursor.toTable(): Table {
        val names = columnNames.toList()
        val rows = buildList {
            while (moveToNext()) {
                add(
                    names.indices.map { index ->
                        when (getType(index)) {
                            Cursor.FIELD_TYPE_NULL -> null
                            Cursor.FIELD_TYPE_INTEGER -> getLong(index)
                            Cursor.FIELD_TYPE_FLOAT -> getDouble(index)
                            Cursor.FIELD_TYPE_BLOB -> getBlob(index)
                            else -> getString(index)
                        }
                    },
                )
            }
        }
        return Table(
            columns = names,
            rows = rows,
            status = extras?.getString(OpenVitalsApi.EXTRA_STATUS),
            historyStart = extras?.getString(OpenVitalsApi.EXTRA_HISTORY_START),
        )
    }

    private companion object {
        const val DailyDays = 7L
        const val SampleHours = 24L
        const val SessionDays = 30L

        /** Routes can hold thousands of points each; the latest few show the reader works. */
        const val MaxRoutes = 3
    }
}
