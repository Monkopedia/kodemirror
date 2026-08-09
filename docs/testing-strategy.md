# Testing strategy

How the two behavioural test suites relate, what each one is allowed to prove, and the rules that
keep them from drifting apart.

Tracked in #196. The parity convention below is the deliverable of #201.

## Two suites, two different questions

`gap-analysis/` holds 188 behavioural Playwright tests, plus two capture specs that are tooling
rather than assertions (`cm6-reference-capture`, whose single `test()` expands over 13 fixture files,
and `keymap-expectations-capture`). The 188 are **differential**: each drives the same input
into real CodeMirror 6 and into KodeMirror in the same browser, then compares the results.

```ts
test("ArrowLeft moves cursor left", async ({ cm6, km }) => {
```

That CM6 oracle cannot exist inside a Compose test — there is no CodeMirror to compare against. So
the Playwright suite is **not being migrated**. Both suites are kept, because they answer different
questions:

| suite | question | platforms | expectations |
|---|---|---|---|
| **Playwright** (`gap-analysis/`) | *do we still match CodeMirror 6?* | wasmJs, in a browser | relative to CM6 |
| **commonTest twin** | *does this behaviour hold on iOS / Android / wasm / native?* | jvm, android, wasmJs, macosArm64, iosArm64, iosSimulatorArm64 | absolute literals |

The differential run is what **discovers** the correct answer. Once known, it is frozen as a literal
expectation in a multiplatform test. Neither suite subsumes the other: drop the Playwright side and
upstream drift goes unnoticed; drop the twin and every assertion is a claim about one platform.

## The parity annotation

Every `commonTest` twin names its Playwright counterpart — spec file and test name, verbatim:

```kotlin
// parity: gap-analysis/tests/keymap-commands.spec.ts "ArrowLeft moves cursor left"
@Test
fun arrowLeftMovesCursorLeft() { ... }
```

And the Playwright side points back:

```ts
// parity: view/src/commonTest/.../KeymapParityTest.kt arrowLeftMovesCursorLeft
test("ArrowLeft moves cursor left", async ({ cm6, km }) => {
```

Both directions, so the link is greppable from either side. **The quoted test name must match the
counterpart exactly** — that is what makes `grep` a reliable audit rather than a guess.

## The obligation

This is the part that must not be softened. A convention nothing enforces decays, so the annotation
is paired with an explicit duty on both the author and the reviewer.

**Any change to a test carrying a `parity:` comment REQUIRES opening its counterpart in the same PR
and stating in the PR body whether the counterpart needed to change.**

That applies to changing an assertion, changing an expected value, renaming, or deleting. It applies
whichever side you touch first. Three outcomes are acceptable, and each must be stated explicitly:

1. **Both updated** — the behaviour genuinely changed. Say so and show both.
2. **Counterpart deliberately unchanged** — say *why* it still holds. "Playwright asserts CM6 parity,
   the twin asserts absolute behaviour on six targets, and only the former moved" is a real answer.
   **"I didn't look" is not.**
3. **Parity intentionally broken** — the two suites are now meant to diverge. Remove the `parity:`
   comments from *both* sides in that same PR and say why. A stale `parity:` comment is worse than
   none, because it asserts a link that no longer holds.

**Reviewer obligation.** For any PR touching a file that contains `parity:` comments, the reviewer
must independently `grep` for the named counterparts and confirm the PR body addresses them. A PR
that changes a parity-annotated test without mentioning its counterpart is `request_changes` — not a
nit. **The reviewer must not accept the author's assertion that the counterpart is unaffected
without checking it**; that is the same verify-don't-relay standard applied everywhere else in this
pipeline.

**Deleting a test.** Deleting one side without deleting or re-annotating the other is
`request_changes`. Orphaned `parity:` comments are the specific decay mode this rule exists to
prevent.

### Why this is a process rule and not a build check

A checked-in name table plus a failing test was considered and rejected *for now*: it is real
infrastructure to build and maintain, and it needs an explicit opt-out list for the browser-bound
specs that will never have twins. **If the process rule visibly fails in practice** — orphaned
comments accumulate, or a drift slips through review — **escalate to the automated check and
reference this section.** Record that honestly rather than quietly tightening it.

## Which harness a twin belongs in

The portable Playwright tests split by what they actually depend on. This split is a deliverable of
#201 and determines where each twin lives.

**Plain `commonTest` — no Compose.** The behaviour is a pure function of `EditorState`: a command
runs, the document and selection change. These need no viewport, no layout and no input injection,
so they are ordinary multiplatform unit tests in the owning module (`:commands`, `:search`, `:vim`).
This is the cheaper harness and is preferred whenever it is sufficient.

**`runComposeUiTest` via `runEditorTest`** (`view/src/commonTest/.../input/InputTestHelper.kt`). The
behaviour depends on layout or on real input dispatch — anything involving a viewport, visual lines,
pointer coordinates, or the key-event path through the view. `runEditorTest` pins the frame to a
fixed `requiredSize(width.dp, height.dp)` with `LocalDensity` at `Density(1f, 1f)`, so dp == px and
raw pixel offsets mean the same thing on every target; see #200 for why that pin is load-bearing.

