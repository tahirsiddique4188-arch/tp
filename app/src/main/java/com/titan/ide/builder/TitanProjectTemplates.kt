package com.titan.ide.builder

/**
 * TitanProjectTemplates - Pre-defined scaffold structures module for Titan Autonomous Builder
 * Developer: Developer Tahir (@Tahirsiddik)
 */
data class ProjectScaffoldTemplate(
    val id: String,
    val title: String,
    val category: String,
    val icon: String,
    val description: String,
    val targetLanguage: TitanAutonomousBuilder.TargetLanguage,
    val fileNames: List<String>,
    val defaultPromptHint: String,
    val scaffoldFiles: Map<String, String> = emptyMap()
)

object TitanProjectTemplatesList {

    val TEMPLATES: List<ProjectScaffoldTemplate> = listOf(
        ProjectScaffoldTemplate(
            id = "compose_app",
            title = "Compose App",
            category = "Android / Kotlin",
            icon = "🤖",
            description = "Scaffolded Jetpack Compose Android app with Material3, ViewModels, and state flow architecture.",
            targetLanguage = TitanAutonomousBuilder.TargetLanguage.KOTLIN,
            fileNames = listOf("MainActivity.kt", "Theme.kt", "AndroidManifest.xml"),
            defaultPromptHint = "Build a responsive Jetpack Compose dashboard app with Material 3 styling",
            scaffoldFiles = mapOf(
                "MainActivity.kt" to """
package com.titan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF12151C)) {
                    Text("Compose Scaffold Active", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
                """.trimIndent()
            )
        ),
        ProjectScaffoldTemplate(
            id = "react_web_app",
            title = "React Web App",
            category = "Web Frontend",
            icon = "⚛️",
            description = "Single-Page Application scaffold powered by React, Tailwind CSS CDN, interactive hooks, and modular UI components.",
            targetLanguage = TitanAutonomousBuilder.TargetLanguage.WEB,
            fileNames = listOf("index.html", "App.jsx", "style.css", "app.js"),
            defaultPromptHint = "Build an interactive React Web App with Tailwind CSS and dynamic dashboard cards",
            scaffoldFiles = mapOf(
                "index.html" to """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>React Web App Scaffold</title>
    <script src="https://cdn.tailwindcss.com"></script>
    <script src="https://unpkg.com/react@18/umd/react.development.js" crossorigin></script>
    <script src="https://unpkg.com/react-dom@18/umd/react-dom.development.js" crossorigin></script>
    <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
    <link rel="stylesheet" href="style.css">
</head>
<body class="bg-[#12151C] text-white min-h-screen">
    <div id="root"></div>
    <script type="text/babel" src="App.jsx"></script>
    <script src="app.js"></script>
</body>
</html>
                """.trimIndent(),
                "App.jsx" to """
function App() {
    const [count, setCount] = React.useState(0);
    return (
        <div className="p-8 max-w-4xl mx-auto space-y-6">
            <header class="bg-[#1B202E] p-6 rounded-2xl border border-cyan-500/30 flex justify-between items-center shadow-xl">
                <div className="flex items-center space-x-3">
                    <span className="text-2xl">⚛️</span>
                    <h1 className="text-2xl font-bold text-white">React Web App Scaffold</h1>
                </div>
                <button onClick={() => setCount(count + 1)} className="bg-cyan-400 text-black font-bold px-4 py-2 rounded-xl">
                    Count: {count}
                </button>
            </header>
        </div>
    );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
                """.trimIndent()
            )
        ),
        ProjectScaffoldTemplate(
            id = "python_script",
            title = "Python Script",
            category = "Python Backend",
            icon = "🐍",
            description = "Structured Python application with service layer, environment loader, requirements.txt, and CLI engine.",
            targetLanguage = TitanAutonomousBuilder.TargetLanguage.PYTHON,
            fileNames = listOf("main.py", "requirements.txt", "engine.py"),
            defaultPromptHint = "Write a modular Python automation script with data processing and JSON logging",
            scaffoldFiles = mapOf(
                "main.py" to """
# Titan Python Scaffold Engine
import sys
import os
from engine import TitanEngineService

def main():
    print("🚀 Python Scaffold Initialized")
    service = TitanEngineService("PythonScaffold")
    service.initialize()

if __name__ == "__main__":
    main()
                """.trimIndent()
            )
        ),
        ProjectScaffoldTemplate(
            id = "java_android",
            title = "Java Android App",
            category = "Android / Java",
            icon = "☕",
            description = "Standard Android Java MVC architecture with AppCompat, ConstraintLayout XML, and EventListeners.",
            targetLanguage = TitanAutonomousBuilder.TargetLanguage.JAVA,
            fileNames = listOf("MainActivity.java", "activity_main.xml", "AndroidManifest.xml"),
            defaultPromptHint = "Build an Android Java App with interactive layouts and SQLite storage",
            scaffoldFiles = mapOf()
        ),
        ProjectScaffoldTemplate(
            id = "fastapi_py",
            title = "FastAPI Python Server",
            category = "Python Backend",
            icon = "⚡",
            description = "Asynchronous Python web service with FastAPI, Pydantic schemas, CORS middleware, and API endpoints.",
            targetLanguage = TitanAutonomousBuilder.TargetLanguage.PYTHON,
            fileNames = listOf("main.py", "models.py", "requirements.txt"),
            defaultPromptHint = "Build a FastAPI Python REST backend with Pydantic models and CRUD endpoints",
            scaffoldFiles = mapOf()
        ),
        ProjectScaffoldTemplate(
            id = "vanilla_web",
            title = "Vanilla Web App",
            category = "Web Frontend",
            icon = "🌐",
            description = "Responsive HTML5, Tailwind CSS, and JavaScript application with zero external framework overhead.",
            targetLanguage = TitanAutonomousBuilder.TargetLanguage.WEB,
            fileNames = listOf("index.html", "style.css", "app.js"),
            defaultPromptHint = "Build a responsive single-page web app with interactive cards and localStorage persistence",
            scaffoldFiles = mapOf()
        )
    )

    fun getTemplateById(id: String): ProjectScaffoldTemplate? {
        return TEMPLATES.find { it.id.equals(id, ignoreCase = true) }
    }
}
