# jdtls-skagedal

This is my fork of [eclipse.jdt.ls](https://github.com/eclipse-jdtls/eclipse.jdt.ls), a Java language server.

# Features

## Configuration file

Some things in jdtls are only configurable through Java system properties. These are possible to set when launching jdtls by adding -Dproperty=value to the JVM arguments, but that's not very convenient. The language server will often be launched by an editor, so either the editor needs to support adding JVM arguments, or the user needs to set up a wrapper script that adds the JVM arguments and then launches jdtls.

This fork allows putting configuration in a file located at ~/.skagedal-tools/jdtls/config.ini. The configuration file is in INI format, and supports a `[system-properties]` section where Java system properties can be specified.

See [commit](https://github.com/skagedal/jdtls-skagedal/commit/98bad32a89b1139cd897444b6447b091a9287c05).

## Make Gradle APT output configurable

See [commit](https://github.com/skagedal/jdtls-skagedal/commit/fd694681bfeb0a033d1cf9325a25dffaf0abb4b0).

## Expose quickassist as normal refactorings

See [commit](https://github.com/skagedal/jdtls-skagedal/commit/5701dd9cf6ccad9cefa526809d6939f220648369).

# Bug fixes

1. [Fix workspace settings in settings URLs](https://github.com/skagedal/jdtls-skagedal/commit/44bd2feb5081cc099e20806a130cd2351014d3a1)

# My configuration

Given the above features, the following `~/.skagedal-tools/jdtls/config.ini` will make jdtls avoid putting any unwanted files in the project tree in Gradle projects, and instead put them in the `build/` directory (separated from other Gradle output)

```ini
[system-properties]
# Redirect Eclipse project metadata (.project, .classpath, .settings/, .factorypath)
# into JDTLS's workspace data directory instead of the project tree.
java.import.generatesMetadataFilesAtProjectRoot = false

# Move annotation-processor output for Gradle projects out of bin/ (upstream default)
# and into build/jdtls-generated/ — which is typically gitignored in Gradle projects.
java.import.gradle.annotationProcessing.generatedSourcesDir = build/jdtls-generated/sources/annotations
java.import.gradle.annotationProcessing.generatedTestSourcesDir = build/jdtls-generated/test-sources/annotations
```
