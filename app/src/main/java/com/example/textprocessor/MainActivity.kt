package com.example.textprocessor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ======================= DATA CLASSES =======================

data class TextStats(
    val characters: Int = 0,
    val words: Int = 0,
    val lines: Int = 1,
    val paragraphs: Int = 0
)

data class ActionItem(
    val text: String,
    val icon: ImageVector,
    val transform: (String) -> String
)

data class FeatureItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color
)

// ======================= TEXT PROCESSING FUNCTIONS =======================

object TextProcessor {

    private val markdownBlockquoteRegex = Regex("(?m)^[ \\t]*>[ \\t]+")
    private val markdownHeadersRegex = Regex("(?m)^[ \\t]*#+\\s*")
    private val markdownListsRegex = Regex("(?m)^[ \\t]*[-*•▪◦‣][ \\t]+")
    private val markdownNumListsRegex = Regex("(?m)^[ \\t]*\\d+[.)][ \\t]+")
    private val doubleSpacesRegex = Regex("[ \\t]{2,}")
    private val multiNewlinesRegex = Regex("\\n{3,}")
    private val wordsRegex = Regex("[\\p{L}\\p{N}]+(?:[-'][\\p{L}\\p{N}]+)*")

    // Массив реальных скрытых символов Unicode для мгновенной зачистки в один проход
    private val hiddenChars = listOf(
        '\uFEFF', '\u200B', '\u200C', '\u200D', '\u2060',
        '\u00AD', '\u00A0', '\u202F', '\u200E', '\u200F'
    )

    fun removeHiddenChars(input: String): String {
        if (input.isEmpty()) return ""
        var result = input

        // Очистка всех невидимых байтов Unicode за один легкий цикл
        hiddenChars.forEach { ch ->
            result = result.replace(ch, ' ')
        }

        // Очистка синтаксиса разметки Markdown и escape-слэшей
        result = result.replace(Regex("\\\\([*_\\-.!#\\[\\]()`>+])"), "$1")
        result = result.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        result = result.replace(Regex("__(.+?)__"), "$1")
        result = result.replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "$1")
        result = result.replace(Regex("(?<!_)_(?!_)(.+?)(?<!_)_(?!_)"), "$1")

        result = markdownHeadersRegex.replace(result, "")
        result = markdownBlockquoteRegex.replace(result, "")
        result = markdownListsRegex.replace(result, "")
        result = markdownNumListsRegex.replace(result, "")

        // Посимвольный перебор для безопасного удаления эмодзи без падения Regex
        val nullTailSb = java.lang.StringBuilder()
        for (ch in result) {
            val type = Character.getType(ch).toByte()
            if (type != Character.SURROGATE &&
                type != Character.OTHER_SYMBOL &&
                ch.code !in 0x2600..0x27BF &&
                ch.code !in 0x2300..0x23FF &&
                ch.code !in 0x2B00..0x2BFF) {
                nullTailSb.append(ch)
            }
        }
        result = nullTailSb.toString()

        result = result.lines().joinToString("\n") { line ->
            doubleSpacesRegex.replace(line, " ").trim()
        }

        return multiNewlinesRegex.replace(result, "\n\n").trim()
    }

    fun removeExtraSpaces(input: String): String {
        if (input.isEmpty()) return ""
        return input.split("\n").joinToString("\n") { line ->
            line.replace(Regex("[ \\t]+"), " ").trim()
        }.trim()
    }

    fun toSingleLine(input: String): String {
        if (input.isEmpty()) return ""
        return input.replace(Regex("\\r\\n|\\r|\\n"), " ").replace(Regex("[ \\t]+"), " ").trim()
    }

    fun fixParagraphs(input: String): String {
        if (input.isEmpty()) return ""
        var result = input.replace(Regex("\\r\\n|\\r"), "\n")
        result = result.lines().joinToString("\n") { it.trimEnd() }
        result = multiNewlinesRegex.replace(result, "\n\n").trim()
        return result.lines().joinToString("\n") { it.trimStart() }
    }

    fun smartNormalize(input: String): String {
        if (input.isEmpty()) return ""
        var result = input

        result = result.replace('«', '"')
        result = result.replace('»', '"')
        result = result.replace('“', '"')
        result = result.replace('”', '"')
        result = result.replace('„', '"')

        result = result.replace(" — ", " - ")
        result = result.replace(" – ", " - ")
        result = result.replace(Regex("\\s+[-–—]{2,}\\s+"), " - ")

        return result.replace(Regex("\\.{3,}"), "…")
    }

    fun fullClean(input: String): String {
        var res = removeHiddenChars(input)
        res = fixParagraphs(res)
        res = smartNormalize(res)
        return removeExtraSpaces(res)
    }

    fun computeStats(text: String): TextStats {
        if (text.isEmpty()) return TextStats()
        val chars = text.length
        val words = wordsRegex.findAll(text).count()
        val lines = text.split("\n").size
        val paragraphs = text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }.size
        return TextStats(chars, words, lines, paragraphs)
    }
}

