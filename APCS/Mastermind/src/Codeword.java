// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.util.Arrays;

// Class to hold a codeword for the Mastermind game.
//
// This is represented two ways just for convenience: one as an array of individual digits, and one
// as a simple integer.

public class Codeword {

  private final byte[] digits;
  private int cacheKey;
  public static final int winningScore = Mastermind.pinCount * 10; // "40" for a 4-pin game.

  // This is an optimization for codeword comparison that trades space for time. It is a sparse 2D
  // array large enough to hold a byte for every pair of codewords. This allows us to lazily compute
  // all codeword combinations and find them with a simple array lookup, which is far, far faster
  // than actually comparing two codewords.
  //
  // However, this simple scheme falls apart for codewords above 5 pins, as the memory requirements
  // exceed what's reasonable. So we only use it for small pin games.
  private static byte[][] scoreMap = null;

  static {
    if (Mastermind.pinCount <= 5) {
      int size = (int) Math.pow(10, Mastermind.pinCount);
      scoreMap = new byte[size][size];
      for (byte[] bytes : scoreMap) {
        Arrays.fill(bytes, (byte) -1);
      }
    }
  }

  Codeword(byte[] digits) {
    this.digits = digits;
    for (byte digit : digits) {
      this.cacheKey = this.cacheKey * 10 + digit;
    }
  }

  // There are a bunch of very reasonable ways to implement the scoring method, and most of them are
  // far more intuitive than the ones I use here. The goal of this project is to show students how
  // to play all games via different algorithms; they should be building their own scoring methods
  // before coming here. For my scoring methods I've focused mostly on efficiency, since I want to
  // be able to play all games with larger pin and color counts quickly.
  //
  // Thus, I don't recommend looking at these if you haven't already made your own correct scoring
  // method. They'll probably be more confusing than helpful :)

  // New scoring method based on counting pin colors and consuming them based on pin positions. By
  // holding all of the counts as 4-bit counters in a single long, it essentially uses constant
  // extra space, and is O(n) in the pinCount. This similar run time to the original version for
  // small pin & color combinations, but a nice win on larger pin counts.
  public int score(Codeword guess) {
    byte result = -1;
    if (scoreMap != null) {
      result = scoreMap[cacheKey][guess.cacheKey];
    }
    if (result < 0) {
      int b = 0;
      int w = 0;
      long colorCounts = 0; // Room for 16 4-bit counters

      for (int i = 0; i < Mastermind.pinCount; i++) {
        if (guess.digits[i] == digits[i]) {
          b++;
        } else {
          colorCounts += 1L << (digits[i] * 4);
        }
      }

      for (int i = 0; i < Mastermind.pinCount; i++) {
        if (guess.digits[i] != digits[i] && (colorCounts & (0xFL << (guess.digits[i] * 4))) > 0) {
            w++;
            colorCounts -= 1L << (guess.digits[i] * 4);
          }
        }
      result = (byte) (b * 10 + w);
      if (scoreMap != null) {
        scoreMap[cacheKey][guess.cacheKey] = result;
      }
    }

    return result;
  }

  // Original scoring method which I made in 2019. Works fine, but is O(n^2) in the number of pins.
  // I used this one for a while because it was either constant (or close-to-constant) extra space,
  // so worked well in practice given our low pin & color counts.
  public int scoreOriginal(Codeword guess) {
    byte result = -1;
    if (scoreMap != null) {
      result = scoreMap[cacheKey][guess.cacheKey];
    }
    if (result < 0) {
      int b = 0;
      int w = 0;
      int used = 0; // 32 bit flags, rather than heap-allocating an array of booleans

      for (int i = 0; i < Mastermind.pinCount; i++) {
        if (guess.digits[i] == digits[i]) {
          b++;
          used |= 1 << i;
        } else {
          for (int j = 0; j < Mastermind.pinCount; j++) {
            if ((used & 1 << j) == 0 && guess.digits[i] == digits[j]
                && guess.digits[j] != digits[j]) {
              w++;
              used |= 1 << j;
              break;
            }
          }
        }
      }
      result = (byte) (b * 10 + w);
      if (scoreMap != null) {
        scoreMap[cacheKey][guess.cacheKey] = result;
      }
    }

    return result;
  }

  public String toString() {
    return Integer.toString(cacheKey);
  }
}
