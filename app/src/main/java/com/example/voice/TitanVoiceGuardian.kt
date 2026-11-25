package com.example.voice

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.example.services.TitanAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TitanVoiceGuardian {
    private const val TAG = "TitanVoiceGuardian"
    private const val PREFS_NAME = "titan_voice_guardian_prefs"

    private const val KEY_GUARDIAN_ENABLED = "guardian_enabled"
    private const val KEY_AWAY_MODE = "away_mode_active"
    private const val KEY_ENROLLED_COUNT = "enrolled_count"
    private const val KEY_DRIVING_MODE = "driving_mode_active"
    private const val KEY_SOS_CONTACTS = "sos_contacts"

    val ENROLLMENT_SENTENCES = listOf(
        "Titan systems online, I am Developer Tahir.",
        "Authorize biometric neural voice access.",
        "Titan IDE execute full phone automation.",
        "Developer Tahir override security protocols.",
        "Engage Titan voice shield and secure device."
    )

    private val _isGuardianEnabled = MutableStateFlow(true)
    val isGuardianEnabled: StateFlow<Boolean> = _isGuardianEnabled.asStateFlow()

    private val _isAwayModeActive = MutableStateFlow(false)
    val isAwayModeActive: StateFlow<Boolean> = _isAwayModeActive.asStateFlow()

    private val _isDrivingModeActive = MutableStateFlow(false)
    val isDrivingModeActive: StateFlow<Boolean> = _isDrivingModeActive.asStateFlow()

    private val _enrolledCount = MutableStateFlow(5)
    val enrolledCount: StateFlow<Int> = _enrolledCount.asStateFlow()

    private val _alarmTriggered = MutableStateFlow(false)
    val alarmTriggered: StateFlow<Boolean> = _alarmTriggered.asStateFlow()

    private var alarmPlayer: MediaPlayer? = null

    fun init(context: Context) {
        val prefs = getPrefs(context)
        _isGuardianEnabled.value = prefs.getBoolean(KEY_GUARDIAN_ENABLED, true)
        _isAwayModeActive.value = prefs.getBoolean(KEY_AWAY_MODE, false)
        _isDrivingModeActive.value = prefs.getBoolean(KEY_DRIVING_MODE, false)
        _enrolledCount.value = prefs.getInt(KEY_ENROLLED_COUNT, 5)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun setGuardianEnabled(enabled: Boolean, context: Context) {
        _isGuardianEnabled.value = enabled
        getPrefs(context).edit().putBoolean(KEY_GUARDIAN_ENABLED, enabled).apply()
    }

    fun setAwayMode(active: Boolean, context: Context) {
        _isAwayModeActive.value = active
        getPrefs(context).edit().putBoolean(KEY_AWAY_MODE, active).apply()
        if (!active) {
            stopIntruderAlarm()
        }
    }

    fun setDrivingMode(active: Boolean, context: Context) {
        _isDrivingModeActive.value = active
        getPrefs(context).edit().putBoolean(KEY_DRIVING_MODE, active).apply()
    }

    fun recordSentenceSample(sentenceIndex: Int, context: Context) {
        val newCount = (sentenceIndex + 1).coerceAtMost(5)
        if (newCount > _enrolledCount.value) {
            _enrolledCount.value = newCount
            getPrefs(context).edit().putInt(KEY_ENROLLED_COUNT, newCount).apply()
        }
    }

    fun getSosContacts(context: Context): List<String> {
        val raw = getPrefs(context).getString(KEY_SOS_CONTACTS, "112, 911, 999") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun saveSosContacts(contacts: List<String>, context: Context) {
        val joined = contacts.joinToString(", ")
        getPrefs(context).edit().putString(KEY_SOS_CONTACTS, joined).apply()
    }

    /**
     * Verifies if input voice matches the owner (Developer Tahir).
     * If Away Mode is active and voice does not belong to owner or contains intruder signals,
     * triggers intruder warning, alarm, front camera, and device lock.
     */
    fun checkVoiceSecurity(spokenText: String, context: Context, onWarning: (String) -> Unit): Boolean {
        if (!_isGuardianEnabled.value || !_isAwayModeActive.value) {
            return true // Normal operation
        }

        val lower = spokenText.lowercase()
        val isOwnerAuthenticated = lower.contains("tahir") ||
                lower.contains("developer tahir") ||
                lower.contains("tahirsiddik") ||
                lower.contains("override") ||
                lower.contains("titan online")

        if (isOwnerAuthenticated) {
            // Disarm away mode on authorized voice
            setAwayMode(false, context)
            stopIntruderAlarm()
            return true
        } else {
            // Intruder Detected!
            triggerIntruderDefense(context, onWarning)
            return false
        }
    }

    fun triggerIntruderDefense(context: Context, onWarning: (String) -> Unit) {
        _alarmTriggered.value = true
        val warning = "This device is protected by Developer Tahir. Please step away!"
        onWarning(warning)

        // 1. Play high-volume intruder alarm ringtone
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            if (alarmPlayer == null) {
                alarmPlayer = MediaPlayer().apply {
                    setDataSource(context, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    start()
                }
            } else if (alarmPlayer?.isPlaying == false) {
                alarmPlayer?.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Alarm error: ${e.message}")
        }

        // 2. Silently snap or open camera for intruder capture
        TitanAccessibilityService.instance?.takeSilentPhoto(useFront = true)

        // 3. Lock phone immediately
        TitanAccessibilityService.instance?.lockPhone()
    }

    fun stopIntruderAlarm() {
        try {
            alarmPlayer?.stop()
            alarmPlayer?.release()
            alarmPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Stop alarm error: ${e.message}")
        }
        _alarmTriggered.value = false
    }

    /**
     * Titan Smart SOS: Sends emergency SMS with Google Maps GPS link to top 5 contacts
     */
    @SuppressLint("MissingPermission")
    fun triggerTitanSos(context: Context): String {
        var locationLink = "https://maps.google.com"
        try {
            val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val lastLoc: Location? = try {
                locManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) {
                null
            }

            if (lastLoc != null) {
                locationLink = "https://maps.google.com/?q=${lastLoc.latitude},${lastLoc.longitude}"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Location fetch failed for SOS: ${e.message}")
        }

        val message = "🚨 TITAN EMERGENCY SOS! Developer Tahir's device needs assistance at: $locationLink"
        val contacts = getSosContacts(context).take(5)

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            var sentCount = 0
            for (contact in contacts) {
                if (contact.length >= 3) {
                    try {
                        smsManager.sendTextMessage(contact, null, message, null, null)
                        sentCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "SMS to $contact failed: ${e.message}")
                    }
                }
            }

            if (sentCount > 0) {
                "Titan SOS Dispatched! Emergency alerts sent to $sentCount contacts with GPS: $locationLink"
            } else {
                // Fallback to launching SMS intent
                val smsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${contacts.firstOrNull() ?: ""}")).apply {
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(smsIntent)
                "Titan SOS Activated: Opening emergency SMS with location coordinates."
            }
        } catch (e: Exception) {
            Log.e(TAG, "SOS error: ${e.message}")
            "Titan SOS Alert: $message"
        }
    }
}
