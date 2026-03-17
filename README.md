# DSM - Dependency Structure Matrix

A Gradle plugin that generates a [Dependency Structure Matrix](https://en.wikipedia.org/wiki/Design_structure_matrix) from compiled JVM bytecode. It analyzes inter-package dependencies to help identify cyclic dependencies, measure coupling, and guide refactoring decisions.

Built primarily for use by **coding agents** (AI assistants that write and refactor code), though it is equally useful for human developers. Works with any JVM language (Kotlin, Java, Scala, etc.) since it operates on compiled `.class` files using [ASM](https://asm.ow2.io/).

## Why use a DSM?

A Dependency Structure Matrix gives you a compact, structured view of how packages in your codebase depend on each other. This matters for several reasons:

**Architecture stays visible.** As codebases grow, the intended architecture erodes silently. A DSM makes the actual dependency structure concrete -- you see immediately whether your `persistence` layer references your `api` layer when it should not.

**Cyclic dependencies surface early.** Mutual dependencies between packages are one of the most common causes of code that is hard to change. The DSM highlights these as cells above the diagonal, making them impossible to miss.

**Refactoring gets direction.** Instead of guessing which packages are too tightly coupled, you get a quantified matrix with exact reference counts and the specific class-level edges responsible. This turns "we should clean up the architecture" into concrete, actionable work.

### Why this is especially useful for coding agents

Coding agents operate without the implicit architectural understanding that human developers accumulate over time. They can read individual files, but they lack a birds-eye view of the dependency structure. A DSM fills that gap:

- **Structured context.** The console output is compact, machine-readable text that fits easily into an agent's context window. An agent can run `./gradlew dsm`, read the matrix, and understand the package-level architecture in seconds.
- **Objective guardrails.** An agent can run the DSM before and after a refactoring to verify it did not introduce new cyclic dependencies or increase coupling. This is a concrete, checkable constraint rather than a vague instruction to "keep the architecture clean."
- **Scoped analysis.** The `rootPackage` and `depth` parameters let you point the agent at the relevant slice of the codebase, keeping the output focused and the signal-to-noise ratio high.

## Getting started

Copy-paste this to your agent:

> Add the no.f12.dsm plugin to this project and update AGENTS.md with instructions to run the DSM to check for status after completing a feature. It is always good to check pre and after status as well.

## Installation

Apply the plugin in your `build.gradle.kts`:

```kotlin
plugins {
    id("no.f12.dsm") version "0.1.0"
}
```

## Configuration

Configure the plugin via the `dsm` extension block:

```kotlin
dsm {
    // Root package prefix to scope the analysis (default: "" = all packages)
    rootPackage.set("com.example.myapp")

    // Number of package segments to group by (default: 2)
    depth.set(3)

    // Directory containing compiled .class files (default: build/classes/kotlin/main)
    classesDir.set(layout.buildDirectory.dir("classes/java/main"))

    // Optional: generate an HTML report
    htmlOutputFile.set(layout.buildDirectory.file("reports/dsm.html"))
}
```

All properties are optional and have sensible defaults.

## Usage

Run the analysis:

```bash
./gradlew dsm
```

### Command-line overrides

Override configuration without editing `build.gradle.kts`:

```bash
# Override depth
./gradlew dsm -Pdsm.depth=3

# Generate HTML report
./gradlew dsm -Pdsm.html=build/reports/dsm.html
```

## Output

### Console

The task prints a numbered DSM matrix to the console:

```
=== Dependency Structure Matrix (DSM) ===

Legend:
    1: api
    2: core
    3: persistence

Reading: row depends on column. Cell value = number of dependency references.
         Cells below the diagonal indicate forward dependencies (good).
         Cells above the diagonal indicate backward/cyclic dependencies (review these).

                     1   2   3
---------------------------------
  1. api             .       2
  2. core            3   .
  3. persistence     1   4   .
```

If cyclic dependencies are detected, they are explicitly called out with the specific class-level edges involved.

### HTML report

When `htmlOutputFile` is configured, the plugin generates a self-contained HTML page with:

- Color-coded cells: **green** for forward dependencies, **red** for backward/cyclic dependencies
- Hover tooltips showing class-level dependency details
- A collapsible package legend

## How it works

1. **Bytecode scanning** -- Walks all `.class` files in the configured classes directory. Uses ASM's `ClassVisitor` to extract type references from superclasses, interfaces, field types, method signatures, and instructions (method calls, field accesses, type casts, new instances).

2. **Dependency collection** -- For each class, collects all referenced types within the configured `rootPackage` scope, filtering out self-references and intra-package dependencies.

3. **Matrix construction** -- Truncates package names to the configured `depth` and aggregates dependency counts into a matrix of `(sourcePackage, targetPackage) -> count`.

4. **Rendering** -- Produces console text output and optionally an HTML report. The matrix is sorted alphabetically by package name, with dependencies below the diagonal considered "forward" (expected) and above the diagonal considered "backward" (potential cycles).

## Building from source

```bash
./gradlew build
```

Requires Gradle 9.4+ (included via the Gradle wrapper).

## License

See [LICENSE](LICENSE) for details.
