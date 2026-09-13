package comp20050.qssboard;

import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;

public class ShowStrategy {
    private static final Color STRATEGY_ATTACK_COLOUR = Color.web("#2FB344");
    private static final Color STRATEGY_BLOCK_COLOUR = Color.web("#F76707");
    private static final Color STRATEGY_BOTH_COLOUR = Color.web("#7048E8");
    private static final Color STRATEGY_CHOSEN_COLOUR = Color.web("#FFD43B");
    private static final Color DARK_TEXT_COLOUR = Color.color(0.15, 0.15, 0.15);
    private static final int STRATEGY_CANDIDATE_LIMIT = 6;

    private final Map<String, Polygon> cellMap;
    private final Button showStrategyButton;
    private final Button hideStrategyButton;
    private final Group strategyOverlay = new Group();

    private boolean visible = false;
    private BotPlayer.StrategyAnalysis currentAnalysis;

    public ShowStrategy(
            Group boardContainer,
            Map<String, Polygon> cellMap,
            Button showStrategyButton,
            Button hideStrategyButton
    ) {
        this.cellMap = cellMap;
        this.showStrategyButton = showStrategyButton;
        this.hideStrategyButton = hideStrategyButton;

        strategyOverlay.setManaged(false);
        strategyOverlay.setMouseTransparent(true);
        boardContainer.getChildren().add(strategyOverlay);

        updateButtons();
        clearOverlay();
    }

    public void show() {
        visible = true;
        updateButtons();
        refresh();
    }

    public void hide() {
        visible = false;
        updateButtons();
        clearOverlay();
    }

    public void setAnalysis(BotPlayer.StrategyAnalysis analysis) {
        currentAnalysis = analysis;
        refresh();
    }

    public void clearAnalysis() {
        currentAnalysis = null;
        refresh();
    }

    private void updateButtons() {
        showStrategyButton.setVisible(!visible);
        showStrategyButton.setDisable(visible);
        hideStrategyButton.setVisible(visible);
        hideStrategyButton.setDisable(!visible);
    }

    private void refresh() {
        clearOverlay();
        if (!hasDisplayableAnalysis()) {
            return;
        }

        BotPlayer.StrategyAnalysis analysis = currentAnalysis;
        addStrategyPanels(analysis);
        addStrategyPaths(analysis);
        Set<String> markedCells = addCandidateMarkers(analysis);
        addSelectedEvaluationMarker(analysis, markedCells);
        addChosenMoveMarkers(analysis);
    }

    private boolean hasDisplayableAnalysis() {
        return visible
                && currentAnalysis != null
                && currentAnalysis.getSelectedMove() != null;
    }

    private void addStrategyPanels(BotPlayer.StrategyAnalysis analysis) {
        strategyOverlay.getChildren().addAll(
                createStrategyLegend(),
                createStrategySummaryPanel(analysis)
        );
    }

    private void addStrategyPaths(BotPlayer.StrategyAnalysis analysis) {
        addPathIfPresent(analysis.getBotPath(), STRATEGY_ATTACK_COLOUR, false);
        addPathIfPresent(analysis.getHumanPath(), STRATEGY_BLOCK_COLOUR, true);
    }

    private void addPathIfPresent(List<String> path, Color colour, boolean dashed) {
        if (!path.isEmpty()) {
            strategyOverlay.getChildren().add(createPathOverlay(path, colour, dashed));
        }
    }

    private Set<String> addCandidateMarkers(BotPlayer.StrategyAnalysis analysis) {
        Set<String> markedCells = new HashSet<>();
        List<BotPlayer.CellEvaluation> evaluations = getRankedEvaluations(analysis);
        int count = Math.min(STRATEGY_CANDIDATE_LIMIT, evaluations.size());

        for (int index = 0; index < count; index++) {
            addRankedCandidateMarker(evaluations.get(index), index + 1, markedCells);
        }
        return markedCells;
    }

