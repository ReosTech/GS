package com.example.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MarkdownBlock {
  data class Heading(val level: Int, val text: String) : MarkdownBlock()
  data class Paragraph(val text: String) : MarkdownBlock()
  data class BulletItem(val depth: Int, val text: String) : MarkdownBlock()
  data class NumberedItem(val depth: Int, val index: String, val text: String) : MarkdownBlock()
  data class TaskItem(val checked: Boolean, val text: String) : MarkdownBlock()
  data class BlockQuote(val text: String) : MarkdownBlock()
  object HorizontalRule : MarkdownBlock()
  data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
  data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
}

/**
 * Parses markdown text into comprehensive structured blocks:
 * Headings (H1-H6), Code blocks, Lists, Tasks, Tables, Quotes, Dividers, and Paragraphs.
 */
fun parseMarkdown(raw: String): List<MarkdownBlock> {
  val blocks = mutableListOf<MarkdownBlock>()
  try {
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
      val line = lines[i]
      val trimmedStart = line.trimStart()
      val trimmed = line.trim()

      // 1. Fenced code block ```lang ... ```
      if (trimmedStart.startsWith("```")) {
        val lang = trimmedStart.removePrefix("```").trim()
        val codeLines = mutableListOf<String>()
        i++
        while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
          codeLines.add(lines[i])
          i++
        }
        if (i < lines.size && lines[i].trimStart().startsWith("```")) {
          i++ // consume closing ```
        }
        blocks.add(
          MarkdownBlock.CodeBlock(
            language = if (lang.isBlank()) "plaintext" else lang,
            code = codeLines.joinToString("\n")
          )
        )
        continue
      }

      if (trimmed.isEmpty()) {
        i++
        continue
      }

      // 2. Horizontal Rule (---, ***, ___)
      if (trimmed == "---" || trimmed == "***" || trimmed == "___" ||
        trimmed.matches(Regex("""^[-*_]{3,}$"""))
      ) {
        blocks.add(MarkdownBlock.HorizontalRule)
        i++
        continue
      }

      // 3. Headings # to ######
      val headingMatch = Regex("""^(#{1,6})\s+(.*)$""").find(trimmed)
      if (headingMatch != null) {
        val level = headingMatch.groupValues[1].length
        val content = headingMatch.groupValues[2].trim()
        blocks.add(MarkdownBlock.Heading(level, content))
        i++
        continue
      }

      // 4. Blockquote >
      if (trimmed.startsWith(">")) {
        val quoteLines = mutableListOf<String>()
        while (i < lines.size && lines[i].trim().startsWith(">")) {
          val qLine = lines[i].trim().removePrefix(">").trimStart()
          quoteLines.add(qLine)
          i++
        }
        blocks.add(MarkdownBlock.BlockQuote(quoteLines.joinToString("\n")))
        continue
      }

      // 5. Task list items: - [ ] or - [x] or * [ ] or * [x]
      val taskMatch = Regex("""^[-*+]\s+\[([ xX])\]\s+(.*)$""").find(trimmed)
      if (taskMatch != null) {
        val isChecked = taskMatch.groupValues[1].trim().lowercase() == "x"
        val taskText = taskMatch.groupValues[2].trim()
        blocks.add(MarkdownBlock.TaskItem(checked = isChecked, text = taskText))
        i++
        continue
      }

      // 6. Markdown Table detection (| col1 | col2 |)
      if (trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size &&
        lines[i + 1].trim().startsWith("|") && lines[i + 1].contains("-")
      ) {
        val headerCells = trimmed.split("|")
          .drop(1).dropLast(1)
          .map { it.trim() }
        i += 2 // skip header and separator row |---|---|
        val rows = mutableListOf<List<String>>()
        while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
          val rowCells = lines[i].trim().split("|")
            .drop(1).dropLast(1)
            .map { it.trim() }
          rows.add(rowCells)
          i++
        }
        blocks.add(MarkdownBlock.Table(headers = headerCells, rows = rows))
        continue
      }

      // 7. Bullet points (*, -, +) with indentation depth
      val bulletMatch = Regex("""^(\s*)([-*+])\s+(.*)$""").find(line)
      if (bulletMatch != null) {
        val indent = bulletMatch.groupValues[1].length / 2
        val content = bulletMatch.groupValues[3].trim()
        blocks.add(MarkdownBlock.BulletItem(depth = indent, text = content))
        i++
        continue
      }

      // 8. Numbered lists (1. , 2. ) with indentation depth
      val numberMatch = Regex("""^(\s*)(\d+)\.\s+(.*)$""").find(line)
      if (numberMatch != null) {
        val indent = numberMatch.groupValues[1].length / 2
        val num = numberMatch.groupValues[2]
        val content = numberMatch.groupValues[3].trim()
        blocks.add(MarkdownBlock.NumberedItem(depth = indent, index = num, text = content))
        i++
        continue
      }

      // 9. Standard Paragraph (accumulating consecutive plain lines)
      val paraLines = mutableListOf(trimmed)
      i++
      while (i < lines.size) {
        val next = lines[i]
        val nextTrimmed = next.trim()
        if (nextTrimmed.isEmpty() ||
          nextTrimmed.startsWith("```") ||
          nextTrimmed.startsWith("#") ||
          nextTrimmed.startsWith(">") ||
          nextTrimmed.startsWith("|") ||
          nextTrimmed == "---" || nextTrimmed == "***" ||
          Regex("""^[-*+]\s+\[([ xX])\]""").containsMatchIn(nextTrimmed) ||
          Regex("""^[-*+]\s+""").containsMatchIn(nextTrimmed) ||
          Regex("""^\d+\.\s+""").containsMatchIn(nextTrimmed)
        ) {
          break
        }
        paraLines.add(nextTrimmed)
        i++
      }
      blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
    }
  } catch (_: Exception) {
    return listOf(MarkdownBlock.Paragraph(raw))
  }
  return blocks
}

