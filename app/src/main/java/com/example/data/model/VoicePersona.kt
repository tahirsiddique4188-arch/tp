package com.example.data.model

import java.util.Locale

data class VoicePersona(
    val id: String,
    val name: String,
    val roleTitle: String,
    val gender: String,
    val accent: String,
    val pitchMultiplier: Float,
    val defaultSpeed: Float,
    val description: String,
    val sampleText: String,
    val edgeVoiceEn: String = "en-IN-NeerjaNeural",
    val edgeVoiceHi: String = "hi-IN-SwaraNeural",
    val edgeVoiceBn: String = "bn-BD-NabanitaNeural",
    val prosodyRate: String = "+14%",
    val prosodyPitch: String = "+0Hz",
    val characterTag: String = roleTitle,
    val locale: Locale = Locale.US
)

object VoicePersonas {

    // ===== 15 GENUINELY DISTINCT MALE VOICES =====
    val ARYAN = VoicePersona("aryan", "Aryan", "Fast Jarvis Male", "Male", "Indian", 0.90f, 1.16f, "", "Hello, I am Aryan. Ready to help.", "en-IN-PrabhatNeural", "hi-IN-MadhurNeural", "bn-BD-PradeepNeural", "+18%", "+0Hz", locale = Locale("en", "IN"))
    val KABIR = VoicePersona("kabir", "Kabir", "Deep Bass Architect", "Male", "US", 0.72f, 1.00f, "", "Hello, I am Kabir. Ready to help.", "en-US-AndrewMultilingualNeural", "en-US-AndrewMultilingualNeural", "bn-IN-BashkarNeural", "+4%", "-16Hz", locale = Locale.US)
    val VIKRAM = VoicePersona("vikram", "Vikram", "British Commander", "Male", "British", 0.80f, 1.08f, "", "Hello, I am Vikram. Ready to help.", "en-GB-RyanNeural", "en-US-BrianMultilingualNeural", "bn-BD-PradeepNeural", "+12%", "-10Hz", locale = Locale.UK)
    val DEV = VoicePersona("dev", "Dev", "Energetic Youth Coder", "Male", "US", 1.04f, 1.24f, "", "Hello, I am Dev. Ready to help.", "en-US-GuyNeural", "en-US-FlorianMultilingualNeural", "bn-IN-BashkarNeural", "+24%", "+14Hz", locale = Locale.US)
    val ARJUN = VoicePersona("arjun", "Arjun", "Deep Studio Narrator", "Male", "Indian", 0.68f, 0.92f, "", "Hello, I am Arjun. Ready to help.", "en-US-ChristopherNeural", "hi-IN-MadhurNeural", "bn-BD-PradeepNeural", "-4%", "-20Hz", locale = Locale("en", "IN"))
    val RISHI = VoicePersona("rishi", "Rishi", "Australian Anchor", "Male", "Australian", 0.96f, 1.14f, "", "Hello, I am Rishi. Ready to help.", "en-AU-WilliamNeural", "en-US-RemyMultilingualNeural", "en-US-RemyMultilingualNeural", "+16%", "+6Hz", locale = Locale("en", "AU"))
    val NEEL = VoicePersona("neel", "Neel", "Cheerful Best Buddy", "Male", "US", 1.08f, 1.18f, "", "Hello, I am Neel. Ready to help.", "en-US-BrianMultilingualNeural", "en-US-BrianMultilingualNeural", "en-US-BrianMultilingualNeural", "+20%", "+18Hz", locale = Locale.US)
    val ZAYAN = VoicePersona("zayan", "Zayan", "Ultra-Deep Cyber Agent", "Male", "Canadian", 0.62f, 0.90f, "", "Hello, I am Zayan. Ready to help.", "en-CA-LiamNeural", "en-US-AndrewMultilingualNeural", "bn-IN-BashkarNeural", "-6%", "-24Hz", locale = Locale.CANADA)
    val SAMAR = VoicePersona("samar", "Samar", "Calm UK Strategist", "Male", "British", 0.84f, 1.04f, "", "Hello, I am Samar. Ready to help.", "en-GB-ThomasNeural", "en-US-GiuseppeMultilingualNeural", "en-US-GiuseppeMultilingualNeural", "+6%", "-6Hz", locale = Locale.UK)
    val KARAN = VoicePersona("karan", "Karan", "Turbo Rapid Debugger", "Male", "US", 0.98f, 1.30f, "", "Hello, I am Karan. Ready to help.", "en-US-EricNeural", "hi-IN-MadhurNeural", "bn-BD-PradeepNeural", "+30%", "+10Hz", locale = Locale.US)
    val REYANSH = VoicePersona("reyansh", "Reyansh", "Soft Gentle Friend", "Male", "Indian", 0.93f, 0.96f, "", "Hello, I am Reyansh. Ready to help.", "en-US-FlorianMultilingualNeural", "en-US-FlorianMultilingualNeural", "en-US-FlorianMultilingualNeural", "+2%", "+8Hz", locale = Locale("en", "IN"))
    val ISHAN = VoicePersona("ishan", "Ishan", "Expressive Creative", "Male", "US", 0.88f, 1.10f, "", "Hello, I am Ishan. Ready to help.", "en-US-RemyMultilingualNeural", "en-US-RemyMultilingualNeural", "bn-IN-BashkarNeural", "+12%", "+4Hz", locale = Locale.US)
    val VIHAAN = VoicePersona("vihaan", "Vihaan", "Irish AI Professor", "Male", "Irish", 0.76f, 0.95f, "", "Hello, I am Vihaan. Ready to help.", "en-IE-ConnorNeural", "en-US-GiuseppeMultilingualNeural", "bn-BD-PradeepNeural", "+0%", "-12Hz", locale = Locale("en", "IE"))
    val AARUSH = VoicePersona("aarush", "Aarush", "Upbeat Podcast Host", "Male", "US", 1.01f, 1.22f, "", "Hello, I am Aarush. Ready to help.", "en-US-RogerNeural", "hi-IN-MadhurNeural", "en-US-AndrewMultilingualNeural", "+22%", "+12Hz", locale = Locale.US)
    val ROHAN = VoicePersona("rohan", "Rohan", "Tactical Specialist", "Male", "US", 0.74f, 1.12f, "", "Hello, I am Rohan. Ready to help.", "en-US-SteffanNeural", "en-US-BrianMultilingualNeural", "bn-IN-BashkarNeural", "+16%", "-14Hz", locale = Locale.US)

