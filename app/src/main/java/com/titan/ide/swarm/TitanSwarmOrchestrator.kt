package com.titan.ide.swarm

import android.content.Context
import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.SenderRole
import com.titan.ide.agent.TitanDynamicAgentLoop
import com.titan.ide.media.video.TitanStoryVideoComposer
import com.titan.ide.network.TitanDirectEngine
import com.titan.ide.voice.TitanNativeVoiceEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SubAgentStatus {
    SPAWNING,
    WORKING,
    COMPLETED,
    FAILED
}

data class TitanSubAgent(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val role: String,
    val taskPrompt: String,
    val capability: String = "TEXT_AND_CODE", // TEXT_AND_CODE | PHONE_CONTROL | MEDIA_IMAGE | STORY_VIDEO
    val status: SubAgentStatus = SubAgentStatus.SPAWNING,
    val resultOutput: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * TitanSwarmOrchestrator (Main Agent: TITAN PRIME)
 * Dynamically spawns unlimited named sub-agents on Android, runs them in parallel
 * across Coding, Research, Media/Video, and Live Phone Control, and synthesizes their work.
 */
object TitanSwarmOrchestrator {

    private const val TAG = "TitanSwarm"
    private val swarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _subAgents = MutableStateFlow<List<TitanSubAgent>>(emptyList())
    val subAgents: StateFlow<List<TitanSubAgent>> = _subAgents.asStateFlow()

    private val _isOrchestrating = MutableStateFlow(false)
    val isOrchestrating: StateFlow<Boolean> = _isOrchestrating.asStateFlow()

    private val _masterSummary = MutableStateFlow("")
    val masterSummary: StateFlow<String> = _masterSummary.asStateFlow()

    private var activeJob: Job? = null

    fun clearSwarm() {
        activeJob?.cancel()
        _isOrchestrating.value = false
        _subAgents.value = emptyList()
        _masterSummary.value = ""
    }

    fun dispatchGoalToSwarm(context: Context, userGoal: String, speakSummary: Boolean = true) {
        if (userGoal.isBlank()) return
        activeJob?.cancel()
        _isOrchestrating.value = true
        _masterSummary.value = "🧠 Titan Prime is analyzing the task and deploying sub-agents..."
        _subAgents.value = emptyList()

        activeJob = swarmScope.launch {
            try {
                // 1. Titan Prime plans & names unlimited specialized sub-agents dynamically
                val plannedAgents = planSubAgentsWithTitanPrime(context, userGoal)
                _subAgents.value = plannedAgents

                // 2. Execute ALL spawned sub-agents concurrently in parallel coroutines
                val completedAgents = coroutineScope {
                    plannedAgents.map { agent ->
                        async(Dispatchers.IO) {
                            updateAgentStatus(agent.id, SubAgentStatus.WORKING, "Working...")
                            try {
                                val output = executeSubAgentTask(context, agent)
                                updateAgentStatus(agent.id, SubAgentStatus.COMPLETED, output)
                                agent.copy(status = SubAgentStatus.COMPLETED, resultOutput = output)
                            } catch (e: Exception) {
                                val err = "Error: ${e.message}"
                                updateAgentStatus(agent.id, SubAgentStatus.FAILED, err)
                                agent.copy(status = SubAgentStatus.FAILED, resultOutput = err)
                            }
                        }
                    }.awaitAll()
                }

                // 3. Titan Prime synthesizes all sub-agent results into one unified final answer
                val synthesisPrompt = buildString {
                    append("You are TITAN PRIME, the Main AI Orchestrator.\n")
                    append("User Goal: \"$userGoal\"\n\n")
                    append("Your Sub-Agents completed their tasks:\n")
                    for (ag in completedAgents) {
                        append("- Agent **${ag.name}** (${ag.role}): ${ag.resultOutput.take(700)}\n")
                    }
                    append("\nSynthesize a clear, complete final response for the user in the SAME language as the goal (Bengali, Hindi, or English).")
                }

                val finalAnswer = TitanDirectEngine.executeChat(
                    context = context,
                    modelId = "gemini-4-hydrogen",
                    messages = listOf(ChatMessage(sender = SenderRole.USER, content = synthesisPrompt))
                )

                _masterSummary.value = finalAnswer
                if (speakSummary) {
                    withContext(Dispatchers.Main) {
                        com.titan.ide.voice.TitanInstantTtsEngine.speakFullResponse(
                            context = context,
                            rawText = finalAnswer.take(450)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Swarm orchestration error: ${e.message}", e)
                _masterSummary.value = "⚠️ Swarm Error: ${e.message}"
            } finally {
                _isOrchestrating.value = false
            }
        }
    }

    private suspend fun planSubAgentsWithTitanPrime(context: Context, userGoal: String): List<TitanSubAgent> {
        val plannerPrompt = """
            You are TITAN PRIME, an Autonomous Multi-Agent Orchestrator inside an Android phone.
            User Request: "$userGoal"

            Break this request into as many specialized Sub-Agents as needed (minimum 2, unlimited as needed).
            Give each Sub-Agent a distinct, cool futuristic codename (e.g., Nova, Vortex, Pixel, Ghost, Sentinel, Atlas, Echo, Pulse, Cipher, Orion) and a clear role.
            Choose each agent's "capability" from:
            - "TEXT_AND_CODE" (research, writing, coding, architecture, debugging, planning)
            - "MEDIA_IMAGE" (generating an AI image)
            - "STORY_VIDEO" (generating a full storytelling MP4 video with voiceover)
            - "PHONE_CONTROL" (opening apps, tapping UI, sending WhatsApp/Telegram messages, controlling phone)

            Return ONLY a valid JSON array:
            [{"name":"Nova","role":"UI Architect","capability":"TEXT_AND_CODE","task":"..."}, ...]
        """.trimIndent()

        val raw = TitanDirectEngine.executeChat(
            context = context,
            modelId = "gemini-2.5-pro",
            messages = listOf(ChatMessage(sender = SenderRole.USER, content = plannerPrompt))
        )

        val list = mutableListOf<TitanSubAgent>()
        try {
            val start = raw.indexOf('[')
            val end = raw.lastIndexOf(']')
            if (start != -1 && end > start) {
                val arr = JSONArray(raw.substring(start, end + 1))
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "Agent-${i + 1}").trim()
                    val role = o.optString("role", "Specialist").trim()
                    val cap = o.optString("capability", "TEXT_AND_CODE").trim()
                    val task = o.optString("task", userGoal).trim()
                    list.add(TitanSubAgent(name = name, role = role, capability = cap, taskPrompt = task))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Planner parse fallback: ${e.message}")
        }

        if (list.isNotEmpty()) return list

        return listOf(
            TitanSubAgent(name = "Vortex", role = "Research & Strategy", capability = "TEXT_AND_CODE", taskPrompt = "Analyze and plan: $userGoal"),
            TitanSubAgent(name = "Nova", role = "Builder & Execution", capability = "TEXT_AND_CODE", taskPrompt = "Execute solution for: $userGoal"),
            TitanSubAgent(name = "Sentinel", role = "QA & Verification", capability = "TEXT_AND_CODE", taskPrompt = "Verify and refine output for: $userGoal")
        )
    }

    private suspend fun executeSubAgentTask(context: Context, agent: TitanSubAgent): String {
        return when (agent.capability.uppercase()) {
            "MEDIA_IMAGE" -> {
                val url = TitanDirectEngine.generateImageDirect(context, agent.taskPrompt)
                "Generated Image URL: $url"
            }
            "STORY_VIDEO" -> {
                val mp4 = TitanStoryVideoComposer.generateFullStoryVideo(context, agent.taskPrompt)
                "Rendered Full Video: ${mp4.absolutePath}"
            }
            "PHONE_CONTROL" -> {
                TitanDynamicAgentLoop.startAssistantMode(context)
                TitanDynamicAgentLoop.executeDynamicGoal(context, agent.taskPrompt)
                "Dispatched to Phone Control Agent (${agent.name})"
            }
            else -> {
                TitanDirectEngine.executeChat(
                    context = context,
                    modelId = "gemini-2.5-flash",
                    messages = listOf(
                        ChatMessage(
                            sender = SenderRole.USER,
                            content = "You are Sub-Agent **${agent.name}** (${agent.role}). Complete ONLY your assigned task concisely and accurately:\n${agent.taskPrompt}"
                        )
                    )
                )
            }
        }
    }

    private fun updateAgentStatus(id: String, status: SubAgentStatus, output: String) {
        _subAgents.value = _subAgents.value.map { ag ->
            if (ag.id == id) ag.copy(status = status, resultOutput = output) else ag
        }
    }
}
