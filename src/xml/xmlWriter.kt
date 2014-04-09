package kanva.annotations.xml

import java.io.Writer
import java.io.File
import java.io.FileWriter
import java.util.HashSet
import java.util.ArrayList
import java.util.HashMap

import kanva.annotations.*
import kanva.annotations.xml.*
import kanva.declarations.*
import kanva.util.*

fun isInteresting(pkg: String): Boolean {
    return pkg.startsWith("java") || pkg.startsWith("javax") || pkg.startsWith("org")
}

fun writeAnnotationsToXmlByPackage(annotations: Annotations<Nullability>, outDir: String) {
    val members = HashSet<ClassMember>()
    annotations.forEachPosition { pos, ann -> members.add(pos.member) }
    val sortedMembers = members.sortBy {it.toString()}

    val sortedParams = ArrayList<AnnotationPosition>()

    for (member in sortedMembers) {
        when (member) {
            is Method ->
                PositionsForMethod(member).forEachValidPosition { pos ->
                    if (annotations[pos] == Nullability.NOT_NULL) {
                        if (pos.relativePosition is ParameterPosition) {
                            sortedParams.add(pos)
                        }
                    }
                }
        }
    }

    doWrite(File(outDir), sortedParams)
}

private fun doWrite(destination: File, positions: List<AnnotationPosition>) {
    val positionsByPackage = HashMap<String, MutableList<AnnotationPosition>>()
    for (pos in positions) {
        val packageName = pos.member.getInternalPackageName()
        positionsByPackage.getOrPut(packageName, { arrayListOf() }).add(pos)
    }

    for ((path, pathAnnotations) in positionsByPackage) {
        val outputDir = File(destination, path)
        outputDir.mkdirs()
        val outputFile = File(outputDir, "annotations.xml")
        val writer = FileWriter(outputFile)
        writeAnnotationsToXML(writer, pathAnnotations)
    }
}


// only for not nulls for now
fun writeAnnotationsToXML(writer: Writer, annotations: List<AnnotationPosition>) {
    val sb = StringBuilder()
    val printer = XmlPrinter(sb)
    printer.openTag("root")
    printer.pushIndent()
    for (typePosition in annotations) {
        printer.openTag("item", hashMapOf("name" to typePosition.toAnnotationKey()))
        printer.pushIndent()
        printer.openTag("annotation", hashMapOf("name" to "org.jetbrains.annotations.NotNull"), true)
        printer.popIndent()
        printer.closeTag("item")
    }
    printer.popIndent()
    printer.closeTag("root")

    writer.write(sb.toString())
    writer.close()
}

val LINE_SEPARATOR: String = System.getProperty("line.separator")!!

private class XmlPrinter(val sb: StringBuilder) {
    private val INDENTATION_UNIT = "    ";
    private var indent = "";

    public fun println() {
        sb.append(LINE_SEPARATOR)
    }

    fun openTag(tagName: String, attributes: Map<String, String>? = null, isClosed: Boolean = false, quoteChar : Char = '\'') {
        sb.append(indent)
        sb.append("<").append(tagName)
        if (attributes != null) {
            for ((name, value) in attributes) {
                sb.append(" ").append(escape(name)).append("=").append(quoteChar).append(escape(value)).append(quoteChar)
            }
        }
        if (isClosed) {
            sb.append("/>")
        }
        else {
            sb.append(">")
        }
        println()
    }

    fun closeTag(tagName: String) {
        sb.append(indent);
        sb.append("</").append(tagName).append(">")
        println()
    }

    public fun pushIndent() {
        indent += INDENTATION_UNIT;
    }

    public fun popIndent() {
        if (indent.length() < INDENTATION_UNIT.length()) {
            throw IllegalStateException("No indentation to pop");
        }

        indent = indent.substring(INDENTATION_UNIT.length());
    }
}

private fun escape(str: String): String {
    return buildString {
        sb ->
        for (c in str) {
            when {
                c == '<' -> sb.append("&lt;")
                c == '>' -> sb.append("&gt;")
                c == '\"' || c == '\'' -> {
                    sb.append("&quot;")
                }
                else -> sb.append(c);
            }
        }
    }
}

