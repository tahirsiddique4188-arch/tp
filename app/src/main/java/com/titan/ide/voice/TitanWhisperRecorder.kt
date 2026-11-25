package com.titan.ide.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.sqrt

/**
 * TitanWhisperRecorder - Zero-Beep Silent AudioRecord & On-Device Multilingual STT
 * Uses AudioRecord (16000 Hz, MONO, PCM_16BIT) with zero android.speech beep noises.
 * Captures clean 16kHz Mono PCM WAV audio for multilingual STT.
 */
object TitanWhisperRecorder {

    private const val TAG = "TitanWhisperRecorder"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    @Volatile
    private var audioRecord: AudioRecord? = null

    @Volatile
    private var recordingThread: Thread? = null

    @Volatile
    private var currentWavFile: File? = null

    @SuppressLint("MissingPermission")
    fun startRecording(
        context: Context,
        onPartial: ((String) -> Unit)? = null,
        onResult: (String) -> Unit
    ) {
        if (_isRecording.value) return

        try {
            val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = maxOf(minBufSize, 4096)

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                onResult("")
                return
            }

            audioRecord = record
            _isRecording.value = true
            record.startRecording()

            val wavFile = File(context.cacheDir, "titan_whisper_input.wav")
            currentWavFile = wavFile

            recordingThread = Thread({
                val pcmOutputStream = ByteArrayOutputStream()
                val shortBuffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteArray(bufferSize)

                while (_isRecording.value && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readShorts = record.read(shortBuffer, 0, shortBuffer.size)
                    if (readShorts > 0) {
                        // Compute RMS Audio Level for Waveform Animation
                        var sum = 0.0
                        for (i in 0 until readShorts) {
                            val v = shortBuffer[i]
                            // Convert short to little-endian bytes
                            byteBuffer[i * 2] = (v.toInt() and 0xFF).toByte()
                            byteBuffer[i * 2 + 1] = ((v.toInt() shr 8) and 0xFF).toByte()
                            sum += (v * v).toDouble()
                        }
                        pcmOutputStream.write(byteBuffer, 0, readShorts * 2)

                        val mean = sum / readShorts
                        val rms = sqrt(mean).toFloat()
                        val normalized = (rms / 32768.0f * 2.5f).coerceIn(0f, 1f)
                        _audioLevel.value = normalized
                    }
                }

                // Write final WAV file with standard 44-byte RIFF/WAVE header
                val pcmData = pcmOutputStream.toByteArray()
                writeWavFile(wavFile, pcmData, SAMPLE_RATE, 1)
                Log.d(TAG, "Saved ${pcmData.size} bytes PCM to ${wavFile.absolutePath}")

            }, "TitanWhisperThread").apply { start() }

        } catch (e: Exception) {
            Log.e(TAG, "startRecording error: ${e.message}", e)
            _isRecording.value = false
            onResult("")
        }
    }

    fun stopRecording(): File? {
        if (!_isRecording.value) return currentWavFile
        _isRecording.value = false
        _audioLevel.value = 0f

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }

        try {
            recordingThread?.join(500)
            recordingThread = null
        } catch (e: Exception) {
            Log.w(TAG, "Error joining recording thread: ${e.message}")
        }

        return currentWavFile
    }

    /**
     * Writes 44-byte canonical RIFF/WAVE header followed by raw PCM data
     */
    private fun writeWavFile(file: File, pcmData: ByteArray, sampleRate: Int, channels: Int) {
        try {
            val totalAudioLen = pcmData.size.toLong()
            val totalDataLen = totalAudioLen + 36
            val byteRate = (sampleRate * channels * 2).toLong()

            FileOutputStream(file).use { out ->
                val header = ByteArray(44)
                header[0] = 'R'.code.toByte()
                header[1] = 'I'.code.toByte()
                header[2] = 'F'.code.toByte()
                header[3] = 'F'.code.toByte()
                header[4] = (totalDataLen and 0xffL).toByte()
                header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
                header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
                header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
                header[8] = 'W'.code.toByte()
                header[9] = 'A'.code.toByte()
                header[10] = 'V'.code.toByte()
                header[11] = 'E'.code.toByte()
                header[12] = 'f'.code.toByte()
                header[13] = 'm'.code.toByte()
                header[14] = 't'.code.toByte()
                header[15] = ' '.code.toByte()
                header[16] = 16
                header[17] = 0
                header[18] = 0
                header[19] = 0
                header[20] = 1 // PCM format
                header[21] = 0
                header[22] = channels.toByte()
                header[23] = 0
                header[24] = (sampleRate and 0xff).toByte()
                header[25] = ((sampleRate shr 8) and 0xff).toByte()
                header[26] = ((sampleRate shr 16) and 0xff).toByte()
                header[27] = ((sampleRate shr 24) and 0xff).toByte()
                header[28] = (byteRate and 0xffL).toByte()
                header[29] = ((byteRate shr 8) and 0xffL).toByte()
                header[30] = ((byteRate shr 16) and 0xffL).toByte()
                header[31] = ((byteRate shr 24) and 0xffL).toByte()
                header[32] = (channels * 2).toByte() // block align
                header[33] = 0
                header[34] = 16 // bits per sample
                header[35] = 0
                header[36] = 'd'.code.toByte()
                header[37] = 'a'.code.toByte()
                header[38] = 't'.code.toByte()
                header[39] = 'a'.code.toByte()
                header[40] = (totalAudioLen and 0xffL).toByte()
                header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
                header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
                header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()

                out.write(header, 0, 44)
                out.write(pcmData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing WAV file: ${e.message}", e)
        }
    }
}