    private List<BotPlayer.CellEvaluation> getRankedEvaluations(
            BotPlayer.StrategyAnalysis analysis
    ) {
        List<BotPlayer.CellEvaluation> rankedEvaluations =
                new ArrayList<>(analysis.getCellEvaluations());

        rankedEvaluations.sort(createEvaluationComparator(analysis));
        return rankedEvaluations;
    }

    private Comparator<BotPlayer.CellEvaluation> createEvaluationComparator(
            BotPlayer.StrategyAnalysis analysis
    ) {
        String selectedMoveId = getSelectedMoveId(analysis);

        return Comparator
                .comparingLong(BotPlayer.CellEvaluation::getScore)
                .thenComparingInt(evaluation -> getSelectedMoveRank(evaluation, selectedMoveId))
                .thenComparing(evaluation -> evaluation.getCell().getId());
    }

    private int getSelectedMoveRank(
            BotPlayer.CellEvaluation evaluation,
            String selectedMoveId
    ) {
        return evaluation.getCell().getId().equals(selectedMoveId) ? 0 : 1;
    }

    private String getSelectedMoveId(BotPlayer.StrategyAnalysis analysis) {
        if (analysis.getSelectedMove() == null) {
            return "";
        }
        return analysis.getSelectedMove().getId();
    }

    private void addRankedCandidateMarker(
            BotPlayer.CellEvaluation evaluation,
            int rank,
            Set<String> markedCells
    ) {
        strategyOverlay.getChildren().add(createCandidateMarker(evaluation, rank));
        markedCells.add(evaluation.getCell().getId());
    }

    private void addSelectedEvaluationMarker(
            BotPlayer.StrategyAnalysis analysis,
            Set<String> markedCells
    ) {
        BotPlayer.CellEvaluation selectedEvaluation = analysis.getSelectedEvaluation();
        if (shouldMarkSelectedEvaluation(selectedEvaluation, markedCells)) {
            strategyOverlay.getChildren().add(createCandidateRoleOverlay(selectedEvaluation));
        }
    }

    private boolean shouldMarkSelectedEvaluation(
            BotPlayer.CellEvaluation evaluation,
            Set<String> markedCells
    ) {
        return evaluation != null && !markedCells.contains(evaluation.getCell().getId());
    }

    private void addChosenMoveMarkers(BotPlayer.StrategyAnalysis analysis) {
        Polygon selectedMove = analysis.getSelectedMove();
        strategyOverlay.getChildren().add(createChosenMoveOverlay(selectedMove, analysis.isFallbackMove()));
        strategyOverlay.getChildren().add(createSelectedMoveTag(selectedMove, getSelectedMoveTag(analysis)));
    }

    private String getSelectedMoveTag(BotPlayer.StrategyAnalysis analysis) {
        return analysis.isFallbackMove() ? "BOT" : "PLAY";
    }

    private void clearOverlay() {
        strategyOverlay.getChildren().clear();
    }

    private Group createStrategySummaryPanel(BotPlayer.StrategyAnalysis analysis) {
        Group panel = new Group();
        panel.setId("strategy-summary");
        panel.getChildren().addAll(
                createSummaryBackground(),
                createSummaryTitle(),
                createChosenMoveText(analysis),
                createRouteText(analysis),
                createReasonText(analysis),
                createInstructionText()
        );
        positionGroup(panel, -150, 638);
        return panel;
    }

    private Rectangle createSummaryBackground() {
        return createRoundedBackground(10, 326, 275, 215, 0.92);
    }

    private Text createSummaryTitle() {
        Font font = Font.font("System", FontWeight.BOLD, 18);
        return createText(24, 352, "Why this move?", font, DARK_TEXT_COLOUR);
    }

    private Text createChosenMoveText(BotPlayer.StrategyAnalysis analysis) {
        String cellLabel = formatCellLabel(analysis.getSelectedMove().getId());
        Font font = Font.font("System", FontWeight.SEMI_BOLD, 14);
        return createText(24, 380, "Chosen: " + cellLabel, font, DARK_TEXT_COLOUR);
    }

