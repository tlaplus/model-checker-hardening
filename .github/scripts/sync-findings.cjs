const fs = require("node:fs");
const path = require("node:path");

const FINDING_LABEL = "finding";
const FINDING_LABEL_COLOR = "f9d0c4";
const FINDING_LABEL_DESCRIPTION = "An issue found by the testing framework";
const FINDING_ID_PATTERN = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*-[0-9]{3}$/;
const GITHUB_API_VERSION = "2026-03-10";
const MANAGED_MARKER_PREFIX = "<!-- finding-sync-id: ";
const MANAGED_MARKER_PATTERN = /^<!-- finding-sync-id: ([a-z0-9]+(?:-[a-z0-9]+)*) -->$/;
const GITHUB_ACTIONS_BOT = "github-actions[bot]";

function versionedRequest(parameters) {
  return {
    ...parameters,
    headers: {
      ...parameters.headers,
      "X-GitHub-Api-Version": GITHUB_API_VERSION,
    },
  };
}

class FindingFormatError extends Error {
  constructor(sourcePath, message) {
    super(`${sourcePath}: ${message}`);
    this.name = "FindingFormatError";
  }
}

function parseFrontMatter(sourcePath, lines) {
  if (lines[0] !== "---") {
    throw new FindingFormatError(sourcePath, "expected YAML front matter");
  }

  const closingIndex = lines.indexOf("---", 1);
  if (closingIndex < 0) {
    throw new FindingFormatError(sourcePath, "unterminated YAML front matter");
  }

  const metadata = new Map();
  for (let index = 1; index < closingIndex; index += 1) {
    const line = lines[index];
    if (line.trim() === "") {
      continue;
    }
    const match = /^([a-z]+):\s*(\S+)\s*$/.exec(line);
    if (!match) {
      throw new FindingFormatError(sourcePath, `invalid front-matter line: ${line}`);
    }
    const [, key, value] = match;
    if (key !== "state") {
      throw new FindingFormatError(sourcePath, `unknown front-matter field: ${key}`);
    }
    if (metadata.has(key)) {
      throw new FindingFormatError(sourcePath, `duplicate front-matter field: ${key}`);
    }
    metadata.set(key, value);
  }

  const state = metadata.get("state");
  if (state !== "open" && state !== "closed") {
    throw new FindingFormatError(sourcePath, "state must be open or closed");
  }

  return { state, bodyStart: closingIndex + 1 };
}

function findingId(sourcePath) {
  const extension = path.posix.extname(sourcePath);
  const id = path.posix.basename(sourcePath, extension);
  if (extension !== ".md" || !FINDING_ID_PATTERN.test(id)) {
    throw new FindingFormatError(
      sourcePath,
      "filename must be a globally unique lowercase id ending in a three-digit number",
    );
  }
  return id;
}

function fenceAt(line) {
  const match = /^\s*(`{3,}|~{3,})/.exec(line);
  return match ? { character: match[1][0], length: match[1].length } : null;
}

function advanceFence(activeFence, line) {
  const fence = fenceAt(line);
  if (!fence) {
    return { activeFence, isFence: false };
  }
  if (!activeFence) {
    return { activeFence: fence, isFence: true };
  }
  if (fence.character === activeFence.character && fence.length >= activeFence.length) {
    return { activeFence: null, isFence: true };
  }
  return { activeFence, isFence: true };
}

function findSummary(sourcePath, lines, startIndex) {
  let activeFence = null;
  const summaryStarts = [];
  let summaryEnd = lines.length;

  for (let index = startIndex; index < lines.length; index += 1) {
    const transition = advanceFence(activeFence, lines[index]);
    activeFence = transition.activeFence;
    if (transition.isFence) {
      continue;
    }
    if (activeFence) {
      continue;
    }
    if (lines[index] === "## Summary") {
      summaryStarts.push(index);
    } else if (
      summaryStarts.length === 1 &&
      summaryEnd === lines.length &&
      /^##\s+/.test(lines[index])
    ) {
      summaryEnd = index;
    }
  }

  if (activeFence) {
    throw new FindingFormatError(sourcePath, "unterminated fenced code block");
  }
  if (summaryStarts.length !== 1) {
    throw new FindingFormatError(
      sourcePath,
      `expected exactly one ## Summary section, found ${summaryStarts.length}`,
    );
  }

  const summaryStart = summaryStarts[0];
  if (summaryEnd < summaryStart) {
    summaryEnd = lines.length;
  }
  const summary = lines.slice(summaryStart, summaryEnd).join("\n").trimEnd();
  if (summary === "## Summary") {
    throw new FindingFormatError(sourcePath, "Summary section must not be empty");
  }
  return summary;
}

