package kanva.graphs

import java.util.HashMap
import java.util.ArrayList
import kanva.util.union

open class Graph<out T>(createNodeMap: Boolean) {
    private val _nodes: MutableCollection<Node<T>> = ArrayList()
    private val nodeMap: MutableMap<T, Node<T>>? = if (createNodeMap) HashMap<T, Node<T>>() else null

    val nodes: Collection<Node<T>> = _nodes

    fun findNode(data: T): Node<T>? = nodeMap?.get(data)

    fun addNode(node: Node<T>) {
        _nodes.add(node)
        nodeMap?.put(node.data, node)
    }
}

public class Node<out T>(public val data: T) {
    private val _incomingEdges: MutableCollection<Edge<T>> = ArrayList()
    private val _outgoingEdges: MutableCollection<Edge<T>> = ArrayList()

    val incomingEdges: Collection<Edge<T>> = _incomingEdges
    val outgoingEdges: Collection<Edge<T>> = _outgoingEdges

    fun addIncomingEdge(edge: Edge<T>) {
        if (!_incomingEdges.contains(edge)) _incomingEdges.add(edge)
    }

    fun addOutgoingEdge(edge: Edge<T>) {
        if (!_outgoingEdges.contains(edge)) _outgoingEdges.add(edge)
    }

    override fun toString(): String {
        return "${data} in$incomingEdges out$outgoingEdges"
    }
}

public val <T> Node<T>.predecessors: Collection<Node<T>>
    get() = incomingEdges.map { e -> e.from }
public val <T> Node<T>.successors: Collection<Node<T>>
    get() = outgoingEdges.map { e -> e.to }

class Edge<T>(public val from: Node<T>, public val to: Node<T>)  {
    override fun toString(): String {
        return "${from.data} -> ${to.data}"
    }

    override fun hashCode(): Int {
        return from.hashCode() * 31 + to.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is Edge<*>) {
            return from == other.from && to == other.to
        }
        return false
    }
}

abstract class GraphBuilder<NodeKey, NodeData, G: Graph<NodeData>>(cacheNodes: Boolean) {
    val nodeCache = if (cacheNodes) HashMap<NodeKey, Node<NodeData>>() else null

    val graph: G = newGraph()

    abstract fun newGraph(): G
    abstract fun newNode(data: NodeKey): Node<NodeData>
    open fun newEdge(from: Node<NodeData>, to: Node<NodeData>): Edge<NodeData> = Edge(from, to)

    fun getOrCreateNode(data: NodeKey): Node<NodeData> {
        val cachedNode = nodeCache?.get(data)
        if (cachedNode != null) {
            return cachedNode
        }

        val node = newNode(data)
        graph.addNode(node)
        nodeCache?.put(data, node)
        return node
    }

    fun getOrCreateEdge(from: Node<NodeData>, to: Node<NodeData>): Edge<NodeData> {
        val edge = newEdge(from, to)
        from.addOutgoingEdge(edge)
        to.addIncomingEdge(edge)
        return edge
    }

    fun toGraph(): G = graph
}