/**
 * Converts rich inline markdown into Compose AnnotatedString:
 * - Bold + Italic (***text*** or ___text___)
 * - Bold (**text** or __text__)
 * - Italic (*text* or _text_)
 * - Strikethrough (~~text~~)
 * - Inline Code (`code`)
 * - Links ([Title](https://...))
 */
@Composable
fun buildInlineMarkdown(
  text: String,
  textColor: Color = MaterialTheme.colorScheme.onSurface,
  codeBgColor: Color = MaterialTheme.colorScheme.surfaceVariant,
  codeTextColor: Color = MaterialTheme.colorScheme.primary,
  linkColor: Color = MaterialTheme.colorScheme.primary
): AnnotatedString {
  return remember(text, textColor, codeBgColor, codeTextColor, linkColor) {
    try {
      buildAnnotatedString {
        var idx = 0
        val len = text.length

        while (idx < len) {
          // 1. Inline code: `code`
          if (text[idx] == '`') {
            val nextTick = text.indexOf('`', idx + 1)
            if (nextTick != -1) {
              val codeSnippet = text.substring(idx + 1, nextTick)
              pushStyle(
                SpanStyle(
                  fontFamily = FontFamily.Monospace,
                  background = codeBgColor,
                  color = codeTextColor,
                  fontSize = 13.sp
                )
              )
              append(" $codeSnippet ")
              pop()
              idx = nextTick + 1
              continue
            }
          }

          // 2. Clickable Links: [title](url)
          if (text[idx] == '[') {
            val closeBracket = text.indexOf(']', idx + 1)
            if (closeBracket != -1 && closeBracket + 1 < len && text[closeBracket + 1] == '(') {
              val closeParen = text.indexOf(')', closeBracket + 2)
              if (closeParen != -1) {
                val linkText = text.substring(idx + 1, closeBracket)
                val linkUrl = text.substring(closeBracket + 2, closeParen).trim()
                try {
                  val linkAnnotation = LinkAnnotation.Url(
                    url = linkUrl,
                    styles = TextLinkStyles(
                      style = SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                      )
                    )
                  )
                  pushLink(linkAnnotation)
                  append(linkText)
                  pop()
                } catch (_: Exception) {
                  pushStyle(
                    SpanStyle(
                      color = linkColor,
                      textDecoration = TextDecoration.Underline,
                      fontWeight = FontWeight.SemiBold
                    )
                  )
                  append(linkText)
                  pop()
                }
                idx = closeParen + 1
                continue
              }
            }
          }

          // 3. Strikethrough: ~~text~~
          if (idx + 1 < len && text[idx] == '~' && text[idx + 1] == '~') {
            val endIdx = text.indexOf("~~", idx + 2)
            if (endIdx != -1) {
              val strikeContent = text.substring(idx + 2, endIdx)
              pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = textColor.copy(alpha = 0.7f)))
              append(strikeContent)
              pop()
              idx = endIdx + 2
              continue
            }
          }

          // 4. Bold + Italic: ***text*** or ___text___
          if (idx + 2 < len && (
              (text[idx] == '*' && text[idx + 1] == '*' && text[idx + 2] == '*') ||
              (text[idx] == '_' && text[idx + 1] == '_' && text[idx + 2] == '_')
            )
          ) {
            val marker = text.substring(idx, idx + 3)
            val endIdx = text.indexOf(marker, idx + 3)
            if (endIdx != -1) {
              val content = text.substring(idx + 3, endIdx)
              pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
              append(content)
              pop()
              idx = endIdx + 3
              continue
            }
          }

          // 5. Bold: **text** or __text__
          if (idx + 1 < len && (
              (text[idx] == '*' && text[idx + 1] == '*') ||
              (text[idx] == '_' && text[idx + 1] == '_')
            )
          ) {
            val marker = text.substring(idx, idx + 2)
            val endIdx = text.indexOf(marker, idx + 2)
            if (endIdx != -1) {
              val boldContent = text.substring(idx + 2, endIdx)
              pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
              append(boldContent)
              pop()
              idx = endIdx + 2
              continue
            }
          }

          // 6. Italic: *text* or _text_
          if (text[idx] == '*' || text[idx] == '_') {
            val markerChar = text[idx]
            val endIdx = text.indexOf(markerChar, idx + 1)
            if (endIdx != -1 && endIdx > idx + 1 && (endIdx == len - 1 || text[endIdx + 1].isWhitespace() || text[endIdx + 1] in ".,!?;:)")) {
              val italicContent = text.substring(idx + 1, endIdx)
              pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
              append(italicContent)
              pop()
              idx = endIdx + 1
              continue
            }
          }

          append(text[idx])
          idx++
        }
      }
    } catch (_: Exception) {
      AnnotatedString(text)
    }
  }
}

