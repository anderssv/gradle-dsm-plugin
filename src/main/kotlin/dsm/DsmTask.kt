package dsm

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.inputStream

/**
 * Gradle task that generates a Dependency Structure Matrix (DSM) from compiled bytecode.
 *
 * Analyzes .class files using ASM to extract inter-package dependencies,
 * then renders the result as a DSM matrix in console text and optionally HTML.
 */
@DisableCachingByDefault(because = "Primary output is console text; re-run is cheap")
abstract class DsmTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val classesDir: DirectoryProperty

    @get:Input
    abstract val rootPackage: Property<String>

    @get:Input
    abstract val depth: Property<Int>

    @get:Optional
    @get:OutputFile
    abstract val htmlOutputFile: RegularFileProperty

    init {
        group = "dsm"
        description = "Generates a Dependency Structure Matrix (DSM) from compiled bytecode"
    }

    @TaskAction
    fun generate() {
        val classesPath = classesDir.get().asFile.toPath()
        val prefix = rootPackage.get()
        val depthValue = depth.get()

        require(Files.isDirectory(classesPath)) { "Not a directory: $classesPath" }

        val dependencies = extractDependencies(classesPath, prefix)
        val matrix = buildMatrix(dependencies, prefix, depthValue)

        printConsoleMatrix(matrix)

        if (htmlOutputFile.isPresent) {
            val html = renderHtmlMatrix(matrix)
            htmlOutputFile.get().asFile.writeText(html)
            logger.lifecycle("HTML DSM written to: ${htmlOutputFile.get().asFile}")
        }
    }
}

// ---------------------------------------------------------------------------
// Data types
// ---------------------------------------------------------------------------

/** A single dependency edge: class in [sourcePackage] references class in [targetPackage]. */
data class Dependency(
    val sourcePackage: String,
    val targetPackage: String,
    val sourceClass: String,
    val targetClass: String,
)

/** The computed DSM matrix ready for rendering. */
data class DsmMatrix(
    val packages: List<String>,
    val cells: Map<Pair<String, String>, Int>,
    val classDependencies: Map<Pair<String, String>, Set<Pair<String, String>>>,
)

// ---------------------------------------------------------------------------
// Bytecode analysis
// ---------------------------------------------------------------------------

internal fun extractDependencies(classesDir: Path, rootPrefix: String): List<Dependency> {
    val prefixPath = rootPrefix.replace('.', '/')
    val dependencies = mutableListOf<Dependency>()

    Files.walk(classesDir)
        .filter { it.extension == "class" }
        .forEach { classFile ->
            val relativePath = classesDir.relativize(classFile).toString()
            if (prefixPath.isNotEmpty() && !relativePath.startsWith(prefixPath)) return@forEach

            classFile.inputStream().use { input ->
                val reader = ClassReader(input)
                val collector = DependencyCollector(rootPrefix)
                reader.accept(collector, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)

                val sourceClass = reader.className.replace('/', '.')
                val sourcePackage = sourceClass.substringBeforeLast('.', "")

                collector.referencedTypes
                    .asSequence()
                    .filter { it != sourceClass }
                    .filter { rootPrefix.isEmpty() || it.startsWith(rootPrefix) }
                    .forEach { targetClass ->
                        val targetPackage = targetClass.substringBeforeLast('.', "")
                        if (targetPackage != sourcePackage) {
                            dependencies += Dependency(sourcePackage, targetPackage, sourceClass, targetClass)
                        }
                    }
            }
        }

    return dependencies
}

/**
 * ASM ClassVisitor that collects all type references from a class:
 * superclass, interfaces, field types, method signatures, annotations,
 * and instructions (method calls, field accesses, type casts, new instances).
 */
private class DependencyCollector(private val rootPrefix: String) : ClassVisitor(Opcodes.ASM9) {
    val referencedTypes = mutableSetOf<String>()

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        superName?.let { addInternalName(it) }
        interfaces?.forEach { addInternalName(it) }
    }

    override fun visitField(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        value: Any?,
    ): FieldVisitor? {
        descriptor?.let { addDescriptorTypes(it) }
        return null
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        descriptor?.let { addDescriptorTypes(it) }
        exceptions?.forEach { addInternalName(it) }

        return object : MethodVisitor(Opcodes.ASM9) {
            override fun visitMethodInsn(
                opcode: Int,
                owner: String?,
                name: String?,
                descriptor: String?,
                isInterface: Boolean,
            ) {
                owner?.let { addInternalName(it) }
                descriptor?.let { addDescriptorTypes(it) }
            }

            override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
                owner?.let { addInternalName(it) }
                descriptor?.let { addDescriptorTypes(it) }
            }

            override fun visitTypeInsn(opcode: Int, type: String?) {
                type?.let { addInternalName(it) }
            }

            override fun visitLdcInsn(value: Any?) {
                if (value is Type) {
                    addType(value)
                }
            }
        }
    }

    private fun addInternalName(internalName: String) {
        val className = internalName.replace('/', '.')
        if (className.startsWith('[')) return
        if (rootPrefix.isNotEmpty() && !className.startsWith(rootPrefix)) return
        val baseName = className.substringBefore('$')
        referencedTypes += baseName
    }

    private fun addDescriptorTypes(descriptor: String) {
        val methodType = runCatching { Type.getType(descriptor) }.getOrNull() ?: return
        when (methodType.sort) {
            Type.METHOD -> {
                addType(methodType.returnType)
                methodType.argumentTypes.forEach { addType(it) }
            }
            else -> addType(methodType)
        }
    }

    private fun addType(type: Type) {
        when (type.sort) {
            Type.OBJECT -> addInternalName(type.internalName)
            Type.ARRAY -> addType(type.elementType)
        }
    }
}

