package kanva.main

import java.io.File

import kanva.analysis.*
import kanva.annotations.xml.writeAnnotationsToXmlByPackage
import kanva.context.Context
import kanva.index.FileBasedClassSource
import kanva.declarations.*

fun main(args: Array<String>) {
    val jarFile = File(args[0])
    inferSDK(jarFile)
}

fun inferSDK(jarFile: File) {
    val jarSource = FileBasedClassSource(listOf(jarFile))
    val context = Context(jarSource, listOf())
    val dependencyGraph = buildFunctionDependencyGraph(context.index, context.source)
    val components = dependencyGraph.getTopologicallySortedStronglyConnectedComponents().reverse()
    inferParams(context, components)
    writeAnnotationsToXmlByPackage(context.annotations, "annotations")
}
