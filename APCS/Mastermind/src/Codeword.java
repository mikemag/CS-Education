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
  // array large enough to hold a byte for every paid of codewords. This allows us to lazily compute
  // all codeword combinations and find them with a simple array lookup, which is far, far faster
  // than actually comparing two codewords.
  //
  // However, this simple scheme falls apart for codewords above 5 pins, as the memory requirements
  // exceed what's reasonable. So we only use it for small pin games.
  private static byte[][] checkMap = null;

  static {
    if (Mastermind.pinCount <= 5) {
      int size = (int) Math.pow(10, Mastermind.pinCount);
      checkMap = new byte[size][size];
      for (byte[] bytes : checkMap) {
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

  // The result of this is encoded in a single byte for the cache.
  public int check(Codeword guess) {
    byte result = -1;
    if (checkMap != null) {
      result = checkMap[cacheKey][guess.cacheKey];
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
      if (checkMap != null) {
        checkMap[cacheKey][guess.cacheKey] = result;
      }
    }

    return result;
  }

  public String toString() {
    return Integer.toString(cacheKey);
  }
}
