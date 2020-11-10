// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

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
  private final TreeMap<Integer, Strategy> nextMoves = new TreeMap<>();

  // These extra members are to allow us to build the strategy lazily, as we play games using any
  // algorithm. Some of these are for specific algorithms only.
  private final ArrayList<Codeword> possibleSolutions;
  private final ArrayList<Codeword> remainingCodewords;

  public Strategy(Codeword guess, ArrayList<Codeword> possibleSolutions,
      ArrayList<Codeword> remainingCodewords) {
    this.guess = guess;
    this.possibleSolutions = possibleSolutions;
    this.remainingCodewords = remainingCodewords;
  }

  public Strategy addMove(int score, Codeword nextGuess, ArrayList<Codeword> possibleSolutions,
      ArrayList<Codeword> remainingCodewords) {
    Strategy n = new Strategy(nextGuess, possibleSolutions, remainingCodewords);
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

  public ArrayList<Codeword> getRemainingCodewords() {
    return remainingCodewords;
  }

  // Output the strategy for visualization with GraphViz. Copy-and-paste the output file to sites
  // like https://dreampuf.github.io/GraphvizOnline or http://www.webgraphviz.com/. Or install
  // GraphViz locally and run with the following command:
  //
  //   twopi -Tjpg mastermind_strategy_4p6c.gv > mastermind_strategy_4p6c.jpg
  //
  // Parameters for the graph are currently set to convey the point while being reasonably readable
  // in a large JPG.
  public static void dump(Strategy root) {
    String filename = String
        .format("mastermind_strategy_%dp%dc.gv", Mastermind.pinCount, Mastermind.colorCount);
    System.out.println("\nWriting strategy to " + filename);
    try {
      FileWriter fw = new FileWriter(filename);
      fw.write(String.format("digraph Mastermind_Strategy_%dp%dc{\n", Mastermind.pinCount,
          Mastermind.colorCount));
      fw.write("size=\"40,40\"\n"); // Good size for jpgs
      fw.write("overlap=true\n"); // scale is cool, but the result is unreadable
      fw.write("ranksep=5\n");
      fw.write("ordering=out\n");
      fw.write("node [shape=plaintext]\n");
      root.dumpRoot(fw);
      fw.write("}");
      fw.close();
    } catch (Exception e) {
      System.out.println(e);
    }
  }

  private void dumpRoot(FileWriter fw) throws IOException {
    fw.write("root=" + hashCode() + "\n");
    fw.write(String.format("%s [label=\"%s - %d\",shape=circle,color=red]\n", hashCode(), guess,
        possibleSolutions.size()));
    dumpChildren(fw);
  }

  private void dump(FileWriter fw) throws IOException {
    if (possibleSolutions.size() > 0) {
      fw.write(String
          .format("%s [label=\"%s - %d\"]\n", hashCode(), guess, possibleSolutions.size() + 1));
    } else {
      fw.write(String.format("%s [label=\"%s\",fontcolor=green,style=bold]\n", hashCode(), guess));
    }
    dumpChildren(fw);
  }

  private void dumpChildren(FileWriter fw) throws IOException {
    for (Map.Entry<Integer, Strategy> m : nextMoves.descendingMap().entrySet()) {
      m.getValue().dump(fw);
      fw.write(String
          .format("%s -> %s [label=\"%02d\"]\n", hashCode(), m.getValue().hashCode(), m.getKey()));
    }
  }
}
