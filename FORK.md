# jdtls-skagedal

This is my fork of [eclipse.jdt.ls](https://github.com/eclipse-jdtls/eclipse.jdt.ls), a Java language server.

# Features

## Configuration file

Some things in jdtls are only configurable through Java system properties. These are possible to set when launching jdtls by adding -Dproperty=value to the JVM arguments, but that's not very convenient. The language server will often be launched by an editor, so either the editor needs to support adding JVM arguments, or the user needs to set up a wrapper script that adds the JVM arguments and then launches jdtls.

This fork allows putting configuration in a file located at ~/.skagedal-tools/jdtls/config.ini. The configuration file is in INI format, and supports a `[system-properties]` section where Java system properties can be specified.

See [commit](https://github.com/skagedal/jdtls-skagedal/commit/98bad32a89b1139cd897444b6447b091a9287c05).

## Make Gradle APT output configurable

See [commit](https://github.com/skagedal/jdtls-skagedal/commit/fd694681bfeb0a033d1cf9325a25dffaf0abb4b0).

## Settings as JVM system properties

Upstream, a preference is either a JVM system property or an LSP setting, and which one it is was decided by whoever added it. `java.import.generatesMetadataFilesAtProjectRoot` is a system property, read straight from `System.getProperty` by the filesystem bundle, and no client can set it over LSP. Everything else — `java.configuration.runtimes`, `java.format.settings.url`, `java.import.gradle.version` — is the other way round, settable only by a client that has somewhere to put LSP settings.

That second group is the problem for clients that have nowhere. Claude Code runs a `jdtls` found on `PATH` and sends no settings at all, so those preferences sit at their defaults no matter what you want.

This fork makes every preference key `Preferences` declares settable as a system property too, which means the config file above can carry any of them. A property wins over the same key sent by the client, so dotfile-managed configuration overrides per-editor configuration. Values are strings, coerced the same way LSP values already are; a value starting with `{` or `[` is parsed as JSON, so structured preferences work:

```ini
java.configuration.runtimes = [{"name": "JavaSE-26", "path": "/Library/Java/JavaVirtualMachines/zulu-26.jdk/Contents/Home", "default": true}]
```

Implemented as `SystemPropertyPreferences`, hooked in with one line in `Preferences.updateFrom` and one in the `PreferenceManager` constructor — the latter because a client sending no settings never reaches `updateFrom` otherwise. It generalises what two hand-written `System.getProperty` overrides in `Preferences` getters used to do for the Gradle APT paths; those are gone, so this fork's delta in `Preferences.java` is now purely additive.

## Expose quickassist as normal refactorings

See [commit](https://github.com/skagedal/jdtls-skagedal/commit/5701dd9cf6ccad9cefa526809d6939f220648369).

# Bug fixes

## Fix workspace settings in settings URLs

- [Commit](https://github.com/skagedal/jdtls-skagedal/commit/44bd2feb5081cc099e20806a130cd2351014d3a1)
- [Report](https://github.com/eclipse-jdtls/eclipse.jdt.ls/pull/3749#discussion_r3292252456) (should probably make an issue)

## Preserve Gradle's annotation processor path order

- [Commit](https://github.com/skagedal/jdtls-skagedal/commit/e14cc89d406d72b180de76df8699e0c33e9442eb)
- Reproducer and upstream issue draft: `~/code/reproducers/jdtls-apt-processor-init-skipped` (not filed yet)

Annotation processors were collected into a `HashSet` before `.factorypath` was
written, so ECJ discovered them in rehashed order rather than the order Gradle
gives javac. That order matters: both compilers stop looking for processors once
every annotation present has been claimed, so a processor that claims nothing —
one that exists only to have `init()` called, to capture the
`ProcessingEnvironment` for a non-processor collaborator to read — runs only if
it precedes the processors doing the claiming.

The visible symptom in Aira's Gradle projects, which pair MapStruct with
`no.entur.mapstruct.spi:protobuf-spi-impl`: every `@Mapper` interface reported
`ProcessingEnvOptionsHolder not initialized yet`, no `*MapperImpl` was generated
in the editor at all, and `./gradlew` on the same sources built cleanly.

Two parts — `init.gradle` keeps Gradle's order instead of rehashing it, and
`GradleBuildSupport` feeds the jars in backwards, because `addExternalJar` adds
to the *head* of the path (it shares `FactoryPath.internalAdd` with
`addEntryToHead`, and `FactoryPath` stores `_path` in reverse of path order,
which `getAllContainers()` undoes via a private `getReversed()`).

# My configuration

Given the above features, the following `~/.skagedal-tools/jdtls/config.ini` keeps unwanted files out of the project tree in Gradle projects, putting them in `build/` (separated from other Gradle output) instead, and gives clients with no LSP settings of their own the same configuration Zed gets.

```ini
[system-properties]
# Redirect Eclipse project metadata (.project, .classpath, .settings/, .factorypath)
# into JDTLS's workspace data directory instead of the project tree.
java.import.generatesMetadataFilesAtProjectRoot = false

# Move annotation-processor output for Gradle projects out of bin/ (upstream default)
# and into build/jdtls-generated/ — which is typically gitignored in Gradle projects.
java.import.gradle.annotationProcessing.generatedSourcesDir = build/jdtls-generated/sources/annotations
java.import.gradle.annotationProcessing.generatedTestSourcesDir = build/jdtls-generated/test-sources/annotations

# The rest need "Settings as JVM system properties" above; they are ordinary LSP
# settings that no -D could reach before.
java.configuration.runtimes = [{"name": "JavaSE-26", "path": "/Library/Java/JavaVirtualMachines/zulu-26.jdk/Contents/Home", "default": true}]

# Re-sync a project when its build files change. The default, "interactive", asks
# the client first, and a client that does not surface the prompt leaves a stale
# .classpath stale through every restart.
java.configuration.updateBuildConfiguration = automatic

# Fallback for project dirs with no Gradle wrapper of their own; the version
# bundled with the Tooling API (8.9) no current JDK can run.
java.import.gradle.version = 9.4.1

# Use the repo's Eclipse formatter profile when it has one. Resolved per-project
# against the workspace root; missing is not an error.
java.format.settings.url = config/spotless/eclipse-formatter.xml
```

This file is not the only way in — anything that can add JVM args works, e.g. `jdtls --jvm-arg=-Djava.import.gradle.version=9.4.1`. The file just means no client needs to.

# Other issues (not fixed)

## Newly generated sources under `build/generated/sources/proto/**` aren't picked up

(This section is Claude-generated, not fully verified by Simon.)

### Symptom

In a Gradle multi-module project using the [`com.google.protobuf`](https://github.com/google/protobuf-gradle-plugin) plugin, a downstream module fails to resolve a type that lives in a freshly-generated proto class — even though:

- the corresponding `.java` file exists on disk under `build/generated/sources/proto/main/java/...`,
- that directory is registered as a source folder in the upstream module's `.classpath`, and
- `./gradlew build` from the CLI succeeds.

Older proto-generated classes in the same package resolve fine; only files generated after jdtls last imported the project are missing.

### Repro

1. Open a Gradle multi-module project where module B uses `protobuf-gradle-plugin` and module A has `implementation(project(":B"))`. Let jdtls import.
2. Add a new `.proto` message to B and run `./gradlew :B:generateProto` (or any build that regenerates).
3. In module A, reference the new type. jdtls reports `cannot be resolved to a type` despite the source file being present.

### Diagnosis

Buildship writes `.classpath` once at import; new files inside an already-declared source folder are supposed to be picked up by Eclipse's resource framework, but jdtls runs headless and the OS file watcher / `IResource.refreshLocal` regularly misses files written by external tools like `./gradlew`. The JDT incremental builder only compiles files the Eclipse resource tree knows about, so until something forces a `refreshLocal(DEPTH_INFINITE)`, the new sources are invisible.

You can see the gap by inspecting the jdtls workspace's compiled output for the upstream module (`<module>/bin/main/<package>/`): the older proto classes are there, the newly-generated ones aren't.

### Workarounds

- **Send `java/projectConfigurationsUpdate`** (LSP notification on the `java/` segment, parameter is a `ProjectConfigurationsUpdateParam` containing the URIs of the relevant `build.gradle[.kts]` files). This triggers Buildship's `SynchronizationJob`, which re-queries Gradle and runs a deep refresh. This is what vscode-java's "Java: Reload Projects" calls. The deprecated singular form is `java/projectConfigurationUpdate` taking a single `TextDocumentIdentifier`. Implemented in `JDTLanguageServer#projectConfigurationUpdate` (`org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/handlers/JDTLanguageServer.java`).
- **Touch the upstream `build.gradle[.kts]`** before reloading — jdtls only re-imports when it detects a changed build-file hash, so a no-op edit can shake things loose.
- **Nuke the jdtls workspace** at `~/Library/Caches/jdtls/jdtls-<hash>/` and reopen. Heavy hammer, always works.

Zed currently has no way to send arbitrary LSP requests/notifications ([zed#13756](https://github.com/zed-industries/zed/issues/13756)), so options 1 and 2 are out of reach from inside Zed today.

### Related upstream issues

No exact match has been filed. Related:

- [eclipse.jdt.ls#3093](https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3093) — "package-private classes failed to be resolved until any dummy change made" (same "touch to wake up" pattern)
- [eclipse.jdt.ls#1852](https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/1852) — only protobuf-tagged issue, but a different symptom
- [eclipse.jdt.ls#3349](https://github.com/eclipse-jdtls/eclipse.jdt.ls/pull/3349) — merged Jan 2025, "Ensure Gradle project reacts to build configuration changes" — confirms maintainers' model: rescans happen via Buildship sync, not via the builder
- [vscode-java#1639](https://github.com/redhat-developer/vscode-java/issues/1639) — "generated-sources/java is not picked up as a source path" (Maven flavor)

Buildship source lives at https://github.com/eclipse-buildship/buildship; the relevant `SynchronizationJob` is under `org.eclipse.buildship.core/src/main/java/org/eclipse/buildship/core/internal/workspace/`.

### Possible fix in this fork

Either (a) wire a filesystem watcher on each project's Gradle-declared generated-source directories so that changes trigger a Buildship resync, or (b) cheaper — always call `IProject.refreshLocal(DEPTH_INFINITE)` on the relevant project before each incremental build kicks off. (b) is closer in spirit to what Eclipse-the-IDE effectively gets from its own resource listeners.

# Zed specifics

My goal is to get things working well under Zed. This section documents some issues with that effort.

- [Support executing LSP commands](https://github.com/zed-industries/zed/issues/13756)

# Links

## Zed

- [Zed Java extension](https://github.com/zed-extensions/java)

## JDTLS

- [Buildship](https://github.com/eclipse-buildship/buildship)

## LSP stuff

- [LSP specification](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)
- [caffeine-ls](https://github.com/cubewhy/caffeine-ls), an early-stage Java LSP written in Rust.
- [lsp-devtools](https://github.com/swyddfa/lsp-devtools)
- [lsp-bridge](https://github.com/ciresnave/lsp-bridge)
- [async-lsp](https://crates.io/crates/async-lsp)
