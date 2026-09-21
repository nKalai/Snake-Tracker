package com.snaketracker.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.snaketracker.app.reminders.AlarmReceiver
import com.snaketracker.app.reminders.FrozenDuePayload
import com.snaketracker.app.reminders.FrozenDueSnake
import com.snaketracker.app.reminders.ReminderScheduler
import com.snaketracker.app.reminders.decodeDuePayload
import com.snaketracker.app.reminders.encodeDuePayload
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Instrumented companion to the JVM payload tests: reads the armed alarm's
 * real `PendingIntent` extras back and decodes them, so both the Bundle
 * `getString` loop in front of [decodeDuePayload] and the
 * `FLAG_UPDATE_CURRENT` replacement on re-arm are covered on a real device —
 * the two pieces no JVM test can reach (issue #37).
 */
@RunWith(AndroidJUnit4::class)
class ArmedAlarmFrozenExtrasTest {

    @Test
    fun armedAlarm_exposesTheFrozenExtras_andReArmReplacesThem() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Distant-future instants keep the PendingIntent unsent, so its
        // intent (and extras) is still readable after arming.
        val first = FrozenDuePayload(
            dueSnakes = listOf(FrozenDueSnake(1, "Noodle")),
            nextAlarmAt = Instant.parse("2099-01-01T07:00:00Z")
        )
        ReminderScheduler.reschedule(context, first)
        assertEquals(first, armedPayloadFor(context, first))

        val second = FrozenDuePayload(
            dueSnakes = listOf(FrozenDueSnake(1, "Noodle II"), FrozenDueSnake(2, "Coil")),
            nextAlarmAt = Instant.parse("2099-01-02T07:00:00Z")
        )
        ReminderScheduler.reschedule(context, second)

        // The re-arm replaced the old payload: the second one is what the
        // armed alarm now carries, and the first no longer matches it.
        assertEquals(second, armedPayloadFor(context, second))
        assertEquals(null, armedPayloadFor(context, first))

        ReminderScheduler.reschedule(context, FrozenDuePayload(emptyList(), null))
    }

    /** Looks up the one armed alarm without touching it (FLAG_NO_CREATE) and
     *  decodes the string extras the fire-time receiver will see. */
    private fun armedPayloadFor(context: Context, payload: FrozenDuePayload): FrozenDuePayload? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FEEDING_DUE)
        encodeDuePayload(payload).forEach { (key, value) -> intent.putExtra(key, value) }
        val armed = PendingIntent.getBroadcast(
            context, ReminderScheduler.ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE
        ) ?: return null
        val extras = armed.intent?.extras ?: return null
        val flat = mutableMapOf<String, String?>()
        for (key in extras.keySet()) {
            flat[key] = extras.getString(key)
        }
        return decodeDuePayload(flat)
    }
}
