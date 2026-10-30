package sc.player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sc.api.plugins.IGameState;
import sc.plugin2027.Move;
import sc.plugin2027.GameState;
import sc.shared.GameResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import java.util.List;

public class Test_bot {
    public final class MinimaxBot<S, M> {
        public interface GameAdapter<S, M> {

            // Liefert alle legalen Züge des aktuellen Spielers.
            List<M> getLegalMoves(S state);

            // Erstellt den Folgezustand nach einem Zug.
            S applyMove(S state, M move);

            // Liefert die Team-ID des Spielers am Zug.
            int getCurrentTeam(S state);

            // Liefert true, wenn das Spiel beendet ist.
            boolean isTerminal(S state);

            // Bewertet einen Zustand aus Sicht des angegebenen Teams.
            int evaluate(S state, int rootTeam);
        }

        private final GameAdapter<S, M> game;
        private final int maxDepth;
        private final int beamWidth;

        public MinimaxBot(
                GameAdapter<S, M> game,
                int maxDepth,
                int beamWidth
        ) {
            if (game == null) {
                throw new IllegalArgumentException(
                        "Der GameAdapter darf nicht null sein."
                );
            }

            if (maxDepth < 1) {
                throw new IllegalArgumentException(
                        "Die Suchtiefe muss mindestens 1 sein."
                );
            }

            if (beamWidth < 1) {
                throw new IllegalArgumentException(
                        "Die Kandidatenanzahl muss mindestens 1 sein."
                );
            }

            this.game = game;
            this.maxDepth = maxDepth;
            this.beamWidth = beamWidth;
        }

        /**
         * Ermittelt den besten Zug für den Spieler am Zug.
         */
        public M chooseMove(S state) {
            if (state == null) {
                throw new IllegalArgumentException(
                        "Der Spielzustand darf nicht null sein."
                );
            }

            if (game.isTerminal(state)) {
                throw new IllegalStateException(
                        "Das Spiel ist bereits beendet."
                );
            }

            int rootTeam = game.getCurrentTeam(state);

            List<M> moves = game.getLegalMoves(state);

            if (moves == null || moves.isEmpty()) {
                throw new IllegalStateException(
                        "Es wurden keine legalen Züge geliefert."
                );
            }

            M bestMove = null;
            int bestScore = Integer.MIN_VALUE;
            int alpha = Integer.MIN_VALUE;
            int beta = Integer.MAX_VALUE;

            for (M move : orderMoves(state, moves, rootTeam, true)) {

                S nextState = game.applyMove(state, move);

                int score = minimax(
                        nextState,
                        maxDepth - 1,
                        rootTeam,
                        alpha,
                        beta
                );

                if (bestMove == null || score > bestScore) {
                    bestMove = move;
                    bestScore = score;
                }

                alpha = Math.max(alpha, bestScore);
            }

            return bestMove;
        }

        /**
         * Rekursive Minimax-Suche mit Alpha-Beta-Pruning.
         */
        private int minimax(
                S state,
                int depth,
                int rootTeam,
                int alpha,
                int beta
        ) {
            if (depth <= 0 || game.isTerminal(state)) {
                return game.evaluate(state, rootTeam);
            }

            List<M> moves = game.getLegalMoves(state);

            if (moves == null || moves.isEmpty()) {
                return game.evaluate(state, rootTeam);
            }

            boolean maximizing =
                    game.getCurrentTeam(state) == rootTeam;

            int bestScore = maximizing
                    ? Integer.MIN_VALUE
                    : Integer.MAX_VALUE;

            List<M> orderedMoves =
                    orderMoves(state, moves, rootTeam, maximizing);

            for (M move : orderedMoves) {

                S nextState = game.applyMove(state, move);

                int score = minimax(
                        nextState,
                        depth - 1,
                        rootTeam,
                        alpha,
                        beta
                );

                if (maximizing) {
                    bestScore = Math.max(bestScore, score);
                    alpha = Math.max(alpha, bestScore);
                } else {
                    bestScore = Math.min(bestScore, score);
                    beta = Math.min(beta, bestScore);
                }

                if (alpha >= beta) {
                    break;
                }
            }

            return bestScore;
        }

        /**
         * Sortiert Züge nach einer vorläufigen Bewertung.
         *
         * Dadurch untersucht Minimax vielversprechende Züge zuerst.
         * Die Kandidatenbegrenzung ist eine Optimierung und kann
         * verhindern, dass der tatsächlich beste Zug untersucht wird.
         */
        private List<M> orderMoves(
                S state,
                List<M> moves,
                int rootTeam,
                boolean maximizing
        ) {
            List<ScoredMove<M>> scoredMoves = new ArrayList<>();

            for (M move : moves) {
                S nextState = game.applyMove(state, move);

                int score = game.evaluate(nextState, rootTeam);

                scoredMoves.add(new ScoredMove<>(move, score));
            }

            Comparator<ScoredMove<M>> comparator =
                    Comparator.comparingInt(ScoredMove::score);

            if (maximizing) {
                comparator = comparator.reversed();
            }

            scoredMoves.sort(comparator);

            List<M> result = new ArrayList<>();

            int count = Math.min(beamWidth, scoredMoves.size());

            for (int i = 0; i < count; i++) {
                result.add(scoredMoves.get(i).move());
            }

            return result;
        }

        private record ScoredMove<M>(M move, int score) {
        }
    }
}
