package kanva.analysis

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.objectweb.asm.tree.analysis.BasicInterpreter
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type

import kanva.context.*
import kanva.declarations.isStable

class ParamValue(tp: Type?) : BasicValue(tp) {
    override fun hashCode() = 1
    override fun equals(other: Any?) = other is ParamValue
}
class InstanceOfCheckValue(tp: Type?): BasicValue(tp) {
    override fun hashCode() = 2
    override fun equals(other: Any?) = other is InstanceOfCheckValue
}

class ParamSpyInterpreter(val context: Context): BasicInterpreter() {
    var dereferenced = false
    fun reset() {
        dereferenced = false
    }

    public override fun unaryOperation(insn: AbstractInsnNode, value: BasicValue): BasicValue? {
        val opCode = insn.getOpcode()
        if (value is ParamValue) {
            when (opCode) {
                GETFIELD, ARRAYLENGTH, MONITORENTER ->
                    dereferenced = true
            }
        }
        if (opCode == CHECKCAST && value is ParamValue) {
            val desc = ((insn as TypeInsnNode)).desc
            return ParamValue(Type.getObjectType(desc))
        }
        if (opCode == INSTANCEOF && value is ParamValue) {
            return InstanceOfCheckValue(Type.INT_TYPE)
        }
        return super.unaryOperation(insn, value);
    }

    public override fun binaryOperation(insn: AbstractInsnNode, v1: BasicValue, v2: BasicValue): BasicValue? {
        if (v1 is ParamValue) {
            when (insn.getOpcode()) {
                IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD, PUTFIELD ->
                    dereferenced = true
            }
        }
        return super.binaryOperation(insn, v1, v2)
    }

    public override fun ternaryOperation(insn: AbstractInsnNode, v1: BasicValue, v2: BasicValue, v3: BasicValue): BasicValue? {
        if (v1 is ParamValue) {
            when (insn.getOpcode()) {
                IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE ->
                    dereferenced = true
            }
        }
        return super.ternaryOperation(insn, v1, v2, v3)
    }

    public override fun naryOperation(insn: AbstractInsnNode, values: List<BasicValue>): BasicValue? {
        if (insn.getOpcode() != INVOKESTATIC) {
            dereferenced = values.first() is ParamValue
        }
        if (insn is MethodInsnNode) {
            val method = context.findMethodByMethodInsnNode(insn)
            if (method != null && method.isStable()) {
                for (position in context.findNotNullParamPositions(method)) {
                    dereferenced = dereferenced || values[position.index] is ParamValue
                }
            }
        }
        return super.naryOperation(insn, values);
    }
}
