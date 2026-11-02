package sc.player;
//Moin
import org.jetbrains.annotations.NotNull;
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

/**
 * Das Herz des Clients:
 * Eine simple Logik, die zufaellige gueltige Zuege macht.
 * <p>
 * Ausserdem werden zum Spielverlauf Konsolenausgaben gemacht.
 */
public class Logic implements IGameHandler {
  private static final int MAX_MOVES = 12;
  private static final long TIME_LIMIT = 1_300_000_000L;
  private static final Logger log = LoggerFactory.getLogger(Logic.class);

  /** Aktueller Spielstatus. */
  private GameState gameState;

  /** In dieser Methode habt ihr 2 Sekunden (berechnet etwas Puffer ein) Zeit,
   * um euren nächsten Zug zu planen. */
  @NotNull
  @Override
  public Move calculateMove() {

    long deadline = System.nanoTime() + TIME_LIMIT;

    List<Move> moves = gameState.getSensibleMoves();

    if (moves.isEmpty()) {
      return null;
    }

    ITeam myTeam = gameState.getCurrentTeam();

    Move bestMove = moves.get(0);
    int bestScore = Integer.MIN_VALUE;

    // Alle möglichen Züge ausprobieren
    for (Move move : moves) {

      // Zeitlimit beachten
      if (System.nanoTime() >= deadline) {
        break;
      }

      try {
        // Spielstand kopieren
        GameState copy = gameState.clone();

        // Zug simulieren
        copy.performMoveDirectly(move);

        // Minimax mit Tiefe 1
        int score = minimax(
                gameState,
                copy,
                1,
                myTeam,
                deadline
        );

        // Besseren Zug merken
        if (score > bestScore) {
          bestScore = score;
          bestMove = move;
        }

      } catch (RuntimeException e) {
        log.error("Fehler bei der Zugsimulation: {}", e.getMessage());
      }
    }

    log.info("Gewählter Zug: {} | Bewertung: {}", bestMove, bestScore);

    return bestMove;
  }
  private int bewerten(GameState vorher, GameState nachher, ITeam myTeam) {

    ITeam enemyTeam = myTeam.equals(Team.ONE) ? Team.TWO : Team.ONE;

    int score = 0;

    // 1. Tatsächliche Spielpunkte
    int myPoints = nachher.getPointsForTeam(myTeam)[0];
    int enemyPoints = nachher.getPointsForTeam(enemyTeam)[0];

    score += 1000 * (myPoints - enemyPoints);

    // 2. Noch mögliche Züge
    // 2. Mobilität des Gegners
    score += bewerteMobilitaet(nachher, enemyTeam);

    // 3. Steinbonus
    score += steinBonus(nachher, myTeam);

    // 4. Neue Felder untersuchen
    var oldBoard = vorher.getBoard();
    var newBoard = nachher.getBoard();

    Color myColor = vorher.getCurrentColor();

    Color partnerColor;

    switch (myColor) {
      case BLUE -> partnerColor = Color.RED;
      case RED -> partnerColor = Color.BLUE;
      case YELLOW -> partnerColor = Color.GREEN;
      case GREEN -> partnerColor = Color.YELLOW;
      default -> throw new IllegalStateException("Unbekannte Farbe");
    }

    int centerScore = 0;
    int enemyContact = 0;
    int partnerContact = 0;

    int minPartnerDistance = Integer.MAX_VALUE;

    for (int x = 0; x < 20; x++) {
      for (int y = 0; y < 20; y++) {

        // Nur Felder betrachten, die gerade neu belegt wurden
        if (oldBoard.get(x, y).getContent() == FieldContent.EMPTY
                && newBoard.get(x, y).getContent() != FieldContent.EMPTY) {

          // 5. Mitte bevorzugen
          int distanceToCenter =
                  Math.abs(x - 9) + Math.abs(y - 9);

            centerScore = (int) (centerScore + Math.max(0, 10 - distanceToCenter) * 1.5);

          // 6. Nachbarfelder untersuchen
          for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {

              if (dx == 0 && dy == 0) continue;

              int nx = x + dx;
              int ny = y + dy;

              if (nx < 0 || nx >= 20 || ny < 0 || ny >= 20)
                continue;

              Color neighborColor =
                      newBoard.get(nx, ny).getContent().toTeamColor();

              if (neighborColor == null || neighborColor == myColor)
                continue;

              // Gegnerkontakt
              if (neighborColor.getTeam().equals(enemyTeam)) {
                enemyContact++;
              }

              // Partnerkontakt
              if (neighborColor == partnerColor) {
                partnerContact++;
              }
            }
          }

          // 7. Abstand zum Partner
          for (int px = 0; px < 20; px++) {
            for (int py = 0; py < 20; py++) {

              Color color =
                      oldBoard.get(px, py).getContent().toTeamColor();

              if (color == partnerColor) {

                int distance =
                        Math.abs(x - px) + Math.abs(y - py);

                minPartnerDistance =
                        Math.min(minPartnerDistance, distance);
              }
            }
          }
        }
      }
    }

    score += centerScore;
    score += enemyContact * 12;
    score += partnerContact * 2;

    // Abstand zum Partner nur in der Anfangsphase berücksichtigen
    if (vorher.getRound() <= 4 && minPartnerDistance < 6) {
      score -= (6 - minPartnerDistance) * 5;
    }

    return score;
  }
  private int bewerteMobilitaet(GameState state, ITeam enemyTeam) {

    // Ist tatsächlich der Gegner am Zug?
    if (!state.getCurrentTeam().equals(enemyTeam)) {
      return 0;
    }

    // Anzahl der legalen Züge des Gegners
    int enemyMoves = state.getSensibleMoves().size();

    // Je mehr Züge der Gegner hat, desto schlechter für uns.
    // Maximal 40 Züge werden berücksichtigt.
    return -Math.min(enemyMoves, 40) * 5;
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
        score -= shape.getSize() * 30;

        // Zusätzlicher Malus für schwierige Steine
        switch (shape) {
          // Besonders schwierig zu platzieren
          case PENTO_X -> score -= 120;
          // Eher schwierige Pentominos
          case PENTO_P, PENTO_W, PENTO_Y, PENTO_U -> score -= 70;
          // Ebenfalls etwas schwieriger
          case PENTO_I, PENTO_T -> score -= 50;
          case MONO -> score+=20;
          case DOMINO -> score+=10;

          // Für alle anderen Steine bleibt es beim Grundwert
          default -> {
          }
        }
      }
    }

    return score;
  }

  private int minimax(
          GameState vorher,
          GameState nachher,
          int depth,
          ITeam myTeam,
          long deadline) {

    // Abbruch, wenn die gewünschte Tiefe erreicht ist
    // oder die verfügbare Zeit abgelaufen ist
    if (depth == 0 || System.nanoTime() >= deadline) {
      return bewerten(vorher, nachher, myTeam);
    }

    return bewerten(vorher, nachher, myTeam);
  }

  /** Ein neuer Spielstatus ist verfügbar, d.h. ein Zug wurde erfolgreich ausgeführt. */
  @Override
  public void onUpdate(IGameState gameState) {
    this.gameState = (GameState) gameState;
    log.info("Zug: {} Dran: {}", gameState.getTurn(), gameState.getCurrentTeam());
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
