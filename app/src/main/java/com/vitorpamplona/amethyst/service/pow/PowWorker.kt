package com.vitorpamplona.amethyst.service.pow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.HexKey
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.model.Event
import java.security.MessageDigest

class PowWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    var nonce: Int = 1
    val tags: MutableList<MutableList<String>> = mutableListOf()
    var id: HexKey = ByteArray(0).toHexKey()
    var target: Int = 0

    private val channelId = "POW_NOTIFICATION"
    private val title = "Adding Proof-of-Work"
    private val cancel = "CANCEL"
    private val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(getId())
    private val notificationId = SystemClock.uptimeMillis().toInt()
    private val notification = NotificationCompat.Builder(applicationContext, channelId)
        .setContentTitle(title)
        .setTicker(title)
//        .setStyle(NotificationCompat.BigTextStyle().bigText("Hello Hello Hello Hello Hello Hello Hello Hello "))
//        .setContentText("nonce $nonce")
        .setSmallIcon(R.drawable.amethyst)
        .setOngoing(true)
        .addAction(android.R.drawable.ic_delete, cancel, cancelIntent)

    override suspend fun doWork(): Result {
        try {
            createChannel()
            Log.d("proofofwork", "begin work")
            setForeground(createForegroundInfo())
            target = inputData.getInt("WORK_TARGET", 0)
            var eventJson: String = inputData.getString("RAW_EVENT")!!
            val event = Event.fromJson(eventJson)

            val rawEvent = mutableListOf(
                0,
                event.pubKey,
                event.createdAt,
                event.kind,
                event.tags,
                event.content
            )

            event.tags.forEach {
                tags.add(it.toMutableList())
            }
            tags.add(mutableListOf("nonce", nonce.toString(), target.toString()))

            val eventTagsIndex = 4
            val nonceTagIndex = tags.size - 1
            val nonceIndex = 1

            var done = false
            while (!done) {
                if (isStopped) {
                    return Result.failure()
                }

                if (nonce % 10000 == 0) {
                    Log.d("proofofwork", "nonce = $nonce")
                    setForeground(createForegroundInfo())
                }

                nonce++
                tags[nonceTagIndex][nonceIndex] = nonce.toString()
                rawEvent[eventTagsIndex] = tags
                val json = Event.gson.toJson(rawEvent)
                val digest = sha256.digest(json.toByteArray())

                if (countLeadingZeroBits(digest) >= target) {
                    eventJson = json
                    id = digest.toHexKey()
                    Log.d("proofofwork", "Done: $nonce, $id")
                    notification.setContentText("Done ${target} bits POW! Nonce: $nonce")
                    setForeground(ForegroundInfo(notificationId, notification.build()))
                    done = true
                }
            }

            val outputData = Data.Builder()
                .putString("RAW_EVENT", eventJson)
                .putString("POW_ID", id.toString())
                .build()

            return Result.success(outputData)
        } catch (t: Throwable) {
            return Result.failure()
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        notification.setContentText("Target: ${target} bits. Nonces: ${formatNumber(nonce)}.")

        return ForegroundInfo(notificationId, notification.build())
    }

    private fun createChannel() {
        val name = "Amethyst POW"
        val description = "Adds Proof of Work before sending events"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(channelId, name, importance)
        channel.description = description
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        val sha256: MessageDigest = MessageDigest.getInstance("SHA-256")
    }
}

fun countLeadingZeroBits(hash: ByteArray): Int {
    var count = 0
    for (b in hash) {
        if (b.toInt() == 0) {
            count += 8
        } else {
            var mask = 0x80
            while (mask > 0 && (b.toInt() and mask) == 0) {
                count++
                mask = mask shr 1
            }
            break
        }
    }
    return count
}

fun formatNumber(num: Int): String {
    val roundedNum = when {
        num >= 1_000_000 -> String.format("%.2fM", num.toFloat() / 1_000_000f)
        num >= 1_000 -> String.format("%.0fk", num.toFloat() / 1_000f)
        else -> num.toString()
    }
    return roundedNum
}