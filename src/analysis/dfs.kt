package kanva.analysis.dfs

import kanva.graphs.*
import java.util.HashSet

trait Action
data class MarkScanned(val node: Int) : Action
data class ExamineEdge(val from: Int, val to: Int) : Action

data class DfsResult(
        val insnSize: Int,
        val graph: Graph<Int>,
        val dfn: Array<Int>,
        val tree: Set<Pair<Int, Int>>,
        val forward: Set<Pair<Int, Int>>,
        // cycle
        val back: Set<Pair<Int, Int>>,
        val cross: Set<Pair<Int, Int>>
) {
    // number of descendants in tree
    val nd = Array<Int>(insnSize){1};
    val antiTree = hashMapOf<Int, Int>();

    {
        for ((from, to) in tree) {
            nd[from] = nd[from] + 1
            antiTree[to] = from
        }
    }



    tailRecursive
    fun isDescendant(y1: Int, w: Int): Boolean {
        if (y1 == w) {
            return true
        }
        val parent = antiTree[y1];
        if (parent == null) {
            return false
        }
        return isDescendant(parent, w)
    }
}

private class TestBuilder: GraphBuilder<Int, Int, Graph<Int>>(true) {
    override fun newNode(data: Int) = Node<Int>(data)
    override fun newGraph() = Graph<Int>(true)
}

// modification of 11.14
// there may be rare cases when some instructions are absense in
fun dfs(graph: Graph<Int>, insnSize: Int): DfsResult {
    val tree = hashSetOf<Pair<Int, Int>>()
    val forward = hashSetOf<Pair<Int, Int>>()
    val back = hashSetOf<Pair<Int, Int>>()
    val cross = hashSetOf<Pair<Int, Int>>()

    val marked = Array<Boolean>(insnSize) { false }
    val scanned = Array<Boolean>(insnSize) { false }
    val dfn = Array<Int>(insnSize) { 0 }

    val stack = linkedListOf<Action>()

    stack.addFirst(MarkScanned(0))
    stack.addAll(0, graph.findNode(0)!!.successors.map { ExamineEdge(0, it.data) })

    var i = 0
    marked[0] = true
    dfn[0] = ++i
    while (stack.notEmpty) {
        val action: Action = stack.pop()
        when (action) {
            is MarkScanned -> {
                scanned[action.node] = true
            }
            is ExamineEdge -> {
                val v = action.from
                val w = action.to
                when {
                    !marked[w] -> {
                        dfn[w] = ++i
                        tree.add(v to w)
                        marked[w] = true
                        stack.addFirst(MarkScanned(w))
                        stack.addAll(0, graph.findNode(w)!!.successors.map { ExamineEdge(w, it.data) })
                    }
                    dfn[w] > dfn[v] ->
                        forward.add(v to w)
                    dfn[w] < dfn[v] && !scanned[w] ->
                        back.add(v to w)
                    else ->
                        cross.add(v to w)
                }
            }
        }
    }

    return DfsResult(insnSize, graph, dfn, tree, forward, back, cross)
}

fun reducible1(graph: Graph<Int>, size: Int): Boolean {
    return reduce(dfs(graph, size))
}

private fun reduce(dfs: DfsResult): Boolean {
    // initialization
    val size = dfs.insnSize

    val cycles2v = Array<HashSet<Int>>(size) {HashSet()}
    val forwards2v = Array<HashSet<Int>>(size) {HashSet()}
    val crosses2v = Array<HashSet<Int>>(size) {HashSet()}
    val trees2v = Array<HashSet<Int>>(size) {HashSet()}
    //val all2v = Array<HashSet<Int>>(size) {HashSet()}

    // to which a vertex is collapsed
    val find = hashMapOf<Int, Int>()

    for (i in 0 .. size - 1) {
        find[i] = i
    }

    for ((from, to) in dfs.tree) {
        trees2v[to].add(from)
        //trees2v[to].add(from)
    }
    for ((from, to) in dfs.back) {
        cycles2v[to].add(from)
        //all2v[to].add(from)
    }
    for ((from, to) in dfs.forward) {
        forwards2v[to].add(from)
        //all2v[to].add(from)
    }
    for ((from, to) in dfs.cross) {
        crosses2v[to].add(from)
        //all2v[to].add(from)
    }
    for (w in dfs.graph.nodes.map{it.data}.sort().reverse()) {
        val p = hashSetOf<Int>()
        for (from in cycles2v[w]) {
            p.add(find[from]!!)
        }
        val q = p.toCollection(hashSetOf<Int>())
        while (q.notEmpty) {
            val x = q.first()
            q.remove(x)
            // TODO - cycle
            for (y in (trees2v[x] + forwards2v[x] + crosses2v[x])) {
                val y1 = find[y]!!
                // if y1 is not descendant
                if (!dfs.isDescendant(y1, w)) {
                //if (! (w <= y1 && y1 < w + dfs.nd[w]) ) {
                    return false
                }
                if (y1 != w && !p.contains(y1)) {
                    p.add(y1)
                    q.add(y1)
                }
            }
        }
        for (x in p) {
            find[x] = w
        }
    }
    return true
}

fun main(args: Array<String>) {
    test1()
    test2()
    test3()
}

fun test1() {
    val builder = TestBuilder()

    val n0 = builder.getOrCreateNode(0)
    val n1 = builder.getOrCreateNode(1)

    builder.getOrCreateEdge(n0, n1)
    builder.getOrCreateEdge(n1, n0)

    val graph = builder.graph

    println(dfs(graph, graph.nodes.size))
    println(reducible1(graph, graph.nodes.size))
}

fun test2() {
    val builder = TestBuilder()

    val n0 = builder.getOrCreateNode(0)
    val n1 = builder.getOrCreateNode(1)
    val n2 = builder.getOrCreateNode(2)
    val n3 = builder.getOrCreateNode(3)
    val n4 = builder.getOrCreateNode(4)
    val n5 = builder.getOrCreateNode(5)

    builder.getOrCreateEdge(n0, n1)
    builder.getOrCreateEdge(n1, n2)
    builder.getOrCreateEdge(n1, n3)
    builder.getOrCreateEdge(n2, n4)
    builder.getOrCreateEdge(n3, n4)
    builder.getOrCreateEdge(n4, n5)
    builder.getOrCreateEdge(n5, n3)


    val graph = builder.graph

    println(dfs(graph, graph.nodes.size))
    println(reducible1(graph, graph.nodes.size))
}

fun test3() {
    val builder = TestBuilder()

    val n0 = builder.getOrCreateNode(0)
    val n1 = builder.getOrCreateNode(1)

    builder.getOrCreateEdge(n0, n1)
    builder.getOrCreateEdge(n0, n1)
    builder.getOrCreateEdge(n1, n0)
    builder.getOrCreateEdge(n1, n0)

    val graph = builder.graph

    println(dfs(graph, graph.nodes.size))
    println(reducible1(graph, graph.nodes.size))
}
