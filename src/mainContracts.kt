package kanva.contracts

import java.io.File

import org.objectweb.asm.Type

import kanva.analysis.*
import kanva.context.Context
import kanva.index.FileBasedClassSource
import kanva.declarations.*
import kanva.util.isPrimitive
import kanva.analysis.constracts.nulliltyToBoolean.NullBoolContractSpeculator
import kanva.context.MethodContext
import kanva.annotations.xml.toAnnotationKeyPrefix
import kanva.analysis.constracts.nulliltyToBoolean.SingleContract
import kanva.analysis.constracts.nulliltyToBoolean.ParamPath
import kanva.analysis.constracts.nulliltyToBoolean.BoolResult

fun main(args: Array<String>) {
    //val jarFile = File(args[0])
    val jarFile = File("data/commons-lang3-3.3.2.jar")
    inferSDK(jarFile)
}

fun inferSDK(jarFile: File) {
    val jarSource = FileBasedClassSource(listOf(jarFile))
    val context = Context(jarSource, listOf())
    inferContracts(context)
    //writeAnnotationsToXmlByPackage(context.annotations, "annotations")
}

fun inferContracts(context: Context) {
    var contracts = 0
    for ((method, methodNode) in context.index.methods) {
        if (method.access.isNative() || method.access.isAbstract()) {
            continue
        }
        val cfg = buildCFG(method, methodNode)
        val shift = if (method.isStatic()) 0 else 1
        val indices = (0 .. (method.getArgumentTypes().size - 1)).toList()
        for (i in indices) {
            val relPos = shift + i
            if (method.getArgumentTypes()[i].isPrimitive()) {
                continue
            }
            val methodContext = MethodContext(context, cfg, method, methodNode)
            val contract = NullBoolContractSpeculator(methodContext, relPos).inferContract()
            if (contract != null) {
                contracts ++
                println("${method.toAnnotationKeyPrefix()}\n   ${contractString(method, contract, indices, i)}\n")
            }
        }
    }
    println("$contracts contracts inferred")
}


fun contractString(method: Method, contract: SingleContract, indices: List<Int>, paramIndex: Int): String =
        indices.map { when (it) {
            paramIndex -> if (contract.path == ParamPath.NULL_PATH) "null" else "!null"
            else -> "_"
        }}.makeString(", ", "@Contract(\"", " -> ${result(method, contract)}\")")

fun result(method: Method, contract: SingleContract): String =
    when {
        (contract.result == BoolResult.TRUE) && (method.getReturnType().getSort() == Type.BOOLEAN) ->
            "true"
        (contract.result == BoolResult.TRUE) && (method.getReturnType().getSort() != Type.BOOLEAN) ->
            "1"
        (contract.result == BoolResult.FALSE) && (method.getReturnType().getSort() == Type.BOOLEAN) ->
            "false"
        (contract.result == BoolResult.FALSE) && (method.getReturnType().getSort() != Type.BOOLEAN) ->
            "0"
        else -> "???"
    }
