package com.example.ubeaconsnitch

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlin.math.*

class AudioRecorder {
    var onBeaconDetected: (() -> Unit)? = null
    private var isRecording = false
    private val sampleRate = 44100
    private val bitDuration = 0.08 // 80 ms
    private val bitsToDetect = UBEACON_UUID.length
    private val samplesPerBit = (sampleRate * bitDuration).toInt()
    private val requiredSamples = samplesPerBit * bitsToDetect
    private val bufferSize = max(
        AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ),
        requiredSamples * 2 // Para tener margen para la ventana deslizante
    )
    private lateinit var audioRecord: AudioRecord
    private lateinit var bpskDecoder: BPSKDecoder
    private var lastDetected = false

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (isRecording) return
        isRecording = true

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord.startRecording()

            bpskDecoder = BPSKDecoder(
                freq0 = FREQ0,
                freq1 = FREQ1,
                sampleRate = sampleRate,
                samplesPerBit = samplesPerBit,
                numBits = bitsToDetect
            )

            Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val buffer = ShortArray(bufferSize)
                val expectedBits = UBEACON_UUID
                val maxErrors = 0 // Solo detección exacta
                // Buffer circular para acumular muestras
                val audioBuffer = ArrayList<Short>()

                while (isRecording) {
                    val bytesRead = audioRecord.read(buffer, 0, bufferSize)
                    if (bytesRead > 0) {
                        audioBuffer.addAll(buffer.copyOf(bytesRead).toList())

                        // Ventana deslizante: analizamos cada step muestras
                        val windowSize = requiredSamples
                        val step = samplesPerBit / 2 // Puedes ajustar el solapamiento

                        var i = 0
                        while (i + windowSize <= audioBuffer.size) {
                            val chunk = audioBuffer.subList(i, i + windowSize).toShortArray()
                            val decoded = bpskDecoder.decode(chunk)
                            val errors = hammingDistance(decoded, expectedBits)
                            val detected = decoded.length == expectedBits.length && errors <= maxErrors

                            Log.d("BPSK", "Detectado: $decoded (errores: $errors)")

                            if (detected && !lastDetected) {
                                lastDetected = true
                                onBeaconDetected?.invoke()
                            } else if (!detected) {
                                lastDetected = false
                            }
                            i += step
                        }
                        // Limpia el buffer para no crecer indefinidamente
                        if (audioBuffer.size > bufferSize * 2) {
                            audioBuffer.subList(0, audioBuffer.size - bufferSize).clear()
                        }
                    }
                }

                audioRecord.stop()
                audioRecord.release()
            }.start()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error al iniciar grabación", e)
            isRecording = false
        }
    }

    fun stop() {
        isRecording = false
    }

    private fun hammingDistance(a: String, b: String): Int {
        if (a.length != b.length) return Int.MAX_VALUE
        return a.zip(b).count { it.first != it.second }
    }
}

class BPSKDecoder(
    private val freq0: Double,
    private val freq1: Double,
    private val sampleRate: Int,
    private val samplesPerBit: Int,
    private val numBits: Int
) {
    fun decode(buffer: ShortArray): String {
        val bits = StringBuilder()
        for (i in 0 until numBits) {
            val start = i * samplesPerBit
            val end = minOf(start + samplesPerBit, buffer.size)
            if (end - start <= 0) break
            val chunk = buffer.sliceArray(start until end)
            val mag0 = goertzel(chunk, freq0)
            val mag1 = goertzel(chunk, freq1)
            bits.append(if (mag1 > mag0) '1' else '0')
        }
        return bits.toString()
    }

    private fun goertzel(buffer: ShortArray, freq: Double): Double {
        val n = buffer.size
        val k = (0.5 + (n * freq) / sampleRate).toInt()
        val w = 2.0 * Math.PI * k / n
        val cosine = Math.cos(w)
        val coeff = 2.0 * cosine
        var q0 = 0.0
        var q1 = 0.0
        var q2 = 0.0
        for (sample in buffer) {
            q0 = coeff * q1 - q2 + sample
            q2 = q1
            q1 = q0
        }
        return q1 * q1 + q2 * q2 - q1 * q2 * coeff
    }
}
