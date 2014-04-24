package analysis.reducible

import kanva.context.Context
import kanva.analysis.buildCFG
import java.util.Date
import java.io.File
import kanva.index.FileBasedClassSource
import kanva.analysis.dfs.reducible1
import org.objectweb.asm.util.Printer

// naive version
// 0 is a start
fun reducible(graph: MutableSet<Pair<Int, Int>>): Boolean {

    val nodes = hashSetOf<Int>()
    for ((from, to) in graph) {
        nodes.add(from)
        nodes.add(to)
    }
    do {
        var changed = false
        // deleting self loops
        /*
        val selfLoop = graph.firstOrNull { it.first == it.second }
        if (selfLoop != null) {
            graph.remove(selfLoop)
            changed = true
        }*/
        //println(graph)
        // collapsing
        val incidents: MutableMap<Int, Int> = hashMapOf<Int, Int>()
        for ((from, to) in graph) {
            incidents[to] = incidents.getOrElse(to, {0}) + 1
        }
        val edge = graph.firstOrNull { it.to != 0 && incidents[it.to] == 1 }
        if (edge != null && edge.from != edge.to) {
            //println("deleting $edge")
            nodes.remove(edge.to)
            graph.remove(edge)
            val edges = graph.filter { it.from == edge.to }
            graph.removeAll(edges)
            for ((from, to) in edges) {
                if (edge.from != to) {
                    graph.add(edge.from to to)
                }
            }
            changed = true
        }
        //println(graph)
    } while (changed)
    val result = nodes == setOf(0)
    if (!result) {
        println("false $nodes")
        println("graph $graph")
    }
    return result
}

inline val Pair<Int, Int>.from: Int
    get() = first

inline val Pair<Int, Int>.to: Int
    get() = second

fun main(args: Array<String>) {

    /*
    val graph1 = hashSetOf(0 to 1, 1 to 0)
    println(reducible(graph1))

    val graph2 = hashSetOf(
            0 to 1,
            1 to 2,
            1 to 3,
            2 to 4,
            3 to 4,
            4 to 5,
            5 to 3)
    println(reducible(graph2))
    */

    //val jarFile = File("data/commons-lang3-3.3.2.jar")
    val jarFile = File("/Library/Java/JavaVirtualMachines/jdk1.7.0_45.jdk/Contents/Home/jre/lib/rt.jar")
    //val jarFile = File("/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Classes/classes.jar")
    val jarSource = FileBasedClassSource(listOf(jarFile))
    val context = Context(jarSource, listOf())
    checkRed(context)

}

fun checkRed(context: Context) {
    println(Date())
    val total = context.index.methods.size
    var count = 0
    for ((method, methodNode) in context.index.methods) {

        val cfg = buildCFG(method, methodNode)
        val graph = hashSetOf<Pair<Int, Int>>()
        val graph1 = hashSetOf<Pair<Int, Int>>()
        for (node in cfg.transitions.nodes) {
            for (edge in node.outgoingEdges) {
                graph.add(edge.from.data to edge.to.data)
                graph1.add(edge.from.data to edge.to.data)
            }
        }

        if((0 .. cfg.transitions.nodes.size - 1).toList() != cfg.transitions.nodes.map { it.data }.sort())
            println("method $method")

        if (cfg.transitions.nodes.notEmpty) {
            try {

                //val xx = reducible(graph)
                val xx = reducible1(cfg.transitions)
                if (!xx) {
                    println(method)
                    println(graph1.toList().sortBy { it.first })
                }
            } catch (e: Throwable) {
                e.printStackTrace();
                println("*** $method")
                println(cfg.transitions.nodes.size)
                println("touched insns: ${cfg.transitions.nodes.map { it.data }.sort()}")
                val code = Array(methodNode.instructions.size()) {
                    val opcode = methodNode.instructions.get(it).getOpcode()
                    it.toString() + " "+ (if (opcode >= 0) Printer.OPCODES[opcode] else "")
                }.makeString("\n")
                println("code:\n$code")

            }

        }
        count ++

        if (count mod 1000 == 0) {
            println("$count/$total")
        }
    }
    println(Date())
}
