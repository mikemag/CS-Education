// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.util.ArrayList;
import java.util.HashMap;

// Gameplay Strategy
//
// This is used to build a tree of plays to make based on previous plays and results. All games
// start with the same guess, which makes the root of the tree. The score received is used to find
// what to play next via the nextMoves map. If there is no entry in the map, then the gameplay
// engine will do whatever work is necessary (possibly large) to find the next play, then add it to
// the tree. As games are played, the tree gets filled in and playtime decreases.
public class Strategy {

  // The strategy is made up of the next guess to play, and a map of where to go based on the result
  // of that play.
  private final Codeword guess;
  private final HashMap<Integer, Strategy> nextMoves = new HashMap<>();

  // These extra members are to allow us to build the strategy lazily, as we play games using any
  // algorithm.
  private final ArrayList<Codeword> possibleSolutions;
  private final ArrayList<Codeword> unguessedCodewords;

  public Strategy(Codeword guess, ArrayList<Codeword> possibleSolutions,
      ArrayList<Codeword> unguessedCodewords) {
    this.guess = guess;
    this.possibleSolutions = possibleSolutions;
    this.unguessedCodewords = unguessedCodewords;
  }

  public Strategy addMove(int score, Codeword nextGuess, ArrayList<Codeword> possibleSolutions,
      ArrayList<Codeword> unguessedCodewords) {
    Strategy n = new Strategy(nextGuess, possibleSolutions, unguessedCodewords);
    nextMoves.put(score, n);
    return n;
  }

  public Strategy getNextMove(int score) {
    return nextMoves.get(score);
  }

  public Codeword getGuess() {
    return guess;
  }

  public ArrayList<Codeword> getPossibleSolutions() {
    return possibleSolutions;
  }

  public ArrayList<Codeword> getUnguessedCodewords() {
    return unguessedCodewords;
  }
}
