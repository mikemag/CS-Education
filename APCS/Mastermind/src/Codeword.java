// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.util.Arrays;

// Class to hold a codeword for the Mastermind game.

public class Codeword {

  private final byte[] digits;

  // 0x40 for a 4-pin game.
  public static final byte winningScore = (byte) (Mastermind.pinCount << 4);

  Codeword(byte[] digits) {
    this.digits = digits;
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
  public byte score(Codeword guess) {
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

    return (byte) ((b << 4) | w);
  }

  // Original scoring method which I made in 2019. Works fine, but is O(n^2) in the number of pins.
  // I used this one for a while because it was either constant (or close-to-constant) extra space,
  // so worked well in practice given our low pin & color counts.
  public byte scoreOriginal(Codeword guess) {
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
    return (byte) ((b << 4) | w);
  }

  public String toString() {
    StringBuilder sb = new StringBuilder(digits.length);
    for (byte d : digits) {
      sb.append(Integer.toHexString(d));
    }
    return sb.toString();
  }
}
