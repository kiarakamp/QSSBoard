package comp20050.qssboard;

import javafx.fxml.FXML;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;

public class QuaxController {

    @FXML private Label turnLabel;
    @FXML private Polygon turnOctagon;
    @FXML private Polygon turnRhombus;
    @FXML private Label winnerLabel;
    @FXML private Button pieRuleButton;
    @FXML private Group boardContainer;
    @FXML private Button showStrategyButton;
    @FXML private Button hideStrategyButton;

    private final Map<String, Polygon> cellMap = new HashMap<>();
    private final List<Polygon> allCells = new ArrayList<>();

    private final BotPlayer bot = new BotPlayer();
    private Player player1;
    private Player player2;
    private ShowStrategy showStrategy;

    private boolean pieRuleAvailable = true;
    private boolean isBlackTurn = true;
    private boolean gameOver = false;
    private boolean botIsBlack = true;

    private long boardVersion = 0;
    private long cachedStrategyVersion = -1;
    private Color cachedStrategyColour = null;
    private BotPlayer.StrategyAnalysis cachedStrategy = null;

    public static final int BOARD_SIZE = 11;
    private static final int LAST_INDEX = BOARD_SIZE - 1;
    private static final int RHO_SIZE = BOARD_SIZE - 1;

    public static final int GRID_SIZE = 21;
    public static final int MAX_COORD = GRID_SIZE - 1;
    public static final int MAX_DISTANCE = 10000;

    @FXML
    private void botMove() {
        if (gameOver) {
            return;
        }

        Color botColour = getBotColour();
        BotPlayer.StrategyAnalysis analysis = getStrategyAnalysisForCurrentBoard();
        Polygon move = analysis == null ? null : analysis.getSelectedMove();
        if (move == null) {
            return;
        }

        applyColour(move, botColour);
        registerBoardChange();
        showStrategy.setAnalysis(analysis);

        updatePieRuleState(botColour);
        checkForWinner();
        switchTurn();
        updateTurnDisplay();
    }

    private boolean isBotTurn() {
        return !gameOver && isBlackTurn == botIsBlack;
    }

    private void requestBotTurn() {
        if (isBotTurn()) {
            botMove();
        }
    }

    private static class PathNode implements Comparable<PathNode> {
        int row;
        int col;
        int cost;

        PathNode(int row, int col, int cost) {
            this.row = row;
            this.col = col;
            this.cost = cost;
        }

        @Override
        public int compareTo(PathNode other) {
            int costComparison = Integer.compare(this.cost, other.cost);
            if (costComparison != 0) {
                return costComparison;
            }

            int rowComparison = Integer.compare(this.row, other.row);
            if (rowComparison != 0) {
                return rowComparison;
            }

            return Integer.compare(this.col, other.col);
        }
    }

    static final class PathSearchResult {
        final int[][] dist;
        final int[][] prevRow;
        final int[][] prevCol;

        PathSearchResult(int size) {
            this.dist = new int[size][size];
            this.prevRow = new int[size][size];
            this.prevCol = new int[size][size];

            for (int row = 0; row < size; row++) {
                Arrays.fill(dist[row], MAX_DISTANCE);
                Arrays.fill(prevRow[row], -1);
                Arrays.fill(prevCol[row], -1);
            }
        }
    }

    public int[] getCoordinateFromId(String id) {
        int number = Integer.parseInt(id.replaceAll("\\D", "")) - 1;

        if (id.startsWith("OctCell")) {
            return new int[]{(number / BOARD_SIZE) * 2, (number % BOARD_SIZE) * 2};
        }

        return new int[]{((number / RHO_SIZE) * 2) + 1, ((number % RHO_SIZE) * 2) + 1};
    }

    public int[][] runDijkstra(Color colour, boolean fromStart) {
        return runDijkstraSearch(colour, fromStart).dist;
    }