    private Text createRouteText(BotPlayer.StrategyAnalysis analysis) {
        String text = String.format("Bot route: %s   Opponent route: %s",
                formatDistance(analysis.getBotMovesToWin()),
                formatDistance(analysis.getHumanMovesToWin()));
        return createText(24, 405, text, Font.font(13), Color.color(0.20, 0.20, 0.20));
    }

    private Text createReasonText(BotPlayer.StrategyAnalysis analysis) {
        Text reasonText = createText(24, 432, buildStrategyReasonText(analysis),
                Font.font(13), Color.color(0.18, 0.18, 0.18));
        reasonText.setWrappingWidth(245);
        return reasonText;
    }

    private Text createInstructionText() {
        Font font = Font.font("System", FontWeight.SEMI_BOLD, 13);
        return createText(24, 518,
                "Showing the reasoning for the bot's last move.", font, DARK_TEXT_COLOUR);
    }

    private String buildStrategyReasonText(BotPlayer.StrategyAnalysis analysis) {
        if (analysis.isFallbackMove()) {
            return "No useful path cell was available, so the bot fell back to a random legal move.";
        }

        BotPlayer.CellEvaluation selectedEvaluation = analysis.getSelectedEvaluation();
        if (selectedEvaluation == null) {
            return "The bot selected a legal move using its current path-based strategy.";
        }

        String reason = getMainReasonText(analysis, selectedEvaluation);
        return appendTieBreakText(reason, analysis, selectedEvaluation);
    }

    private String getMainReasonText(
            BotPlayer.StrategyAnalysis analysis,
            BotPlayer.CellEvaluation selectedEvaluation
    ) {
        if (selectedEvaluation.isBotUseful() && selectedEvaluation.isHumanUseful()) {
            return getSharedRouteReasonText(analysis);
        }

        if (selectedEvaluation.isHumanUseful()) {
            return "This is mainly a defensive move because it lies on the opponent's shortest available route.";
        }

        return "This is mainly an attacking move because it lies on the bot's shortest available route.";
    }

    private String getSharedRouteReasonText(BotPlayer.StrategyAnalysis analysis) {
        if (analysis.getHumanMovesToWin() <= analysis.getBotMovesToWin()) {
            return "This move blocks the opponent's most urgent route while still helping the bot's own route.";
        }
        return "This move improves the bot's best route and still interferes with the opponent.";
    }

    private String appendTieBreakText(
            String reason,
            BotPlayer.StrategyAnalysis analysis,
            BotPlayer.CellEvaluation selectedEvaluation
    ) {
        int tiedBestMoves = countTiedBestMoves(analysis, selectedEvaluation);
        if (tiedBestMoves <= 1) {
            return reason;
        }
        return reason + " " + tiedBestMoves
                + " moves shared the best score, so the bot randomly picked one of them.";
    }

    private int countTiedBestMoves(
            BotPlayer.StrategyAnalysis analysis,
            BotPlayer.CellEvaluation selectedEvaluation
    ) {
        if (selectedEvaluation == null) {
            return 0;
        }

        int tiedBestMoves = 0;
        long selectedScore = selectedEvaluation.getScore();
        for (BotPlayer.CellEvaluation evaluation : analysis.getCellEvaluations()) {
            if (evaluation.getScore() == selectedScore) {
                tiedBestMoves++;
            }
        }
        return tiedBestMoves;
    }

    private String formatDistance(int distance) {
        return distance >= QuaxController.MAX_DISTANCE
                ? "blocked"
                : distance + " move" + (distance == 1 ? "" : "s");
    }

    private String formatCellLabel(String cellId) {
        if (cellId == null) {
            return "unknown";
        }

        if (cellId.startsWith("OctCell")) {
            return "Oct " + cellId.replace("OctCell", "");
        }

        if (cellId.startsWith("RhoCell")) {
            return "Rho " + cellId.replace("RhoCell", "");
        }

        return cellId;
    }

