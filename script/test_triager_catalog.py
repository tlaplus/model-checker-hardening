"""Pin signature semantics independently of their declaration helpers."""

import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import triager


class CatalogReplayTest(unittest.TestCase):
    def test_matches_pre_refactor_catalog_including_order_and_regex_flags(self):
        def alternatives(signature):
            return [[[pattern.pattern, pattern.flags] for pattern in alternative.required]
                    for alternative in signature.alternatives]

        actual = {
            "crashes": [[signature.finding_file, signature.crash_kind.name, alternatives(signature)]
                        for signature in triager.SIGNATURES],
            "aggregator": [[signature.issue_file, signature.failed_checker.name,
                            signature.code, alternatives(signature)]
                           for signature in triager.AGGREGATOR_SIGNATURES],
        }
        fixture = Path(__file__).resolve().parent.parent / "src/test/resources/triager-catalog.json"
        self.assertEqual(json.loads(fixture.read_text()), actual)

    def test_unique_match_preserves_ambiguity_wording_and_sorting(self):
        self.assertEqual("NEW", triager.unique_match(set(), "path", "issues"))
        self.assertEqual("a.md", triager.unique_match({"a.md"}, "path", "issues"))
        with self.assertRaisesRegex(triager.TriageError, "path matches multiple findings: a.md, b.md"):
            triager.unique_match({"b.md", "a.md"}, "path", "findings")


if __name__ == "__main__":
    unittest.main()
