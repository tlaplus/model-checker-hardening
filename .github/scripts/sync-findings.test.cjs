const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const test = require("node:test");

const syncFindings = require("./sync-findings.cjs");

const RENDER_OPTIONS = {
  serverUrl: "https://github.example",
  repository: "owner/repo",
  branch: "main",
};

function findingText({ state = "open", labels = ["apalache"], title = "A defect" } = {}) {
  return `---
state: ${state}
labels: [${labels.join(", ")}]
---

# ${title}

## Summary

The [reproducer](../../corpus/input.cbor) fails.

\`\`\`text
## This is not a section
# This is not another title
[literal](../../not-a-link)
\`\`\`

## Reproduction

Details that must not enter the issue.
`;
}

function remoteIssue(finding, overrides = {}) {
  return {
    number: 7,
    title: finding.title,
    body: syncFindings.renderIssue(finding, RENDER_OPTIONS),
    state: finding.state,
    labels: [{ name: "finding" }, ...finding.labels.map((name) => ({ name })), { name: "triage" }],
    user: { login: "github-actions[bot]" },
    ...overrides,
  };
}

test("derives the id from the filename and parses title, state, and Summary", () => {
  const finding = syncFindings.parseFinding("findings/tool/tool-001.md", findingText());

  assert.equal(finding.id, "tool-001");
  assert.equal(finding.state, "open");
  assert.deepEqual(finding.labels, ["apalache"]);
  assert.equal(finding.title, "A defect");
  assert.match(finding.summary, /## This is not a section/);
  assert.doesNotMatch(finding.summary, /Details that must not enter/);
});

test("rejects malformed metadata and source markers", () => {
  assert.throws(
    () => syncFindings.parseFinding("findings/tool/issue.md", findingText()),
    /filename must be a globally unique lowercase id/,
  );
  assert.throws(
    () => syncFindings.parseFinding("bad.md", findingText({ state: "resolved" })),
    /state must be open or closed/,
  );
  assert.throws(
    () => syncFindings.parseFinding("bad.md", findingText({ labels: ["other"] })),
    /unknown label other/,
  );
  assert.throws(
    () =>
      syncFindings.parseFinding(
        "bad.md",
        findingText().replace("## Summary", "<!-- finding-sync-id: forged -->\n## Summary"),
      ),
    /reserved synchronization marker/,
  );
});

test("loads the repository catalog with unique ids", () => {
  const findings = syncFindings.loadFindings(path.resolve(__dirname, "../.."));
  assert.ok(findings.length > 0);
  assert.equal(new Set(findings.map((finding) => finding.id)).size, findings.length);
});

test("rejects duplicate ids while loading a catalog", () => {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "finding-sync-"));
  try {
    fs.mkdirSync(path.join(root, "findings", "one"), { recursive: true });
    fs.mkdirSync(path.join(root, "findings", "two"), { recursive: true });
    fs.writeFileSync(path.join(root, "findings", "one", "tool-001.md"), findingText());
    fs.writeFileSync(path.join(root, "findings", "two", "tool-001.md"), findingText());
    assert.throws(() => syncFindings.loadFindings(root), /duplicate id tool-001/);
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
});

test("renders a source notice, marker, summary, and repository-relative links", () => {
  const finding = syncFindings.parseFinding("findings/tool/tool-001.md", findingText());
  const body = syncFindings.renderIssue(finding, RENDER_OPTIONS);

  assert.ok(body.startsWith("<!-- finding-sync-id: tool-001 -->\n"));
  assert.match(body, /> \[!WARNING\]/);
  assert.match(body, /automatically generated\. Do not edit it directly\./);
  assert.match(body, /owner\/repo\/blob\/main\/findings\/tool\/tool-001\.md/);
  assert.match(body, /owner\/repo\/blob\/main\/corpus\/input\.cbor/);
  assert.match(body, /\[literal\]\(\.\.\/\.\.\/not-a-link\)/);
  assert.doesNotMatch(body, /Details that must not enter/);
});