    private Group createStrategyLegend() {
        Group legend = new Group();
        legend.setId("strategy-legend");
        legend.getChildren().addAll(
                createLegendBackground(),
                createLegendTitle(),
                createRankedOptionsKey(),
                createPathKey(234, STRATEGY_ATTACK_COLOUR, false, 7, "bot route / attack"),
                createPathKey(262, STRATEGY_BLOCK_COLOUR, true, 6, "block / defend"),
                createChosenMoveKey()
        );
        positionGroup(legend, -150, 600);
        return legend;
    }

    private Rectangle createLegendBackground() {
        return createRoundedBackground(10, 155, 208, 160, 0.90);
    }

    private Text createLegendTitle() {
        return createText(24, 180, "Bot strategy",
                Font.font("System", FontWeight.BOLD, 18), DARK_TEXT_COLOUR);
    }

    private Group createRankedOptionsKey() {
        Group key = new Group();
        Circle badgeKey = createLegendBadgeCircle();
        Text badgeNumber = createLegendBadgeNumber();
        Text badgeText = createLegendText(54, 210, "ranked options");
        key.getChildren().addAll(badgeKey, badgeNumber, badgeText);
        return key;
    }

    private Circle createLegendBadgeCircle() {
        Circle badgeKey = new Circle(35, 205, 11);
        badgeKey.setFill(Color.color(1.0, 1.0, 1.0, 0.96));
        badgeKey.setStroke(STRATEGY_BOTH_COLOUR);
        badgeKey.setStrokeWidth(3);
        return badgeKey;
    }

    private Text createLegendBadgeNumber() {
        Text badgeNumber = createText("1", Font.font("System", FontWeight.EXTRA_BOLD, 14));
        badgeNumber.setFill(DARK_TEXT_COLOUR);
        centreTextOnPoint(badgeNumber, 35, 205);
        return badgeNumber;
    }

    private Group createPathKey(
            double y,
            Color colour,
            boolean dashed,
            double strokeWidth,
            String label
    ) {
        Group key = new Group();
        Line line = createLegendLine(y, colour, dashed, strokeWidth);
        Text text = createLegendText(54, y + 5, label);
        key.getChildren().addAll(line, text);
        return key;
    }

    private Line createLegendLine(double y, Color colour, boolean dashed, double strokeWidth) {
        Line line = new Line(26, y, 44, y);
        line.setStroke(colour.deriveColor(0, 1, 1, 0.95));
        line.setStrokeWidth(strokeWidth);
        line.setStrokeLineCap(StrokeLineCap.ROUND);
        if (dashed) {
            line.getStrokeDashArray().addAll(10.0, 7.0);
        }
        return line;
    }

    private Text createLegendText(double x, double y, String textValue) {
        return createText(x, y, textValue, Font.font(14), Color.BLACK);
    }

    private Group createChosenMoveKey() {
        Group key = new Group();
        Rectangle chosenKey = new Rectangle(25, 276, 20, 20);
        chosenKey.setFill(Color.color(1.0, 0.83, 0.16, 0.20));
        chosenKey.setStroke(STRATEGY_CHOSEN_COLOUR);
        chosenKey.setStrokeWidth(3);
        key.getChildren().addAll(chosenKey, createLegendText(54, 291, "move the bot plays"));
        return key;
    }

    private Group createCandidateMarker(BotPlayer.CellEvaluation evaluation, int rank) {
        Group marker = createCandidateRoleOverlay(evaluation);
        marker.getChildren().add(createCandidateBadge(
                evaluation.getCell(),
                rank,
                getCandidateAccentColour(evaluation),
                rank <= 3
        ));
        return marker;
    }

