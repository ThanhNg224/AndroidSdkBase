package sdkbase.abi

import kotlin.metadata.ClassKind
import kotlin.metadata.KmClass
import kotlin.metadata.KmFunction
import kotlin.metadata.KmProperty
import kotlin.metadata.Visibility
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.fieldSignature
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.setterSignature
import kotlin.metadata.jvm.signature
import kotlin.metadata.kind
import kotlin.metadata.visibility
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File

// -------------------------------------------------------------------------------------------
// Kotlin `internal`/`private` is not a JVM concept for top-level declarations, companion-object
// members or const vals: the compiler still emits a JVM-`public` member, and no amount of
// `javap`-level inspection (isPublicApiClass/isMangledMember in AbiTasks.kt) can recover what the
// author actually wrote. The one place that information still exists is the `kotlin.Metadata`
// annotation every Kotlin-compiled class carries. This file reads it directly from the class
// file's constant pool — WITHOUT loading or linking the class via a ClassLoader, which would
// require every type the class references (Android, Compose, coroutines, ...) to also be
// resolvable, and isn't reliable across JVMs (verification can eagerly resolve supertypes).
//
// `kotlin-metadata-jvm`'s real entry point (verified against the resolved 2.4.20 jar with javap,
// not assumed): `KotlinClassMetadata.readStrict(kotlin.Metadata)` — a static method on
// `KotlinClassMetadata` itself (there is also a `Companion.readStrict`, but the static forwarder
// is simpler to call). It needs an actual `kotlin.Metadata` annotation instance, which this file
// builds directly: Kotlin lets source code instantiate an `annotation class` (which `kotlin.Metadata`
// is) like a regular constructor call, so the raw annotation element values read out of the class
// file (k, mv, d1, d2, xs, pn, xi) can be assembled into a real `Metadata` instance without
// reflection or a ClassLoader. (An earlier attempt used the library's own `KotlinClassHeader` —
// a concrete class implementing `kotlin.Metadata` for Java callers — but the resolved 2.4.20 jar
// marks it error-level-deprecated for Kotlin callers specifically in favour of this.)
// -------------------------------------------------------------------------------------------

private val PUBLIC_VISIBILITIES = setOf(Visibility.PUBLIC, Visibility.PROTECTED)

/** Per-class outcome of reading its `kotlin.Metadata`: whether the class itself is Kotlin-public/
 * protected, which JVM member names (by real, possibly-mangled, JVM name) are Kotlin-public, and
 * — for a class kind only — its companion's simple nested name, if it has one. */
private class ClassVisibility(
    val classIsPublic: Boolean,
    val declaredMembers: Map<String, Boolean>,
    val companionSimpleName: String? = null,
)

/**
 * Builds a predicate over the release AAR/jar's own extracted `.class` files: given a class's
 * fully-qualified binary name (dot-separated, `$` for nested classes — exactly the form the
 * `javap` header line in [GenerateApiDumpTask]/[GenerateJvmApiDumpTask] renders) and one trimmed
 * `javap -protected` member line from that class's body, answers whether that member is real
 * Kotlin-public (or protected) API.
 *
 * Deliberately permissive by default: a class with no `kotlin.Metadata` at all (plain Java, or a
 * compiler-synthetic class such as Compose's `ComposableSingletons` or a lambda `SyntheticClass`)
 * is not something this reader can judge, so every one of its members is kept and left to the
 * existing package-convention/synthetic-name filters. Likewise, a member whose *name* is not
 * found anywhere in the class's own Kotlin declarations (for example a Compose-compiler-injected
 * `$stable` field, which the Kotlin compiler's own metadata never sees) is kept. The only way a
 * member disappears is a *positive* match: the real Kotlin declaration behind that exact JVM name
 * was recorded as `private`/`internal`, or the class that declares it was.
 */
