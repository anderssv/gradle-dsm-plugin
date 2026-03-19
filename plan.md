# Plan

## 1. Maven plugin support (High value)

Create a Maven plugin equivalent in the same repo. The core bytecode analysis logic (dependency extraction, matrix building, rendering) is build-tool-agnostic — it operates on `.class` file directories. Factor the core into a shared module and wire it into both a Gradle plugin and a Maven plugin (Mojo). This would make the DSM available to the large Maven user base without duplicating the analysis code.
