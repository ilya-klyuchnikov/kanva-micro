package kanva.test

import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode
import kanva.declarations.Method
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.MethodVisitor
import kanva.declarations.ClassName
import kanva.util.createMethodNodeStub
import kanva.context.Context
import kanva.index.ClassSource
import kanva.analysis.buildCFG
import org.junit.Test
import org.junit.Assert
import kanva.context.MethodContext
import kanva.analysis.constracts.nulliltyToBoolean.NullBoolContractSpeculator
import kanva.analysis.constracts.nulliltyToBoolean.Contract
import kanva.analysis.constracts.nulliltyToBoolean.SingleContract
import kanva.analysis.constracts.nulliltyToBoolean.ParamPath
import kanva.analysis.constracts.nulliltyToBoolean.BoolResult

class NullityBooleanContractSpeculatorTest {
    val testClass = javaClass<data.Contracts>()

    fun inferContract(methodName: String, i: Int): Contract? {
        val internalName = Type.getInternalName(testClass)
        var methodNode: MethodNode? = null
        var method: Method? = null

        ClassReader(testClass.getCanonicalName()).accept(object : ClassVisitor(Opcodes.ASM4) {
            public override fun visitMethod(
                    access: Int,
                    name: String,
                    desc: String,
                    signature: String?,
                    exceptions: Array<out String>?
            ): MethodVisitor? {
                if (name == methodName) {
                    method = Method(ClassName.fromInternalName(internalName), access, name, desc, signature)
                    methodNode = method!!.createMethodNodeStub()
                    return methodNode
                }
                return null
            }
        }, 0)

        val context = Context(object: ClassSource {override fun forEach(body: (ClassReader) -> Unit) {}}, listOf())
        val methodContext = MethodContext(context, buildCFG(method!!, methodNode!!), method!!, methodNode!!)
        return NullBoolContractSpeculator(methodContext, i).inferContract()
    }

    Test
    fun isEmptyList() {
        val ctr = inferContract("isEmptyList", 0)
        Assert.assertEquals(SingleContract(ParamPath.NULL_PATH, BoolResult.TRUE), ctr)
    }

    Test
    fun isNotEmptyList() {
        val ctr = inferContract("isNotEmptyList", 0)
        Assert.assertEquals(SingleContract(ParamPath.NULL_PATH, BoolResult.FALSE), ctr)
    }

    Test
    fun isTraversable() {
        val ctr = inferContract("isNotEmptyList", 0)
        Assert.assertEquals(SingleContract(ParamPath.NULL_PATH, BoolResult.FALSE), ctr)
    }

    Test
    fun endsWith() {
        val ctr = inferContract("endsWith", 0)
        Assert.assertNull(ctr)
    }

    Test
    fun isArrayType() {
        val ctr = inferContract("isArrayType", 0)
        Assert.assertEquals(SingleContract(ParamPath.NULL_PATH, BoolResult.FALSE), ctr)
    }
}