package dsm

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * Configuration extension for the DSM plugin.
 *
 * Example:
 * ```
 * dsm {
 *     rootPackage.set("no.bankid.selvbetjening")
 *     depth.set(3)
 *     htmlOutputFile.set(layout.buildDirectory.file("reports/dsm.html"))
 * }
 * ```
 */
abstract class DsmExtension {
    /** Root directory containing compiled .class files. Defaults to build/classes/kotlin/main. */
    abstract val classesDir: DirectoryProperty

    /** Root package prefix to scope the analysis. Defaults to "" (all packages). */
    abstract val rootPackage: Property<String>

    /** Number of package segments to group by. Defaults to 2. */
    abstract val depth: Property<Int>

    /** Optional HTML output file. If not set, only console output is produced. */
    abstract val htmlOutputFile: RegularFileProperty
}
