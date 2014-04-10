package kanva.util

import java.io.File

import org.objectweb.asm.Type
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.FieldNode

import kanva.declarations.*
import java.util.jar.JarFile
import org.objectweb.asm.Opcodes
import kanva.graphs.Node
import java.util.regex.Matcher
import java.util.LinkedHashSet

fun processJar(file: File, block: (jarFile: File, classType: Type, classReader: ClassReader) -> Unit) {
    val jar = JarFile(file)
    for (entry in jar.entries()) {
        val name = entry.getName()
        if (!name.endsWith(".class")) continue

        val internalName = name.removeSuffix(".class")
        val classType = Type.getType("L$internalName;")

        val inputStream = jar.getInputStream(entry)
        val classReader = ClassReader(inputStream)

        block(file, classType, classReader)
    }
}

public fun Method.createMethodNodeStub(): MethodNode =
        MethodNode(access.flags, id.methodName, id.methodDesc, genericSignature, null)

public fun Field.createFieldNodeStub(): FieldNode =
        FieldNode(access.flags, id.fieldName, desc, genericSignature, null)

public fun Type.isPrimitive() : Boolean =
        when (getSort()) {
            Type.VOID, Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT,
            Type.INT, Type.FLOAT, Type.LONG, Type.DOUBLE ->
                true
            else ->
                false
        }

fun ClassMember.getInternalPackageName(): String {
    val className = declaringClass.internal
    val delimiter = className.lastIndexOf('/')
    return if (delimiter >= 0) className.substring(0, delimiter) else ""
}

public fun <T> java.util.Enumeration<out T>.iterator(): Iterator<T> = object: Iterator<T> {
    override fun hasNext(): Boolean = hasMoreElements()
    public override fun next() : T = nextElement()
}

fun Int.isReturn() =
        this == Opcodes.IRETURN ||
        this == Opcodes.LRETURN ||
        this == Opcodes.FRETURN ||
        this == Opcodes.DRETURN ||
        this == Opcodes.ARETURN ||
        this == Opcodes.RETURN

fun Int.isNotVoidReturn() =
        this == Opcodes.IRETURN ||
        this == Opcodes.LRETURN ||
        this == Opcodes.FRETURN ||
        this == Opcodes.DRETURN ||
        this == Opcodes.ARETURN


fun Int.isThrow() =
        this == Opcodes.ATHROW

val Node<Int>.insnIndex: Int
    get() = data

public fun String.removeSuffix(suffix: String): String {
    if (!endsWith(suffix)) return this
    return substring(0, size - suffix.size)
}

public fun buildString(body: (sb: StringBuilder) -> Unit): String {
    val sb = StringBuilder()
    body(sb)
    return sb.toString()
}

fun String.suffixAfterLast(delimiter: String): String {
    val index = this.lastIndexOf(delimiter)
    if (index < 0) return this
    return this.substring(index + 1)
}

public fun String.prefix(length: Int): String = if (length == 0) "" else substring(0, length)

public fun String.prefixUpToLast(ch: Char): String? {
    val lastIndex = lastIndexOf(ch)
    return if (lastIndex != -1) prefix(lastIndex) else null
}

public fun File.recurseFiltered(fileFilter: (File) -> Boolean = {true}, block: (File) -> Unit): Unit {
    if (fileFilter(this)) {
        block(this)
    }
    if (this.isDirectory()) {
        for (child in this.listFiles()!!) {
            child.recurseFiltered(fileFilter, block)
        }
    }
}

fun Matcher.get(groupIndex: Int): String? = group(groupIndex)

public fun <T> Collection<T>.union(other: Collection<T>): Set<T> {
    val resultSet = LinkedHashSet(this)
    resultSet.addAll(other)
    return resultSet
}

public fun flags(f1: Int, f2: Int): Int = f1 or f2