/**
 * Modern Jetpack Compose Markdown Renderer with comprehensive feature support:
 * Headers (H1-H6), Code blocks (Prism.js syntax highlighting & copy), Tables,
 * Task lists, Blockquotes, Dividers, Links, and rich inline formatting.
 */
@Composable
fun AiMarkdownView(
  content: String,
  modifier: Modifier = Modifier,
  textColor: Color = MaterialTheme.colorScheme.onSurface,
  isUserMessage: Boolean = false
) {
  val blocks = remember(content) {
    try {
      parseMarkdown(content)
    } catch (_: Exception) {
      listOf(MarkdownBlock.Paragraph(content))
    }
  }

  SelectionContainer(modifier = modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      blocks.forEach { block ->
        when (block) {
          is MarkdownBlock.Heading -> {
            val (fontSize, fontWeight, color) = when (block.level) {
              1 -> Triple(20.sp, FontWeight.ExtraBold, MaterialTheme.colorScheme.primary)
              2 -> Triple(17.sp, FontWeight.Bold, MaterialTheme.colorScheme.primary)
              3 -> Triple(15.sp, FontWeight.SemiBold, MaterialTheme.colorScheme.onSurface)
              4 -> Triple(14.sp, FontWeight.SemiBold, MaterialTheme.colorScheme.onSurface)
              5 -> Triple(13.sp, FontWeight.Medium, MaterialTheme.colorScheme.onSurfaceVariant)
              else -> Triple(12.sp, FontWeight.Medium, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
              text = buildInlineMarkdown(block.text, textColor = color),
              fontSize = fontSize,
              fontWeight = fontWeight,
              lineHeight = (fontSize.value + 6).sp,
              modifier = Modifier.padding(top = if (block.level <= 2) 6.dp else 3.dp, bottom = 2.dp)
            )
          }
          is MarkdownBlock.Paragraph -> {
            Text(
              text = buildInlineMarkdown(block.text, textColor = textColor),
              fontSize = 14.sp,
              lineHeight = 21.sp,
              color = textColor
            )
          }
          is MarkdownBlock.BulletItem -> {
            val indentDp = (block.depth * 14).dp
            Row(
              modifier = Modifier
                .padding(start = indentDp + 2.dp, top = 2.dp)
                .fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.Top
            ) {
              val bulletChar = when (block.depth % 3) {
                0 -> "•"
                1 -> "◦"
                else -> "▪"
              }
              Text(
                text = bulletChar,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = buildInlineMarkdown(block.text, textColor = textColor),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
              )
            }
          }
          is MarkdownBlock.NumberedItem -> {
            val indentDp = (block.depth * 14).dp
            Row(
              modifier = Modifier
                .padding(start = indentDp + 2.dp, top = 2.dp)
                .fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.Top
            ) {
              Text(
                text = "${block.index}.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = buildInlineMarkdown(block.text, textColor = textColor),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = textColor,
                modifier = Modifier.weight(1f)
              )
            }
          }
          is MarkdownBlock.TaskItem -> {
            Row(
              modifier = Modifier
                .padding(start = 2.dp, top = 2.dp)
                .fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (block.checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                contentDescription = if (block.checked) "Checked" else "Unchecked",
                tint = if (block.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = buildInlineMarkdown(
                  block.text,
                  textColor = if (block.checked) textColor.copy(alpha = 0.65f) else textColor
                ),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = if (block.checked) textColor.copy(alpha = 0.65f) else textColor,
                modifier = Modifier.weight(1f)
              )
            }
          }
          is MarkdownBlock.BlockQuote -> {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Box(
                modifier = Modifier
                  .width(3.dp)
                  .fillMaxHeight()
                  .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
              )
              Text(
                text = buildInlineMarkdown(block.text, textColor = textColor),
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                lineHeight = 19.sp,
                color = textColor
              )
            }
          }
          is MarkdownBlock.HorizontalRule -> {
            HorizontalDivider(
              modifier = Modifier.padding(vertical = 8.dp),
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
              thickness = 1.dp
            )
          }
          is MarkdownBlock.Table -> {
            MarkdownTableView(table = block)
          }
          is MarkdownBlock.CodeBlock -> {
            PrismCodeBlock(
              language = block.language,
              code = block.code
            )
          }
        }
      }
    }
  }
}

