// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

// Mastermind
//
// Play the game Mastermind, which is to guess a sequence of colors (the "secret") with feedback 
// about how many you have guessed correctly, and how many are in the right place.
//
// This will play the game for every possible secret and tell us the average and maximum number of 
// tries needed across all of them.
//
// Move results are in the form of a two digit number. The first digit is the number of colors in
// the guess which are correct, and in the correct position. The second digit is the number of colors
// in the guess which are correct, but in the wrong position.
//
// E.g.: 21 -- 2 correct in the right place, and 1 correct but in the wrong place. 
//
// There are a few algorithms to play with.
//
// Knuth's algorithm for this is quite good, if a bit hard to understand at first:
// http://www.cs.uni.edu/~wallingf/teaching/cs3530/resources/knuth-mastermind.pdf
// Also see the summary of it on https://en.wikipedia.org/wiki/Mastermind_(board_game)
//
// Recent survey of the space: https://arxiv.org/pdf/1305.1010.pdf

import java.util.ArrayList;
import java.util.Random;
import java.io.*;

public class Mastermind {

	enum Algo {
		// Pick the first of the remaining choices.
		// ~2m comps, 5.0216 avg turns, 8 tries turns, ~0.09s on mid-2012 MacBook Pro,
		// Java SE 12.0.2
		FirstOne,

		// Pick any of the remaining choices.
		// ~2m comps, ~4.6-4.7 avg turns, 7 turns max, ~0.10s on mid-2012 MacBook Pro,
		// Java SE 12.0.2
		Random,

		// Pick the one that will eliminate the most remaining choices.
		// ~391m comps, 4.4761 avg turns, 5 turns max, ~4.16s on mid-2012 MacBook Pro,
		// Java SE 12.0.2
		Knuth
	}

	private static Random rand = new Random();

	// Make a list of all codewords for a given number of "colors". Colors are
	// represented by the digits 1 thru n.
	private static ArrayList<Codeword> allCodewords = null;

	private static ArrayList<Codeword> makeAllCodewords(int colors) {
		if (allCodewords == null) {
			ArrayList<Codeword> l = new ArrayList<Codeword>((int) Math.pow(colors, 4));

			for (int a = 1; a <= colors; a++) {
				for (int b = 1; b <= colors; b++) {
					for (int c = 1; c <= colors; c++) {
						for (int d = 1; d <= colors; d++) {
							l.add(new Codeword(a, b, c, d));
						}
					}
				}
			}
			allCodewords = l;
		}

		// This is cached and copied so we save time one each play of the game.
		return new ArrayList<Codeword>(allCodewords);
	}

	// The core of Knuth's algorithm: find the remaining solution which will
	// eliminate the most possibilities on the next round, favoring, but not
	// requiring, any choice which may still be the final answer.
	private static int hitCounts[][] = new int[5][5];

	private static Codeword findKnuthGuess(Codeword lastGuess, ArrayList<Codeword> allCodewords,
			ArrayList<Codeword> possibleSolutions, Codeword.CheckCounter cc, PrintStream p) {

		// Pull out the last guess from the list of all remaining candidates.
		allCodewords.remove(lastGuess);

		Codeword bestGuess = null;
		int bestScore = 0;
		boolean bestIsPossibleSolution = false;
		for (int i = 0; i < allCodewords.size(); i++) {
			Codeword g = allCodewords.get(i);

			// Compute a score for this guess based on how many possible solutions it will
			// remove.
			int highestHitCount = 0;
			boolean isPossbileSolution = false;
			for (int j = 0; j < possibleSolutions.size(); j++) {
				Codeword c = possibleSolutions.get(j);
				if (g.equals(c)) {
					isPossbileSolution = true; // Remember if this guess is in the set of possible solutions
				} else {
					int r = g.check(c);
					cc.recordCheck();
					hitCounts[r / 10][r % 10]++;
				}
			}

			for (int m = 0; m < 5; m++) {
				for (int n = 0; n < 5; n++) {
					if (hitCounts[m][n] > highestHitCount) {
						highestHitCount = hitCounts[m][n];
					}
					hitCounts[m][n] = 0; // Reset the storage to 0 as we go
				}
			}

			int score = possibleSolutions.size() - highestHitCount; // Minimum codewords eliminated
			if (score > bestScore) {
				bestScore = score;
				bestGuess = g;
				bestIsPossibleSolution = isPossbileSolution;
			} else if (!bestIsPossibleSolution && isPossbileSolution && score == bestScore) {
				bestGuess = g;
				bestIsPossibleSolution = isPossbileSolution;
			}
		}

		p.println("Selecting Knuth's best guess: " + bestGuess + "\tscore: " + bestScore);
		return bestGuess;
	}

