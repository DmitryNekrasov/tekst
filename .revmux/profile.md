# utf8-string

## What it is

- A Kotlin Multiplatform library, `utf8-string/`: `Utf8String` and `String.u8`, which encodes a string as UTF-8 with
  unpaired surrogates as U+FFFD, on 16 targets (JVM, JS, Wasm JS and WASI, Kotlin/Native for Linux, macOS, Windows,
  iOS, watchOS, tvOS).
- A K2 compiler plugin, `utf8-string-compiler-plugin/`, that works only in IR and replaces `"...".u8` on a compile-time
  constant string with precomputed bytes.
- A Gradle plugin, `utf8-string-gradle-plugin/`, that applies it, and `utf8-string-plugin-tests/`, which compiles tests
  with the plugin on every target.
- One maintainer. Nothing is published yet. The library binaries are pre-release because of experimental companion
  blocks.

## What a real failure looks like

- A program that behaves differently with the plugin than without it: other bytes or `codePointCount`, a side effect
  dropped, duplicated or reordered (the initialization of a file's top-level properties too), an exception lost or
  added.
- The compiler crashing, or generated code failing IR validation or a backend, on valid code.
- A constant literal in an ordinary position left unrewritten, or a warning reported wrongly or missing.
- The build, the tests or CI (`./gradlew build` on ubuntu) failing, or a test that stays up to date or passes while the
  code under it is broken.
- A change to `u8Literal` or `u8LiteralLatin1` (signature, file, the JVM class `U8Kt`) that breaks code already
  compiled with the plugin.
- A statement in the README or a comment that is false.

## Blast radius

- A miscompiled literal gives users wrong bytes silently. Everything else stays inside builds of this repository.

## Reporting bar

- Report what changes behavior, breaks a build or misleads a reader. Style, naming and refactoring wishes are noise.
- A missing test is worth reporting only for a risky path: the IR rewrite, the folding, the emitted data.
- Speculation about other Kotlin versions is noise: the plugin works only with Kotlin 2.4.20 and fails the build on any
  other version.

## Where the rules live

- No CLAUDE.md, AGENTS.md or CONTRIBUTING.md. User-facing behavior is in `README.md`. The design decisions are in the
  approved plan, which a round carries in its context as `plan.md` (in Russian).

## Deliberate conventions

- Each literal is created once, in a private object of its file (`U8Literals$<File>Kt`), and the call site reads it.
  A literal inside an inline function is created on each evaluation, because inlined code cannot reach the object.
- The object creates a literal from a Latin-1 string constant on the JVM and above 1 KiB on other targets, and from
  `byteArrayOf` up to 1 KiB on klib targets.
- A receiver that does not fold stays a run-time call with the `U8_NOT_CONSTANT` warning. Float, Double and unsigned
  values in templates are not folded on purpose.
- The warnings come from IR, so they repeat for every target compilation, the IDE editor does not show them, and
  `@Suppress` works only on declarations.
- Callable references such as `String::u8` are not rewritten and get no warning.
- The entry points are `@PublishedApi internal`, because strict klib IR visibility validation rejects plain internal.
- The compiler plugin compiles against the non-relocated `kotlin-compiler`, and a test checks its classes against both
  embeddable compilers instead of shading.
- The Kotlin/JS IR incremental cache is off (a regression of KT-31614). The configuration cache is on, with a 4 GB
  Gradle daemon heap.
- Kotlin sources are pure ASCII: non-ASCII text is written as `\uXXXX` escapes.
- Comments are short and keep only essentials. Commit messages are subject-only.

## Languages

- Kotlin (library, compiler plugin, Gradle plugin, tests), Gradle Kotlin DSL, Markdown, and one JavaScript file,
  `repl.js`, copied from the Kotlin compiler test framework.