    private Group createCandidateRoleOverlay(BotPlayer.CellEvaluation evaluation) {
        Group marker = new Group();
        Polygon cell = evaluation.getCell();

        if (evaluation.isBotUseful() && evaluation.isHumanUseful()) {
            marker.getChildren().add(createCellOutline(cell, STRATEGY_BLOCK_COLOUR, 4.6, true));
            marker.getChildren().add(createCellOutline(cell, STRATEGY_ATTACK_COLOUR, 2.8, false));
            return marker;
        }

        if (evaluation.isBotUseful()) {
            marker.getChildren().add(createCellOutline(cell, STRATEGY_ATTACK_COLOUR, 4.0, false));
            return marker;
        }

        marker.getChildren().add(createCellOutline(cell, STRATEGY_BLOCK_COLOUR, 4.0, true));
        return marker;
    }

    private Polygon createCellOutline(Polygon source, Color colour, double strokeWidth, boolean dashed) {
        Polygon outline = clonePolygon(source);
        outline.setFill(Color.TRANSPARENT);
        outline.setStroke(colour.deriveColor(0, 1, 1, 0.96));
        outline.setStrokeWidth(strokeWidth);
        if (dashed) {
            outline.getStrokeDashArray().addAll(12.0, 8.0);
        }
        return outline;
    }

    private Group createCandidateBadge(Polygon cell, int rank, Color accentColour, boolean emphasised) {
        Group badge = new Group();
        Bounds bounds = cell.getBoundsInParent();
        double radius = cell.getId().startsWith("RhoCell") ? 10.0 : 12.0;
        double centreX = bounds.getMinX() + (bounds.getWidth() * 0.76);
        double centreY = bounds.getMinY() + (bounds.getHeight() * 0.28);

        Circle shadow = new Circle(centreX + 1.0, centreY + 1.0, radius + 0.4);
        shadow.setFill(Color.color(0.0, 0.0, 0.0, 0.18));

        Circle bubble = new Circle(centreX, centreY, radius);
        bubble.setFill(Color.color(1.0, 1.0, 1.0, 0.96));
        bubble.setStroke(accentColour);
        bubble.setStrokeWidth(emphasised ? 3.3 : 2.5);

        Text number = new Text(Integer.toString(rank));
        number.setFont(Font.font(
                "System",
                FontWeight.EXTRA_BOLD,
                cell.getId().startsWith("RhoCell") ? 12 : 14
        ));
        number.setFill(DARK_TEXT_COLOUR);

        centreTextOnPoint(number, centreX, centreY);
        badge.getChildren().addAll(shadow, bubble, number);
        return badge;
    }

    private Color getCandidateAccentColour(BotPlayer.CellEvaluation evaluation) {
        if (evaluation.isBotUseful() && evaluation.isHumanUseful()) {
            return STRATEGY_BOTH_COLOUR;
        }
        return evaluation.isBotUseful() ? STRATEGY_ATTACK_COLOUR : STRATEGY_BLOCK_COLOUR;
    }

    private Group createPathOverlay(List<String> cellIds, Color colour, boolean dashed) {
        Group pathGroup = new Group();
        if (cellIds.size() < 2) {
            return pathGroup;
        }

        Polyline shadow = createPathLine(Color.color(0.0, 0.0, 0.0, 0.28), dashed, true);
        Polyline line = createPathLine(colour.deriveColor(0, 1, 1, 0.90), dashed, false);
        addPathPoints(cellIds, shadow, line);
        pathGroup.getChildren().addAll(shadow, line);
        return pathGroup;
    }

    private Polyline createPathLine(Color colour, boolean dashed, boolean shadow) {
        Polyline pathLine = new Polyline();
        pathLine.setFill(Color.TRANSPARENT);
        pathLine.setStroke(colour);
        pathLine.setStrokeWidth(getPathLineWidth(dashed, shadow));
        pathLine.setStrokeLineCap(StrokeLineCap.ROUND);
        if (dashed) {
            pathLine.getStrokeDashArray().addAll(18.0, 10.0);
        }
        return pathLine;
    }

