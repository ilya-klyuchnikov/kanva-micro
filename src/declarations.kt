package kanva.declarations

import org.objectweb.asm.Type
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.Opcodes

import kanva.util.*

trait ClassMember {
    val declaringClass: ClassName
    val access: Access
    val name: String
}

data class MethodId(val methodName: String,val methodDesc: String) {
    override fun toString() = methodName + methodDesc
}

fun MethodId.getReturnType(): Type = Type.getReturnType(methodDesc)
fun MethodId.getArgumentTypes(): Array<Type> = Type.getArgumentTypes(methodDesc)
/**
 * for method  findClass(Ljava/lang/String;)Ljava/lang/Class; it will return a piece of its signature: findClass(Ljava/lang/String;
 */
fun MethodId.getSignatureDescriptor(): String {
    return methodName + methodDesc.substring(0, methodDesc.lastIndexOf(')'))
}

enum class Visibility {
    PUBLIC
    PROTECTED
    PACKAGE
    PRIVATE
}

data class Access(val flags: Int) {
    fun has(flag: Int) = flags and flag != 0
    override fun toString() = "" + Integer.toHexString(flags)
}

fun Method(
        declaringClass: ClassName,
        access: Int,
        name: String,
        desc: String,
        signature: String? = null
): Method = Method(declaringClass, Access(access), MethodId(name, desc), signature)

data class Method(
        override val declaringClass: ClassName,
        override val access: Access,
        val id: MethodId,
        val genericSignature: String? = null) : ClassMember {

    override val name: String
        get() = id.methodName

    override fun toString(): String {
        return declaringClass.toType().getClassName() + ":" + id.methodName + id.methodDesc;
    }
}

fun Method(className: ClassName, methodNode: MethodNode): Method = Method(
        className, methodNode.access, methodNode.name, methodNode.desc, methodNode.signature)

fun Method.getReturnType(): Type = id.getReturnType()
fun Method.getArgumentTypes(): Array<out Type> = id.getArgumentTypes()

fun Access.isStatic(): Boolean = has(Opcodes.ACC_STATIC)
fun Access.isFinal(): Boolean = has(Opcodes.ACC_FINAL)
fun Access.isPrivate(): Boolean = has(Opcodes.ACC_PRIVATE)
fun Access.isProtected(): Boolean = has(Opcodes.ACC_PROTECTED)
fun Access.isPublic(): Boolean = has(Opcodes.ACC_PUBLIC)
fun Access.isNative(): Boolean = has(Opcodes.ACC_NATIVE)
fun Access.isAbstract(): Boolean = has(Opcodes.ACC_ABSTRACT)
fun Access.isPublicOrProtected(): Boolean = isPublic() || isProtected()

fun ClassMember.isStatic(): Boolean = access.isStatic()
fun ClassMember.isFinal(): Boolean = access.isFinal()
fun ClassMember.isPublicOrProtected(): Boolean = access.isPublicOrProtected()
fun ClassMember.isPrivate(): Boolean = access.isPrivate()

fun ClassMember.isStable(): Boolean =
        isStatic() || isPrivate() || isFinal()

fun ClassDeclaration.isPublic(): Boolean = access.isPublic()

fun Method.isConstructor(): Boolean = id.methodName == "<init>"
fun Method.isClassInitializer(): Boolean = id.methodName == "<clinit>"

val ClassMember.visibility: Visibility get() = when {
    access.has(Opcodes.ACC_PUBLIC) -> Visibility.PUBLIC
    access.has(Opcodes.ACC_PROTECTED) -> Visibility.PROTECTED
    access.has(Opcodes.ACC_PRIVATE) -> Visibility.PRIVATE
    else -> Visibility.PACKAGE
}

fun Method.isVarargs(): Boolean = access.has(Opcodes.ACC_VARARGS)

fun Method.toFullString(): String {
    return buildString {
        it.append(visibility.toString().toLowerCase() + " ")
        it.append(if (isStatic()) "static " else "")
        it.append(if (isFinal()) "final " else "")
        it.append("flags[$access] ")
        it.append(declaringClass.internal)
        it.append("::")
        it.append(id.methodName)
        it.append(id.methodDesc)
        if (genericSignature != null) {
            it.append(" :: ")
            it.append(genericSignature)
        }
    }
}

data class ClassName private (val internal: String) {
    val typeDescriptor: String
        get() = "L$internal;"

    override fun toString() = internal

    val simple: String
        get() = canonicalName.suffixAfterLast(".")

    class object {
        fun fromInternalName(name: String): ClassName {
            return ClassName(name)
        }

        fun fromType(_type: Type): ClassName {
            return ClassName.fromInternalName(_type.getInternalName())
        }
    }
}

data class ClassDeclaration(val className: ClassName, val access: Access)

val ClassName.canonicalName: String
    get() = internal.internalNameToCanonical()

fun String.internalNameToCanonical(): String = replace('/', '.').toCanonical()

fun String.toCanonical(): String {
    //keep last $ in class name: it's generated in scala bytecode
    val lastCharIndex = this.size - 1
    return this.substring(0, lastCharIndex).replace('$', '.') + this.substring(lastCharIndex)
}

fun ClassName.toType(): Type {
    return Type.getType(typeDescriptor)
}

val ClassName.packageName: String
    get() = internal.prefixUpToLast('/') ?: ""

val ClassMember.packageName: String
    get() = declaringClass.packageName

data class FieldId(val fieldName: String) {
    override fun toString() = fieldName
}

fun Field(
        declaringClass: ClassName,
        access: Int,
        name: String,
        desc: String,
        signature: String? = null,
        value: Any? = null
): Field = Field(declaringClass, Access(access), FieldId(name), desc, signature, value)

data class Field(
        override val declaringClass: ClassName,
        override val access: Access,
        val id: FieldId,
        desc: String,
        val genericSignature: String? = null,
        value: Any? = null
) : ClassMember {

    override val name = id.fieldName

    public val value : Any? = value
    public val desc : String = desc

    override fun toString(): String {
        return declaringClass.toType().getClassName() + ":" + id.fieldName;
    }
}

fun Field.getType(): Type = Type.getReturnType(desc)
