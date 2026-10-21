"""MkDocs hook: links to repository files outside the manual point to GitHub.

The manual's pages link to ADRs, findings and scripts by relative path, which
works on GitHub but not on the published site. This hook rewrites each such
link to the file on the main branch of the repository.
"""

import posixpath
import re
from pathlib import Path

BLOB = "https://github.com/tlaplus/model-checker-hardening/blob/main/"
TREE = "https://github.com/tlaplus/model-checker-hardening/tree/main/"
REPO = Path(__file__).resolve().parent.parent

# A relative link target, inline as ](target#anchor) or in a reference
# definition as [label]: target#anchor at the start of a line.
LINK = re.compile(
    r"(\]\(|^\[[^\]]+\]:[ \t]+)(?!https?://|mailto:|#)([^)\s#]+)(#[^)\s]*)?",
    re.MULTILINE,
)


def on_page_markdown(markdown, page, config, files):
    docs = Path(config["docs_dir"]).resolve().relative_to(REPO).as_posix()
    page_dir = posixpath.dirname(posixpath.join(docs, page.file.src_uri))

    def rewrite(match):
        lead, target, anchor = match.group(1), match.group(2), match.group(3) or ""
        path = posixpath.normpath(posixpath.join(page_dir, target))
        if path == docs or path.startswith(docs + "/"):
            return match.group(0)
        base = TREE if (REPO / path).is_dir() else BLOB
        return f"{lead}{base}{path}{anchor}"

    return LINK.sub(rewrite, markdown)
