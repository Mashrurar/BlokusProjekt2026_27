package sc.player;
//Moin
import kotlin.TuplesKt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.api.plugins.IGameState;
import sc.plugin2027.Move;
import sc.plugin2027.GameState;
import sc.shared.GameResult;

import java.util.List;
import sc.api.plugins.ITeam;
import sc.api.plugins.Team;

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
  @Override
  public Move calculateMove() {

    long deadline = System.nanoTime() + TIME_LIMIT;

    List<Move> moves = gameState.getSensibleMoves();

    if (moves.isEmpty()) {
      return null;
    }

    // Sicherer Standardzug
    Move bestMove = moves.get(0);

    ITeam myTeam = gameState.getCurrentTeam();

    int bestScore = Integer.MIN_VALUE;

    // Zunächst höchstens 12 Züge untersuchen
    int limit = Math.min(moves.size(), MAX_MOVES);

    for (int i = 0; i < limit; i++) {

      if (System.nanoTime() >= deadline) {
        break;
      }

      Move move = moves.get(i);

      try {

        GameState copy = gameState.clone();
        copy.performMoveDirectly(move);

        int score = bewerten(copy, myTeam);


      } catch (NoSuchMethodError e) {

        log.error("Fehler bei der Zugsimulation!", e);

        // Zur Sicherheit einen gültigen Zug zurückgeben
        return bestMove;
      }
    }

    log.info("Gewählter Zug: {} | Bewertung: {}", bestMove, bestScore);

    return bestMove;
  }
  private int bewerten(GameState state, ITeam myTeam) {

    ITeam enemyTeam =
            myTeam.equals(Team.ONE)
                    ? Team.TWO
                    : Team.ONE;

    int myPoints = state.getPointsForTeam(myTeam)[0];
    int enemyPoints = state.getPointsForTeam(enemyTeam)[0];

    int score = 0;

    // Teamführung
    score += 1000 * (myPoints - enemyPoints);

    // Mobilität
    score += 25 * state.getSensibleMoves().size();

    return score;
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
