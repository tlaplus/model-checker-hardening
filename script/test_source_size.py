import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from source_size import count, production, tla_code


class SourceSizeTest(unittest.TestCase):
    def test_scope_includes_new_helpers_but_not_tests(self):
        for path in ("src/main/java/new/Helper.java", "src/main/resources/new.tla",
                     "script/helper.py", "bin/fuzztla"):
            self.assertTrue(production(path), path)
        for path in ("src/test/java/Test.java", "script/test_new.py", "script/source_size.py",
                     "docs/plan.md", "script/__pycache__/triager.pyc", "pom.xml"):
            self.assertFalse(production(path), path)

    def test_java_comments_do_not_hide_string_contents(self):
        source = '/* first\n second */\nclass Example { // note\n  String x = "/* value */";\n}\n'
        self.assertEqual((5, 3), count("Example.java", source))

    def test_python_docstrings_and_comments_are_excluded(self):
        source = '"""first\nsecond"""\nx = "# value"\n# note\n'
        self.assertEqual((4, 1), count("example.py", source))

    def test_shell_quoted_comment_markers_are_code(self):
        self.assertEqual((2, 1), count("bin/example", "printf '%s' '# value'\n# note\n"))

    def test_tla_nested_comments_preserve_lines_and_strings(self):
        source = '---- MODULE X ----\n(* outer\n (* inner *)\n*)\nx == "(* value *)" \\* note\n====\n'
        self.assertEqual((6, 3), count("X.tla", source))
        self.assertIn('"(* value *)"', tla_code(source))

    def test_physical_lines_use_splitlines_without_requiring_a_final_newline(self):
        self.assertEqual((1, 1), count("x.py", "x = 1"))
        self.assertEqual((1, 1), count("x.py", "x = 1\n"))
        self.assertEqual((2, 1), count("x.py", "x = 1\n\n"))


if __name__ == "__main__":
    unittest.main()