function parseFinding(sourcePath, text) {
  const normalized = text.replace(/\r\n/g, "\n");
  if (normalized.includes(MANAGED_MARKER_PREFIX)) {
    throw new FindingFormatError(sourcePath, "contains the reserved synchronization marker");
  }
  const lines = normalized.split("\n");
  const { state, bodyStart } = parseFrontMatter(sourcePath, lines);
  const id = findingId(sourcePath);

  let titleIndex = bodyStart;
  while (titleIndex < lines.length && lines[titleIndex].trim() === "") {
    titleIndex += 1;
  }
  const titleMatch = /^#\s+(.+?)\s*$/.exec(lines[titleIndex] || "");
  if (!titleMatch) {
    throw new FindingFormatError(sourcePath, "expected an H1 title after front matter");
  }
  const title = titleMatch[1];

  let activeFence = null;
  for (let index = titleIndex + 1; index < lines.length; index += 1) {
    const transition = advanceFence(activeFence, lines[index]);
    activeFence = transition.activeFence;
    if (transition.isFence || activeFence) {
      continue;
    }
    if (/^#\s+/.test(lines[index])) {
      throw new FindingFormatError(sourcePath, "expected exactly one H1 title");
    }
  }

  return {
    id,
    state,
    title,
    summary: findSummary(sourcePath, lines, titleIndex + 1),
    sourcePath,
  };
}

function findingFiles(rootDirectory) {
  const findingsDirectory = path.join(rootDirectory, "findings");
  const readme = path.join(findingsDirectory, "README.md");
  const files = [];

  function visit(directory) {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const entryPath = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        visit(entryPath);
      } else if (entry.isFile() && entry.name.endsWith(".md") && entryPath !== readme) {
        files.push(entryPath);
      }
    }
  }

  visit(findingsDirectory);
  return files.sort();
}

function loadFindings(rootDirectory = process.cwd()) {
  const findings = findingFiles(rootDirectory).map((file) => {
    const sourcePath = path.relative(rootDirectory, file).split(path.sep).join("/");
    return parseFinding(sourcePath, fs.readFileSync(file, "utf8"));
  });

  const ids = new Map();
  for (const finding of findings) {
    const previous = ids.get(finding.id);
    if (previous) {
      throw new FindingFormatError(
        finding.sourcePath,
        `duplicate id ${finding.id}, also used by ${previous}`,
      );
    }
    ids.set(finding.id, finding.sourcePath);
  }
  return findings;
}

function encodeRepositoryPath(repositoryPath) {
  return repositoryPath.split("/").map(encodeURIComponent).join("/");
}

function repositoryUrl(options, repositoryPath, image = false) {
  const route = image ? "raw" : "blob";
  return `${options.serverUrl}/${options.repository}/${route}/${encodeURIComponent(options.branch)}/${encodeRepositoryPath(repositoryPath)}`;
}

function rewriteTarget(target, sourcePath, options, image) {
  const enclosed = target.startsWith("<") && target.endsWith(">");
  const rawTarget = enclosed ? target.slice(1, -1) : target;
  if (
    rawTarget.startsWith("#") ||
    rawTarget.startsWith("/") ||
    rawTarget.startsWith("//") ||
    /^[a-z][a-z0-9+.-]*:/i.test(rawTarget)
  ) {
    return target;
  }

  const suffixIndex = rawTarget.search(/[?#]/);
  const relativePath = suffixIndex < 0 ? rawTarget : rawTarget.slice(0, suffixIndex);
  const suffix = suffixIndex < 0 ? "" : rawTarget.slice(suffixIndex);
  const resolved = path.posix.normalize(path.posix.join(path.posix.dirname(sourcePath), relativePath));
  if (resolved === ".." || resolved.startsWith("../") || path.posix.isAbsolute(resolved)) {
    throw new FindingFormatError(sourcePath, `relative link escapes the repository: ${rawTarget}`);
  }
  const rewritten = repositoryUrl(options, resolved, image) + suffix;
  return enclosed ? `<${rewritten}>` : rewritten;
}

function rewriteRelativeLinks(markdown, sourcePath, options) {
  let activeFence = null;
  return markdown
    .split("\n")
    .map((line) => {
      const transition = advanceFence(activeFence, line);
      activeFence = transition.activeFence;
      if (transition.isFence) {
        return line;
      }
      if (activeFence) {
        return line;
      }

      let rewritten = line.replace(
        /(!?\[[^\]]*\]\()(<[^>]+>|[^)\s]+)([^)]*\))/g,
        (match, prefix, target, suffix) =>
          `${prefix}${rewriteTarget(target, sourcePath, options, prefix.startsWith("!"))}${suffix}`,
      );
      rewritten = rewritten.replace(
        /^(\s*\[[^\]]+\]:\s*)(<[^>]+>|\S+)(.*)$/,
        (match, prefix, target, suffix) =>
          `${prefix}${rewriteTarget(target, sourcePath, options, false)}${suffix}`,
      );
      return rewritten;
    })
    .join("\n");
}

