package io.github.tlaplus.hardening.cli;

import static io.github.tlaplus.hardening.cli.RunMetric.Field.*;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.jline.terminal.Size;
import org.jline.utils.AttributedString;

/** Pure, size-bounded flow and table layouts for the same statistics and styles. */
final class ProgressLayout {
    private static final Size FLOW_MINIMUM = new Size(80, 24);
    private static final Size TABLE_MINIMUM = new Size(60, 16);

    /** Flow geometry: every arrow, bar, and card column derives from these. */
    private static final int CARD_WIDTH = 39;
    private static final int CARD_SECOND_RESULT = 16;
    private static final int BRANCH_COLUMN = 3;
    private static final int JOIN_COLUMN = 18;
    private static final int FEEDBACK_COLUMN = 13;
    private static final int INPUT_DETAIL_COLUMN = 9;

    /** Table geometry. */
    private static final int TABLE_QUEUE_COLUMN = 15;
    private static final int TABLE_FIRST_RESULT_COLUMN = 23;
    private static final int TABLE_RESULT_WIDTH = 8;

    private ProgressLayout() {}

    static List<AttributedString> render(RunText text, Size size, boolean feedback) {
        List<AttributedString> lines;
        if (fits(size, FLOW_MINIMUM) && size.getColumns() > branchCount() * CARD_WIDTH) {
            lines = flow(text, feedback);
        } else if (fits(size, TABLE_MINIMUM)) {
            lines = table(text, feedback);
        } else {
            lines = List.of(header(text).field("  ", ENTRIES).build());
        }
        var width = Math.max(0, size.getColumns() - 1);
        var height = Math.max(0, size.getRows() - 1);
        return lines.stream().limit(height)
                .map(line -> line.columnSubSequence(0, Math.min(width, line.columnLength())))
                .toList();
    }

    private static boolean fits(Size size, Size minimum) {
        return size.getColumns() >= minimum.getColumns() && size.getRows() >= minimum.getRows();
    }

    private static List<AttributedString> flow(RunText text, boolean feedback) {
        var glyphs = text.palette().glyphs();
        var lines = new ArrayList<AttributedString>();
        lines.add(header(text).field("  ", GENERATION).field("  ", ENTRIES).field("  ", TOTAL_ELAPSED).build());
        lines.add(text.line().text("Cumulative totals / time").build());
        inputDetails(lines, text);
        lines.add(text.line().padTo(BRANCH_COLUMN).text(glyphs.vertical()).build());
        lines.add(text.line().padTo(BRANCH_COLUMN).text(glyphs.arrowDown()).build());
        lines.add(stageSummary(text, CorpusStage.PARSER));
        lines.add(text.line().padTo(BRANCH_COLUMN).text(glyphs.vertical() + " each pass goes to every checker").build());
        lines.add(text.line().text(connector(glyphs, false)).build());
        var arrows = text.line();
        for (var branch = 0; branch < branchCount(); branch++) {
            arrows.padTo(branchColumn(branch)).text(glyphs.arrowDown());
        }
        lines.add(arrows.build());
        checkerCards(lines, text);
        lines.add(text.line().text(connector(glyphs, true)).build());
        lines.add(text.line().padTo(joinColumn()).text(glyphs.arrowDown() + " both non-crash results").build());
        lines.add(stageSummary(text, CorpusStage.AGGREGATOR));
        lines.add(text.line().padTo(joinColumn())
                .text(glyphs.arrowDown() + " agreeing entries; gate at generation end").build());
        lines.add(stageSummary(text, CorpusStage.QUALITY));
        lines.add(text.line().padTo(FEEDBACK_COLUMN).text(feedback(glyphs, feedback)).build());
        return lines;
    }

    private static void inputDetails(List<AttributedString> lines, RunText text) {
        lines.add(text.line().heading(RunText.INPUTS_HEADING).field("   ", GENERATED).field(" / ", ATTEMPTS)
                .field("   ", GENERATOR_ELAPSED).build());
        lines.add(text.line().padTo(INPUT_DETAIL_COLUMN)
                .field("", REJECTED).field(" / ", LOW_RICHNESS).field(" / ", DUPLICATES).build());
        lines.add(text.line().padTo(INPUT_DETAIL_COLUMN).field("", CLONES).field(" / ", KNOWN_DEFECTS).build());
        lines.add(text.line().padTo(INPUT_DETAIL_COLUMN).text("Richness")
                .field(" ", MIN_RICHNESS).field(" / ", AVG_RICHNESS).field(" / ", MAX_RICHNESS).build());
    }

    private static void checkerCards(List<AttributedString> lines, RunText text) {
        var cards = CorpusStage.checkerBranches().stream().map(stage -> card(text, stage)).toList();
        var rows = cards.stream().mapToInt(List::size).max().orElse(0);
        for (var row = 0; row < rows; row++) {
            var line = text.line();
            for (var column = 0; column < cards.size(); column++) {
                line.padTo(column * CARD_WIDTH);
                if (row < cards.get(column).size()) {
                    line.append(cards.get(column).get(row));
                }
            }
            lines.add(line.build());
        }
    }

