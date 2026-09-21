package com.snaketracker.app

import android.Manifest
import android.app.Instrumentation.ActivityMonitor
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.snaketracker.app.reminders.NotificationHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #38: the notification helper's contract against the real
 * [NotificationManager] — one heads-up (high-importance) channel, a
 * tap-to-open content intent on every posted notification, and the per-id
 * replace-not-stack rule.
 *
 * The per-id cases are table-driven: a notification id added later only adds
 * a row to [PER_ID_CASES]. The [uiSuppressionChain] supplies POST_NOTIFICATIONS
 * up front so the helper's permission-gated post path runs (the same rule the
 * other instrumented tests compose).
 */
@RunWith(AndroidJUnit4::class)
class FeedingNotificationContractTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule: TestRule = uiSuppressionChain.around(composeRule)

    /** One row per notification id the helper uses: id + the snake name it posts. */
    private data class PerIdCase(val id: Long, val snakeName: String)

    private companion object {
        val PER_ID_CASES = listOf(
            PerIdCase(id = 1L, snakeName = "Nagini"),
            PerIdCase(id = 2L, snakeName = "Kaa"),
        )
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    @After
    fun clearNotifications() {
        PER_ID_CASES.forEach { manager.cancel(it.id.toInt()) }
    }

    /** WB1: the single channel is created (once, at application start) with high importance. */
    @Test
    fun singleChannel_isCreatedWithHighImportance() {
        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
            ?: run {
                // The application's onCreate already created it; creating again
                // is this helper's one channel-creation site, idempotently.
                NotificationHelper.createChannel(context)
                manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
            }
        assertNotNull("the single channel must exist", channel)
        assertEquals(
            "heads-up banner requires high importance",
            NotificationManager.IMPORTANCE_HIGH,
            channel!!.importance
        )
    }

    /** WB2: a posted feeding-due notification carries a tap intent to the launch activity. */
    @Test
    fun feedingDueNotification_exposesContentIntentToLaunchActivity_andAutoCancels() {
        assertEquals(
            "POST_NOTIFICATIONS must be granted for the post path",
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        )
        for ((id, snakeName) in PER_ID_CASES) {
            NotificationHelper.showFeedingDueNotification(context, id, snakeName)

            // The active-notifications list reflects asynchronously after
            // notify(); pump through the Compose rule's waitUntil.
            composeRule.waitUntil(timeoutMillis = 5_000) {
                manager.activeNotifications.any { it.id == id.toInt() }
            }
            val posted = manager.activeNotifications
                .first { it.id == id.toInt() }
                .notification
            val contentIntent = posted.contentIntent
            assertNotNull("id $id must expose a non-null content intent", contentIntent)

            // Resolve the tap the way the system does: send the PendingIntent
            // and observe the launch through a class-filtered ActivityMonitor
            // (PendingIntent exposes no public Intent accessor on the
            // compiled SDK).
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val monitor = ActivityMonitor(MainActivity::class.java.name, null, false)
            instrumentation.addMonitor(monitor)
            try {
                contentIntent.send()
                // The PI launch is dispatched on the main thread; the Compose
                // rule's waitUntil pumps that queue while we poll the monitor.
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    monitor.lastActivity != null
                }
                val launched = monitor.lastActivity
                assertNotNull("the content intent must launch an activity", launched)
                assertEquals(
                    "the tap must open the launch activity",
                    MainActivity::class.java.name,
                    launched!!.intent.component?.className
                )
            } finally {
                instrumentation.removeMonitor(monitor)
            }

            assertEquals(
                "id $id keeps auto-cancel so the tap clears the notification",
                Notification.FLAG_AUTO_CANCEL,
                posted.flags and Notification.FLAG_AUTO_CANCEL
            )
            // The copy is string-resource driven and names the snake.
            assertEquals(
                context.getString(R.string.notification_feeding_due_title, snakeName),
                posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
            )
        }
    }

    /** WB3: snakes due at one instant keep their own ids — one notification per id. */
    @Test
    fun twoSnakesDueAtOneInstant_yieldOneNotificationPerId() {
        for ((id, snakeName) in PER_ID_CASES) {
            NotificationHelper.showFeedingDueNotification(context, id, snakeName)
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            PER_ID_CASES.all { c -> manager.activeNotifications.any { it.id == c.id.toInt() } }
        }
        val postedIds = manager.activeNotifications
            .filter { entry -> PER_ID_CASES.any { it.id.toInt() == entry.id } }
            .map { it.id }
            .sorted()
        assertEquals(
            "each due snake keeps its own notification id",
            PER_ID_CASES.map { it.id.toInt() }.sorted(),
            postedIds
        )
    }

    /** WB3: re-posting one id replaces that id's notification instead of stacking. */
    @Test
    fun postingTwiceForOneId_replacesItsOwnNotification() {
        for ((id, snakeName) in PER_ID_CASES) {
            NotificationHelper.showFeedingDueNotification(context, id, snakeName)
            NotificationHelper.showFeedingDueNotification(context, id, snakeName)

            composeRule.waitUntil(timeoutMillis = 5_000) {
                manager.activeNotifications.count { it.id == id.toInt() } == 1
            }
            val countForId = manager.activeNotifications.count { it.id == id.toInt() }
            assertEquals("re-posting id $id must replace, not stack", 1, countForId)
        }
    }
}
