package kanva.context

import java.io.File
import java.io.FileReader
import java.io.Reader
import java.util.ArrayList

import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode

import kanva.index.*
import kanva.annotations.*
import kanva.annotations.xml.*
import kanva.declarations.*
import kanva.graphs.*
import kanva.util.*

class Context(val source: ClassSource, val index: DeclarationIndexImpl, val annotations: Annotations<Nullability>)
class MethodContext(val ctx: Context, val cfg: Graph<Int>, val method: Method, val methodNode: MethodNode)

fun Context(classSource: ClassSource, annotationDirs: Collection<File>): Context {
    val declarationIndex = DeclarationIndexImpl(classSource)
    val xmls = ArrayList<File>()

    for (annFile in annotationDirs)
        annFile.recurseFiltered({it.name.endsWith(".xml")}, {xmls.add(it)})

    val annotations = loadExternalAnnotations(xmls map {{FileReader(it)}}, declarationIndex)
    return Context(classSource, declarationIndex, annotations)
}

fun Context.findMethodByMethodInsnNode(methodInsnNode: MethodInsnNode): Method? {
    val owner = methodInsnNode.owner!!
    val name = methodInsnNode.name!!
    val result = index.findMethod(ClassName.fromInternalName(owner), name, methodInsnNode.desc)
    return result
}

fun Context.findNotNullParamPositions(method: Method?): Collection<ParameterPosition> {
    val notNullPositions = arrayListOf<ParameterPosition>()
    if (method != null) {
        PositionsForMethod(method).forEachValidPosition { pos ->
            if (annotations[pos] == Nullability.NOT_NULL) {
                if (pos is MethodPosition) {
                    val relPosition = pos.relativePosition
                    if (relPosition is ParameterPosition) {
                        notNullPositions.add(relPosition)
                    }

                }
            }
        }

    }
    return notNullPositions
}

private fun loadExternalAnnotations(xmls: Collection<() -> Reader>, index: DeclarationIndex): Annotations<Nullability> {

    val result = Annotations<Nullability>()
    for (xml in xmls) {
        xml() use { parseAnnotations(it) {
            key, annotations ->
            val position = index.findPositionByAnnotationKeyString(key)
            if (position != null) {
                val classNames = annotations.toSet()
                val nullability = classNamesToNullabilityAnnotation(classNames)
                if (nullability != null) {
                    result[position] = nullability
                }
            } else {
                println("Position not found for $key")
            }
        }}
    }

    var paramAnns = 0
    var fieldAnns = 0
    var returnAnns = 0
    result.forEachPosition { pos, ann ->
        when {
            pos is MethodPosition && pos.relativePosition is ParameterPosition ->
                paramAnns ++
            pos is MethodPosition && pos.relativePosition == RETURN_POSITION ->
                returnAnns ++
            pos is FieldPosition ->
                fieldAnns ++
        }
    }

    return result
}
