# Language Module Guide

How to create a `:lang-*` module for a new programming language.

## Module Structure

Each language module follows a standard layout:

```
lang-mylang/
  build.gradle.kts
  api/lang-mylang.api          (auto-generated)
  src/commonMain/kotlin/
    com/monkopedia/kodemirror/lang/mylang/
      MyLang.kt                 (main entry point)
      MyLangComplete.kt         (completion support, optional)
```

## build.gradle.kts

```kotlin
plugins {
    id("kodemirror.library")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":state"))
            implementation(project(":view"))
            implementation(project(":language"))
            implementation(project(":lezer-common"))
            implementation(project(":lezer-lr"))      // if using LR parser
            implementation(project(":lezer-highlight"))
            implementation(project(":autocomplete"))   // if providing completions
            implementation(compose.ui)
            implementation(compose.runtime)
        }
    }
}
```

## Required Exports

Every language module should export:

### 1. Language Definition

```kotlin
/** The MyLang [Language] definition. */
val myLangLanguage: Language = LRLanguage.define(
    parser = myLangParser,      // Your Lezer parser
    languageData = mapOf(
        "commentTokens" to mapOf("line" to "//", "block" to mapOf("open" to "/*", "close" to "*/")),
        "closeBrackets" to mapOf("brackets" to listOf("(", "[", "{", "\"", "'"))
    )
)
```

### 2. Main Entry Point

```kotlin
/**
 * Returns a [LanguageSupport] bundle for MyLang, including the parser,
 * highlight style, and optional completion source.
 */
fun myLang(config: MyLangConfig = MyLangConfig()): LanguageSupport {
    return LanguageSupport(
        language = myLangLanguage,
        support = extensionListOf(
            myLangLanguage.data.of(myLangCompletionSource),
            syntaxHighlighting(myLangHighlighting)
        )
    )
}
```

### 3. Highlight Style

```kotlin
val myLangHighlighting = HighlightStyle.define(listOf(
    TagStyleSpec(Tags.keyword, SpanStyle(color = Color(0xFF770088))),
    TagStyleSpec(Tags.string, SpanStyle(color = Color(0xFFAA1111))),
    // ...
))
```

## Optional Exports

### Completion Source

```kotlin
val myLangCompletionSource: CompletionSource = { ctx ->
    val word = ctx.matchBefore(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))
    if (word != null || ctx.explicit) {
        CompletionResult(
            from = word?.from ?: ctx.pos,
            options = MY_KEYWORDS.map { Completion(label = it, type = "keyword") }
        )
    } else null
}
```

### Auto-Close Tags (for markup languages)

See `:lang-html` for the `autoCloseTags` pattern.

### Code Folding

Register fold-related language data if your language supports
block structures.

## Registration

1. Add `include(":lang-mylang")` to `settings.gradle.kts`
2. Add `api(project(":lang-mylang"))` to `kodemirror-bom/build.gradle.kts`
3. Run `./gradlew apiDump` to generate the API file

## Testing

Create parser tests in `src/commonTest/kotlin/`:

```kotlin
@Test
fun testKeywordHighlighting() {
    val state = EditorState.create(EditorStateConfig(
        doc = DocSpec.StringDoc("if (true) { }"),
        extensions = myLang().extension
    ))
    // Verify parsing succeeded
    assertNotNull(state)
}
```

## Checklist

- [ ] `myLangLanguage` val exported
- [ ] `myLang()` function exported
- [ ] Language data includes `commentTokens` and `closeBrackets`
- [ ] Highlight style defined and applied
- [ ] Module registered in `settings.gradle.kts`
- [ ] Module added to `kodemirror-bom`
- [ ] API dump generated
- [ ] Basic tests pass