    // ===== 15 GENUINELY DISTINCT FEMALE VOICES =====
    val ZARA = VoicePersona("zara", "Zara", "Warm AI Companion", "Female", "US", 1.12f, 1.10f, "", "Hello, I am Zara. Ready to help.", "en-US-EmmaMultilingualNeural", "en-US-EmmaMultilingualNeural", "bn-BD-NabanitaNeural", "+14%", "+4Hz", locale = Locale.US)
    val KIARA = VoicePersona("kiara", "Kiara", "Lively Indian Bestie", "Female", "Indian", 1.22f, 1.16f, "", "Hello, I am Kiara. Ready to help.", "en-IN-NeerjaNeural", "hi-IN-SwaraNeural", "bn-IN-TanishaaNeural", "+18%", "+14Hz", locale = Locale("en", "IN"))
    val MEERA = VoicePersona("meera", "Meera", "Ultra-Fast Studio Voice", "Female", "Indian", 1.16f, 1.28f, "", "Hello, I am Meera. Ready to help.", "en-IN-NeerjaExpressiveNeural", "hi-IN-SwaraNeural", "bn-BD-NabanitaNeural", "+28%", "+8Hz", locale = Locale("en", "IN"))
    val ALINA = VoicePersona("alina", "Alina", "Sleek Futuristic AI", "Female", "US", 1.00f, 1.08f, "", "Hello, I am Alina. Ready to help.", "en-US-AvaMultilingualNeural", "en-US-AvaMultilingualNeural", "en-US-AvaMultilingualNeural", "+12%", "-8Hz", locale = Locale.US)
    val RIYA = VoicePersona("riya", "Riya", "British F.R.I.D.A.Y.", "Female", "British", 1.06f, 1.18f, "", "Hello, I am Riya. Ready to help.", "en-GB-SoniaNeural", "en-US-SeraphinaMultilingualNeural", "en-US-SeraphinaMultilingualNeural", "+20%", "-4Hz", locale = Locale.UK)
    val NISHA = VoicePersona("nisha", "Nisha", "Warm Deep Storyteller", "Female", "US", 0.95f, 0.94f, "", "Hello, I am Nisha. Ready to help.", "en-US-JennyNeural", "hi-IN-SwaraNeural", "bn-IN-TanishaaNeural", "-2%", "-14Hz", locale = Locale.US)
    val ANANYA = VoicePersona("ananya", "Ananya", "Australian Mentor", "Female", "Australian", 1.20f, 1.12f, "", "Hello, I am Ananya. Ready to help.", "en-AU-NatashaNeural", "en-US-VivienneMultilingualNeural", "en-US-VivienneMultilingualNeural", "+14%", "+10Hz", locale = Locale("en", "AU"))
    val TARA = VoicePersona("tara", "Tara", "Soothing Night Owl", "Female", "US", 0.90f, 0.90f, "", "Hello, I am Tara. Ready to help.", "en-US-AriaNeural", "hi-IN-SwaraNeural", "bn-BD-NabanitaNeural", "-6%", "-18Hz", locale = Locale.US)
    val SANYA = VoicePersona("sanya", "Sanya", "Rapid British Coder", "Female", "British", 1.26f, 1.24f, "", "Hello, I am Sanya. Ready to help.", "en-GB-LibbyNeural", "en-US-EmmaMultilingualNeural", "bn-IN-TanishaaNeural", "+24%", "+16Hz", locale = Locale.UK)
    val DIYA = VoicePersona("diya", "Diya", "Playful Bubbly Spark", "Female", "Canadian", 1.34f, 1.20f, "", "Hello, I am Diya. Ready to help.", "en-CA-ClaraNeural", "en-US-ThalitaMultilingualNeural", "en-US-ThalitaMultilingualNeural", "+20%", "+22Hz", locale = Locale.CANADA)
    val IRA = VoicePersona("ira", "Ira", "Executive Strategist", "Female", "US", 0.97f, 1.04f, "", "Hello, I am Ira. Ready to help.", "en-US-MichelleNeural", "en-US-VivienneMultilingualNeural", "bn-BD-NabanitaNeural", "+8%", "-12Hz", locale = Locale.US)
    val KAVYA = VoicePersona("kavya", "Kavya", "Melodic Soft Poet", "Female", "Indian", 1.14f, 0.94f, "", "Hello, I am Kavya. Ready to help.", "en-IN-NeerjaNeural", "hi-IN-SwaraNeural", "bn-IN-TanishaaNeural", "-4%", "+6Hz", locale = Locale("en", "IN"))
    val MYRA = VoicePersona("myra", "Myra", "Youthful Hologram AI", "Female", "US", 1.30f, 1.15f, "", "Hello, I am Myra. Ready to help.", "en-US-AnaNeural", "en-US-AvaMultilingualNeural", "bn-BD-NabanitaNeural", "+16%", "+20Hz", locale = Locale.US)
    val NAINA = VoicePersona("naina", "Naina", "Irish News Anchor", "Female", "Irish", 1.03f, 1.16f, "", "Hello, I am Naina. Ready to help.", "en-IE-EmilyNeural", "en-US-SeraphinaMultilingualNeural", "bn-IN-TanishaaNeural", "+18%", "-6Hz", locale = Locale("en", "IE"))
    val SIYA = VoicePersona("siya", "Siya", "Gentle Soft Companion", "Female", "US", 1.09f, 0.92f, "", "Hello, I am Siya. Ready to help.", "en-US-SaraNeural", "en-US-EmmaMultilingualNeural", "en-US-EmmaMultilingualNeural", "-4%", "-10Hz", locale = Locale.US)

