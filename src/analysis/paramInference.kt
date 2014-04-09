package kanva.analysis

import kanva.annotations.*
import kanva.context.*
import kanva.declarations.*
import kanva.graphs.*
import kanva.util.*

fun inferParams(context: Context, components: List<Set<Node<Method>>>) {
    for (component in components) {
        iterateComponent(context, component)
    }
    println("${context.annotations.size()} annotations inferred")
}

fun iterateComponent(context: Context, component: Set<Node<Method>>) {
    while (stepComponent(context, component)){}
}

fun stepComponent(context: Context, component: Set<Node<Method>>): Boolean {
    var changed = false
    for (node in component) {
        val method = node.data
        if (method.access.isNative() || method.access.isAbstract()) {
            continue
        }
        val methodNode = context.index.methods[method]!!
        val cfg = buildCFG(method, methodNode)

        val methodPositions = PositionsForMethod(method)
        val shift = if (method.isStatic()) 0 else 1
        val indices = (0 .. (method.getArgumentTypes().size - 1)).toList()
        for (i in indices) {
            val relPos = shift + i
            if (method.getArgumentTypes()[i].isPrimitive()) {
                continue
            }
            if (context.annotations[methodPositions[ParameterPosition(relPos)]] != Nullability.NOT_NULL) {
                val methodContext = MethodContext(context, cfg, method, methodNode)
                val shouldBeNotNull = NullParamSpeculator(methodContext, relPos).shouldBeNotNull()
                if (shouldBeNotNull) {
                    context.annotations[methodPositions[ParameterPosition(relPos)]] = Nullability.NOT_NULL
                    changed = true
                }
            }
        }
    }
    return changed
}
