package kanva.annotations.xml

import java.util.regex.Pattern
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor
import org.objectweb.asm.Opcodes

import kanva.annotations.*
import kanva.declarations.*
import kanva.util.*

// annotations.xml - reading/writing
data class MethodAnnotationKeyData(
        val canonicalClassName: String,
        val returnType: String,
        val methodName: String
)

data class FieldAnnotationKeyData(
        val canonicalClassName: String,
        val fieldName: String
)

private val METHOD_ANNOTATION_KEY_PATTERN = Pattern.compile("""([\w\.]+\$?) (.*?)\s?([\w<>$]+)\(""")
private val FIELD_ANNOTATION_KEY_PATTERN = Pattern.compile("""(\w+(\.\w+)*\$?)\s+([\w\$]+)""")

// reading
fun tryParseMethodAnnotationKey(annotationKey: String): MethodAnnotationKeyData? {
    val matcher = METHOD_ANNOTATION_KEY_PATTERN.matcher(annotationKey)
    if (!matcher.find()) {
        return null
    }
    return MethodAnnotationKeyData(matcher[1]!!, matcher[2]!!, matcher[3]!!)
}

fun tryParseFieldAnnotationKey(annotationKey: String): FieldAnnotationKeyData? {
    val matcher = FIELD_ANNOTATION_KEY_PATTERN.matcher(annotationKey)
    if (!matcher.find()) {
        return null
    }
    return FieldAnnotationKeyData(matcher[1]!!, matcher[3]!!)
}

// serialization
fun AnnotationPosition.toAnnotationKey(): String {
    return when(this) {
        is MethodPosition -> method.toAnnotationKeyPrefix() + relativePosition.toAnnotationKeySuffix(method)
        is FieldPosition -> field.toFieldAnnotationKey()
        else -> throw UnsupportedOperationException()
    }
}

private fun Field.toFieldAnnotationKey() : String {
    return "${declaringClass.canonicalName} ${id.fieldName}"
}

fun Method.toAnnotationKeyPrefix(): String {
    return declaringClass.canonicalName + " " +
    getAnnotationKeyReturnTypeString() +
    getMethodNameAccountingForConstructor() + parameterTypesString()
}

private fun Method.getAnnotationKeyReturnTypeString(): String
        = if (id.methodName == "<init>")
    ""
else if (genericSignature != null) {
    renderReturnType(genericSignature) + " "
}
else canonicalName(getReturnType()) + " "

private fun canonicalName(_type: Type): String {
    return _type.getClassName()?.toCanonical() ?: "!null"
}

fun Method.getMethodNameAccountingForConstructor(): String {
    if (id.methodName == "<init>") return declaringClass.simple
    return id.methodName
}

private fun PositionWithinDeclaration.toAnnotationKeySuffix(method: Method): String {
    return when (this) {
        RETURN_POSITION -> ""
        is ParameterPosition -> " " + correctIfNotStatic(method, this.index)
        else -> throw IllegalArgumentException("Unknown position: $this")
    }
}

private fun correctIfNotStatic(method: Method, parameterIndex: Int): Int {
    // 'this' has index 0
    return if (method.isStatic()) parameterIndex else parameterIndex - 1
}

private fun Method.parameterTypesString(): String {
    val result = if (genericSignature == null) {
        (id.getArgumentTypes() map {it -> canonicalName(it) }).makeString(", ", "(", ")")
    }
    else {
        renderMethodParameters(genericSignature)
    }
    if (this.isVarargs()) {
        return result.replaceAll("""\[\]\)""", "...)")
    }
    return result
}

// generics

fun renderMethodParameters(genericSignature: String): String {
    val renderer = GenericMethodParametersRenderer()
    SignatureReader(genericSignature).accept(renderer)
    return renderer.parameters()
}

fun renderReturnType(genericSignature: String): String {
    val sb = StringBuilder()
    SignatureReader(genericSignature).accept(object : SignatureVisitor(Opcodes.ASM4) {

        public override fun visitReturnType(): SignatureVisitor {
            return GenericTypeRenderer(sb)
        }
    })
    return sb.toString()
}

private class GenericMethodParametersRenderer: SignatureVisitor(Opcodes.ASM4) {

    private val sb = StringBuilder("(")
    private var first = true

    fun parameters(): String = sb.toString() + ")"

    override fun visitParameterType(): SignatureVisitor {
        if (first) {
            first = false
            // "(" is already appended
        }
        else {
            sb.append(", ")
        }
        return GenericTypeRenderer(sb)
    }
}

private open class GenericTypeRenderer(val sb: StringBuilder): SignatureVisitor(Opcodes.ASM4) {

    private var angleBracketOpen = false

    private fun openAngleBracketIfNeeded(): Boolean {
        if (!angleBracketOpen) {
            angleBracketOpen = true
            sb.append("<")
            return true
        }
        return false
    }

    private fun closeAngleBracketIfNeeded() {
        if (angleBracketOpen) {
            angleBracketOpen = false
            sb.append(">")
        }
    }

    private fun beforeTypeArgument() {
        val first = openAngleBracketIfNeeded()
        if (!first) sb.append(",")
    }

    protected open fun endType() {}

    override fun visitBaseType(descriptor: Char) {
        sb.append(when (descriptor) {
            'V' -> "void"
            'B' -> "byte"
            'J' -> "long"
            'Z' -> "boolean"
            'I' -> "int"
            'S' -> "short"
            'C' -> "char"
            'F' -> "float"
            'D' -> "double"
            else -> throw IllegalArgumentException("Unknown base type: $descriptor")
        })
        endType()
    }

    override fun visitTypeVariable(name: String) {
        sb.append(name)
        endType()
    }

    override fun visitArrayType(): SignatureVisitor {
        return object : GenericTypeRenderer(sb) {
            override fun endType() {
                sb.append("[]")
            }
        }
    }

    override fun visitClassType(name: String) {
        sb.append(name.internalNameToCanonical())
    }

    override fun visitInnerClassType(name: String) {
        closeAngleBracketIfNeeded()
        sb.append(".").append(name.internalNameToCanonical())

    }

    override fun visitTypeArgument() {
        beforeTypeArgument()
        sb.append("?")
    }

    override fun visitTypeArgument(wildcard: Char): SignatureVisitor {
        beforeTypeArgument()
        when (wildcard) {
            SignatureVisitor.EXTENDS -> sb.append("? extends ")
            SignatureVisitor.SUPER -> sb.append("? super ")
            SignatureVisitor.INSTANCEOF -> {}
            else -> throw IllegalArgumentException("Unknown wildcard: $wildcard")
        }
        return GenericTypeRenderer(sb)
    }

    override fun visitEnd() {
        closeAngleBracketIfNeeded()
        endType()
    }
}