    val DEFAULT_MALE = ARYAN
    val DEFAULT_FEMALE = ZARA
    val DEFAULT = ZARA

    val ALL_PERSONAS: List<VoicePersona> = listOf(
        ARYAN, KABIR, VIKRAM, DEV, ARJUN, RISHI, NEEL, ZAYAN, SAMAR, KARAN, REYANSH, ISHAN, VIHAAN, AARUSH, ROHAN,
        ZARA, KIARA, MEERA, ALINA, RIYA, NISHA, ANANYA, TARA, SANYA, DIYA, IRA, KAVYA, MYRA, NAINA, SIYA
    )

    val SUPPORTED_LANGUAGES = listOf(
        SupportedLanguage("Bengali", "bn-IN", Locale.Builder().setLanguage("bn").setRegion("IN").build()),
        SupportedLanguage("Hindi", "hi-IN", Locale.Builder().setLanguage("hi").setRegion("IN").build()),
        SupportedLanguage("English (India)", "en-IN", Locale.Builder().setLanguage("en").setRegion("IN").build()),
        SupportedLanguage("English (US)", "en-US", Locale.US),
        SupportedLanguage("Bengali", "bn-BD", Locale.Builder().setLanguage("bn").setRegion("BD").build()),
        SupportedLanguage("Marathi", "mr-IN", Locale.Builder().setLanguage("mr").setRegion("IN").build()),
        SupportedLanguage("Gujarati", "gu-IN", Locale.Builder().setLanguage("gu").setRegion("IN").build()),
        SupportedLanguage("Nepali", "ne-NP", Locale.Builder().setLanguage("ne").setRegion("NP").build()),
        SupportedLanguage("Arabic", "ar-SA", Locale.Builder().setLanguage("ar").setRegion("SA").build())
    )

    fun getById(id: String): VoicePersona {
        val clean = id.trim().lowercase()
        return ALL_PERSONAS.find {
            it.id.equals(clean, ignoreCase = true) ||
            it.name.equals(clean, ignoreCase = true)
        } ?: when (clean) {
            "puck" -> ARYAN
            "fenrir" -> KABIR
            "charon" -> VIKRAM
            "kore" -> RIYA
            "orus" -> DEV
            "aoede" -> NISHA
            "maria" -> ALINA
            "swara" -> MEERA
            else -> ALL_PERSONAS.find {
                it.roleTitle.contains(clean, ignoreCase = true)
            } ?: DEFAULT
        }
    }

    fun getMaleVoices(): List<VoicePersona> = ALL_PERSONAS.filter { it.gender.equals("Male", ignoreCase = true) }
    fun getFemaleVoices(): List<VoicePersona> = ALL_PERSONAS.filter { it.gender.equals("Female", ignoreCase = true) }
}

data class SupportedLanguage(
    val displayName: String,
    val code: String,
    val locale: Locale
)
