"""Measure production source, including new helpers, independently of test size."""

import argparse
import subprocess
from pathlib import Path

from pygments import lex
from pygments.lexers import BashLexer, JavaLexer, PythonLexer
from pygments.token import Comment, String


def production(path):
    # This counter remains verification tooling even when stored alongside application scripts.
    if path == "script/source_size.py":
        return False
    return (path.startswith("src/main/") or path.startswith("bin/")
            or (path.startswith("script/") and path.endswith(".py")
                and not Path(path).name.startswith("test_")))


def tla_code(text):
    result, index, depth, string = [], 0, 0, False
    while index < len(text):
        if depth:
            if text.startswith("(*", index):
                depth += 1
                index += 2
            elif text.startswith("*)", index):
                depth -= 1
                index += 2
            else:
                if text[index] == "\n":
                    result.append("\n")
                index += 1
        elif string:
            result.append(text[index])
            if text[index] == "\\" and index + 1 < len(text):
                result.append(text[index + 1])
                index += 2
            else:
                string = text[index] != '"'
                index += 1
        elif text.startswith("(*", index):
            depth = 1
            index += 2
        elif text.startswith("\\*", index):
            end = text.find("\n", index)
            index = len(text) if end < 0 else end
        else:
            result.append(text[index])
            string = text[index] == '"'
            index += 1
    return "".join(result)


def count(path, text):
    if path.endswith(".tla"):
        code = tla_code(text)
    else:
        lexer = (JavaLexer() if path.endswith(".java") else
                 PythonLexer() if path.endswith(".py") else BashLexer())
        code = "".join(value if token not in Comment and token not in String.Doc
                       else "\n" * value.count("\n") for token, value in lex(text, lexer))
    return len(text.splitlines()), sum(bool(line.strip()) for line in code.splitlines())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", help="Git revision; otherwise include the working tree and new files")
    options = parser.parse_args()
    if options.revision:
        names = subprocess.check_output(
            ["git", "ls-tree", "-r", "--name-only", options.revision], text=True).splitlines()
    else:
        names = [str(path) for root in ("src/main", "script", "bin")
                 for path in Path(root).rglob("*") if path.is_file()]
    totals = [0, 0]
    for name in sorted(filter(production, names)):
        text = (subprocess.check_output(["git", "show", f"{options.revision}:{name}"], text=True)
                if options.revision else Path(name).read_text())
        physical, code = count(name, text)
        totals[0] += physical
        totals[1] += code
    print(f"physical={totals[0]} noncomment={totals[1]}")


if __name__ == "__main__":
    main()
