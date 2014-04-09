package kanva.annotations

import kanva.declarations.ClassMember
import kanva.declarations.Method
import kanva.declarations.Field
import java.util.HashMap
import kanva.declarations.isStatic
import kanva.declarations.getArgumentTypes
import java.util.ArrayList

trait PositionWithinDeclaration

object FIELD_POSITION: PositionWithinDeclaration
object RETURN_POSITION : PositionWithinDeclaration
data class ParameterPosition(val index: Int): PositionWithinDeclaration

trait AnnotationPosition {
    val member: ClassMember
    val relativePosition: PositionWithinDeclaration
}

trait MethodPosition : AnnotationPosition {
    val method: Method
}

trait FieldPosition : AnnotationPosition {
    val field: Field
}

class PositionsForMethod(val method: Method) {
    public fun get(positionWithinMethod: PositionWithinDeclaration): AnnotationPosition =
            MethodTypePositionImpl(method, positionWithinMethod)

    public fun forParameter(parameterIndex: Int): AnnotationPosition =
            get(ParameterPosition(parameterIndex))

    public fun forReturnType(): AnnotationPosition =
            get(RETURN_POSITION)
}

public fun getFieldPosition(field: Field) : AnnotationPosition =
        FieldTypePositionImpl(field)

fun PositionsForMethod.forEachValidPosition(body: (AnnotationPosition) -> Unit) {
    val skip = if (method.isStatic()) 0 else 1
    for (i in skip..method.getArgumentTypes().size) {
        body(forParameter(i))
    }
    body(forReturnType())
}

fun PositionsForMethod.getValidPositions(): Collection<AnnotationPosition> {
    val result = ArrayList<AnnotationPosition>()
    forEachValidPosition {result.add(it)}
    return result
}

private data class MethodTypePositionImpl(
        override val method: Method,
        override val relativePosition: PositionWithinDeclaration
) : MethodPosition {
    override val member: ClassMember get() { return method }
}

private data class FieldTypePositionImpl(override val field: Field): FieldPosition {
    override val member: ClassMember get() { return field }
    override val relativePosition: PositionWithinDeclaration = FIELD_POSITION
}

class Annotations<A: Any> {
    private val data = HashMap<AnnotationPosition, A>()
    fun get(typePosition: AnnotationPosition): A? {
        return data[typePosition]
    }
    fun set(typePosition: AnnotationPosition, annotation: A) {
        data[typePosition] = annotation
    }
    fun size() = data.size
    fun forEachPosition(block: (AnnotationPosition, A) -> Unit) {
        for ((pos, ann) in data) block(pos, ann)
    }
}

enum class Nullability {
    NOT_NULL
}

public val JB_NOT_NULL: String = "org.jetbrains.annotations.NotNull"

fun classNamesToNullabilityAnnotation(canonicalClassNames: Set<String>) : Nullability? {
    val containsNotNull = canonicalClassNames.contains(JB_NOT_NULL)
    return if (containsNotNull)
        Nullability.NOT_NULL
    else
        null
}