function managedMarker(id) {
  return `${MANAGED_MARKER_PREFIX}${id} -->`;
}

function renderIssue(finding, options) {
  const sourceUrl = repositoryUrl(options, finding.sourcePath);
  const summary = rewriteRelativeLinks(finding.summary, finding.sourcePath, options);
  return [
    managedMarker(finding.id),
    "> [!WARNING]",
    "> This issue is automatically generated. Do not edit it directly.",
    `> Edit [the source finding](${sourceUrl}) to change the title, summary, or state.`,
    "",
    summary,
    "",
  ].join("\n");
}

function issueLabels(issue) {
  return (issue.labels || []).map((label) => (typeof label === "string" ? label : label.name));
}

function managedIssueId(issue) {
  if (issue.pull_request || typeof issue.body !== "string") {
    return null;
  }
  const firstLine = issue.body.replace(/\r\n/g, "\n").split("\n", 1)[0];
  const marker = MANAGED_MARKER_PATTERN.exec(firstLine);
  if (!marker) {
    return null;
  }
  const privilegedMarker =
    issueLabels(issue).includes(FINDING_LABEL) || issue.user?.login === GITHUB_ACTIONS_BOT;
  return privilegedMarker ? marker[1] : null;
}

function indexManagedIssues(issues) {
  const managed = new Map();
  for (const issue of issues) {
    const id = managedIssueId(issue);
    if (!id) {
      continue;
    }
    if (managed.has(id)) {
      throw new Error(
        `GitHub issues #${managed.get(id).number} and #${issue.number} both claim finding id ${id}`,
      );
    }
    managed.set(id, issue);
  }
  return managed;
}

function planReconciliation(findings, issues, renderOptions) {
  const sourceIds = new Set(findings.map((finding) => finding.id));
  if (sourceIds.size !== findings.length) {
    throw new Error("source findings contain duplicate ids");
  }
  const managed = indexManagedIssues(issues);
  const operations = [];

  for (const finding of findings) {
    const desired = {
      title: finding.title,
      body: renderIssue(finding, renderOptions),
      state: finding.state,
    };
    const issue = managed.get(finding.id);
    if (!issue) {
      operations.push({ kind: "create", id: finding.id, desired });
      continue;
    }

    const patch = {};
    if (issue.title !== desired.title) {
      patch.title = desired.title;
    }
    if (issue.body !== desired.body) {
      patch.body = desired.body;
    }
    if (issue.state !== desired.state) {
      patch.state = desired.state;
    }
    const addLabel = !issueLabels(issue).includes(FINDING_LABEL);
    operations.push({
      kind: Object.keys(patch).length > 0 || addLabel ? "update" : "unchanged",
      id: finding.id,
      issueNumber: issue.number,
      patch,
      addLabel,
      previousState: issue.state,
    });
  }

  for (const [id, issue] of managed) {
    if (sourceIds.has(id)) {
      continue;
    }
    const patch = issue.state === "open" ? { state: "closed" } : {};
    const addLabel = !issueLabels(issue).includes(FINDING_LABEL);
    operations.push({
      kind: Object.keys(patch).length > 0 || addLabel ? "update" : "unchanged",
      id,
      issueNumber: issue.number,
      patch,
      addLabel,
      previousState: issue.state,
      removed: true,
    });
  }

  return operations;
}