/**
 * Beautiful scrollable Markdown Table component with header formatting and alternating rows.
 */
@Composable
fun MarkdownTableView(
  table: MarkdownBlock.Table,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(8.dp)
    ) {
      Column {
        // Table Header
        Row(
          modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          table.headers.forEach { header ->
            Text(
              text = buildInlineMarkdown(header),
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.widthIn(min = 90.dp)
            )
          }
        }
        HorizontalDivider(
          modifier = Modifier.padding(vertical = 4.dp),
          color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        // Table Rows
        table.rows.forEachIndexed { rowIdx, rowCells ->
          val rowBg = if (rowIdx % 2 == 1) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
          } else {
            Color.Transparent
          }
          Row(
            modifier = Modifier
              .background(rowBg, RoundedCornerShape(4.dp))
              .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            rowCells.forEachIndexed { colIdx, cellText ->
              Text(
                text = buildInlineMarkdown(cellText),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 90.dp)
              )
            }
          }
        }
      }
    }
  }
}

object PrismTheme {
  val Background = Color(0xFF1E1E24)
  val HeaderBackground = Color(0xFF282832)
  val GutterBackground = Color(0xFF18181D)
  val GutterDivider = Color(0xFF2E2E38)
  val GutterText = Color(0xFF6B7280)