	// Play the game to find the given secret codeword and return how many turns it
	// took.
	private static int findSecret(int colors, Algo algo, Codeword secret, Codeword.CheckCounter cc, PrintStream p)
			throws Exception {
		ArrayList<Codeword> possibleSolutions = makeAllCodewords(colors);
		ArrayList<Codeword> allCodewords = null;
		if (algo == Algo.Knuth) {
			allCodewords = new ArrayList<>(possibleSolutions);
		}
		Codeword guess = new Codeword(1, 1, 2, 2); // Start w/ Knuth's first guess for all algorithms.
		possibleSolutions.remove(guess);

		p.println("Starting with secret " + secret);
		p.format("Solution space contains %d possibilities.\n", possibleSolutions.size());
		p.println("Initial guess is " + guess);

		int turns = 0;

		while (true) {
			int r = secret.check(guess); // Is our guess the winner?
			cc.recordCheck();
			p.println("\nTried guess " + guess + " against secret " + secret + " => " + r);
			turns++;

			if (r == 40) { // Remember: "40" is "4 correct in the correct place", thus a win.
				p.format("Solution found after %d tries\n", turns);
				break;
			}

			// "5. Otherwise, remove from S any code that would not give the same response
			// if it (the guess) were the code (secret)." -- from the description of Knuth's
			// algorithm at https://en.wikipedia.org/wiki/Mastermind_(board_game)
			//
			// This describes something common to all good solutions: since the scoring
			// function is commutative, and since we know the secret remains in our set of
			// possible solutions, we can quickly eliminate lots and lots of solutions from
			// the space on every iteration.
			p.println("Removing solutions that have no chance of being correct...");
			final Codeword g = guess;
			possibleSolutions.removeIf(c -> {
				cc.recordCheck();
				return c.check(g) != r;
			});
			p.format("Solution space now contains %d possibilities.\n", possibleSolutions.size());

			if (possibleSolutions.size() == 0) {
				// This is only possible if there is a bug in our scoring function.
				throw new Exception("Failed to find solution with secret " + secret);
			} else if (possibleSolutions.size() == 1) {
				guess = possibleSolutions.remove(0);
				p.println("Only remainig solution must be correct: " + guess);
			} else if (algo == Algo.FirstOne) {
				guess = possibleSolutions.remove(0);
				p.println("Selecting the first possibility blindly: " + guess);
			} else if (algo == Algo.Random) {
				guess = possibleSolutions.remove(rand.nextInt(possibleSolutions.size()));
				p.println("Selecting a random possibility: " + guess);
			} else if (algo == Algo.Knuth) {
				guess = findKnuthGuess(guess, allCodewords, possibleSolutions, cc, p);
			}
		}

		p.println("Done with secret " + secret + "\n");

		return turns;
	}

	public static void main(String[] args) {
		final int colors = 6; // The traditional number of colors

		try {
			// Pick which algo to run. If I was cool I'd either make this a command line
			// arg, or have it run thru all of them.
			Algo algo = Algo.Knuth;

			// Run the example from Knuth's paper so we can compare with his results.
			Codeword.CheckCounter cc = new Codeword.CheckCounter();
			findSecret(colors, algo, new Codeword(3, 6, 3, 2), cc, System.out);
			System.out.format("Codeword comparisons: %,d\n\n", cc.getCount());

			// Run thru all possible secret codewords and keep track of the maximum number
			// of turns it takes to find them.
			System.out.println("----------");
			System.out.println("Playing the game for every possible secret...");
			ArrayList<Codeword> allCodewords = makeAllCodewords(colors);
			int maxTurns = 0;
			int totalTurns = 0;
			Codeword maxSecret = null;
			cc = new Codeword.CheckCounter();
			long s = System.nanoTime();

			for (Codeword secret : allCodewords) {
				int turns = findSecret(colors, algo, secret, cc, new PrintStream(OutputStream.nullOutputStream()));
				totalTurns += turns;
				if (turns > maxTurns) {
					maxTurns = turns;
					maxSecret = secret;
				}
			}

			long e = System.nanoTime();
			long checkCallCount = cc.getCount();
			double averageTurns = (double) totalTurns / allCodewords.size();
			System.out.format("Average number of turns was %.4f\n", averageTurns);
			System.out.println(
					"Maximum number of turns over all possible secrets was " + maxTurns + " with secret " + maxSecret);
			System.out.format("Codeword comparisons: %,d\n", checkCallCount);
			double elapsed = (e - s) / 1000000.0;
			System.out.format("Elapsed time %.4fs, average search %.04fms\n", elapsed / 1000,
					elapsed / allCodewords.size());
		} catch (Exception e) {
			System.out.println("Exception: " + e);
		}

		System.out.println("Done");
	}
}
