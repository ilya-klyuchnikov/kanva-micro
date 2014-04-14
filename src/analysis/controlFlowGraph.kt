package kanva.analysis

import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.BasicInterpreter

import kanva.declarations.Method
import kanva.graphs.*
import java.util.HashMap
import org.objectweb.asm.tree.TryCatchBlockNode

data class ControlFlowGraph(val transitions: Graph<Int>, val exceptionTransitions: Map<Pair<Int, Int>, String>)

fun buildCFG(method: Method, methodNode: MethodNode): ControlFlowGraph =
        ControlFlowBuilder(method, methodNode).buildCFG()

private class ControlFlowBuilder(
        val method: Method, val methodNode: MethodNode
): Analyzer<BasicValue>(BasicInterpreter()) {
    private var methodWithExceptions = false
    private class CfgBuilder: GraphBuilder<Int, Int, Graph<Int>>(true) {
        val exceptionTransitions = HashMap<Pair<Int, Int>, String>()
        val cfg = ControlFlowGraph(graph, exceptionTransitions)
        override fun newNode(data: Int) = Node<Int>(data)
        override fun newGraph() = Graph<Int>(true)
    }

    private var builder = CfgBuilder()

    fun buildCFG(): ControlFlowGraph {
        builder = CfgBuilder()
        analyze(method.declaringClass.internal, methodNode)
        return builder.cfg
    }

    override protected fun newControlFlowEdge(insn: Int, successor: Int) {
        val fromNode = builder.getOrCreateNode(insn)
        val toNode = builder.getOrCreateNode(successor)
        builder.getOrCreateEdge(fromNode, toNode)
    }

    override protected fun newControlFlowExceptionEdge(insn: Int, tcb: TryCatchBlockNode): Boolean {
        val successor = methodNode.instructions.indexOf(tcb.handler)
        val fromNode = builder.getOrCreateNode(insn)
        val toNode = builder.getOrCreateNode(successor)
        builder.getOrCreateEdge(fromNode, toNode)
        builder.exceptionTransitions[insn to successor] = tcb.`type` ?: "java/lang/Throwable"
        return true
    }
}