internal fun kotlinPublicApiFilter(classesRoot: File): (className: String, memberLine: String) -> Boolean {
    val rawPerClass: Map<String, ClassVisibility> = classesRoot.walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .mapNotNull { file ->
            val binaryName = file.relativeTo(classesRoot).path
                .removeSuffix(".class")
                .replace(File.separatorChar, '.')
            classVisibilityOf(file.readBytes())?.let { binaryName to it }
        }
        .toMap()

    // A `const val` declared inside a companion object is recorded in the COMPANION's own
    // kotlin.Metadata, but the Kotlin compiler emits it as a static field on the OUTER class, not
    // on the companion's own class file (verified: `private companion object { const val X }`
    // produces `public static final ... X;` on the outer class's javap dump, with nothing of the
    // kind on the companion's). Left alone, that means a private companion's const never matches
    // anything in the outer class's own declaredMembers map and falls through to the "unknown ->
    // keep" default — exactly the leak this task exists to close, just relocated. So the
    // companion's effective visibility (its own class visibility combined with each member's) is
    // folded into its outer class's map here, keyed by the outer's own binary name + "$" +
    // companion simple name (the same computation the outer class's own header would resolve to).
    val perClass: Map<String, ClassVisibility> = rawPerClass.mapValues { (binaryName, info) ->
        val companionBinaryName = info.companionSimpleName?.let { "$binaryName$$it" }
        val companionInfo = companionBinaryName?.let { rawPerClass[it] } ?: return@mapValues info
        val merged = info.declaredMembers.toMutableMap()
        companionInfo.declaredMembers.forEach { (name, isPublic) ->
            mergePublic(merged, name, companionInfo.classIsPublic && isPublic)
        }
        ClassVisibility(info.classIsPublic, merged, info.companionSimpleName)
    }

    return { className, memberLine ->
        val info = perClass[className]
        when {
            info == null -> true
            !info.classIsPublic -> false
            else -> info.declaredMembers[memberNameOf(memberLine, className)] ?: true
        }
    }
}

/** Reads the class's `kotlin.Metadata`, if any, and classifies it. Never throws: a class this
 * reader cannot make sense of (unreadable constant pool, an unsupported/future metadata kind, a
 * metadata version the resolved reader rejects) is treated exactly like "not Kotlin" — kept. */
private fun classVisibilityOf(classBytes: ByteArray): ClassVisibility? {
    val header = readKotlinMetadataHeader(classBytes) ?: return null
    val metadata = try {
        KotlinClassMetadata.readStrict(header)
    } catch (malformed: Exception) {
        return null
    }
    return when (metadata) {
        is KotlinClassMetadata.Class -> classVisibilityOfClass(metadata.kmClass)
        is KotlinClassMetadata.FileFacade ->
            ClassVisibility(classIsPublic = true, declaredMembers = declaredMembersOf(metadata.kmPackage.functions, metadata.kmPackage.properties))
        is KotlinClassMetadata.MultiFileClassPart ->
            ClassVisibility(classIsPublic = true, declaredMembers = declaredMembersOf(metadata.kmPackage.functions, metadata.kmPackage.properties))
        // MultiFileClassFacade holds no declarations of its own (just the part class names), and
        // SyntheticClass (e.g. a lambda body, or Compose's ComposableSingletons holder) holds no
        // KmClass/KmPackage either. Unknown is a metadata kind this reader's version predates.
        // None of these carry per-member visibility this filter can judge — keep everything.
        else -> null
    }
}

private fun classVisibilityOfClass(kmClass: KmClass): ClassVisibility {
    val declared = declaredMembersOf(kmClass.functions, kmClass.properties).toMutableMap()

    // A companion/nested class's own constructor is never listed as a function or property —
    // it needs its own walk. All overloads share the JVM name `<init>`; Kotlin metadata records
    // exactly one constructor per non-default-argument overload, and the marker/default-argument
    // bridge overloads javap also prints are matched by that same `<init>` name (see memberNameOf).
    kmClass.constructors.forEach { ctor ->
        mergePublic(declared, "<init>", ctor.visibility in PUBLIC_VISIBILITIES)
    }

    // Enum constants and the compiler-synthesized values()/valueOf()/getEntries() are not
    // recorded as functions or properties at all — kmClass.enumEntries is the only place they
    // exist. They inherit the enum class's own visibility; Kotlin has no per-entry visibility.
    if (kmClass.kind == ClassKind.ENUM_CLASS) {
        kmClass.kmEnumEntries.forEach { entry -> declared[entry.name] = true }
        declared["values"] = true
        declared["valueOf"] = true
        declared["getEntries"] = true
    }

    return ClassVisibility(
        classIsPublic = kmClass.visibility in PUBLIC_VISIBILITIES,
        declaredMembers = declared,
        companionSimpleName = kmClass.companionObject,
    )
}

