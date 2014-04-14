package kanva.analysis

import kanva.context.MethodContext
import org.objectweb.asm.tree.analysis.Frame
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

import kanva.analysis.*
import kanva.graphs.*
import kanva.util.*
import kanva.declarations.isStatic
import kanva.declarations.Method
import org.objectweb.asm.tree.MethodNode

enum class Result {
    CYCLE {
        override fun join(other: Result) = other
    }
    ERROR {
        override fun join(other: Result) = other
    }
    NPE {
        override fun join(other: Result) = when (other) {
            RETURN -> RETURN
            else -> NPE
        }
    }
    RETURN {
        override fun join(other: Result) = RETURN
    }
    abstract fun join(other: Result): Result
}

class Configuration(val insnIndex: Int, val frame: Frame<BasicValue>)
class TooManyIterationsException(): Exception()

class NullParamSpeculator(val methodContext: MethodContext, val paramIndex: Int) {
    val method = methodContext.method
    val transitions = methodContext.cfg.transitions
    val exceptionTransitions = methodContext.cfg.exceptionTransitions
    val methodNode = methodContext.methodNode
    val interpreter = ParamSpyInterpreter(methodContext.ctx)

    fun shouldBeNotNull(): Boolean =
        transitions.nodes.notEmpty && when (speculate()) {
            Result.NPE -> true
            else -> false
        }

    fun speculate(): Result =
            try {
                speculate(Configuration(0, createStartFrame(method, methodNode, paramIndex)), listOf(), false, false)
            } catch (e: TooManyIterationsException) {
                Result.RETURN
            }

    var iterations = 0

    fun speculate(
            conf: Configuration,
            history: List<Configuration>,
            alreadyDereferenced: Boolean,
            nullPath: Boolean
    ): Result {
        if (iterations ++ > 1000) {
            throw TooManyIterationsException()
        }
        val insnIndex = conf.insnIndex
        val frame = conf.frame
        if (history.any{it.insnIndex == insnIndex && isInstanceOf(frame, it.frame)})
            return Result.CYCLE
        val cfgNode = transitions.findNode(insnIndex)!!
        val insnNode = methodNode.instructions[insnIndex]
        val (nextFrame, dereferencedHere) = execute(frame, insnNode)
        val nextConfs = cfgNode.successors.map { node ->
            val nextInsnIndex = node.insnIndex
            val excType = exceptionTransitions[insnIndex to nextInsnIndex]
            val nextFrame1 =
                    if (excType == null)
                        nextFrame
                    else {
                        val handler = Frame(frame)
                        handler.clearStack()
                        handler.push(BasicValue(Type.getType(excType)))
                        handler
                    }
            Configuration(nextInsnIndex, nextFrame1)
        }
        val nextHistory = history + conf
        val dereferenced = alreadyDereferenced || dereferencedHere
        val opCode = insnNode.getOpcode()
        return when {
            dereferenced ->
                Result.NPE
            opCode.isReturn() ->
                Result.RETURN
            opCode.isThrow() && dereferenced->
                Result.NPE
            opCode.isThrow() && nullPath ->
                Result.NPE
            opCode.isThrow() ->
                Result.ERROR
            opCode == Opcodes.IFNONNULL && Frame(frame).pop() is ParamValue ->
                speculate(nextConfs.first(), nextHistory, dereferenced, true)
            opCode == Opcodes.IFNULL && Frame(frame).pop() is ParamValue ->
                speculate(nextConfs.last(), nextHistory, dereferenced, true)
            opCode == Opcodes.IFEQ && Frame(frame).pop() is InstanceOfCheckValue ->
                speculate(nextConfs.last(), nextHistory, dereferenced, true)
            opCode == Opcodes.IFNE && Frame(frame).pop() is InstanceOfCheckValue ->
                speculate(nextConfs.first(), nextHistory, dereferenced, true)

            else ->
                nextConfs map { conf ->
                    speculate(conf, nextHistory, dereferenced, nullPath)
                } reduce { r1, r2 ->
                    r1 join r2
                }
        }
    }

    fun execute(frame: Frame<BasicValue>, insnNode: AbstractInsnNode): Pair<Frame<BasicValue>, Boolean> {
        return when (insnNode.getType()) {
            AbstractInsnNode.LABEL, AbstractInsnNode.LINE, AbstractInsnNode.FRAME ->
                Pair(frame, false)
            else -> {
                val nextFrame = Frame(frame)
                interpreter.reset()
                nextFrame.execute(insnNode, interpreter)
                Pair(nextFrame, interpreter.dereferenced)
            }
        }
    }
}

fun createStartFrame(
        method: Method, methodNode: MethodNode, paramIndex: Int
): Frame<BasicValue> {
    val frame = Frame<BasicValue>(methodNode.maxLocals, methodNode.maxStack)
    val returnType = Type.getReturnType(methodNode.desc)
    val returnValue = if (returnType == Type.VOID_TYPE) null else BasicValue(returnType)
    frame.setReturn(returnValue)
    val args = Type.getArgumentTypes(methodNode.desc)
    var local = 0
    var shift = 0
    if (!method.access.isStatic()) {
        val basicValue = BasicValue(Type.getObjectType(method.declaringClass.internal))
        frame.setLocal(local++, basicValue)
        shift = 1
    }
    for (i in 0..args.size - 1) {
        val value = if (i + shift == paramIndex) ParamValue(args[i]) else BasicValue(args[i])
        frame.setLocal(local++, value)
        if (args[i].getSize() == 2) {
            frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE)
        }
    }
    while (local < methodNode.maxLocals) {
        frame.setLocal(local++, BasicValue.UNINITIALIZED_VALUE)
    }
    return frame
}

fun isInstanceOf(current: Frame<BasicValue>, previous: Frame<BasicValue>): Boolean {
    for (i in 0 .. current.getLocals() - 1) {
        if (!isInstanceOf(current.getLocal(i), previous.getLocal(i))) {
            return false
        }
    }
    for (i in 0 .. current.getStackSize() - 1) {
        if (!isInstanceOf(current.getStack(i), previous.getStack(i))) {
            return false
        }
    }
    return true
}

fun isInstanceOf(current: BasicValue?, previous: BasicValue?): Boolean = when (previous) {
    is ParamValue -> current is ParamValue
    is InstanceOfCheckValue -> current is InstanceOfCheckValue
    else -> true
}
