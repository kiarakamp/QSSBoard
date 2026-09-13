package comp20050.qssboard;

import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class BotPlayer {
    private static final int INF = QuaxController.MAX_DISTANCE;

    public static final class CellEvaluation {
        private final Polygon cell;
        private final int botForward;
        private final int botBackward;
        private final int humanForward;
        private final int humanBackward;
        private final boolean botUseful;
        private final boolean humanUseful;
        private final long score;

        private CellEvaluation(
                Polygon cell,
                int botForward,
                int botBackward,
                int humanForward,
                int humanBackward,
                boolean botUseful,
                boolean humanUseful,
                long score
        ) {
            this.cell = cell;
            this.botForward = botForward;
            this.botBackward = botBackward;
            this.humanForward = humanForward;
            this.humanBackward = humanBackward;
            this.botUseful = botUseful;
            this.humanUseful = humanUseful;
            this.score = score;
        }

        public Polygon getCell() {
            return cell;
        }

        public int getBotForward() {
            return botForward;
        }

        public int getBotBackward() {
            return botBackward;
        }

        public int getHumanForward() {
            return humanForward;
        }

        public int getHumanBackward() {
            return humanBackward;
        }

        public boolean isBotUseful() {
            return botUseful;
        }

        public boolean isHumanUseful() {
            return humanUseful;
        }

        public long getScore() {
            return score;
        }
    }

    public static final class StrategyAnalysis {
        private final Color botColour;
        private final Color humanColour;
        private final int botMovesToWin;
        private final int humanMovesToWin;
        private final List<CellEvaluation> cellEvaluations;
        private final Polygon selectedMove;
        private final List<String> botPath;
        private final List<String> humanPath;
        private final boolean fallbackMove;

        private StrategyAnalysis(
                Color botColour,
                Color humanColour,
                int botMovesToWin,
                int humanMovesToWin,
                List<CellEvaluation> cellEvaluations,
                Polygon selectedMove,
                List<String> botPath,
                List<String> humanPath,
                boolean fallbackMove
        ) {
            this.botColour = botColour;
            this.humanColour = humanColour;
            this.botMovesToWin = botMovesToWin;
            this.humanMovesToWin = humanMovesToWin;
            this.cellEvaluations = List.copyOf(cellEvaluations);
            this.selectedMove = selectedMove;
            this.botPath = List.copyOf(botPath);
            this.humanPath = List.copyOf(humanPath);
            this.fallbackMove = fallbackMove;
        }

        public Color getBotColour() {
            return botColour;
        }

        public Color getHumanColour() {
            return humanColour;
        }

        public int getBotMovesToWin() {
            return botMovesToWin;
        }

        public int getHumanMovesToWin() {
            return humanMovesToWin;
        }

        public List<CellEvaluation> getCellEvaluations() {
            return cellEvaluations;
        }

        public Polygon getSelectedMove() {
            return selectedMove;
        }

        public List<String> getBotPath() {
            return botPath;
        }

        public List<String> getHumanPath() {
            return humanPath;
        }

        public boolean isFallbackMove() {
            return fallbackMove;
        }

        public CellEvaluation getSelectedEvaluation() {
            if (selectedMove == null) {
                return null;
            }

            String selectedId = selectedMove.getId();
            for (CellEvaluation evaluation : cellEvaluations) {
                if (evaluation.getCell().getId().equals(selectedId)) {
                    return evaluation;
                }
            }
            return null;
        }
    }

    private static final class CandidateScore {
        private final Polygon cell;
        private final int botForward;
        private final int botBackward;
        private final int humanForward;
        private final int humanBackward;
        private final boolean botUseful;
        private final boolean humanUseful;
        private final long score;

        private CandidateScore(
                Polygon cell,
                int botForward,
                int botBackward,
                int humanForward,
                int humanBackward,
                boolean botUseful,
                boolean humanUseful,
                long score
        ) {
            this.cell = cell;
            this.botForward = botForward;
            this.botBackward = botBackward;
            this.humanForward = humanForward;
            this.humanBackward = humanBackward;
            this.botUseful = botUseful;
            this.humanUseful = humanUseful;
            this.score = score;
        }
    }

    public Polygon chooseMove(List<Polygon> cells, Color botColour, QuaxController controller) {
        StrategyAnalysis analysis = analyseMove(cells, botColour, controller);
        return analysis == null ? null : analysis.getSelectedMove();
    }

    public StrategyAnalysis analyseMove(List<Polygon> cells, Color botColour, QuaxController controller) {
        Color humanColour = (botColour == Color.BLACK) ? Color.WHITE : Color.BLACK;

        // Run Dijkstra's algorithm from both sides for both players to find connected paths
        QuaxController.PathSearchResult botForwardSearch = controller.runDijkstraSearch(botColour, true);
        QuaxController.PathSearchResult botBackwardSearch = controller.runDijkstraSearch(botColour, false);
        QuaxController.PathSearchResult humanForwardSearch = controller.runDijkstraSearch(humanColour, true);
        QuaxController.PathSearchResult humanBackwardSearch = controller.runDijkstraSearch(humanColour, false);

        int botMovesToWin = findShortestWinDistance(botColour, botForwardSearch.dist, controller);
        int humanMovesToWin = findShortestWinDistance(humanColour, humanForwardSearch.dist, controller);

        List<CandidateScore> candidates = new ArrayList<>();
        long bestScore = calculateScores(cells, candidates, botForwardSearch, botBackwardSearch,
                humanForwardSearch, humanBackwardSearch, botMovesToWin, humanMovesToWin, controller);

        return chooseMoveFromCandidates(candidates, bestScore, botColour, humanColour,
                botMovesToWin, humanMovesToWin, cells, controller);
    }

    private long calculateScores(List<Polygon> cells, List<CandidateScore> candidates,
                                   QuaxController.PathSearchResult botForwardSearch,
                                   QuaxController.PathSearchResult botBackwardSearch,
                                   QuaxController.PathSearchResult humanForwardSearch,
                                   QuaxController.PathSearchResult humanBackwardSearch,
                                   int botMovesToWin, int humanMovesToWin, QuaxController controller) {
        long bestScore = Long.MAX_VALUE;

        for (Polygon cell : cells) {
            if (controller.cellIsOccupied(cell)) { continue; }

            int[] coordinates = controller.getCoordinateFromId(cell.getId());
            int row = coordinates[0];
            int col = coordinates[1];

            CandidateScore scoreResult = evaluateCell(cell, row, col, botForwardSearch, botBackwardSearch,
                    humanForwardSearch, humanBackwardSearch, botMovesToWin, humanMovesToWin);

            if (scoreResult != null) {
                candidates.add(scoreResult);
                bestScore = Math.min(bestScore, scoreResult.score);
            }
        }
        return bestScore;
    }

    private CandidateScore evaluateCell(Polygon cell, int row, int col,
                                        QuaxController.PathSearchResult botForwardSearch,
                                        QuaxController.PathSearchResult botBackwardSearch,
                                        QuaxController.PathSearchResult humanForwardSearch,
                                        QuaxController.PathSearchResult humanBackwardSearch,
                                        int botMovesToWin, int humanMovesToWin) {
        int botForwardCost = botForwardSearch.dist[row][col];
        int botBackwardCost = botBackwardSearch.dist[row][col];
        int humanForwardCost = humanForwardSearch.dist[row][col];
        int humanBackwardCost = humanBackwardSearch.dist[row][col];

        boolean botUseful = botForwardCost < INF && botBackwardCost < INF;
        boolean humanUseful = humanForwardCost < INF && humanBackwardCost < INF;

        if (!botUseful && !humanUseful) { return null; }

        long score;
        if (botUseful && humanUseful) {
            // -1 to adjust the current tile being counted in distance calculations
            int botPathCost = botForwardCost + botBackwardCost - 1;
            int humanPathCost = humanForwardCost + humanBackwardCost - 1;

            // Weigh move value by how close each player is to winning
            score = (long) humanMovesToWin * humanPathCost + (long) botMovesToWin * botPathCost;
        } else if (botUseful) {
            score = (long) botMovesToWin * (botForwardCost + botBackwardCost - 1);
        } else {
            score = (long) humanMovesToWin * (humanForwardCost + humanBackwardCost - 1);
        }

        return new CandidateScore(cell, botForwardCost, botBackwardCost, humanForwardCost, humanBackwardCost,
                botUseful, humanUseful, score);
    }

    private StrategyAnalysis chooseMoveFromCandidates(List<CandidateScore> candidates, long bestScore,
                                                      Color botColour, Color humanColour, int botMovesToWin,
                                                      int humanMovesToWin, List<Polygon> cells, QuaxController controller) {
        List<Polygon> bestMoves = new ArrayList<>();
        List<CellEvaluation> evaluations = new ArrayList<>();
        boolean isFallback = candidates.isEmpty();

        if (isFallback) {
            // If no strategic moves exist, pick any empty tile
            for (Polygon cell : cells) {
                if (!controller.cellIsOccupied(cell)) {
                    bestMoves.add(cell);
                }
            }
        } else {
            for (CandidateScore candidate : candidates) {
                if (candidate.score == bestScore) {
                    bestMoves.add(candidate.cell);
                }

                evaluations.add(new CellEvaluation(candidate.cell,
                        candidate.botForward, candidate.botBackward, candidate.humanForward, candidate.humanBackward,
                        candidate.botUseful, candidate.humanUseful, candidate.score));
            }
        }
        // Pick randomly from best moves to avoid predictability
        Polygon chosenMove = bestMoves.get(new Random().nextInt(bestMoves.size()));

        return new StrategyAnalysis(botColour, humanColour, botMovesToWin, humanMovesToWin, evaluations,
                chosenMove, Collections.emptyList(), Collections.emptyList(), isFallback);
    }

    private int findShortestWinDistance(Color colour, int[][] dist, QuaxController controller) {
        int bestDistance = INF;
        for (int i = 0; i < controller.BOARD_SIZE; i++) {
            int row = (colour == Color.BLACK) ? controller.MAX_COORD : i * 2;
            int col = (colour == Color.BLACK) ? i * 2 : controller.MAX_COORD;
            bestDistance = Math.min(bestDistance, dist[row][col]);
        }
        return bestDistance;
    }
}