private fun declaredMembersOf(functions: List<KmFunction>, properties: List<KmProperty>): Map<String, Boolean> {
    val result = mutableMapOf<String, Boolean>()

    functions.forEach { function ->
        val jvmName = function.signature?.name ?: function.name
        mergePublic(result, jvmName, function.visibility in PUBLIC_VISIBILITIES)
    }

    properties.forEach { property ->
        // Kotlin does not record a separate visibility for a property's getter/setter in this
        // codebase (no `private set`/`internal set` is used anywhere under sdk/ — verified by
        // grep), so the property's own visibility applies to its field, getter and setter alike.
        val isPublic = property.visibility in PUBLIC_VISIBILITIES
        property.fieldSignature?.let { mergePublic(result, it.name, isPublic) }
        property.getterSignature?.let { mergePublic(result, it.name, isPublic) }
        property.setterSignature?.let { mergePublic(result, it.name, isPublic) }
    }

    return result
}

/** Combines with OR: if any overload of a JVM name is Kotlin-public, the name counts as public.
 * This repo has no same-class overloads with mixed visibility (verified against every committed
 * baseline before this filter shipped), so this only ever matters for the constructor case above,
 * where it is exactly right — but OR is also the safe direction if that ever changes: it can only
 * under-filter (keep a name that one overload doesn't justify), never hide real public API. */
private fun mergePublic(target: MutableMap<String, Boolean>, name: String, isPublic: Boolean) {
    target[name] = (target[name] ?: false) || isPublic
}

/** Extracts the JVM member name a `javap -protected` line declares, given the exact binary class
 * name (dot/`$` form) it belongs to. A constructor line repeats the fully-qualified class name
 * where a method would put its own name, so it is matched against [className] directly rather
 * than guessed from a "simple name" — this also makes the default-argument/marker-bridge
 * constructor overload described in docs/COMPATIBILITY.md match the same `<init>` key as the
 * real one, since both print the class's full name before `(`. */
private fun memberNameOf(line: String, className: String): String {
    if ('(' !in line) {
        return line.removeSuffix(";").trim().substringAfterLast(' ')
    }
    val beforeParen = line.substringBefore('(').trim().substringAfterLast(' ')
    return if (beforeParen == className) "<init>" else beforeParen
}

// ===========================================================================================
// Minimal class-file reader: just enough of the constant pool and the class-level
// RuntimeVisibleAnnotations attribute to pull out the `kotlin.Metadata` annotation's element
// values. Deliberately does not use a ClassLoader/reflection (would require every referenced
// type — Android, Compose, coroutines — to resolve too) or a bytecode library beyond
// kotlin-metadata-jvm itself (no new dependency beyond the one this task asks for).
// ===========================================================================================

private const val KOTLIN_METADATA_DESCRIPTOR = "Lkotlin/Metadata;"

private fun readKotlinMetadataHeader(classBytes: ByteArray): Metadata? {
    val input = DataInputStream(ByteArrayInputStream(classBytes))
    if (input.readInt() != -0x35014542) return null // 0xCAFEBABE

    input.readUnsignedShort() // minor_version
    input.readUnsignedShort() // major_version

    val pool = readConstantPool(input) ?: return null

    input.readUnsignedShort() // access_flags
    input.readUnsignedShort() // this_class
    input.readUnsignedShort() // super_class
    val interfaceCount = input.readUnsignedShort()
    input.skipBytes(interfaceCount * 2)

    skipMembers(input) // fields
    skipMembers(input) // methods

    val attributeCount = input.readUnsignedShort()
    repeat(attributeCount) {
        val nameIndex = input.readUnsignedShort()
        val length = input.readInt()
        if (pool.getOrNull(nameIndex) == "RuntimeVisibleAnnotations") {
            val attributeBytes = ByteArray(length)
            input.readFully(attributeBytes)
            val header = readMetadataAnnotation(DataInputStream(ByteArrayInputStream(attributeBytes)), pool)
            if (header != null) return header
        } else {
            input.skipBytes(length)
        }
    }
    return null
}

/** Reads just enough of each constant-pool entry to skip it correctly and to resolve the Utf8
 * and Integer entries annotation parsing needs (Utf8 for names/strings, Integer for `k`/`mv`/`xi`);
 * returns null (meaning "give up, not filterable") on a tag this reader does not recognise rather
 * than guessing and misreading the rest of the class file. */