    PathSearchResult runDijkstraSearch(Color colour, boolean fromStart) {
        PathSearchResult result = new PathSearchResult(QuaxBoard.SIZE);
        PriorityQueue<PathNode> queue = new PriorityQueue<>();
        boolean isBlack = (colour == Color.BLACK);
        Color humanColour = isBlack ? Color.WHITE : Color.BLACK;

        for (int index = 0; index < BOARD_SIZE; index++) {
            int row;
            int col;
            if (isBlack) {
                row = fromStart ? 0 : MAX_COORD;
                col = index * 2;
            } else {
                row = index * 2;
                col = fromStart ? 0 : MAX_COORD;
            }

            String cellId = getCellIdFromCoordinate(row, col);
            Polygon polygon = cellMap.get(cellId);
            if (polygon == null || isCellOwnedBy(cellId, humanColour)) {
                continue;
            }

            int tileCost = isCellOwnedBy(cellId, colour) ? 0 : 1;
            result.dist[row][col] = tileCost;
            queue.add(new PathNode(row, col, tileCost));
        }

        while (!queue.isEmpty()) {
            PathNode current = queue.poll();
            if (current.cost > result.dist[current.row][current.col]) {
                continue;
            }

            for (int[] neighbour : getNeighbourCoordinates(current.row, current.col)) {
                int nextRow = neighbour[0];
                int nextCol = neighbour[1];
                String nextId = getCellIdFromCoordinate(nextRow, nextCol);

                if (isCellOwnedBy(nextId, humanColour)) {
                    continue;
                }

                int tileCost = isCellOwnedBy(nextId, colour) ? 0 : 1;
                int newDistance = result.dist[current.row][current.col] + tileCost;
                if (newDistance < result.dist[nextRow][nextCol]) {
                    result.dist[nextRow][nextCol] = newDistance;
                    result.prevRow[nextRow][nextCol] = current.row;
                    result.prevCol[nextRow][nextCol] = current.col;
                    queue.add(new PathNode(nextRow, nextCol, newDistance));
                }
            }
        }

        return result;
    }

    List<String> buildPathFromSearches(
            String cellId,
            PathSearchResult forwardSearch,
            PathSearchResult backwardSearch
    ) {
        int[] coordinate = getCoordinateFromId(cellId);
        int row = coordinate[0];
        int col = coordinate[1];

        if (forwardSearch.dist[row][col] >= MAX_DISTANCE || backwardSearch.dist[row][col] >= MAX_DISTANCE) {
            return Collections.emptyList();
        }

        List<String> startToCell = new ArrayList<>();
        int currentRow = row;
        int currentCol = col;
        while (currentRow != -1 && currentCol != -1) {
            startToCell.add(getCellIdFromCoordinate(currentRow, currentCol));
            int previousRow = forwardSearch.prevRow[currentRow][currentCol];
            int previousCol = forwardSearch.prevCol[currentRow][currentCol];
            currentRow = previousRow;
            currentCol = previousCol;
        }
        Collections.reverse(startToCell);

        List<String> fullPath = new ArrayList<>(startToCell);
        currentRow = backwardSearch.prevRow[row][col];
        currentCol = backwardSearch.prevCol[row][col];
        while (currentRow != -1 && currentCol != -1) {
            fullPath.add(getCellIdFromCoordinate(currentRow, currentCol));
            int previousRow = backwardSearch.prevRow[currentRow][currentCol];
            int previousCol = backwardSearch.prevCol[currentRow][currentCol];
            currentRow = previousRow;
            currentCol = previousCol;
        }

        return fullPath;
    }

    @FXML
    void handleCellClick(MouseEvent event) {
        if (gameOver) {
            return;
        }

        Polygon clickedCell = (Polygon) event.getSource();
        if (cellIsOccupied(clickedCell)) {
            return;
        }

        showStrategy.clearAnalysis();
        Color currentColour = placePiece(clickedCell);
        registerBoardChange();

        updatePieRuleState(currentColour);
        checkForWinner();
        switchTurn();
        updateTurnDisplay();

        if (!gameOver && isBotTurn()) {
            requestBotTurn();
        }
    }

    public boolean cellIsOccupied(Polygon cell) {
        return cell.getFill().equals(Color.BLACK) || cell.getFill().equals(Color.WHITE);
    }

    private Color placePiece(Polygon cell) {
        Color pieceColour = isBlackTurn ? Color.BLACK : Color.WHITE;
        applyColour(cell, pieceColour);
        return pieceColour;
    }

    private void applyColour(Polygon cell, Color colour) {
        cell.setFill(colour);
        cell.setStroke(Color.BLACK);
    }

    private void updatePieRuleState(Color currentColour) {
        if (!pieRuleAvailable) {
            return;
        }

        if (currentColour.equals(Color.BLACK)) {
            showPieButton();
            return;
        }

        hidePieButton();
        pieRuleAvailable = false;
    }

