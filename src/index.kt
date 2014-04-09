package kanva.index

import org.objectweb.asm.ClassReader
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

import java.io.File
import java.io.FileInputStream
import java.util.HashMap
import java.util.HashSet

import kanva.util.*
import kanva.declarations.*
import kanva.annotations.*
import kanva.annotations.xml.*
import java.util.ArrayList
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.FieldNode

trait ClassSource {
    fun forEach(body: (ClassReader) -> Unit)
}

public class FileBasedClassSource(val jarOrClassFiles: Collection<File>) : ClassSource {
    override fun forEach(body: (ClassReader) -> Unit) {
        for (file in jarOrClassFiles) {
            if (!file.exists()) throw IllegalStateException("File does not exist: $file")
            if (file.isFile()) {
                if (file.name.endsWith(".jar")) {
                    processJar(file, {f, o, reader -> body(reader)})
                }
                else if (file.name.endsWith(".class")) {
                    FileInputStream(file) use {body(ClassReader(it))}
                }
            }
        }
    }
}

trait DeclarationIndex {
    fun findClass(className: ClassName): ClassDeclaration?
    fun findMethod(owner: ClassName, name: String, desc: String) : Method?
    fun findField(owner: ClassName, name: String) : Field?
    fun findPositionByAnnotationKeyString(annotationKey: String): AnnotationPosition?
}

public fun DeclarationIndexImpl(classSource: ClassSource): DeclarationIndexImpl {
    val index = DeclarationIndexImpl()
    index.addClasses(classSource)
    return index
}

public class DeclarationIndexImpl: DeclarationIndex {

    val methods = HashMap<Method, MethodNode>()
    val fields = HashMap<Field, FieldNode>()

    data class ClassData(
            val classDecl: ClassDeclaration,
            val methodsById: Map<MethodId, Method>,
            val methodsByName: Map<String, Collection<Method>>,
            val fieldsById: Map<FieldId, Field>,
            val superClasses: List<ClassName>
    ) {
        val constructors = methodsById.values().filter { it.isConstructor() }
    }

    val classes = HashMap<ClassName, ClassData>()
    private val classesByCanonicalName = HashMap<String, MutableCollection<ClassData>>();

    public fun addClasses(classSource: ClassSource) {
        classSource.forEach {addClass(it)}
    }

    public fun addClass(reader: ClassReader) {
        val className = ClassName.fromInternalName(reader.getClassName())

        val methodsById = HashMap<MethodId, Method>()
        val fieldsById = HashMap<FieldId, Field>()
        val methodsByNameForAnnotationKey = HashMap<String, MutableList<Method>>()
        val superClasses = linkedListOf<ClassName>()

        reader.accept(object: ClassVisitor(Opcodes.ASM4) {
            public override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                interfaces?.forEach { superClasses.add(ClassName.fromInternalName(it)) }
                if (superName != null) {
                    superClasses.add(ClassName.fromInternalName(superName))
                }
            }

            public override fun visitMethod(access: Int, name: String, desc: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                val method = Method(className, access, name, desc, signature)
                methodsById[method.id] = method
                methodsByNameForAnnotationKey.getOrPut(method.getMethodNameAccountingForConstructor(), { ArrayList() }).add(method)
                // TODO: read annotations from bytecode
                val methodNode = method.createMethodNodeStub()
                methods[method] = methodNode
                return methodNode
            }

            public override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
                val field = Field(className, access, name, desc, signature, value)
                fieldsById[field.id] = field
                // TODO: read annotations from bytecode
                val fieldNode = field.createFieldNodeStub()
                fields[field] = fieldNode
                return fieldNode
            }
        }, 0);

        val classData = ClassData(
                ClassDeclaration(className, Access(reader.getAccess())),
                methodsById,
                methodsByNameForAnnotationKey,
                fieldsById,
                superClasses
        )
        classes[className] = classData
        classesByCanonicalName.getOrPut(className.canonicalName, { HashSet() }).add(classData)
    }

    override fun findClass(className: ClassName): ClassDeclaration? {
        return classes[className]?.classDecl
    }

    // looks up the hierarchy
    override fun findMethod(owner: ClassName, name: String, desc: String): Method? {
        val clazz = classes[owner] ?: classes[ClassName.fromInternalName("java/lang/Object")]
        if (clazz == null) {
            return null;
        }
        val thisMethod = clazz.methodsById[MethodId(name, desc)]
        if (thisMethod != null) {
            return thisMethod
        }
        for (superOwner in clazz.superClasses) {
            val superMethod = findMethod(superOwner, name, desc)
            if (superMethod != null) {
                return superMethod
            }
        }
        return null
    }

    override fun findField(owner: ClassName, name: String): Field? {
        return classes[owner]?.fieldsById?.get(FieldId(name))
    }

    private fun findMethodPositionByAnnotationKeyString(
            annotationKey: String, classes: MutableCollection<ClassData>, methodName: String
    ): AnnotationPosition? {
        for (classData in classes) {
            val methods = classData.methodsByName[methodName]
            if (methods == null) continue
            for (method in methods) {
                for (pos in PositionsForMethod(method).getValidPositions()) {
                    if (annotationKey == pos.toAnnotationKey()) {
                        return pos
                    }
                }
            }
        }
        return null
    }

    private fun findFieldPositionByAnnotationKeyString(
            classes: MutableCollection<ClassData>, fieldName: String
    ): AnnotationPosition? {
        val fieldId = FieldId(fieldName)
        for (classData in classes) {
            val field = classData.fieldsById[fieldId]
            if (field == null) continue
            return getFieldPosition(field)
        }
        return null
    }

    override fun findPositionByAnnotationKeyString(annotationKey: String): AnnotationPosition? {
        val methodAnnotationKey = tryParseMethodAnnotationKey(annotationKey)
        if (methodAnnotationKey != null) {
            val classes = classesByCanonicalName[methodAnnotationKey.canonicalClassName]
            return if (classes != null) findMethodPositionByAnnotationKeyString(annotationKey, classes, methodAnnotationKey.methodName) else null
        }

        val fieldAnnotationKey = tryParseFieldAnnotationKey(annotationKey)
        if (fieldAnnotationKey != null) {
            val classes = classesByCanonicalName[fieldAnnotationKey.canonicalClassName]
            return if (classes != null) findFieldPositionByAnnotationKeyString(classes, fieldAnnotationKey.fieldName) else null
        }
        return null
    }
}
