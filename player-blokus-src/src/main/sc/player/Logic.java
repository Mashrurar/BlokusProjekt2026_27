package sc.player;
//Moin

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.api.plugins.IGameState;
import sc.plugin2027.Color;
import sc.plugin2027.Move;
import sc.plugin2027.GameState;
import sc.plugin2027.PieceShape;
import sc.shared.GameResult;

import java.util.List;
import sc.api.plugins.ITeam;
import sc.api.plugins.Team;
import sc.plugin2027.FieldContent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Herz des Clients:
 * Eine simple Logik, die zufaellige gueltige Zuege macht.
 * <p>
 * Ausserdem werden zum Spielverlauf Konsolenausgaben gemacht.
 */
public class Logic implements IGameHandler {
  private static final int MAX_MOVES = 12;
  private static final long TIME_LIMIT = 1_500_000_000L;
  private static final Logger log = LoggerFactory.getLogger(Logic.class);
  private volatile GameState gameState;
  /** Aktueller Spielstatus. */

  /** In dieser Methode habt ihr 2 Sekunden (berechnet etwas Puffer ein) Zeit,
   * um euren nächsten Zug zu planen. */

  @Override
  public Move calculateMove() {
    GameState state = this.gameState;
    if (state == null) return null;

    long deadline = System.nanoTime() + TIME_LIMIT;

    List<Move> moves = state.getSensibleMoves();
    if (moves.isEmpty()) return null;

    ITeam myTeam = state.getCurrentTeam();

    // STUFE 1: Schnelle Vorauswahl
    List<Move> kandidaten = new ArrayList<>();

    Map<Move, Integer> bewertungen = new HashMap<>();

    for (Move move : moves) {
      if (System.nanoTime() >= deadline - 500_000_000L) {
        break;
      }

      try {
        GameState copy = state.clone();
        copy.performMoveDirectly(move);

        int score = schnellBewerten(copy, myTeam);

        bewertungen.put(move, score);
        kandidaten.add(move);

      } catch (RuntimeException e) {
        log.error("Fehler bei Vorauswahl: {}", e.getMessage());
      }
    }

    if (kandidaten.isEmpty()) {
      return moves.get(0);
    }

    kandidaten.sort((a, b) ->
            Integer.compare(bewertungen.get(b), bewertungen.get(a)));

    int anzahl = Math.min(7, kandidaten.size());

    List<Move> besteZuege = new ArrayList<>(
            kandidaten.subList(0, anzahl)
    );

    // STUFE 2: Minimax für die zehn besten Züge
    Move bestMove = besteZuege.get(0);
    int bestScore = Integer.MIN_VALUE;
    int untersucht = 0;

    for (Move move : besteZuege) {
      if (System.nanoTime() >= deadline) break;

      try {
        GameState copy = state.clone();
        copy.performMoveDirectly(move);

        int score = minimax(
                state,
                copy,
                2,
                myTeam,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                deadline
        );

        if (score > bestScore) {
          bestScore = score;
          bestMove = move;
        }

        untersucht++;

      } catch (RuntimeException e) {
        log.error("Fehler bei Minimax: {}", e.getMessage());
      }
    }

    log.info(
            "Gewählter Zug: {} | Bewertung: {} | Legale Züge: {} | Vorauswahl: {} | Minimax untersucht: {}",
            bestMove, bestScore, moves.size(), anzahl, untersucht
    );

    return bestMove;
  }
  private int schnellBewerten(GameState state, ITeam myTeam) {
    int score = 0;

    // 1. Zentrum
    var board = state.getBoard();

    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 20; y++) {
        Color color = board.get(x, y).getContent().toTeamColor();

        if (color == null) continue;

        int distance = Math.abs(x - 9) + Math.abs(y - 9);
        int centerValue = Math.max(0, 10 - distance);

        if (color.getTeam().equals(myTeam)) {
          score += centerValue;
        } else {
          score -= centerValue;
        }
      }
    }

    // 2. Steinbonus
    score += steinBonus(state, myTeam);
    score -= steinBonus(state,
            myTeam.equals(Team.ONE) ? Team.TWO : Team.ONE);

    return score;
  }
  private int bewerten(GameState state, ITeam myTeam) {

    ITeam enemyTeam = myTeam.equals(Team.ONE)
            ? Team.TWO : Team.ONE;

    int score = 0;

    // 1. Tatsächliche Spielpunkte
    int myPoints = state.getPointsForTeam(myTeam)[0];
    int enemyPoints = state.getPointsForTeam(enemyTeam)[0];

    score += 100 * (myPoints - enemyPoints);

    // 2. Noch übrige Steine
    score += steinBonus(state, myTeam);
    score -= steinBonus(state, enemyTeam);

    // 3. Brettmitte kontrollieren
    var board = state.getBoard();

    int myCenter = 0;
    int enemyCenter = 0;

    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 20; y++) {

        Color color = board.get(x, y).getContent().toTeamColor();
        if (color == null) {
          continue;
        }
        int distance = Math.abs(x - 9)
                + Math.abs(y - 9);

        int centerValue = Math.max(0, 10 - distance);

        if (color.getTeam().equals(myTeam)) {
          myCenter += centerValue;
        } else {
          enemyCenter += centerValue;
        }
      }
    }

    score += 6 * (myCenter - enemyCenter);

    // 4. Zukunftsmöglichkeiten und diagonale Anlegestellen
    int myFrontier = 0;
    int enemyFrontier = 0;

    for (Color color : farbenFuerTeam(myTeam)) {
      myFrontier += Math.min(zaehleAnkerfelder(state, color), 35);
    }

    for (Color color : farbenFuerTeam(enemyTeam)) {
      enemyFrontier += Math.min(zaehleAnkerfelder(state, color), 35);
    }
    // Gute eigene Anschlussmöglichkeiten sind wichtig.
    // Viele gegnerische Anschlussmöglichkeiten sind schlecht.
    score += 5 * (myFrontier - enemyFrontier);

    // Eigene Anlegestellen in der Nähe des Gegners belohnen.
    int myPressure = 0;
    int enemyPressure = 0;
    for (Color color : farbenFuerTeam(myTeam)) {
      myPressure += Math.min(zaehleNaheAnkerfelder(state, color, enemyTeam), 15);
    }
    for (Color color : farbenFuerTeam(enemyTeam)) {
      enemyPressure += Math.min(zaehleNaheAnkerfelder(state, color, myTeam), 15);
    }
    score += 2 * (myPressure - enemyPressure);
    // 6. Bewegungsfreiheit des aktiven Spielers
    score += bewerteMobilitaet(state, myTeam);

    return score;
  }
  private Color[] farbenFuerTeam(ITeam team) {
    if (team.equals(Team.ONE)) {
      return new Color[]{Color.BLUE, Color.RED};
    }
    return new Color[]{Color.YELLOW, Color.GREEN};
  }
  private Color farbeBei(GameState state, int x, int y) {
    if (x < 0 || x >= 20 || y < 0 || y >= 20) {
      return null;
    }
    return state.getBoard().get(x, y).getContent().toTeamColor();
  }

  private boolean istAnkerfeld(GameState state,
          int x, int y, Color color) {

    // Das Feld muss leer sein.
    if (farbeBei(state, x, y) != null) {
      return false;
    }
    // Eigene Steine dürfen nicht direkt aneinandergrenzen.
    int[][] gerade = {
            {0, -1},
            {0, 1},
            {-1, 0},
            {1, 0}
    };

    for (int[] richtung : gerade) {

      Color nachbar = farbeBei(
              state,
              x + richtung[0],
              y + richtung[1]
      );

      if (color.equals(nachbar)) {
        return false;
      }
    }
    // Es muss mindestens einen diagonalen Kontakt geben.
    int[][] diagonal = {
            {-1, -1},
            {-1, 1},
            {1, -1},
            {1, 1}
    };
    for (int[] richtung : diagonal) {

      Color nachbar = farbeBei(
              state,
              x + richtung[0],
              y + richtung[1]
      );

      if (color.equals(nachbar)) {
        return true;
      }
    }
    return false;
  }

  private int zaehleAnkerfelder(GameState state, Color color) {
    int count = 0;
    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 20; y++) {

        if (istAnkerfeld(state, x, y, color)) {
          count++;
        }
      }
    }

    return count;
  }

  private int zaehleNaheAnkerfelder(GameState state,
                                    Color color, ITeam enemyTeam) {
    int count = 0;
    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 20; y++) {
        if (!istAnkerfeld(state, x, y, color)) {
          continue;
        }
        boolean nahe = false;
        for (int dx = -2; dx <= 2 && !nahe; dx++) {
          for (int dy = -2; dy <= 2 && !nahe; dy++) {

            if (dx == 0 && dy == 0) {
              continue;
            }
            Color neighbor = farbeBei(
                    state,
                    x + dx,
                    y + dy
            );

            if (neighbor != null
                    && neighbor.getTeam().equals(enemyTeam)) {
              nahe = true;
            }
          }
        }
        if (nahe) {
          count++;
        }
      }
    }

    return count;
  }
  private int bewerteMobilitaet(GameState state, ITeam myTeam) {

    ITeam currentTeam = state.getCurrentTeam();

    int moves = state.getSensibleMoves().size();

    moves = Math.min(moves, 60);

    if (currentTeam.equals(myTeam)) {
      return moves * 4;
    }

    return -moves * 3;
  }
  private int steinBonus(GameState state, ITeam myTeam) {
    int score = 0;
    // Beide Farben des eigenen Teams bestimmen
    Color[] myColors;

    if (myTeam.equals(Team.ONE)) {
      myColors = new Color[]{Color.BLUE, Color.RED};
    } else {
      myColors = new Color[]{Color.YELLOW, Color.GREEN};
    }

    // Alle noch nicht verwendeten Steine beider Farben bewerten
    for (Color color : myColors) {

      for (PieceShape shape : state.undeployedPieceShapes(color)) {
        // Grundwert: Große Steine sollen möglichst früh gelegt werden
        score -= shape.getSize() * 40 +30;

        // Zusätzlicher Malus für schwierige Steine
        switch (shape) {
          // Besonders schwierig zu platzieren
          case PENTO_X -> score -= 130;
          // Eher schwierige Pentominos
          case PENTO_P, PENTO_W, PENTO_Y, PENTO_U -> score -= 90;
          // Ebenfalls etwas schwieriger
          case PENTO_T -> score -= 70;
          case PENTO_I  -> score -= 50;
          case MONO -> score+=100;
          case DOMINO -> score+=30;

          // Für alle anderen Steine bleibt es beim Grundwert
          default -> {
          }
        }
      }
    }

    return score;
  }

  private int minimax(GameState basis, GameState state, int depth,
          ITeam myTeam, int alpha, int beta, long deadline) {
    // Abbruchbedingungen
    if (depth == 0 || state.isOver() || System.nanoTime() >= deadline) {
      return bewerten(state, myTeam);
    }
    List<Move> moves = state.getSensibleMoves();
    // Keine legalen Züge mehr
    if (moves.isEmpty()) {
      return bewerten(state, myTeam);
    }
    // Ist mein Team gerade am Zug?
    boolean maximizing = state.getCurrentTeam().equals(myTeam);

    int bestScore;

    if (maximizing) {
      bestScore = Integer.MIN_VALUE;
    } else {
      bestScore = Integer.MAX_VALUE;
    }
    boolean searchedAny = false;
    for (Move move : moves) {

      // Zeitlimit überprüfen
      if (System.nanoTime() >= deadline) {
        break;
      }
      // Spielstand kopieren
      GameState child = state.clone();
      try {
        child.performMoveDirectly(move);
      } catch (Throwable e) {
        log.error("Fehler bei simuliertem Zug: " + move, e);
        throw e;
      }

      // Nächste Ebene berechnen
      int score = minimax(basis, child, depth - 1,
              myTeam, alpha, beta, deadline);
      if (maximizing) {
        //Mein Team möchte den Wert maximieren
        bestScore = Math.max(bestScore, score);
        alpha = Math.max(alpha, bestScore);

      } else {
        // Gegner möchte meinen Wert minimieren
        bestScore = Math.min(bestScore, score);
        beta = Math.min(beta, bestScore);
      }
      searchedAny = true;
      // Alpha-Beta-Abbruch
      if (beta <= alpha) {
        break;
      }
    }
    if (!searchedAny) {
      return bewerten(state, myTeam);
    }
    return bestScore;
  }

  /** Ein neuer Spielstatus ist verfügbar, d.h. ein Zug wurde erfolgreich ausgeführt. */
//  @Override
//  public void onUpdate(IGameState gameState) {
//    this.gameState = (GameState) gameState;
//    log.info("Zug: {} Dran: {}", gameState.getTurn(), gameState.getCurrentTeam());
//  }
  @Override
  public void onUpdate(IGameState gameState) {

    GameState newState = (GameState) gameState;

    this.gameState = newState;

    log.info(
            "UPDATE: Zug={}, Team={}, Farbe={}",
            newState.getTurn(),
            newState.getCurrentTeam(),
            newState.getCurrentColor()
    );
  }

  /** Wird aufgerufen, wenn das Spiel beendet ist. */
  public void onGameOver(GameResult data) {
    log.info("Das Spiel ist beendet, Ergebnis: {}", data);
  }

  /** Wird aufgerufen, wenn der Server einen Fehler meldet.
   * Bedeutet auch den Abbruch des Spiels. */
  @Override
  public void onError(String error) {
    log.warn("Fehler: {}", error);
  }
}