    private void checkForWinner() {
        if (isBlackConnectedTopToBottom()) {
            gameOver = true;
            winnerLabel.setText("BLACK wins!");
            return;
        }

        if (isWhiteConnectedLeftToRight()) {
            gameOver = true;
            winnerLabel.setText("WHITE wins!");
        }
    }

    private void switchTurn() {
        if (!gameOver) {
            isBlackTurn = !isBlackTurn;
        }
    }

    private int[] octToRowCol(int id) {
        return new int[]{(id - 1) / BOARD_SIZE, (id - 1) % BOARD_SIZE};
    }

    private int rowColToOct(int row, int col) {
        return row * BOARD_SIZE + col + 1;
    }

    private int getRhoCellID(int row1, int col1, int row2, int col2) {
        int rhoRow = Math.min(row1, row2);
        int rhoCol = Math.min(col1, col2);
        return rhoRow * RHO_SIZE + rhoCol + 1;
    }

    private boolean isCellOwnedBy(String cellId, Color color) {
        Polygon cell = cellMap.get(cellId);
        return cell != null && cell.getFill().equals(color);
    }

    public List<String> getConnectedChain(String startCellID, Color playerColor) {
        List<String> visited = new ArrayList<>();
        Queue<String> queue = new LinkedList<>();

        if (!startCellID.startsWith("OctCell") || !isCellOwnedBy(startCellID, playerColor)) {
            return visited;
        }

        queue.add(startCellID);
        visited.add(startCellID);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int id = Integer.parseInt(current.replace("OctCell", ""));
            int[] rowCol = octToRowCol(id);
            int row = rowCol[0];
            int col = rowCol[1];

            int[][] directOffsets = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
            for (int[] offset : directOffsets) {
                int nextRow = row + offset[0];
                int nextCol = col + offset[1];
                if (nextRow < 0 || nextRow > LAST_INDEX || nextCol < 0 || nextCol > LAST_INDEX) {
                    continue;
                }

                String nextId = "OctCell" + rowColToOct(nextRow, nextCol);
                if (!visited.contains(nextId) && isCellOwnedBy(nextId, playerColor)) {
                    visited.add(nextId);
                    queue.add(nextId);
                }
            }

            int[][] diagonalOffsets = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            for (int[] offset : diagonalOffsets) {
                int nextRow = row + offset[0];
                int nextCol = col + offset[1];
                if (nextRow < 0 || nextRow > LAST_INDEX || nextCol < 0 || nextCol > LAST_INDEX) {
                    continue;
                }

                int rhoId = getRhoCellID(row, col, nextRow, nextCol);
                String rhombusId = "RhoCell" + rhoId;
                String nextId = "OctCell" + rowColToOct(nextRow, nextCol);
                if (!visited.contains(nextId)
                        && isCellOwnedBy(rhombusId, playerColor)
                        && isCellOwnedBy(nextId, playerColor)) {
                    visited.add(nextId);
                    queue.add(nextId);
                }
            }
        }