async function ensureFindingLabel(github, owner, repo) {
  try {
    await github.rest.issues.getLabel(
      versionedRequest({ owner, repo, name: FINDING_LABEL }),
    );
  } catch (error) {
    if (error.status !== 404) {
      throw error;
    }
    await github.rest.issues.createLabel(
      versionedRequest({
        owner,
        repo,
        name: FINDING_LABEL,
        color: FINDING_LABEL_COLOR,
        description: FINDING_LABEL_DESCRIPTION,
      }),
    );
  }
}

async function executePlan(github, owner, repo, operations, core) {
  const counts = { created: 0, updated: 0, reopened: 0, closed: 0, unchanged: 0 };

  for (const operation of operations) {
    if (operation.kind === "unchanged") {
      counts.unchanged += 1;
      core.info(`${operation.id}: unchanged`);
      continue;
    }

    if (operation.kind === "create") {
      const response = await github.rest.issues.create(
        versionedRequest({
          owner,
          repo,
          title: operation.desired.title,
          body: operation.desired.body,
          labels: [FINDING_LABEL],
        }),
      );
      counts.created += 1;
      core.info(`${operation.id}: created #${response.data.number}`);
      if (operation.desired.state === "closed") {
        await github.rest.issues.update(
          versionedRequest({
            owner,
            repo,
            issue_number: response.data.number,
            state: "closed",
          }),
        );
        counts.closed += 1;
      }
      continue;
    }

    const patchEntries = Object.keys(operation.patch);
    if (patchEntries.length > 0) {
      await github.rest.issues.update(
        versionedRequest({
          owner,
          repo,
          issue_number: operation.issueNumber,
          ...operation.patch,
        }),
      );
      if (operation.patch.state === "open" && operation.previousState === "closed") {
        counts.reopened += 1;
      } else if (operation.patch.state === "closed" && operation.previousState === "open") {
        counts.closed += 1;
      }
      if (patchEntries.some((key) => key !== "state")) {
        counts.updated += 1;
      }
    }
    if (operation.addLabel) {
      await github.rest.issues.addLabels(
        versionedRequest({
          owner,
          repo,
          issue_number: operation.issueNumber,
          labels: [FINDING_LABEL],
        }),
      );
      counts.updated += 1;
    }
    core.info(`${operation.id}: synchronized #${operation.issueNumber}`);
  }

  return counts;
}

async function synchronize({ github, context, core }) {
  const findings = loadFindings();
  const { owner, repo } = context.repo;
  const issues = await github.paginate(
    github.rest.issues.listForRepo,
    versionedRequest({
      owner,
      repo,
      state: "all",
      per_page: 100,
    }),
  );
  const renderOptions = {
    serverUrl: process.env.GITHUB_SERVER_URL || "https://github.com",
    repository: `${owner}/${repo}`,
    branch: context.payload.repository?.default_branch || "main",
  };
  const operations = planReconciliation(findings, issues, renderOptions);

  await ensureFindingLabel(github, owner, repo);
  const counts = await executePlan(github, owner, repo, operations, core);
  await core.summary
    .addHeading("Finding synchronization")
    .addTable([
      [
        { data: "Created", header: true },
        { data: "Updated", header: true },
        { data: "Reopened", header: true },
        { data: "Closed", header: true },
        { data: "Unchanged", header: true },
      ],
      [
        String(counts.created),
        String(counts.updated),
        String(counts.reopened),
        String(counts.closed),
        String(counts.unchanged),
      ],
    ])
    .write();
  return counts;
}

if (require.main === module) {
  try {
    const findings = loadFindings();
    process.stdout.write(`Validated ${findings.length} finding files.\n`);
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exitCode = 1;
  }
}

module.exports = synchronize;
module.exports.FindingFormatError = FindingFormatError;
module.exports.executePlan = executePlan;
module.exports.indexManagedIssues = indexManagedIssues;
module.exports.loadFindings = loadFindings;
module.exports.managedIssueId = managedIssueId;
module.exports.managedMarker = managedMarker;
module.exports.parseFinding = parseFinding;
module.exports.planReconciliation = planReconciliation;
module.exports.renderIssue = renderIssue;
module.exports.rewriteRelativeLinks = rewriteRelativeLinks;
module.exports.versionedRequest = versionedRequest;
