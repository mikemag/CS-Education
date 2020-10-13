// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

// Class to hold a codeword for the Mastermind game.
// 
// This is represented two ways just for convenience: one as an array of individual 
// digits, and one as a simple integer.

public class Codeword {

  byte[] digits = new byte[4];
  int cacheKey;

  // This is an optimization for codeword comparison that trades space for time.
  // It is a sparse array large enough to hold the combination of two 4-digit
  // codewords up to 9 colors each.
  //
  // Given c1 and c2, the key to the array is c1c2.
  //
  // This allows us to lazily compute all codeword combinations and find them
  // with a simple array lookup, which is far, far faster than actually comparing
  // two codewords.
  private static final byte[] checkMap = new byte[99999999];

  static {
    for (int i = 0; i < checkMap.length; i++) {
      checkMap[i] = -1;
    }
  }

  // Helper class to count how many times we call check() in some of our
  // algorithms.
  public static class CheckCounter {

    private long n = 0;

    public void recordCheck() {
      n++;
    }

    public long getCount() {
      return n;
    }
  }

  Codeword(int a, int b, int c, int d) {
    this.digits[0] = (byte) a;
    this.digits[1] = (byte) b;
    this.digits[2] = (byte) c;
    this.digits[3] = (byte) d;
    cacheKey = a * 1000 + b * 100 + c * 10 + d;
  }

  // The result of this is encoded in a single byte for the cache.
  public int check(Codeword guess) {
    byte cached = checkMap[cacheKey * 10000 + guess.cacheKey];
    if (cached < 0) {
      int b = 0;
      int w = 0;
      boolean[] used = new boolean[4];

      for (int i = 0; i < 4; i++) {
        if (guess.digits[i] == digits[i]) {
          b++;
          used[i] = true;
        } else {
          for (int j = 0; j < 4; j++) {
            if (!used[j] && guess.digits[i] == digits[j] && guess.digits[j] != digits[j]) {
              w++;
              used[j] = true;
              break;
            }
          }
        }
      }
      cached = (byte) (b * 10 + w);
      checkMap[cacheKey * 10000 + guess.cacheKey] = cached;
    }
    return cached;
  }

  public boolean equals(Object o) {
    if (o instanceof Codeword) {
      Codeword x = (Codeword) o;
      return digits[0] == x.digits[0] && digits[1] == x.digits[1] && digits[2] == x.digits[2]
          && digits[3] == x.digits[3];

    }
    return false;
  }

  public String toString() {
    return Integer.toString(cacheKey);
  }
}
