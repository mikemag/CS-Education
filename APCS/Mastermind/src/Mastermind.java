// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Random;

// Mastermind
//
// Play the game Mastermind, which is to guess a sequence of colors (the "secret") with feedback 
// about how many you have guessed correctly, and how many are in the right place.
//
// This will play the game for every possible secret and tell us the average and maximum number of 
// tries needed across all of them.
//
// Move results are in the form of a two-digit number. The first digit is the number of colors in
// the guess which are correct, and in the correct position. The second digit is the number of
// colors in the guess which are correct, but in the wrong position.
//
// E.g.: 21 -- 2 correct in the right place, and 1 correct but in the wrong place. 
//
// There are a few algorithms to play with.
//
// Knuth's algorithm for this is quite good, if a bit hard to understand at first:
// http://www.cs.uni.edu/~wallingf/teaching/cs3530/resources/knuth-mastermind.pdf Also see the
// summary of it on https://en.wikipedia.org/wiki/Mastermind_(board_game)
//
// Recent survey of the space: https://arxiv.org/pdf/1305.1010.pdf

public class Mastermind {

  public static final int pinCount = 4; // 1-15, 4 is classic
  public static final int colorCount = 6; // 1-15, 6 is classic

  // Timings taken on a MacBook Pro (16-inch, 2019), macOS Ventura 13.6 (22G120), Intel(R)
  // Core(TM) i9-9980HK CPU @ 2.40GHz (boost to 5.0GHz), openjdk 14.0.2 2020-07-14

  enum Algo {
    // Pick the first of the remaining choices.
    // 46,409 comps, 5.0216 avg turns, 8 turns max, ~0.1313s
    FirstOne,

    // Pick any of the remaining choices.
    // ~48k comps, ~4.6-4.7 avg turns, 7 turns max, ~0.1398s
    Random,

    // Pick the one that will eliminate the most remaining choices.
    // 4,248,326 comps, 4.4761 avg turns, 5 turns max, ~0.3195s
    Knuth
  }

  // Pick which algo to run. If I was cool I'd either make this a command line arg, or have it run
  // through all of them.
  private static final Algo algo = Algo.Knuth;

  private static long scoreCounter = 0;
  private static final Random rand = new Random();

  // Make a list of all codewords for a given number of "colors". Colors are represented by the
  // digits 1 through n. This figures out how many codewords there are, which is colorCount ^ pinCount,
  // then converts the base-10 number of each codeword to it's base-colorCount representation.
  private static ArrayList<Codeword> allCodewords = null;

  private static ArrayList<Codeword> makeAllCodewords() {
    if (allCodewords == null) {
      int totalWords = (int) Math.pow(colorCount, pinCount);
      ArrayList<Codeword> l = new ArrayList<>(totalWords);

      for (int i = 0; i < totalWords; i++) {
        int w = i;
        byte[] digits = new byte[pinCount];
        int di = pinCount - 1;
        do {
          digits[di--] = (byte) (w % colorCount);
          w /= colorCount;
        } while (w > 0);

        // Colors start at 1, not 0.
        for (di = 0; di < pinCount; di++) {
          digits[di] += 1;
        }

        l.add(new Codeword(digits));
      }
      allCodewords = l;
    }

    // This is cached and copied, so we save time on each play of the game.
    return new ArrayList<>(allCodewords);
  }

  // Knuth's initial guess for 4-pin 6-color Mastermind is 1122. Generalize this to any pin count
  // by using half 1's and half 2's.
  private static Codeword knuthInitialGuess;

  private static Codeword getKnuthInitialGuess() {
    if (knuthInitialGuess == null) {
      byte[] digits = new byte[pinCount];
      for (int i = 0; i < pinCount; i++) {
        digits[i] = (i < pinCount / 2) ? (byte) 1 : 2;
      }
      knuthInitialGuess = new Codeword(digits);
    }

    return knuthInitialGuess;
  }

  // The core of Knuth's algorithm: find the remaining solution which will eliminate the most
  // possibilities on the next round, favoring, but not requiring, any choice which may still be the
  // final answer.
  private static final int[] scoreCounts = new int[Codeword.winningScore + 1];

