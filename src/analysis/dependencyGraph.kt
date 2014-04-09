package kanva.analysis

import java.util.ArrayList
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Stack
import java.util.LinkedList
import java.util.HashSet

import org.objectweb.asm.ClassReader.*
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

import kanva.index.*
import kanva.graphs.*
import kanva.declarations.*
import kanva.util.*

fun <T> Graph<T>.getTopologicallySortedStronglyConnectedComponents(): List<Set<Node<T>>> {
    val sccFinder = SCCFinder(this, { g -> g.nodes }, { m -> m.outgoingEdges.map{ it.to } })
    val components = sccFinder.getAllComponents()

    return components.topologicallySort {
        c ->
        c.map {
            m -> sccFinder.findComponent(m)
        }
    }
}

public fun buildFunctionDependencyGraph(declarationIndex: DeclarationIndex, classSource: ClassSource) : Graph<Method> =
        FunDependencyGraphBuilder(declarationIndex, classSource).build()

public class FunDependencyGraphBuilder(
        private val index: DeclarationIndex,
        private val source: ClassSource
): GraphBuilder<Method, Method, Graph<Method>>(true) {
    private var currentFromNode : Node<Method>? = null
    private var currentClassName : ClassName? = null

    private val classVisitor = object : ClassVisitor(Opcodes.ASM4) {
        public override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
            currentClassName = ClassName.fromInternalName(name)
        }

        public override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
            val method = Method(currentClassName!!, access, name, desc, signature)
            currentFromNode = getOrCreateNode(method)
            return methodVisitor
        }
    }

    private val methodVisitor = object : MethodVisitor(Opcodes.ASM4) {
        public override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String) {
            val ownerClassName = ClassName.fromInternalName(owner)
            val method = index.findMethod(ownerClassName, name, desc)
            if (method != null) {
                getOrCreateEdge(currentFromNode!!, getOrCreateNode(method))
            }
        }
    }

    override fun newGraph(): Graph<Method> = Graph(false)
    override fun newNode(data: Method): Node<Method> = Node(data)

    public fun build(): Graph<Method> {
        source.forEach { it.accept(classVisitor, flags(SKIP_DEBUG, SKIP_FRAMES)) }
        return toGraph()
    }
}

private fun <T: Any> identityHashSet(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap())

fun <T> Iterable<T>.topologicallySort(adjacentNodes: (T) -> Iterable<T>): List<T> {
    val result = LinkedList<T>()
    val visited = HashSet<T>()

    fun processNode(node: T) {
        if (!visited.add(node)) return
        for (next in adjacentNodes(node)) {
            processNode(next)
        }
        result.addFirst(node)
    }

    for (node in this) {
        processNode(node)
    }

    return result
}

private class SCCFinder<Graph, Node>(
        val graph: Graph,
        val graphNodes: (Graph) -> Iterable<Node>,
        val adjacentNodes: (Node) -> Iterable<Node>) {

    private class IdentityHashStack<T> {
        val stack = Stack<T>()
        val identitySet = identityHashSet<T>()

        fun contains(elem : T) : Boolean = identitySet.contains(elem)
        fun push(elem : T) {
            stack.push(elem)
            assert(identitySet.add(elem))
        }
        fun pop() : T {
            val elem = stack.pop()!!
            assert(identitySet.remove(elem))
            return elem
        }
        fun isEmpty() : Boolean {
            assert(stack.isEmpty() == identitySet.isEmpty())
            return stack.isEmpty()
        }
    }

    private val components = ArrayList<Set<Node>>()
    private val nodeToComponent = IdentityHashMap<Node, Set<Node>>()

    // Index of visited nodes
    private val nodeIndex = IdentityHashMap<Node, Int>()

    // Current index for not-visited node
    private var index = 0

    public fun findComponent(node: Node): Set<Node> {
        if (!nodeIndex.containsKey(node)) {
            execute(node)
        }

        return nodeToComponent[node] ?: throw IllegalStateException("Can't find component for node ${node}")
    }

    public fun getAllComponents(): List<Set<Node>> {
        for (node in graphNodes(graph)) {
            findComponent(node)
        }
        return components
    }

    private fun execute(node: Node) {
        val stack = IdentityHashStack<Node>();
        val minUnvisitedReachable = IdentityHashMap<Node, Int>()

        executeOnNode(node, stack, minUnvisitedReachable)

        assert(stack.isEmpty())
    }

    private fun executeOnNode(node: Node, stack: IdentityHashStack<Node>, minUnvisitedReachable: MutableMap<Node, Int>) {
        val currentNodeIndex = index

        minUnvisitedReachable[node] = currentNodeIndex
        nodeIndex[node] = currentNodeIndex

        index++

        stack.push(node);

        for (nextNode in adjacentNodes(node)) {
            val currentComponentIndex = minUnvisitedReachable[node]!!

            if (!nodeIndex.containsKey(nextNode)) {
                executeOnNode(nextNode, stack, minUnvisitedReachable);
                minUnvisitedReachable[node] = Math.min(currentComponentIndex, minUnvisitedReachable[nextNode]!!)
            } else if (stack.contains(nextNode)) {
                minUnvisitedReachable[node] = Math.min(currentComponentIndex, nodeIndex[nextNode]!!)
            }
        }

        if (minUnvisitedReachable[node] == currentNodeIndex){
            val component = identityHashSet<Node>()
            do {
                val componentNode = stack.pop()
                component.add(componentNode)
                nodeToComponent[componentNode] = component

                if (componentNode identityEquals node) {
                    break
                }
            } while(true)

            components.add(component)
        }
    }
}