// ---------------------------------------------------------------------------
// Matrix building
// ---------------------------------------------------------------------------

internal fun buildMatrix(dependencies: List<Dependency>, rootPrefix: String, depth: Int): DsmMatrix {
    fun truncatePackage(pkg: String): String {
        val relative = if (rootPrefix.isNotEmpty() && pkg.startsWith("$rootPrefix.")) {
            pkg.removePrefix("$rootPrefix.")
        } else if (rootPrefix.isNotEmpty() && pkg == rootPrefix) {
            ""
        } else {
            pkg
        }
        val parts = relative.split('.')
        return parts.take(depth).joinToString(".")
    }

    val cells = mutableMapOf<Pair<String, String>, Int>()
    val classDeps = mutableMapOf<Pair<String, String>, MutableSet<Pair<String, String>>>()

    dependencies.forEach { dep ->
        val src = truncatePackage(dep.sourcePackage)
        val tgt = truncatePackage(dep.targetPackage)
        if (src != tgt && src.isNotEmpty() && tgt.isNotEmpty()) {
            val key = src to tgt
            cells[key] = (cells[key] ?: 0) + 1
            classDeps.getOrPut(key) { mutableSetOf() } +=
                dep.sourceClass.substringAfterLast('.') to dep.targetClass.substringAfterLast('.')
        }
    }

    val packages = (cells.keys.map { it.first } + cells.keys.map { it.second })
        .distinct()
        .sorted()

    return DsmMatrix(packages, cells, classDeps)
}

// ---------------------------------------------------------------------------
// Console rendering
// ---------------------------------------------------------------------------

internal fun printConsoleMatrix(matrix: DsmMatrix) {
    val packages = matrix.packages
    if (packages.isEmpty()) {
        println("No inter-package dependencies found.")
        return
    }

    val indexLabels = packages.mapIndexed { i, _ -> (i + 1).toString() }
    val colWidth = maxOf(indexLabels.maxOf { it.length }, 4)

    println("=== Dependency Structure Matrix (DSM) ===")
    println()
    println("Legend:")
    packages.forEachIndexed { i, pkg ->
        println("  ${(i + 1).toString().padStart(3)}: $pkg")
    }
    println()

    println("Reading: row depends on column. Cell value = number of dependency references.")
    println("         Cells below the diagonal indicate forward dependencies (good).")
    println("         Cells above the diagonal indicate backward/cyclic dependencies (review these).")
    println()

    val labelWidth = packages.maxOf { it.length }.coerceAtLeast(10)
    print("".padEnd(labelWidth + 6))
    packages.forEachIndexed { i, _ ->
        print((i + 1).toString().padStart(colWidth))
    }
    println()

    val totalWidth = labelWidth + 6 + packages.size * colWidth
    println("-".repeat(totalWidth))

    packages.forEachIndexed { rowIdx, rowPkg ->
        val rowLabel = "${(rowIdx + 1).toString().padStart(3)}. ${rowPkg.padEnd(labelWidth)}"
        print(rowLabel)
        packages.forEachIndexed { colIdx, colPkg ->
            val cell = when {
                rowIdx == colIdx -> "."
                else -> matrix.cells[rowPkg to colPkg]?.toString() ?: ""
            }
            print(cell.padStart(colWidth))
        }
        println()
    }

    println()

    val cyclicPairs = matrix.packages.flatMapIndexed { rowIdx, rowPkg ->
        matrix.packages.mapIndexedNotNull { colIdx, colPkg ->
            if (colIdx > rowIdx &&
                matrix.cells.containsKey(rowPkg to colPkg) &&
                matrix.cells.containsKey(colPkg to rowPkg)
            ) {
                Triple(rowPkg, colPkg, matrix.cells[rowPkg to colPkg]!! to matrix.cells[colPkg to rowPkg]!!)
            } else {
                null
            }
        }
    }

    if (cyclicPairs.isNotEmpty()) {
        println("WARNING: Cyclic dependencies detected:")
        cyclicPairs.forEach { (a, b, counts) ->
            println("  $a <-> $b  (${counts.first} refs / ${counts.second} refs)")
            val fwd = matrix.classDependencies[a to b]
            val bwd = matrix.classDependencies[b to a]
            fwd?.take(5)?.forEach { (src, tgt) -> println("    $a.$src -> $b.$tgt") }
            bwd?.take(5)?.forEach { (src, tgt) -> println("    $b.$src -> $a.$tgt") }
        }
    }
}