    private static List<AttributedString> card(RunText text, CorpusStage stage) {
        var lines = new ArrayList<AttributedString>();
        lines.add(text.line().stage(stage).build());
        lines.add(text.line().queue(stage).build());
        var verdicts = stage.resultVerdicts();
        for (var i = 0; i < verdicts.size(); i += 2) {
            var line = text.line().result(stage, verdicts.get(i));
            if (i + 1 < verdicts.size()) {
                line.padTo(CARD_SECOND_RESULT).result(stage, verdicts.get(i + 1));
            }
            lines.add(line.build());
        }
        lines.add(text.line().work(stage).build());
        return lines;
    }

    /**
     * The fan-out bar below the parser, or the join bar above the aggregator. Each column's glyph
     * follows from the lines it connects: the parser or aggregator on one side, the branches on the
     * other, and the bar itself.
     */
    private static String connector(Glyphs glyphs, boolean join) {
        var last = branchColumn(branchCount() - 1);
        var bar = new StringBuilder(" ".repeat(BRANCH_COLUMN));
        for (var column = BRANCH_COLUMN; column <= last; column++) {
            var branch = (column - BRANCH_COLUMN) % CARD_WIDTH == 0;
            var trunk = column == (join ? joinColumn() : BRANCH_COLUMN);
            bar.append(glyphs.junction(join ? branch : trunk, join ? trunk : branch,
                    column > BRANCH_COLUMN, column < last));
        }
        return bar.toString();
    }

    /** The aggregator's arrow leaves the join bar here, within the bar's extent. */
    private static int joinColumn() {
        return Math.min(JOIN_COLUMN, branchColumn(branchCount() - 1));
    }

    private static int branchCount() {
        return CorpusStage.checkerBranches().size();
    }

    private static int branchColumn(int branch) {
        return BRANCH_COLUMN + branch * CARD_WIDTH;
    }

    private static List<AttributedString> table(RunText text, boolean feedback) {
        var lines = new ArrayList<AttributedString>();
        lines.add(header(text).field("  ", GENERATION).field("  ", ENTRIES).build());
        lines.add(text.line().field("", TOTAL_ELAPSED).text("; cumulative totals / time").build());
        var arrow = " " + text.palette().glyphs().arrowRight(2) + " ";
        lines.add(text.line().text(String.join(arrow, "Inputs", "Parser", "checkers", "join", "Quality")).build());
        var headings = text.line().text("Stage").padTo(TABLE_QUEUE_COLUMN).text(RunMetric.Queue.LABEL);
        resultColumns(headings, verdict -> headings.text(columnLabel(verdict)));
        lines.add(headings.build());
        lines.add(text.line().heading(RunText.INPUTS_HEADING).field("  ", GENERATED).field(" / ", ATTEMPTS).build());
        for (var stage : CorpusStage.values()) {
            var line = text.line().stage(stage);
            if (checksInputs(stage)) {
                line.padTo(TABLE_QUEUE_COLUMN).value(new RunMetric.Queue(stage));
                resultColumns(line, verdict -> {
                    if (stage.resultVerdicts().contains(verdict)) {
                        line.value(new RunMetric.Result(stage, verdict));
                    } else {
                        line.text("-");
                    }
                });
            } else {
                line.append(verdicts(text, stage));
            }
            lines.add(line.build());
        }
        lines.add(text.line().text(feedback(text.palette().glyphs(), feedback)).build());
        lines.add(text.line().text("Full statistics at completion").build());
        return lines;
    }

    /** Pads to each verdict column in turn; header and rows share the same geometry. */
    private static void resultColumns(RunText.Line line, Consumer<CorpusVerdict> cell) {
        var column = TABLE_FIRST_RESULT_COLUMN;
        for (var verdict : CorpusVerdict.values()) {
            line.padTo(column);
            cell.accept(verdict);
            column += TABLE_RESULT_WIDTH;
        }
    }

    /** Tabulated rows are the input-checking stages, so their meanings name the columns. */
    private static String columnLabel(CorpusVerdict verdict) {
        return Arrays.stream(CorpusStage.values())
                .filter(stage -> checksInputs(stage) && stage.resultVerdicts().contains(verdict))
                .findFirst()
                .map(stage -> stage.meaning(verdict).label())
                .orElseThrow();
    }

    /**
     * The parser and checkers run tools on queued inputs, so the layouts show their queues and give
     * each verdict a column. Later stages judge results and are summarized in one line.
     */
    private static boolean checksInputs(CorpusStage stage) {
        return stage == CorpusStage.PARSER || CorpusStage.checkerBranches().contains(stage);
    }

    private static RunText.Line header(RunText text) {
        return text.line().heading("FuzzTLA ").value(RunMetric.State.STATUS);
    }

    private static AttributedString stageSummary(RunText text, CorpusStage stage) {
        var line = text.line().stage(stage);
        if (checksInputs(stage)) {
            line.text("  ").queue(stage);
        }
        return line.append(verdicts(text, stage)).text("  ").work(stage).build();
    }

    private static AttributedString verdicts(RunText text, CorpusStage stage) {
        var line = text.line();
        for (var verdict : stage.resultVerdicts()) {
            line.text("  ").result(stage, verdict);
        }
        return line.build();
    }

    private static String feedback(Glyphs glyphs, boolean enabled) {
        return enabled
                ? glyphs.corner() + glyphs.arrowRight(3) + " mutation parents " + glyphs.arrowRight(3)
                        + " next generation " + RunText.INPUTS_HEADING
                : "Mutation feedback disabled";
    }
}