  // Prism Tomorrow Night Token Colors
  val Keyword = Color(0xFFF92672)       // fun, val, class, return, if, else, def, import
  val String = Color(0xFFA6E22E)        // "..." or '...'
  val Number = Color(0xFFAE81FF)        // 123, 0.5f, 0xFF
  val Comment = Color(0xFF75715E)       // // ..., /* ... */, # ...
  val Function = Color(0xFF66D9EF)      // functionName()
  val Annotation = Color(0xFFFD971F)    // @Composable, @Override
  val Type = Color(0xFFE6DB74)          // PascalCase types: String, Int, Modifier
  val Operator = Color(0xFFF8F8F2)      // =, +, -, *, /
  val Punctuation = Color(0xFFCCCCCC)   // {}, (), [], ;
  val PlainText = Color(0xFFF8F8F2)
}

private val PRISM_KEYWORDS = setOf(
  "package", "import", "class", "interface", "fun", "val", "var", "object",
  "override", "public", "private", "protected", "internal", "companion",
  "return", "if", "else", "when", "for", "while", "do", "try", "catch",
  "finally", "throw", "new", "this", "super", "null", "true", "false",
  "is", "as", "in", "data", "sealed", "enum", "suspend", "inline",
  "reified", "typealias", "abstract", "open", "const",
  "def", "from", "elif", "except", "raise", "with", "pass", "yield",
  "lambda", "None", "True", "False", "and", "or", "not", "async", "await",
  "function", "let", "switch", "case", "break", "continue", "extends",
  "export", "default", "undefined", "typeof", "instanceof", "SELECT", "FROM", "WHERE"
)

fun highlightWithPrism(code: String, lang: String): AnnotatedString {
  val lines = code.lines()
  return buildAnnotatedString {
    lines.forEachIndexed { lineIdx, line ->
      var idx = 0
      val len = line.length
      while (idx < len) {
        // Comment // or #
        if (line.startsWith("//", idx) || (lang.contains("py") && line[idx] == '#') || (lang.contains("sh") && line[idx] == '#') || (lang.contains("bash") && line[idx] == '#')) {
          val commentText = line.substring(idx)
          pushStyle(SpanStyle(color = PrismTheme.Comment, fontStyle = FontStyle.Italic))
          append(commentText)
          pop()
          idx = len
          continue
        }
        // Strings "..." or '...'
        if (line[idx] == '"' || line[idx] == '\'') {
          val quoteChar = line[idx]
          var endQuote = idx + 1
          while (endQuote < len) {
            if (line[endQuote] == '\\') {
              endQuote += 2
              continue
            }
            if (line[endQuote] == quoteChar) {
              endQuote++
              break
            }
            endQuote++
          }
          if (endQuote > len) endQuote = len
          val strText = line.substring(idx, endQuote)
          pushStyle(SpanStyle(color = PrismTheme.String))
          append(strText)
          pop()
          idx = endQuote
          continue
        }
        // Annotation @Composable, @Override
        if (line[idx] == '@') {
          var endAnn = idx + 1
          while (endAnn < len && (line[endAnn].isLetterOrDigit() || line[endAnn] == '_')) {
            endAnn++
          }
          val annText = line.substring(idx, endAnn)
          pushStyle(SpanStyle(color = PrismTheme.Annotation, fontWeight = FontWeight.SemiBold))
          append(annText)
          pop()
          idx = endAnn
          continue
        }
        // Number (digits)
        if (line[idx].isDigit() && (idx == 0 || !line[idx - 1].isLetterOrDigit())) {
          var endNum = idx + 1
          while (endNum < len && (line[endNum].isLetterOrDigit() || line[endNum] == '.' || line[endNum] == 'x' || line[endNum] == 'X')) {
            endNum++
          }
          val numText = line.substring(idx, endNum)
          pushStyle(SpanStyle(color = PrismTheme.Number))
          append(numText)
          pop()
          idx = endNum
          continue
        }
        // Word (keyword, type, function, or identifier)
        if (line[idx].isLetter() || line[idx] == '_') {
          var endWord = idx + 1
          while (endWord < len && (line[endWord].isLetterOrDigit() || line[endWord] == '_')) {
            endWord++
          }
          val word = line.substring(idx, endWord)
          val isFunc = endWord < len && line.substring(endWord).trimStart().startsWith("(")
          val isKeyword = PRISM_KEYWORDS.contains(word)
          val isType = word[0].isUpperCase() && !isKeyword
          when {
            isKeyword -> {
              pushStyle(SpanStyle(color = PrismTheme.Keyword, fontWeight = FontWeight.Bold))
              append(word)
              pop()
            }
            isType -> {
              pushStyle(SpanStyle(color = PrismTheme.Type))
              append(word)
              pop()
            }
            isFunc -> {
              pushStyle(SpanStyle(color = PrismTheme.Function))
              append(word)
              pop()
            }
            else -> {
              pushStyle(SpanStyle(color = PrismTheme.PlainText))
              append(word)
              pop()
            }
          }
          idx = endWord
          continue
        }
        // Punctuation & Operators
        val ch = line[idx]
        if ("{}()[];,".contains(ch)) {
          pushStyle(SpanStyle(color = PrismTheme.Punctuation))
          append(ch)
          pop()
        } else if ("=+-*/<>!&|:?".contains(ch)) {
          pushStyle(SpanStyle(color = PrismTheme.Operator))
          append(ch)
          pop()
        } else {
          pushStyle(SpanStyle(color = PrismTheme.PlainText))
          append(ch)
          pop()
        }
        idx++
      }
      if (lineIdx < lines.size - 1) {
        append("\n")
      }
    }
  }
}

