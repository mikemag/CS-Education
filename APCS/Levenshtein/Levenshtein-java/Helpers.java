// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.function.*;

// Helpers for the Levenshtein distance project.

public class Helpers {

  // Load the dictionary of words. There's just under 371k words in the file.
  public static ArrayList<String> loadDictionary(String filename, int lengthLimit) {
    ArrayList<String> words = new ArrayList<>(371000);
    try {
      Scanner scanner = new Scanner(new File(filename));
      while (scanner.hasNextLine()) {
        String s = scanner.nextLine();
        if (s.length() <= lengthLimit) {
          words.add(s);
        }
      }
      scanner.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
    System.out.format("Loaded %,d words from %s\n\n", words.size(), filename);
    return words;
  }

  // A little helper to dump a 2D array of ints.
  public static void dumpArray(int[][] a) {
    for (int i = 0; i < a.length; i++) {
      for (int j = 0; j < a[i].length; j++) {
        System.out.printf("%7d ", a[i][j]);
      }
      System.out.println();
    }
    System.out.println();
  }

  public static class Test {
    public static class Args {
      Args(String w1, String w2, long i) {
        this.w1 = w1;
        this.w2 = w2;
        this.i = i;
      }

      public String w1;
      public String w2;
      public long i;
    }

    Test(String name, BiFunction<String, String, Integer> func, Args[] tests, Args perftest) {
      this.name = name;
      this.func = func;
      this.tests = tests;
      this.perftest = perftest;
    }

    public String name;
    public BiFunction<String, String, Integer> func;
    public Args tests[];
    public Args perftest;
  }

  public static boolean runTests(Test[] tests) {
    boolean passed = true;
    for (Test t : tests) {
      if (t.tests != null) {
        for (Test.Args a : t.tests) {
          int d = t.func.apply(a.w1, a.w2);
          System.out.printf("'%s': '%s' -> '%s' = %d", t.name, a.w1, a.w2, d);
          if (d != a.i) {
            System.out.printf(" -- failed, expected %d", a.i);
            passed = false;
          }
          System.out.println();
        }
      }
    }
    System.out.println();
    return passed;
  }

  public static void runPerfTests(Test[] tests) {
    for (Test t : tests) {
      BiFunction<String, String, Integer> f = t.func;
      String w1 = t.perftest.w1;
      String w2 = t.perftest.w2;
      long n = t.perftest.i;

      for (int i = 0; i < 50000; i++) {
        f.apply(w1, w2); // Warm-up
      }

      long s = System.nanoTime();

      for (long i = 0; i < n; i++) {
        f.apply(w1, w2);
      }

      long e = System.nanoTime();
      double elapsedMS = (e - s) / 1000000.0;
      System.out.format("Avg time %,.04fus -- %s, '%s' -> '%s', elapsed time %,.4fms, %,d calls\n",
          elapsedMS / n * 1000.0, t.name, w1, w2, elapsedMS, n);
    }

    System.out.println();
  }

  // Helper to learn about the properties of the neighbor maps.
  public static void neighborAnalysis(Map<String, List<String>> neighbors, int wordLengthLimit) {
    System.out.format("%,d total words with any neighbors\n", neighbors.size());

    int kmin = Integer.MAX_VALUE;
    int kmax = 0;
    int ktotal = 0;
    int nmin = Integer.MAX_VALUE;
    int nmax = 0;
    int ntotal = 0;
    int[][] m = new int[wordLengthLimit + 1][wordLengthLimit + 1];

    for (Map.Entry<String, List<String>> e : neighbors.entrySet()) {
      int klen = e.getKey().length();
      kmin = Math.min(kmin, klen);
      kmax = Math.max(kmax, klen);
      ktotal += klen;

      int nlen = e.getValue().size();
      nmin = Math.min(nmin, nlen);
      nmax = Math.max(nmax, nlen);
      ntotal += nlen;

      for (String s : e.getValue()) {
        m[klen][s.length()]++;
      }
    }

    System.out.format("Min/avg/max word size: %d/%.2f/%d\n", kmin, (double) ktotal / neighbors.size(), kmax);
    System.out.format("Min/avg/max neighbor count: %d/%.2f/%d\n\n", nmin, (double) ntotal / neighbors.size(), nmax);
//		Helpers.dumpArray(m);
  }

}