private fun readConstantPool(input: DataInputStream): List<Any?>? {
    val count = input.readUnsignedShort()
    val pool = arrayOfNulls<Any>(count)
    var index = 1
    while (index < count) {
        when (val tag = input.readUnsignedByte()) {
            1 -> pool[index] = input.readUTF()
            7, 8, 16, 19, 20 -> input.readUnsignedShort()
            15 -> {
                input.readUnsignedByte()
                input.readUnsignedShort()
            }
            3 -> pool[index] = input.readInt()
            4 -> input.readFloat()
            5, 6 -> {
                input.readLong()
                index++ // Long/Double each occupy two constant-pool slots.
            }
            9, 10, 11, 12, 17, 18 -> {
                input.readUnsignedShort()
                input.readUnsignedShort()
            }
            else -> return null
        }
        index++
    }
    return pool.asList()
}

private fun skipMembers(input: DataInputStream) {
    val count = input.readUnsignedShort()
    repeat(count) {
        input.readUnsignedShort() // access_flags
        input.readUnsignedShort() // name_index
        input.readUnsignedShort() // descriptor_index
        val attributeCount = input.readUnsignedShort()
        repeat(attributeCount) {
            input.readUnsignedShort() // attribute_name_index
            input.skipBytes(input.readInt())
        }
    }
}

private fun readMetadataAnnotation(input: DataInputStream, pool: List<Any?>): Metadata? {
    val numAnnotations = input.readUnsignedShort()
    repeat(numAnnotations) {
        val typeIndex = input.readUnsignedShort()
        val typeDescriptor = pool.getOrNull(typeIndex)
        val numPairs = input.readUnsignedShort()
        val values = mutableMapOf<String, Any?>()
        repeat(numPairs) {
            val nameIndex = input.readUnsignedShort()
            val value = readElementValue(input, pool)
            (pool.getOrNull(nameIndex) as? String)?.let { values[it] = value }
        }
        if (typeDescriptor == KOTLIN_METADATA_DESCRIPTOR) {
            return buildHeader(values)
        }
    }
    return null
}

@Suppress("UNCHECKED_CAST")
private fun buildHeader(values: Map<String, Any?>): Metadata {
    val k = values["k"] as? Int ?: 1
    val mv = (values["mv"] as? List<Any?>)?.map { it as Int }?.toIntArray() ?: IntArray(0)
    val d1 = (values["d1"] as? List<Any?>)?.map { it as String }?.toTypedArray() ?: emptyArray()
    val d2 = (values["d2"] as? List<Any?>)?.map { it as String }?.toTypedArray() ?: emptyArray()
    val xs = values["xs"] as? String ?: ""
    val pn = values["pn"] as? String ?: ""
    val xi = values["xi"] as? Int ?: 0
    // Kotlin allows instantiating an `annotation class` (kotlin.Metadata is one) with an ordinary
    // constructor call — this produces a real object satisfying the `kotlin.Metadata` interface
    // that KotlinClassMetadata.readStrict can read, with no ClassLoader or reflection involved.
    return Metadata(kind = k, metadataVersion = mv, data1 = d1, data2 = d2, extraString = xs, packageName = pn, extraInt = xi)
}

/** Reads one `element_value` structure (JVM class file format §4.7.16.1). Only the tags that can
 * appear inside `@kotlin.Metadata` are resolved to a value (int/string constant-pool lookups, and
 * arrays of those); enum/class/nested-annotation tags are consumed (to keep the stream aligned)
 * and discarded since `@kotlin.Metadata` never uses them. */
private fun readElementValue(input: DataInputStream, pool: List<Any?>): Any? {
    return when (input.readUnsignedByte().toChar()) {
        'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's' -> pool.getOrNull(input.readUnsignedShort())
        'e' -> {
            input.readUnsignedShort()
            input.readUnsignedShort()
            null
        }
        'c' -> {
            input.readUnsignedShort()
            null
        }
        '@' -> {
            input.readUnsignedShort()
            val numPairs = input.readUnsignedShort()
            repeat(numPairs) {
                input.readUnsignedShort()
                readElementValue(input, pool)
            }
            null
        }
        '[' -> {
            val num = input.readUnsignedShort()
            (0 until num).map { readElementValue(input, pool) }
        }
        else -> null
    }
}
