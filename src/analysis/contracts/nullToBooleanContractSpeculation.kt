package kanva.analysis.constracts.nullToBoolean

import org.objectweb.asm.Type
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.analysis.Frame
import kanva.context.MethodContext
import kanva.graphs.successors
import kanva.util.insnIndex
import kanva.util.isThrow
import org.objectweb.asm.Opcodes
import kanva.declarations.Method
import org.objectweb.asm.tree.MethodNode
import kanva.declarations.isStatic
import kanva.util.isNotVoidReturn
import kanva.util.isReturn

class ParamValue(tp: Type?) : BasicValue(tp) {
    override fun hashCode() = 1
    override fun equals(other: Any?) = other is ParamValue
}
class InstanceOfCheckValue(tp: Type?): BasicValue(tp) {
    override fun hashCode() = 2
    override fun equals(other: Any?) = other is InstanceOfCheckValue
}

abstract class BooleanConstant: BasicValue(Type.INT_TYPE) {
    abstract fun toBoolResult(): BoolResult
}
object TrueConstant: BooleanConstant() {
    override fun toBoolResult(): BoolResult = BoolResult.TRUE
    override fun equals(other: Any?) = other is TrueConstant
}
object FalseConstant: BooleanConstant() {
    override fun toBoolResult(): BoolResult = BoolResult.FALSE
    override fun equals(other: Any?) = other is FalseConstant
}

enum class BoolResult {
    TRUE
    FALSE
}

enum class ParamPath {
    NULL_PATH
}

trait Contract
data class SingleContract(val nullTaken: Boolean, val result: BoolResult) : Contract
data class CycledContract() : Contract
data class NoContract() : Contract

// We should consider that at least one null path was taken
fun combineContracts(contract1: Contract, contract2: Contract): Contract {
    val result = when {
        contract1 is NoContract ->
            contract1
        contract2 is NoContract ->
            contract2
        contract1 is CycledContract ->
            contract2
        contract2 is CycledContract ->
            contract1
        contract1 is SingleContract && contract2 is SingleContract && contract1.result == contract2.result ->
            SingleContract(contract1.nullTaken || contract2.nullTaken, contract1.result)
        else ->
            NoContract()
    }
    return result
}

// interpreter which is aware of ParamValue and about BooleanConstants
class ParamBoolInterpreter(): BasicInterpreter() {
    public override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()
        if (opCode == CHECKCAST && value is ParamValue) {
            val desc = ((insn as TypeInsnNode)).desc
            return ParamValue(Type.getObjectType(desc))
        }
        if (opCode == INSTANCEOF && value is ParamValue) {
            return InstanceOfCheckValue(Type.INT_TYPE)
        }
        return super.unaryOperation(insn, value);
    }

    public override fun newOperation(insn: AbstractInsnNode): BasicValue? {
        val opCode = insn.getOpcode()
        when (opCode) {
            ICONST_0 ->
                return FalseConstant
            ICONST_1 ->
                return TrueConstant
        }
        return super.newOperation(insn)
    }
}

class Configuration(val insnIndex: Int, val frame: Frame<BasicValue>)
class TooManyIterationsException(): Exception()

class NullBoolContractSpeculator(val methodContext: MethodContext, val paramIndex: Int) {
    val method = methodContext.method
    val cfg = methodContext.cfg
    val methodNode = methodContext.methodNode
    val interpreter = ParamBoolInterpreter()

    fun inferContract(): SingleContract? {
        when {
            cfg.nodes.notEmpty ->
                try {
                    val inferred = speculate(
                            Configuration(0, createStartFrame(method, methodNode, paramIndex)),
                            listOf(),
                            false)
                    if (inferred is SingleContract && inferred.nullTaken) {
                        return inferred
                    } else {
                        return null
                    }
                } catch (e: TooManyIterationsException) {
                    return null
                }
            else -> return null
        }
    }

    var iterations = 0

    fun speculate(conf: Configuration, history: List<Configuration>, nullTaken: Boolean): Contract {
        if (iterations ++ > 1000) {
            throw TooManyIterationsException()
        }
        val insnIndex = conf.insnIndex
        val frame = conf.frame
        val cfgNode = cfg.findNode(insnIndex)!!
        val insnNode = methodNode.instructions[insnIndex]
        val nextFrame = execute(frame, insnNode)
        val nextConfs =
                cfgNode.successors.map{Configuration(it.insnIndex, nextFrame)}
        val nextHistory = history + conf
        val opCode = insnNode.getOpcode()

        return when {
            opCode.isNotVoidReturn() -> {
                val returnValue = Frame(frame).pop()
                when {
                    returnValue is BooleanConstant ->
                        SingleContract(nullTaken, returnValue.toBoolResult())
                    else ->
                        NoContract()
                }
            }
            opCode.isReturn() -> {
                NoContract()
            }
            opCode.isThrow() ->
                NoContract()
            opCode == Opcodes.IFNONNULL && Frame(frame).pop() is ParamValue ->
                speculate(nextConfs.first(), nextHistory, true)
            opCode == Opcodes.IFNULL && Frame(frame).pop() is ParamValue ->
                speculate(nextConfs.last(), nextHistory, true)
            opCode == Opcodes.IFEQ && Frame(frame).pop() is InstanceOfCheckValue ->
                speculate(nextConfs.last(), nextHistory, true)
            opCode == Opcodes.IFNE && Frame(frame).pop() is InstanceOfCheckValue ->
                speculate(nextConfs.first(), nextHistory, true)
            else ->
                nextConfs.map { speculate(it, nextHistory, nullTaken) }.reduce { c1, c2 -> combineContracts(c1, c2) }
        }
    }

    fun execute(frame: Frame<BasicValue>, insnNode: AbstractInsnNode): Frame<BasicValue> {
        return when (insnNode.getType()) {
            AbstractInsnNode.LABEL, AbstractInsnNode.LINE, AbstractInsnNode.FRAME ->
                frame
            else -> {
                val nextFrame = Frame(frame)
                nextFrame.execute(insnNode, interpreter)
                nextFrame
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
    is BooleanConstant -> previous == current
    else -> true
}