  private static Codeword findKnuthGuess(ArrayList<Codeword> allCodewords,
      ArrayList<Codeword> possibleSolutions, PrintStream p) {

    Codeword bestGuess = null;
    int bestWorstCase = Integer.MAX_VALUE;
    boolean bestIsPossibleSolution = false;
    for (Codeword g : allCodewords) {
      // Compute a score for this guess based on how many possible solutions it will remove.
      int maxScoreCount = 0;
      boolean isPossibleSolution = false;
      for (Codeword possibleSolution : possibleSolutions) {
        int s = g.score(possibleSolution);
        scoreCounter++;
        scoreCounts[s]++;
        if (s == Codeword.winningScore) {
          isPossibleSolution = true; // Remember if this guess is in the set of possible solutions
        }
      }

      for (int i = 0; i < scoreCounts.length; i++) {
        if (scoreCounts[i] > maxScoreCount) {
          maxScoreCount = scoreCounts[i];
        }
        scoreCounts[i] = 0; // Reset the storage to 0 as we go
      }

      if (maxScoreCount < bestWorstCase) {
        bestWorstCase = maxScoreCount;
        bestGuess = g;
        bestIsPossibleSolution = isPossibleSolution;
      } else if (!bestIsPossibleSolution && isPossibleSolution && maxScoreCount == bestWorstCase) {
        bestGuess = g;
        bestIsPossibleSolution = true;
      }
    }

    p.println(
        "Selecting Knuth's best guess: " + bestGuess + "\tworst case PS size: " + bestWorstCase);
    return bestGuess;
  }

  // This is the gameplay strategy we build up as we play. There are a lot of common plays, and this
  // allows us to reuse them almost instantly for greatly increased speed. This is optional.
  private static final boolean useStrategy = true;
  private static Strategy gameStrategy = null;

  // Play the game to find the given secret codeword and return how many turns it took.
  private static int findSecret(Codeword secret, PrintStream p) throws Exception {
    ArrayList<Codeword> allCodewords = makeAllCodewords();
    ArrayList<Codeword> possibleSolutions;

    // Note: if we're not building a global gameplay strategy as we go, then we start each game
    // fresh with a new set of codewords.
    if (gameStrategy == null || !useStrategy) {
      possibleSolutions = new ArrayList<>(allCodewords);

      // Start w/ Knuth's first guess for all algorithms.
      gameStrategy = new Strategy(getKnuthInitialGuess(), possibleSolutions);
    }

    Strategy strategy = gameStrategy;
    Codeword guess = strategy.getGuess();
    possibleSolutions = strategy.getPossibleSolutions();

    p.println("Starting with secret " + secret);
    p.format("Solution space contains %d possibilities.\n", possibleSolutions.size());
    p.println("Initial guess is " + guess);

    int turns = 0;

    while (true) {
      byte s = secret.score(guess); // Is our guess the winner?
      scoreCounter++;
      p.println(
          "\nTried guess " + guess + " against secret " + secret + " => " + Integer.toHexString(s));
      turns++;

      if (s == Codeword.winningScore) {
        p.format("Solution found after %d tries\n", turns);
        break;
      }

      // Try to pull the next move from the strategy we're building, and use that when available.
      if (useStrategy) {
        Strategy nextMove = strategy.getNextMove(s);
        if (nextMove != null) {
          strategy = nextMove;
          guess = strategy.getGuess();
          p.println("Using next guess from strategy: " + guess);
          p.format("Solution space now contains %d possibilities.\n",
              strategy.getPossibleSolutions().size() + 1);
          continue;
        }

        // The strategy holds data structures needed by various algorithms which are used as a
        // starting point for multiple next moves. Thus, we copy them to ensure they remain a stable
        // starting point for other moves.
        possibleSolutions = new ArrayList<>(strategy.getPossibleSolutions());
      }

      // "5. Otherwise, remove from S any code that would not give the same response if it (the
      // guess) were the code (secret)." -- from the description of Knuth's algorithm at
      // https://en.wikipedia.org/wiki/Mastermind_(board_game)
      //
      // This describes something common to all good solutions: since the scoring function is
      // commutative, and since we know the secret remains in our set of possible solutions, we can
      // quickly eliminate lots and lots of solutions on every iteration.
      p.println("Removing solutions that have no chance of being correct...");
      final Codeword g = guess;
      possibleSolutions.removeIf(c -> {
        scoreCounter++;
        return c.score(g) != s;
      });
      p.format("Solution space now contains %d possibilities.\n", possibleSolutions.size());

      if (possibleSolutions.isEmpty()) {
        // This is only possible if there is a bug in our scoring function.
        throw new Exception("Failed to find solution with secret " + secret);
      } else if (possibleSolutions.size() == 1) {
        guess = possibleSolutions.get(0);
        p.println("Only remaining possibility must be correct: " + guess);
      } else if (possibleSolutions.size() == 2) {
        guess = possibleSolutions.get(0); // Fun to work out why this is correct.
        p.println("Selecting first of the last two remaining: " + guess);
      } else if (algo == Algo.FirstOne) {
        guess = possibleSolutions.get(0);
        p.println("Selecting the first possibility blindly: " + guess);
      } else if (algo == Algo.Random) {
        guess = possibleSolutions.get(rand.nextInt(possibleSolutions.size()));
        p.println("Selecting a random possibility: " + guess);
      } else if (algo == Algo.Knuth) {
        guess = findKnuthGuess(allCodewords, possibleSolutions, p);
      }

      if (useStrategy) {
        strategy = strategy.addMove(s, guess, possibleSolutions);
      }
    }

    p.println("Done with secret " + secret + "\n");

    return turns;
  }