| Playwright spec | tests | harness | note |
|---|---|---|---|
| `keymap-commands` | 60 | mixed | see the breakdown below |
| `navigation` | 11 | mostly `commonTest` | `column memory across lines` needs layout |
| `selection` | 9 | `commonTest` | pure selection arithmetic |
| `editing` | 6 | `commonTest` | doc mutations + history |
| `typing` | 6 | `runEditorTest` | drives the text-input path |
| `clipboard` | 5 | `commonTest` | platform-split already exists — see `ClipboardCommandsTest` |
| `features` | 4 | mixed | search panel needs the view; doc/cursor assertions do not |
| vim (`vim-*`) | 69 | **none — do not write twins** | see below |

### `keymap-commands` (the proof batch)

The 60 tests are the largest single portable spec and are the proof that the pattern works. They
divide as:

- **Layout-dependent — `runEditorTest`.** `PageUp` / `PageDown` (viewport-sized), and the vertical
  motions whose result depends on visual rather than logical lines. The Playwright versions of
  PageUp/PageDown already assert only a *direction* rather than a position, precisely because the
  page size differs between CM6's DOM viewport and KodeMirror's Compose canvas; a twin should assert
  the same direction property, not a frozen offset.
- **Everything else — plain `commonTest`.** Arrow keys, `Home`/`End`, `Ctrl-Home`/`Ctrl-End`, word
  motion and word delete, `Enter`, `Backspace`/`Delete`, line move/copy/delete, indent/dedent,
  bracket matching, transpose, the ten Shift-selection variants, select-all, and undo/redo are all
  state transitions. They are exercised by resolving the binding through the keymap and running the
  resulting command against an `EditorState` — the same probe-the-wiring pattern `ClipboardCommandsTest`
  uses, which asserts against the binding the platform actually has rather than a hardcoded modifier.

**Derive the modifier, never hardcode it.** `standardKeymap` declares bindings such as
`KeyBinding(key = "Ctrl-x", mac = "Meta-x")`, and `platformOsName()` reports `"Mac"` on *every*
Kotlin/Native target (#217). A twin that hardcodes `Ctrl` passes on the JVM and fails on macOS and
iOS for a reason that is not a bug. Read the modifier from the same `currentOs` the product reads,
and assert both branches: the unbound modifier must leave the document untouched, and the bound one
must act.

### Do not write vim twins — measured, not assumed

#201 asked for the overlap between the vim Playwright specs and the 626 vim tests moved to
`commonTest` in #198 to be measured before any vim twin was written. It was, and the answer is that
**the twins would be redundant.**

The four vim specs hold **69** tests, not the 83 the issue estimated (`vim-extended` 31,
`vim-functional` 19, `vim-keyboard` 14, `vim-visual` 5). Matching each test's key sequence against
the vim `commonTest` sources — which spell sequences as individual keys, `h.doKeys("d", "d")`, not as
`"dd"` — gives **53 of 69 already covered**. Of the remaining 16:

- **six** are mode or rendering assertions (block vs thin cursor, selection highlight), which are
  visual and belong in Playwright permanently;
- **six** are covered through a different API and were missed by key-sequence matching — the ex
  commands (`:5`, `:set number`, `:s/old/new/`) run via `h.doEx("s/foo/baz/")` in `VimExCommandTest`
  (39 tests) and `VimSubstituteTest` (51 tests), and `Escape`, `Ctrl-r` and `/` are present as
  `"<Esc>"`, `"<C-r>"` and `"/"`;
- **one**, `ci(`, looks like a genuine hole: `di(` is covered in `VimTextObjectTest` and `ci"` in
  `VimMiscTest`, but change-inside-paren is not. That is one test to add to the existing vim suite,
  not a reason to build a parallel one.

So vim's multiplatform coverage is already there. Writing twins for it would add maintenance for no
coverage, which is exactly what the measurement was for.

## What stays Playwright-only, by design

Eighteen tests, all rendering-, comparison- or browser-bound, none expressible as an absolute
expectation on another platform:

| spec | tests |
|---|---|
| `vim-prompt-compare` | 5 |
| `performance` | 3 |
| `search-panel-compare` | 3 |
| `visual` | 3 |
| `tab-render-compare` | 2 |
| `completion-popup-paint` | 1 |
| `vim-cursor-compare` | 1 |

**These never carry `parity:` comments**, and their absence from the twin suite is intentional rather
than an omission. `188 = 170 portable + 18` exactly, which is the arithmetic that keeps this
document honest — if those two numbers stop summing, one of them is stale.

The two capture specs (`cm6-reference-capture`, `keymap-expectations-capture`) are also Playwright-only
but are excluded from both counts: they generate reference data rather than assert behaviour.

One caveat that limits what the twin suite can be asked to prove: the headless CanvasKit runner
renders a fallback font rather than the bundled Compose-Resources one (#210), so pixel and screenshot
assertions stay off the wasm test runner regardless of which harness they would otherwise fit.