        return visited;
    }

    public boolean isBlackConnectedTopToBottom() {
        for (int col = 0; col <= LAST_INDEX; col++) {
            String startId = "OctCell" + rowColToOct(0, col);
            if (!isCellOwnedBy(startId, Color.BLACK)) {
                continue;
            }

            List<String> chain = getConnectedChain(startId, Color.BLACK);
            for (String cellId : chain) {
                int id = Integer.parseInt(cellId.replace("OctCell", ""));
                int[] rowCol = octToRowCol(id);
                if (rowCol[0] == LAST_INDEX) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isWhiteConnectedLeftToRight() {
        for (int row = 0; row <= LAST_INDEX; row++) {
            String startId = "OctCell" + rowColToOct(row, 0);
            if (!isCellOwnedBy(startId, Color.WHITE)) {
                continue;
            }

            List<String> chain = getConnectedChain(startId, Color.WHITE);
            for (String cellId : chain) {
                int id = Integer.parseInt(cellId.replace("OctCell", ""));
                int[] rowCol = octToRowCol(id);
                if (rowCol[1] == LAST_INDEX) {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateTurnDisplay() {
        if (isBlackTurn) {
            turnLabel.setText("BLACK to play");
            turnOctagon.setFill(Color.BLACK);
            turnRhombus.setFill(Color.BLACK);
        } else {
            turnLabel.setText("WHITE to play");
            turnOctagon.setFill(Color.WHITE);
            turnOctagon.setStroke(Color.BLACK);
            turnRhombus.setFill(Color.WHITE);
            turnRhombus.setStroke(Color.BLACK);
        }
    }

    private void showPieButton() {
        pieRuleButton.setVisible(true);
        pieRuleButton.setDisable(false);
    }

    private void hidePieButton() {
        pieRuleButton.setVisible(false);
        pieRuleButton.setDisable(true); // test commit
    }

    @FXML
    private void onShowStrategyButton() {
        showStrategy.show();
    }

    @FXML
    private void onHideStrategyButton() {
        showStrategy.hide();
    }

    @FXML
    private void onPieRule() {
        if (!pieRuleAvailable) {
            return;
        }

        GameControl.PlayerTurn temp = player1.getPlayerColor();
        player1.setPlayerColor(player2.getPlayerColor());
        player2.setPlayerColor(temp);

        botIsBlack = !botIsBlack;
        invalidateStrategyCache();
        showStrategy.clearAnalysis();

        hidePieButton();
        pieRuleAvailable = false;

        updateTurnDisplay();

        if (isBotTurn()) {
            requestBotTurn();
        }
    }

    private void populateAllCells() {
        for (Node node : boardContainer.getChildren()) {
            if (node instanceof Polygon polygon) {
                String id = polygon.getId();
                if (id != null && (id.startsWith("OctCell") || id.startsWith("RhoCell"))) {
                    allCells.add(polygon);
                    cellMap.put(id, polygon);
                }
            }
        }
    }

    @FXML
    void initialize() {
        assert pieRuleButton != null : "fx:id=\"pieRuleButton\" was not injected: check your FXML file 'quax-view.fxml'.";
        assert showStrategyButton != null : "fx:id=\"showStrategyButton\" was not injected: check your FXML file 'quax-view.fxml'.";
        assert hideStrategyButton != null : "fx:id=\"hideStrategyButton\" was not injected: check your FXML file 'quax-view.fxml'.";

        player1 = new Player(GameControl.PlayerTurn.BLACK);
        player2 = new Player(GameControl.PlayerTurn.WHITE);

        botIsBlack = true;
        isBlackTurn = true;
        pieRuleAvailable = true;

        populateAllCells();
        showStrategy = new ShowStrategy(boardContainer, cellMap, showStrategyButton, hideStrategyButton);

        if (allCells.isEmpty()) {
            System.err.println("ERROR: no cells were injected");
        }

        updateTurnDisplay();
        javafx.application.Platform.runLater(this::requestBotTurn);
    }

    private Color getBotColour() {
        return botIsBlack ? Color.BLACK : Color.WHITE;
    }

    private void registerBoardChange() {
        boardVersion++;
        invalidateStrategyCache();
    }

    private void invalidateStrategyCache() {
        cachedStrategy = null;
        cachedStrategyVersion = -1;
        cachedStrategyColour = null;
    }

    private BotPlayer.StrategyAnalysis getStrategyAnalysisForCurrentBoard() {
        Color botColour = getBotColour();
        if (cachedStrategy != null
                && cachedStrategyVersion == boardVersion
                && Objects.equals(cachedStrategyColour, botColour)) {
            return cachedStrategy;
        }

        cachedStrategy = bot.analyseMove(allCells, botColour, this);
        cachedStrategyVersion = boardVersion;
        cachedStrategyColour = botColour;
        return cachedStrategy;
    }

    private List<int[]> getNeighbourCoordinates(int row, int col) {
        List<int[]> neighbours = new ArrayList<>();
        int[][] offsets = (row % 2 == 0 && col % 2 == 0)
                ? new int[][]{{-2, 0}, {0, -2}, {0, 2}, {2, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}}
                : new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

        for (int[] offset : offsets) {
            int nextRow = row + offset[0];
            int nextCol = col + offset[1];
            if (nextRow < 0 || nextRow > MAX_COORD || nextCol < 0 || nextCol > MAX_COORD) {
                continue;
            }
            neighbours.add(new int[]{nextRow, nextCol});
        }
        return neighbours;
    }

    String getCellIdFromCoordinate(int row, int col) {
        if (row % 2 == 0 && col % 2 == 0) {
            return "OctCell" + rowColToOct(row / 2, col / 2);
        }

        return "RhoCell" + (((row / 2) * RHO_SIZE) + (col / 2) + 1);
    }
}
