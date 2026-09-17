-- Per-decade quality of one experiment arm. Run with:
--   sqlite3 -header -column ARM/corpus.sqlite < analyze.sql
-- Expects ARM/03aggregator-fail-triage.csv imported as table `triage` (analyze.sh does it).

CREATE TEMP TABLE t AS
SELECT e.id, e.hash, e.generation AS g, e.parent, e.directory AS d,
       s.projectedDepth AS pd, s.projectedStates AS ps, s.actionsDiscovering AS ad,
       s.maxStateNodes AS sn, s.maxCardinality AS mc, s.maxNesting AS mn,
       coalesce(s.durationMillis, 0) + coalesce(a.durationMillis, 0) AS checkMillis
FROM entry e
LEFT JOIN stage s ON s.entryId = e.id AND s.stage = 'tlc'
LEFT JOIN stage a ON a.entryId = e.id AND a.stage = 'apalache'
WHERE e.directory NOT LIKE '02apa-%';
CREATE INDEX t_hash ON t(hash);

CREATE TEMP TABLE root AS
WITH RECURSIVE r(id, cur, p) AS (
  SELECT id, hash, parent FROM t
  UNION ALL
  SELECT r.id, t.hash, t.parent FROM r JOIN t ON t.hash = r.p)
SELECT id, cur AS root FROM r WHERE p IS NULL;

.print '== Elite diversity per decade'
SELECT (t.g / 10) * 10 AS decade,
       count(*) AS selected,
       count(DISTINCT root.root) AS roots,
       sum(t.parent IS NULL) AS pbtSelected,
       count(DISTINCT t.pd || ',' || t.ps || ',' || t.ad || ',' || t.sn || ',' || t.mc || ',' || t.mn) AS metricVectors,
       sum(t.ad >= 2) AS twoDiscoveringActions,
       sum(t.ps > t.pd + 1) AS branching,
       max(t.ps) AS maxProjectedStates
FROM t JOIN root USING (id)
WHERE t.d = '04quality-pass'
GROUP BY decade ORDER BY decade;

.print '== All agreeing entries per decade (tierA: projectedDepth = 5, tierB: projectedStates >= 2, beyondLinear: two discovering actions or more projected states than a single chain)'
SELECT (g / 10) * 10 AS decade,
       count(*) AS entries,
       sum(d LIKE '04quality-%') AS agreeing,
       sum(d LIKE '04quality-%' AND pd = 5) AS tierA,
       sum(d LIKE '04quality-%' AND ps >= 2) AS tierB,
       sum(d LIKE '04quality-%' AND (ad >= 2 OR ps > pd + 1)) AS beyondLinear,
       round(sum(checkMillis) / 3600000.0, 1) AS checkerHours
FROM t GROUP BY decade ORDER BY decade;

.print '== Operators: selection and behavioural clones (child metric vector = parent)'
SELECT m.operator,
       count(*) AS mutants,
       round(100.0 * sum(c.d LIKE '04quality-%') / count(*), 1) AS agreePct,
       round(100.0 * sum(c.d = '04quality-pass') / count(*), 1) AS selectedPct,
       round(100.0 * sum(c.d LIKE '04quality-%' AND p.pd = c.pd AND p.ps = c.ps AND p.ad = c.ad
                         AND p.sn = c.sn AND p.mc = c.mc AND p.mn = c.mn)
             / nullif(sum(c.d LIKE '04quality-%'), 0), 1) AS cloneVecPct,
       round(100.0 * sum(c.d = '03aggregator-fail') / count(*), 1) AS disagreePct
FROM mutationOperator m
JOIN t c ON c.id = m.entryId
JOIN t p ON p.hash = c.parent AND p.d = '04quality-pass'
GROUP BY m.operator ORDER BY selectedPct DESC;

.print '== Disagreements by origin; NEW = not matched by a known issue'
SELECT CASE WHEN t.parent IS NULL THEN 'pbt' ELSE 'mutant' END AS origin,
       count(*) AS disagreements,
       sum(x.issue = 'NEW') AS new,
       count(DISTINCT x.issue) AS issues
FROM t LEFT JOIN triage x ON x.entry_hash = t.hash
WHERE t.d = '03aggregator-fail'
GROUP BY origin;

.print '== NEW disagreements per checker-hour'
SELECT round(sum(x.issue = 'NEW') / (SELECT sum(checkMillis) / 3600000.0 FROM t), 2) AS newPerCheckerHour
FROM t JOIN triage x ON x.entry_hash = t.hash
WHERE t.d = '03aggregator-fail';
