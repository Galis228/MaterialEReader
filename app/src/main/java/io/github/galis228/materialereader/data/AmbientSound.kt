package io.github.galis228.materialereader.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Quiet background sound while reading. Everything is synthesised on the fly from filtered noise,
 * so there are no audio files to ship or license and nothing loops audibly.
 */
object AmbientSound {
    private const val RATE = 22050
    @Volatile private var kind: Ambient = Ambient.NONE
    @Volatile private var volume = 0.5f
    private var thread: Thread? = null

    fun play(a: Ambient, vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        if (a == kind && thread?.isAlive == true) return
        stop()
        if (a == Ambient.NONE) return
        kind = a
        thread = Thread({ run(a) }, "ambient").apply { isDaemon = true; start() }
    }

    fun setVolume(vol: Float) { volume = vol.coerceIn(0f, 1f) }

    fun stop() {
        kind = Ambient.NONE
        thread?.interrupt(); thread = null
    }

    private fun run(a: Ambient) {
        val min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(min, RATE / 2 * 2))
            .build()
        val buf = ShortArray(1024)
        val rnd = Random(System.nanoTime())
        // filter state
        var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var brown = 0.0; var lp = 0.0; var bp = 0.0
        var t = 0L
        var drop = 0.0; var crackle = 0.0
        var fade = 0.0 // fades in so the sound never starts with a click
        try {
            track.play()
            while (!Thread.currentThread().isInterrupted && kind == a) {
                for (i in buf.indices) {
                    val white = rnd.nextDouble() * 2 - 1
                    // pink noise (Paul Kellet's economy filter)
                    b0 = 0.99765 * b0 + white * 0.0990460
                    b1 = 0.96300 * b1 + white * 0.2965164
                    b2 = 0.57000 * b2 + white * 1.0526913
                    val pink = (b0 + b1 + b2 + white * 0.1848) * 0.16
                    brown = (brown + white * 0.02) / 1.02
                    val sec = t.toDouble() / RATE
                    val s = when (a) {
                        Ambient.RAIN -> {
                            if (rnd.nextDouble() < 0.0016) drop = 0.5 + rnd.nextDouble() * 0.5
                            drop *= 0.985
                            lp += (pink - lp) * 0.35
                            lp * 0.8 + white * drop * 0.25
                        }
                        Ambient.FIRE -> {
                            if (rnd.nextDouble() < 0.0007) crackle = 0.6 + rnd.nextDouble() * 0.4
                            crackle *= 0.93
                            brown * 3.2 + white * crackle * 0.5 + pink * 0.08
                        }
                        Ambient.SEA -> {
                            // two slow overlapping swells
                            val swell = 0.35 + 0.65 * (0.5 + 0.5 * sin(2 * PI * sec / 9.0)) * (0.6 + 0.4 * sin(2 * PI * sec / 23.0 + 1.3))
                            lp += (pink - lp) * (0.08 + 0.25 * swell)
                            lp * 1.6 * swell + brown * 1.2
                        }
                        Ambient.WIND -> {
                            val gust = 0.4 + 0.6 * (0.5 + 0.5 * sin(2 * PI * sec / 13.0)) * (0.7 + 0.3 * sin(2 * PI * sec / 5.3))
                            val f = 0.02 + 0.05 * gust
                            bp += (pink - bp) * f
                            bp * 3.0 * gust
                        }
                        Ambient.NONE -> 0.0
                    }
                    if (fade < 1.0) fade += 1.0 / RATE
                    val v = (s * volume * fade).coerceIn(-1.0, 1.0)
                    buf[i] = (v * 22000).toInt().toShort()
                    t++
                }
                track.write(buf, 0, buf.size)
            }
        } catch (_: Throwable) {
        } finally {
            runCatching { track.pause(); track.flush(); track.release() }
        }
    }
}
