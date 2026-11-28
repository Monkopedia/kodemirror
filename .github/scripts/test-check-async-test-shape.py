#!/usr/bin/env python3
"""Tests for `check-async-test-shape.py`. Run: `python3 .github/scripts/test-check-async-test-shape.py`.

Each case that must be reported is a negative control: a checker that cannot be shown to fire
is indistinguishable from no checker (#361).
"""

import importlib.util
import tempfile
import unittest
from pathlib import Path

_spec = importlib.util.spec_from_file_location(
    "shape", Path(__file__).with_name("check-async-test-shape.py")
)
shape = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(shape)

HELPER = """
fun runEditorTest(doc: String = "", block: ComposeUiTest.(SessionHolder) -> Unit) =
    SessionHolder().let { holder ->
        runComposeUiTest {
            block(holder)
        }
    }
"""


class Shape(unittest.TestCase):
    def problems(self, *sources, source_set="commonTest", modules=None):
        root = Path(tempfile.mkdtemp())
        files = []
        for i, src in enumerate(sources):
            module = modules[i] if modules else "m"
            path = root / module / "src" / source_set / "kotlin" / f"F{i}.kt"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(src, encoding="utf-8")
            files.append(path)
        return shape.scan(list(shape.test_files(root)))[1]

    def test_expression_body_passes(self):
        self.assertEqual([], self.problems(HELPER, """
            @Test
            fun typing() = runEditorTest(doc = "a") { holder ->
                assertTrue(false)
            }
        """))

    def test_block_body_calling_a_helper_is_reported(self):
        found = self.problems(HELPER, """
            @Test
            fun typing() {
                val x = 1
                runEditorTest(doc = "a") { holder -> }
            }
        """)
        self.assertEqual(1, len(found))
        self.assertIn("`typing` calls `runEditorTest`", found[0])

    def test_block_body_calling_runComposeUiTest_is_reported(self):
        found = self.problems("""
            @Test
            fun direct() {
                runComposeUiTest { }
            }
        """)
        self.assertEqual(1, len(found))

    def test_block_bodied_helper_is_reported(self):
        # A wrapper that swallows the promise makes every expression-bodied caller vacuous.
        found = self.problems("""
            fun runWrapped(block: () -> Unit) {
                runComposeUiTest { block() }
            }
            @Test
            fun t() = runWrapped { }
        """)
        self.assertEqual(1, len(found))
        self.assertIn("`runWrapped`", found[0])

    def test_runTest_is_a_runner(self):
        self.assertEqual(1, len(self.problems("""
            @Test
            fun debounced() {
                runTest { delay(10) }
            }
        """)))

    def test_transitive_helper_is_a_runner(self):
        found = self.problems(HELPER, """
            fun runUnfocused(block: () -> Unit) = runEditorTest { block() }
            @Test
            fun clicks() {
                runUnfocused { }
            }
        """)
        self.assertEqual(1, len(found))
        self.assertIn("`runUnfocused`", found[0])

    def test_returned_call_passes(self):
        self.assertEqual([], self.problems("""
            @Test
            fun explicit(): TestResult {
                return runComposeUiTest { }
            }
        """))

    def test_comments_and_strings_are_ignored(self):
        self.assertEqual([], self.problems("""
            @Test
            fun note() {
                // runComposeUiTest { } would be wrong here
                val s = "runTest { }"
            }
        """))

    def test_jvm_only_sources_are_not_checked(self):
        # A JVM test runs the body synchronously, so a block body there is fine.
        self.assertEqual([], self.problems(HELPER, """
            @Test
            fun jvmOnly() {
                runEditorTest { }
            }
        """, source_set="jvmTest"))

    # An expression body counts only when the runner call is its *value* (#503 review).

    def test_statement_after_runner_in_run_is_reported(self):
        # The reviewer confirmed this exact shape green on wasm with a false assertion.
        found = self.problems(HELPER, """
            class HoverTooltipMountTest {
                @Test
                fun mounts() = run {
                    runEditorTest(doc = "a") { holder -> holder.assertDoc("PLANTED-WRONG") }
                    println("trailing statement")
                }
            }
        """)
        self.assertEqual(1, len(found))
        self.assertIn("`mounts` calls `runEditorTest` but its expression body's value", found[0])

    def test_runner_as_let_receiver_is_reported(self):
        found = self.problems(HELPER, """
            @Test
            fun t() = runEditorTest { }.let { }
        """)
        self.assertEqual(1, len(found))
        self.assertIn("`t`", found[0])

    def test_runner_inside_non_scope_lambda_is_reported(self):
        found = self.problems(HELPER, """
            @Test
            fun t() = listOf(1, 2).forEach { runEditorTest { } }
        """)
        self.assertEqual(1, len(found))

    def test_runner_last_in_run_passes(self):
        # Used by 7 tests (HighlightingTest, TextInputTest, HoverTooltipMountTest).
        self.assertEqual([], self.problems(HELPER, """
            class HighlightingTest {
                @Test
                fun highlights() = run {
                    val lang = javascript()
                    runEditorTest(
                        doc = "let x = 1",
                        extensions = lang
                    ) { holder ->
                        holder.assertDoc("let x = 1")
                    }
                }

                @Test
                fun second() = run { val x = 1; runEditorTest { } }
            }
        """))

    def test_nested_scope_lambdas_pass(self):
        self.assertEqual([], self.problems(HELPER, """
            @Test
            fun t() = Pair(SessionHolder(), SessionHolder()).let { (a, b) ->
                with(a) {
                    run { runComposeUiTest { setContent { } } }
                }
            }
        """))

    def test_runner_receiver_of_apply_passes(self):
        self.assertEqual([], self.problems(HELPER, """
            @Test
            fun t() = runEditorTest { }.also { println(it) }
        """))

    def test_non_runner_value_is_not_promoted_to_runner(self):
        # Only the helper is reported; it is not a runner, so its caller is not misjudged.
        found = self.problems(HELPER, """
            fun runSwallowed() = run {
                runEditorTest { }
                Unit
            }
            @Test
            fun t() = runSwallowed()
        """)
        self.assertEqual(1, len(found))
        self.assertIn("`runSwallowed`", found[0])

    def test_runner_names_are_scoped_to_their_module(self):
        a = """
            class A {
                @Test
                fun build() = runTest { }
            }
        """
        b = """
            class B {
                private fun build(): Int = 1
                @Test
                fun t() { build() }
            }
        """
        self.assertEqual([], self.problems(a, b, modules=["a", "b"]))
        # Within one module the name is ambiguous to a text scan, so it still fails closed.
        self.assertEqual(1, len(self.problems(a, b, modules=["a", "a"])))


if __name__ == "__main__":
    unittest.main()