test("plans an idempotent create followed by no changes", () => {
  const finding = syncFindings.parseFinding("findings/tool/tool-001.md", findingText());
  const firstPlan = syncFindings.planReconciliation([finding], [], RENDER_OPTIONS);
  assert.deepEqual(firstPlan.map((operation) => operation.kind), ["create"]);

  const secondPlan = syncFindings.planReconciliation(
    [finding],
    [remoteIssue(finding)],
    RENDER_OPTIONS,
  );
  assert.deepEqual(secondPlan.map((operation) => operation.kind), ["unchanged"]);
});

test("plans content, state, and managed-label repairs without removing other labels", () => {
  const finding = syncFindings.parseFinding(
    "findings/tool/tool-001.md",
    findingText({ state: "open", title: "Current title" }),
  );
  const issue = remoteIssue(finding, {
    title: "Old title",
    body: "<!-- finding-sync-id: tool-001 -->\nold body",
    state: "closed",
    labels: [{ name: "triage" }, { name: "tlc" }],
  });

  const [operation] = syncFindings.planReconciliation([finding], [issue], RENDER_OPTIONS);
  assert.equal(operation.kind, "update");
  assert.equal(operation.patch.title, "Current title");
  assert.equal(operation.patch.state, "open");
  assert.deepEqual(operation.labels, ["triage", "finding", "apalache"]);
  assert.deepEqual(issue.labels, [{ name: "triage" }, { name: "tlc" }]);
});

test("closes a managed issue when its source id disappears", () => {
  const issue = {
    number: 9,
    title: "Removed finding",
    body: "<!-- finding-sync-id: removed-001 -->\nbody",
    state: "open",
    labels: [{ name: "finding" }],
    user: { login: "someone" },
  };
  const [operation] = syncFindings.planReconciliation([], [issue], RENDER_OPTIONS);
  assert.deepEqual(operation.patch, { state: "closed" });
  assert.equal(operation.removed, true);
});

test("ignores unprivileged markers and rejects duplicate managed markers", () => {
  const unprivileged = {
    number: 1,
    body: "<!-- finding-sync-id: tool-001 -->\nbody",
    labels: [],
    user: { login: "external-user" },
  };
  assert.equal(syncFindings.managedIssueId(unprivileged), null);

  const first = { ...unprivileged, number: 2, labels: [{ name: "finding" }] };
  const second = { ...first, number: 3 };
  assert.throws(() => syncFindings.indexManagedIssues([first, second]), /both claim finding id/);
});

test("executes planned mutations through Octokit", async () => {
  const calls = [];
  const github = {
    rest: {
      issues: {
        create: async (request) => {
          calls.push(["create", request]);
          return { data: { number: 11 } };
        },
        update: async (request) => {
          calls.push(["update", request]);
        },
        setLabels: async (request) => {
          calls.push(["setLabels", request]);
        },
      },
    },
  };
  const core = { info: () => {} };
  const operations = [
    {
      kind: "create",
      id: "new-001",
      desired: { title: "New", body: "Body", state: "closed", labels: ["finding", "tlc"] },
    },
    {
      kind: "update",
      id: "old-001",
      issueNumber: 5,
      patch: { state: "open" },
      labels: ["triage", "finding", "sany"],
      previousState: "closed",
    },
  ];

  const counts = await syncFindings.executePlan(github, "owner", "repo", operations, core);
  assert.deepEqual(
    calls.map(([method]) => method),
    ["create", "update", "update", "setLabels"],
  );
  assert.ok(
    calls.every(
      ([, request]) => request.headers["X-GitHub-Api-Version"] === "2026-03-10",
    ),
  );
  assert.deepEqual(counts, { created: 1, updated: 1, reopened: 1, closed: 1, unchanged: 0 });
});
