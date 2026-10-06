package tech.mmarca.openvitals.bridge.openvitals

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import org.junit.Test

class OpenVitalsApiTest {

    @Test
    fun `names follow the OpenVitals build they belong to`() {
        assertThat(OpenVitalsApi.authority(OpenVitalsBuild.DEBUG.packageName)).isEqualTo("tech.mmarca.openvitals.debug.data")
        assertThat(OpenVitalsApi.permission(OpenVitalsBuild.RELEASE.packageName, OpenVitalsGroup.SEXUAL_ACTIVITY))
            .isEqualTo("tech.mmarca.openvitals.permission.READ_SEXUAL_ACTIVITY")
    }

    @Test
    fun `paths carry their range in the API's formats`() {
        val day = LocalDate.of(2026, 10, 1)

        assertThat(OpenVitalsPaths.daily(OpenVitalsGroup.SLEEP, day.minusDays(6), day))
            .isEqualTo("sleep/daily?start=2026-09-25&end=2026-10-01")
        assertThat(OpenVitalsPaths.samples(OpenVitalsGroup.HEART, Instant.ofEpochMilli(1_000), Instant.ofEpochMilli(2_000)))
            .isEqualTo("heart/samples?start=1000&end=2000")
    }

    @Test
    fun `a session id stays one path segment`() {
        assertThat(OpenVitalsPaths.route("a/b c")).isEqualTo("routes/a%2Fb%20c")
        assertThat(OpenVitalsPaths.splits("abc")).isEqualTo("workouts/sessions/abc/splits")
    }

    @Test
    fun `every group but routes has daily and sample tables`() {
        assertThat(OpenVitalsGroup.entries.filterNot { it.hasDailyAndSamples }).containsExactly(OpenVitalsGroup.ROUTES)
        assertThat(OpenVitalsGroup.entries.filter { it.hasSessions })
            .containsExactly(OpenVitalsGroup.WORKOUTS, OpenVitalsGroup.SLEEP, OpenVitalsGroup.RECOVERY)
    }
}