// ---------------------------------------------------------------------------
// HTML rendering
// ---------------------------------------------------------------------------

internal fun renderHtmlMatrix(matrix: DsmMatrix): String {
    val packages = matrix.packages
    return buildString {
        appendLine("<!DOCTYPE html>")
        appendLine("<html lang=\"en\">")
        appendLine("<head>")
        appendLine("<meta charset=\"UTF-8\">")
        appendLine("<title>DSM — Dependency Structure Matrix</title>")
        appendLine("<style>")
        appendLine(
            """
            body { font-family: 'Segoe UI', system-ui, sans-serif; margin: 2rem; background: #f8f9fa; }
            h1 { color: #212529; }
            .legend { margin-bottom: 1rem; font-size: 0.9rem; }
            .legend dt { font-weight: bold; }
            table { border-collapse: collapse; font-size: 0.85rem; }
            th, td { border: 1px solid #dee2e6; padding: 4px 8px; text-align: center; min-width: 36px; }
            th { background: #e9ecef; font-weight: 600; }
            td.diagonal { background: #343a40; color: #343a40; }
            td.empty { background: #fff; }
            td.forward { background: #d4edda; color: #155724; font-weight: 600; cursor: pointer; }
            td.backward { background: #f8d7da; color: #721c24; font-weight: 600; cursor: pointer; }
            td.row-label { text-align: left; background: #e9ecef; font-weight: 600; white-space: nowrap; }
            .tooltip { display: none; position: absolute; background: #fff; border: 1px solid #adb5bd;
                       padding: 8px 12px; border-radius: 4px; box-shadow: 0 2px 8px rgba(0,0,0,0.15);
                       font-size: 0.8rem; z-index: 10; max-width: 400px; text-align: left; }
            td:hover .tooltip { display: block; }
            .note { margin-top: 1rem; font-size: 0.85rem; color: #6c757d; }
            """.trimIndent(),
        )
        appendLine("</style>")
        appendLine("</head>")
        appendLine("<body>")
        appendLine("<h1>Dependency Structure Matrix</h1>")
        appendLine(
            "<p class=\"note\">Row depends on column. " +
                "<span style=\"color:#155724\">Green = forward dependency (expected)</span>, " +
                "<span style=\"color:#721c24\">Red = backward dependency (potential cycle, review)</span>.</p>",
        )

        appendLine("<details><summary>Package legend</summary><ol>")
        packages.forEach { pkg -> appendLine("<li>$pkg</li>") }
        appendLine("</ol></details>")

        appendLine("<table>")
        appendLine("<tr><th></th>")
        packages.forEachIndexed { i, _ -> appendLine("<th>${i + 1}</th>") }
        appendLine("</tr>")

        packages.forEachIndexed { rowIdx, rowPkg ->
            appendLine("<tr>")
            appendLine("<td class=\"row-label\">${rowIdx + 1}. $rowPkg</td>")
            packages.forEachIndexed { colIdx, colPkg ->
                val count = matrix.cells[rowPkg to colPkg]
                when {
                    rowIdx == colIdx -> appendLine("<td class=\"diagonal\">·</td>")
                    count == null -> appendLine("<td class=\"empty\"></td>")
                    else -> {
                        val cssClass = if (rowIdx > colIdx) "forward" else "backward"
                        val deps = matrix.classDependencies[rowPkg to colPkg]
                            ?.joinToString("<br>") { (src, tgt) -> "$src -> $tgt" }
                            ?: ""
                        appendLine("<td class=\"$cssClass\" style=\"position:relative\">$count")
                        if (deps.isNotEmpty()) {
                            appendLine(
                                "<div class=\"tooltip\"><strong>$rowPkg -> $colPkg</strong><br>$deps</div>",
                            )
                        }
                        appendLine("</td>")
                    }
                }
            }
            appendLine("</tr>")
        }

        appendLine("</table>")
        appendLine("<p class=\"note\">Generated by dsm-generator. Analyzed ${packages.size} packages.</p>")
        appendLine("</body></html>")
    }
}