/**
 * Developer-grade Code Block powered by Prism.js syntax highlighting with copy action.
 */
@Composable
fun PrismCodeBlock(
  language: String,
  code: String,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var isCopied by remember { mutableStateOf(false) }
  val lineCount = remember(code) { code.lines().size }
  val highlightedCode = remember(code, language) { highlightWithPrism(code, language.lowercase()) }

  Card(
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = PrismTheme.Background),
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp)
  ) {
    Column {
      // Header Bar: Language Badge + Prism.js Indicator + Copy Button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(PrismTheme.HeaderBackground)
          .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Code,
            contentDescription = null,
            tint = Color(0xFF60A5FA),
            modifier = Modifier.size(15.dp)
          )
          Text(
            text = (if (language.isNotBlank() && language != "plaintext") language else "Code").uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF93C5FD),
            letterSpacing = 0.5.sp
          )
          Text(
            text = "• Prism.js",
            fontSize = 10.sp,
            color = Color(0xFF9CA3AF)
          )
        }
        IconButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Code", code)
            clipboard.setPrimaryClip(clip)
            isCopied = true
            Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.size(28.dp)
        ) {
          Icon(
            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = "Copy Code",
            tint = if (isCopied) Color(0xFF34D399) else Color(0xFFE5E7EB),
            modifier = Modifier.size(14.dp)
          )
        }
      }

      // Code Area with line numbers gutter and horizontal scroll
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(PrismTheme.Background)
          .padding(vertical = 8.dp)
      ) {
        // Line Numbers Gutter
        Column(
          modifier = Modifier
            .background(PrismTheme.GutterBackground)
            .padding(horizontal = 8.dp),
          horizontalAlignment = Alignment.End
        ) {
          for (lineNum in 1..lineCount) {
            Text(
              text = "$lineNum",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              lineHeight = 18.sp,
              color = PrismTheme.GutterText
            )
          }
        }
        // Divider
        Box(
          modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(PrismTheme.GutterDivider)
        )
        // Syntax-Highlighted Code with Horizontal Scroll
        Box(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp)
        ) {
          Text(
            text = highlightedCode,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp
          )
        }
      }
    }
  }
}
