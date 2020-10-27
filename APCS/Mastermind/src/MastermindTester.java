import java.io.FileWriter;
import java.util.ArrayList;

public class MastermindTester {

  // Make a list of all codewords for a given number of "colors". Colors are represented by the
  // digits 0 thru n. This figures out how many codewords there are, which is colorCount ^ pinCount,
  // then converts the base-10 number of each codeword to it's base-colorCount representation.
  private static ArrayList<byte[]> makeAllCodewords(int colorCount, int pinCount) {
    int totalWords = (int) Math.pow(colorCount, pinCount);
    ArrayList<byte[]> l = new ArrayList<>(totalWords);

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

      l.add(digits);
    }

    return l;
  }

  public static void writeAllScores(ArrayList<byte[]> allCodewords)
  {
    try {
      FileWriter fw = new FileWriter("mastermind_4x6.txt");
      fw.write("Secret,Guess,Correct Digit and Location,Correct Digit Wrong Location\n");
      for (byte[] a : allCodewords) {
        Codeword ca = new Codeword(a);
        for (byte[] b : allCodewords) {
          Codeword cb = new Codeword(b);
          int r1 = ca.score(cb);
          fw.write(String.format("%s,%s,%d,%d\n", ca, cb, r1 / 10, r1 % 10));
        }
      }
      fw.close();
    } catch (Exception e) {
      System.out.println(e);
    }
  }

  public static void main(String[] args) {
    ArrayList<byte[]> allCodewords = makeAllCodewords(6, 4);
//    writeAllScores(allCodewords);

    for (byte[] a : allCodewords) {
      Codeword ca = new Codeword(a);
      for (byte[] b : allCodewords) {
        Codeword cb = new Codeword(b);
        int r1 = ca.score(cb);
        int r2 = ca.scoreOriginal(cb);
        if (r1 != r2) {
          System.out.printf("Wrong answer: %s %s %d %d\n", ca, cb, r1, r2);
        }
      }
    }

  }
}