    private double getPathLineWidth(boolean dashed, boolean shadow) {
        if (shadow) {
            return dashed ? 10 : 11;
        }
        return dashed ? 6 : 7;
    }

    private void addPathPoints(List<String> cellIds, Polyline shadow, Polyline line) {
        for (String cellId : cellIds) {
            Polygon cell = cellMap.get(cellId);
            if (cell != null) {
                addCellCentreToPath(cell, shadow, line);
            }
        }
    }

    private void addCellCentreToPath(Polygon cell, Polyline shadow, Polyline line) {
        Bounds bounds = cell.getBoundsInParent();
        double centreX = bounds.getMinX() + (bounds.getWidth() / 2.0);
        double centreY = bounds.getMinY() + (bounds.getHeight() / 2.0);
        shadow.getPoints().addAll(centreX, centreY);
        line.getPoints().addAll(centreX, centreY);
    }

    private Node createChosenMoveOverlay(Polygon chosenMove, boolean fallbackMove) {
        Polygon overlay = clonePolygon(chosenMove);
        overlay.setFill(Color.color(1.0, 0.83, 0.16, 0.18));
        overlay.setStroke(STRATEGY_CHOSEN_COLOUR);
        overlay.setStrokeWidth(5.5);
        if (fallbackMove) {
            overlay.getStrokeDashArray().addAll(14.0, 8.0);
        }
        return overlay;
    }

    private Text createSelectedMoveTag(Polygon selectedMove, String textValue) {
        Bounds bounds = selectedMove.getBoundsInParent();
        double centreX = bounds.getMinX() + (bounds.getWidth() / 2.0);
        double centreY = bounds.getMinY() + (bounds.getHeight() / 2.0);

        Text tag = new Text(textValue);
        tag.setFont(Font.font("System", FontWeight.EXTRA_BOLD, 18));
        tag.setFill(Color.BLACK);
        tag.setStroke(Color.WHITE);
        tag.setStrokeWidth(1.2);

        centreTextOnPoint(tag, centreX, centreY);
        return tag;
    }

    private Rectangle createRoundedBackground(
            double x,
            double y,
            double width,
            double height,
            double opacity
    ) {
        Rectangle background = new Rectangle(x, y, width, height);
        background.setArcWidth(18);
        background.setArcHeight(18);
        background.setFill(Color.color(1.0, 1.0, 1.0, opacity));
        background.setStroke(Color.color(0.0, 0.0, 0.0, 0.20));
        return background;
    }

    private Text createText(double x, double y, String value, Font font, Color fill) {
        Text text = createText(value, font);
        text.setX(x);
        text.setY(y);
        text.setFill(fill);
        return text;
    }

    private Text createText(String value, Font font) {
        Text text = new Text(value);
        text.setFont(font);
        return text;
    }

    private void centreTextOnPoint(Text text, double centreX, double centreY) {
        Bounds textBounds = text.getLayoutBounds();
        text.setX(centreX - (textBounds.getWidth() / 2.0));
        text.setY(centreY + (textBounds.getHeight() / 4.0));
    }

    private void positionGroup(Group group, double layoutX, double layoutY) {
        group.setLayoutX(layoutX);
        group.setLayoutY(layoutY);
    }

    private Polygon clonePolygon(Polygon source) {
        Polygon copy = new Polygon();
        copy.getPoints().addAll(source.getPoints());
        copy.setLayoutX(source.getLayoutX());
        copy.setLayoutY(source.getLayoutY());
        copy.setScaleX(source.getScaleX());
        copy.setScaleY(source.getScaleY());
        copy.setScaleZ(source.getScaleZ());
        copy.setTranslateX(source.getTranslateX());
        copy.setTranslateY(source.getTranslateY());
        copy.setRotate(source.getRotate());
        copy.setStrokeType(source.getStrokeType());
        copy.setMouseTransparent(true);
        return copy;
    }
}