// ======================= COLORS / THEME =======================

private val BackgroundGray = Color(0xFFF8F9FA)
private val CardWhite = Color(0xFFFFFFFF)
private val BorderGray = Color(0xFFE9ECEF)
private val TextPrimary = Color(0xFF212529)
private val TextSecondary = Color(0xFF6C757D)
private val AccentBlue = Color(0xFF4263EB)
private val IconBgBlue = Color(0xFFE7EBFF)
private val IconBgGreen = Color(0xFFE3FAF0)
private val IconBgIndigo = Color(0xFFEDEBFE)

// ======================= MAIN ACTIVITY =======================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    background = BackgroundGray,
                    surface = CardWhite,
                    primary = AccentBlue
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundGray) {
                    TextProcessorScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextProcessorScreen() {
    val context = LocalContext.current
    val clipboardManager = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val coroutineScope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var showFeaturesSection by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val inputStats = remember(inputText) { TextProcessor.computeStats(inputText) }
    val outputStats = remember(outputText) { TextProcessor.computeStats(outputText) }
    val scrollState = rememberScrollState()

    val onAction: (String) -> Unit = { processedText ->
        outputText = processedText
        selectedTab = 1
        coroutineScope.launch {
            scrollState.animateScrollTo(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обработчик текста", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary) },
                actions = {
                    Row(modifier = Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Language, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RU", color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundGray, titleContentColor = TextPrimary)
            )
        },
        containerColor = BackgroundGray
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Text(
                text = "Очистите и отформатируйте ваш текст с помощью продвинутых инструментов обработки",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 12.dp)
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = BackgroundGray,
                contentColor = AccentBlue,
                modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(CardWhite).border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(12.dp)),
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Исходный текст", fontSize = 14.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Обработанный текст", fontSize = 14.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                )
            }

            Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(horizontal = 16.dp)) {
                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTab == 0) {
                    InputCard(
                        inputText = inputText,
                        onInputChange = { inputText = it },
                        stats = inputStats,
                        onClear = { inputText = ""; outputText = "" },
                        onPaste = {
                            val clipData = clipboardManager.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                inputText = clipData.getItemAt(0).coerceToText(context).toString()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ActionButtonsSection(onAction = onAction, inputText = inputText)
                } else {
                    OutputCard(
                        outputText = outputText,
                        stats = outputStats,
                        onClear = { outputText = "" },
                        onCopy = {
                            val clip = ClipData.newPlainText("Processed Text", outputText)
                            clipboardManager.setPrimaryClip(clip)
                            Toast.makeText(context, "Скопировано в буфер", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                FeaturesSection(expanded = showFeaturesSection, onToggle = { showFeaturesSection = !showFeaturesSection })
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// ======================= COMPONENT CARDS =======================

@Composable
fun InputCard(inputText: String, onInputChange: (String) -> Unit, stats: TextStats, onClear: () -> Unit, onPaste: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, BorderGray)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Текст для очистки", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onPaste, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ContentPaste, null, tint = AccentBlue, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Delete, null, tint = Color(0xFFDC3545), modifier = Modifier.size(18.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = inputText, onValueChange = onInputChange, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                placeholder = { Text("Вставьте ваш текст сюда для очистки...", color = TextSecondary, fontSize = 14.sp) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentBlue, unfocusedBorderColor = BorderGray),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Символов: ${stats.characters}", fontSize = 12.sp, color = TextSecondary)
                Text("Слов: ${stats.words}", fontSize = 12.sp, color = TextSecondary)
                Text("Строк: ${stats.lines}", fontSize = 12.sp, color = TextSecondary)
                Text("Абзацев: ${stats.paragraphs}", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun OutputCard(outputText: String, stats: TextStats, onClear: () -> Unit, onCopy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = CardWhite), border = BorderStroke(1.dp, BorderGray)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Результат", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onCopy, enabled = outputText.isNotEmpty(), modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ContentCopy, null, tint = AccentBlue, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Delete, null, tint = Color(0xFFDC3545), modifier = Modifier.size(18.dp)) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Внутренний скроллбокс для безопасного чтения огромных результатов без лагов интерфейса
            Box(modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp, max = 350.dp)
                .background(BackgroundGray, RoundedCornerShape(12.dp))
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
            ) {
                if (outputText.isEmpty()) {
                    Text("Обработанный текст появится здесь", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center))
                } else {
                    Text(outputText, fontSize = 14.sp, color = TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Символов: ${stats.characters}", fontSize = 12.sp, color = TextSecondary)
                Text("Слов: ${stats.words}", fontSize = 12.sp, color = TextSecondary)
                Text("Строк: ${stats.lines}", fontSize = 12.sp, color = TextSecondary)
                Text("Абзацев: ${stats.paragraphs}", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun ActionButtonsSection(onAction: (String) -> Unit, inputText: String) {
    val actions = listOf(
        ActionItem("Убрать пробелы", Icons.Filled.ContentCut) { TextProcessor.removeExtraSpaces(it) },
        ActionItem("Одна строка", Icons.Filled.Description) { TextProcessor.toSingleLine(it) },
        ActionItem("Исправить абзацы", Icons.Filled.FormatAlignLeft) { TextProcessor.fixParagraphs(it) },
        ActionItem("Нормализовать", Icons.Filled.AutoFixHigh) { TextProcessor.smartNormalize(it) }
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Быстрые действия", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(bottom = 12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.take(2).forEach { action ->
                ActionButton(action, onClick = { onAction(action.transform(inputText)) }, modifier = Modifier.weight(1f), enabled = inputText.isNotEmpty())
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.drop(2).forEach { action ->
                ActionButton(action, onClick = { onAction(action.transform(inputText)) }, modifier = Modifier.weight(1f), enabled = inputText.isNotEmpty())
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { onAction(TextProcessor.fullClean(inputText)) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
            enabled = inputText.isNotEmpty()
        ) {
            Icon(Icons.Filled.CleaningServices, null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Полная очистка текста", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ActionButton(action: ActionItem, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) BorderGray else BorderGray.copy(alpha = 0.5f)),
        color = CardWhite
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(action.icon, null, tint = if (enabled) AccentBlue else TextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(action.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun FeaturesSection(expanded: Boolean, onToggle: () -> Unit) {
    val features = listOf(
        FeatureItem("Удаление скрытых символов", "Очистка от невидимых байтов Unicode и мусора", Icons.Filled.VisibilityOff, IconBgBlue, AccentBlue),
        FeatureItem("Очистка Markdown", "Удаление заголовков, списков и форматирования", Icons.Filled.Code, IconBgGreen, Color(0xFF28A745)),
        FeatureItem("Умная типографика", "Замена кавычек, тире и многоточий на правильные", Icons.Filled.FormatQuote, IconBgIndigo, Color(0xFF6610F2))
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Возможности процессора", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                features.forEach { feature ->
                    FeatureCard(feature)
                }
            }
        }
    }
}

@Composable
fun FeatureCard(feature: FeatureItem) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardWhite, RoundedCornerShape(12.dp)).border(BorderStroke(1.dp, BorderGray), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).background(feature.iconBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(feature.icon, null, tint = feature.iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(feature.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(feature.description, fontSize = 12.sp, color = TextSecondary)
        }
    }
}
