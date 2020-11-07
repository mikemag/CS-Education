import java.io.FileReader;
import java.util.Scanner;

public class MastermindTester {

  private static final String testFilename = "mastermind_4p6c.txt";

  private static byte[] codeStringToBytes(String s) {
    byte[] r = s.getBytes();
    for (int i = 0; i < r.length; i++) {
      r[i] -= '0';
    }
    return r;
  }

  private static int resultsToInt(String b, String w) {
    return Integer.parseInt(b) * 10 + Integer.parseInt(w);
  }

  private static void runTestsFromFile(String filename) {
    System.out.println("Running tests from file " + filename);
    try {
      int total = 0;
      FileReader fr = new FileReader(filename);
      Scanner lineScanner = new Scanner(fr);
      lineScanner.nextLine(); // Drop the header row

      Results mikemag = new Results("mikemag"); // Use your own name for your results
      while (lineScanner.hasNextLine()) {
        total++;
        String line = lineScanner.nextLine();
        String[] testData = line.split(",");

        // Update this block to work for your scoring function. Convert testData as needed.
        Codeword ca = new Codeword(codeStringToBytes(testData[0]));
        Codeword cb = new Codeword(codeStringToBytes(testData[1]));
        int expected = resultsToInt(testData[2], testData[3]);
        int r = ca.score(cb);
        if (r == expected) {
          mikemag.success();
        } else {
          mikemag.failure(testData, String.format("%02d", r));
        }
      }

      System.out.format("Done running %,d test cases.\n\n", total);
      System.out.println(mikemag);
    } catch (Exception e) {
      System.out.println(e);
    }
  }

  public static void main(String[] args) {
    runTestsFromFile(testFilename);
  }

  // Helper class to hold the results of a test run.
  static class Results {

    private final String name;
    private int totalRun;
    private int totalFailed;
    private String firstFailure;

    public Results(String name) {
      this.name = name;
    }

    public void success() {
      totalRun++;
    }

    public void failure(String[] testData, String result) {
      totalRun++;
      totalFailed++;
      if (firstFailure == null) {
        firstFailure = String.format("%s vs %s expected %s%s, got %s",
            testData[0], testData[1], testData[2], testData[3], result);
      }
    }

    @Override
    public String toString() {
      String r = String.format("%s: passed %.2f%%",
          name, (float) (totalRun - totalFailed) / totalRun * 100.0);
      if (firstFailure != null) {
        r += ", first failure: " + firstFailure;
      }
      return r;
    }
  }
}