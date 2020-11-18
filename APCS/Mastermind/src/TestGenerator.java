// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.io.FileWriter;

public class TestGenerator {

  private static final boolean includeMiyoshiTests = true;

  // Generate a codeword from an int given a number of pins and colors. Pin values start at 1.
  // Converts the given base-10 number to it's base-colorCount representation.
  private static byte[] generateCodeword(int w, int pinCount, int colorCount) {
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
    return digits;
  }

  private static final String[] miyoshiTests = {
      "6684,0000,0,0",
      "6684,6666,2,0",
      "6684,0123,0,0",
      "6684,4567,0,2",
      "6684,4589,1,1",
      "6684,6700,1,0",
      "6684,0798,0,1",
      "6684,6484,3,0",
      "6684,6480,2,1",
      "6684,6884,3,0",
      "6684,6684,4,0",
      "6684,8468,0,3",
      "6684,8866,0,3",
      "6684,8466,0,4",
  };

  private static void writeAllScores(int pinCount, int colorCount) {
    try {
      int total = 0;
      String filename = String.format("mastermind_%dp%dc.txt", pinCount, colorCount);
      System.out.println("Writing tests to " + filename);
      FileWriter fw = new FileWriter(filename);
      fw.write("Secret,Guess,Correct Digit and Location,Correct Digit Wrong Location\n");

      if (includeMiyoshiTests) {
        System.out.println("Including Miyoshi's test cases");
        for (String t : miyoshiTests) {
          fw.write(t);
          fw.write("\n");
          total++;
        }
      }

      int totalWords = (int) Math.pow(colorCount, pinCount);
      for (int i = 0; i < totalWords; i++) {
        Codeword ca = new Codeword(generateCodeword(i, pinCount, colorCount));
        for (int j = 0; j < totalWords; j++) {
          Codeword cb = new Codeword(generateCodeword(j, pinCount, colorCount));
          byte r1 = ca.score(cb);
          fw.write(String.format("%s,%s,%d,%d\n", ca, cb, r1 >> 4, r1 & 0xF));
          total++;
        }
      }

      fw.close();
      System.out.format("Wrote %,d test cases\n", total);
    } catch (Exception e) {
      System.out.println(e);
    }
  }

  public static void main(String[] args) {
    int pinCount = 4;
    int colorCount = 6;
    writeAllScores(pinCount, colorCount);
  }
}