  public static void main(String[] args) {
    try {
      if (pinCount == 4) {
        // Test cases from Miyoshi
        Codeword testSecret = new Codeword(new byte[]{6, 6, 8, 4});
        boolean success = true;
        success &= (testSecret.score(new Codeword(new byte[]{0, 0, 0, 0})) == 0x00);
        success &= (testSecret.score(new Codeword(new byte[]{6, 6, 6, 6})) == 0x20);
        success &= (testSecret.score(new Codeword(new byte[]{0, 1, 2, 3})) == 0x00);
        success &= (testSecret.score(new Codeword(new byte[]{4, 5, 6, 7})) == 0x02);
        success &= (testSecret.score(new Codeword(new byte[]{4, 5, 8, 9})) == 0x11);
        success &= (testSecret.score(new Codeword(new byte[]{6, 7, 0, 0})) == 0x10);
        success &= (testSecret.score(new Codeword(new byte[]{0, 7, 9, 8})) == 0x01);
        success &= (testSecret.score(new Codeword(new byte[]{6, 4, 8, 4})) == 0x30);
        success &= (testSecret.score(new Codeword(new byte[]{6, 4, 8, 0})) == 0x21);
        success &= (testSecret.score(new Codeword(new byte[]{6, 8, 8, 4})) == 0x30);
        success &= (testSecret.score(new Codeword(new byte[]{6, 6, 8, 4})) == 0x40);

        // Three extra tests to detect subtly broken scoring functions.
        success &= (testSecret.score(new Codeword(new byte[]{8, 4, 6, 8})) == 0x03);
        success &= (testSecret.score(new Codeword(new byte[]{8, 8, 6, 6})) == 0x03);
        success &= (testSecret.score(new Codeword(new byte[]{8, 4, 6, 6})) == 0x04);

        if (success) {
          System.out.println("Tests pass");
        } else {
          System.out.println("Whoa, some codeword tests failed!");
          System.exit(-1);
        }
      }

      if (pinCount == 4 && colorCount == 6) {
        System.out.println("Run the example from Knuth's paper to compare with his results.");
        scoreCounter = 0;
        findSecret(new Codeword(new byte[]{3, 6, 3, 2}), System.out);
        System.out.format("Codeword comparisons: %,d\n\n", scoreCounter);
      }

      // Reset the game strategy, so we start fresh after testing.
      gameStrategy = null;

      // Run through all possible secret codewords and keep track of the maximum number of turns it
      // takes to find them.
      System.out
          .printf("Playing %d pins %d colors game for every possible secret with algorithm %s...\n",
              pinCount, colorCount, algo);
      ArrayList<Codeword> allCodewords = makeAllCodewords();
      System.out.printf("Total codewords: %,d\n", allCodewords.size());
      int maxTurns = 0;
      int totalTurns = 0;
      Codeword maxSecret = null;
      scoreCounter = 0;
      long s = System.nanoTime();

      for (Codeword secret : allCodewords) {
        int turns = findSecret(secret, new PrintStream(OutputStream.nullOutputStream()));
        totalTurns += turns;
        if (turns > maxTurns) {
          maxTurns = turns;
          maxSecret = secret;
        }
      }

      long e = System.nanoTime();
      double averageTurns = (double) totalTurns / allCodewords.size();
      System.out.format("Average number of turns was %.4f\n", averageTurns);
      System.out.println(
          "Maximum number of turns over all possible secrets was " + maxTurns + " with secret "
              + maxSecret);
      System.out.format("Codeword comparisons: %,d\n", scoreCounter);
      double elapsed = (e - s) / 1_000_000.0;
      System.out.format("Elapsed time %.4fs, average search %.04fms\n", elapsed / 1000,
          elapsed / allCodewords.size());
    } catch (Exception e) {
      System.out.println("Exception: " + e);
    }

    Strategy.dump(gameStrategy);

    System.out.println("Done");
  }
}
