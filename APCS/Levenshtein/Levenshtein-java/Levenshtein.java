// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Scanner;

// AP CS Levenshtein distance project.
//
// Find a path between two words by making a single character edit at each step, using only valid
// words.
//
// This project shows a progression thru a number of different ways to try to compute the
// Levenshtein distance between two words in a reasonable amount of time. It also shows how to
// compute all shortest paths and display them.
//
// There are a few ways to do this. The most obvious one, and most often suggested, is to build up
// the entire neighbor map first, then let the user pick words and give them an answer. This gives
// them a quick answer at the expense of waiting for the entire map to be built. It also highlights
// the algorithmic and implementation issues nicely. Thus, I really like this version as the first
// exercise.
//
// Another way is to build the map lazily, only finding the neighbors you need to consider for the
// given words. This works just fine, and overall is faster than building the entire map up front,
// but it comes at the expense of making the user wait, sometimes quite a while, for each answer as
// the map is incrementally populated while they wait. It's also easier to gloss over the issues
// this way ;)
//
// I've also added a parallel version of building the entire neighbor map first, with options to
// tweak how much work is farmed out to each task and how many threads to use. See
// LevenshtienParallel.java for the parallel versions.
//
// See the top of the Levenshtein class for options you can tweak, including full vs. lazy building,
// parallelism, etc. There are more options over in LevenshteinParallel.java.
//
// For pre-building the map, this processes the entire 370k word dictionary in ~5 min. The basic
// version with no improvements takes over 33 hours. The parallel version takes ~30 seconds w/ 16
// threads.
//
// For the lazy building tests, the normal version takes ~108s while the parallel version takes ~29s
// w/ 16 threads.
//
// NB: there are a bunch of functions in the Helpers class just to keep this file focused on the
// algorithm and its variations.
//
// Test machine: MacBook Pro (16-inch, 2019), macOS Catalina 10.15.6 (19G2021), Intel(R) Core(TM)
// i9-9980HK CPU @ 2.40GHz (boost to 5.0GHz, 8 cores, hyperthreaded), 64 GB 2667 MHz DDR4,
// plugged in, run from the command
// line with nothing else running.
//
// $ java --version
// openjdk 14.0.2 2020-07-14
// OpenJDK Runtime Environment (build 14.0.2+12-46)
// OpenJDK 64-Bit Server VM (build 14.0.2+12-46, mixed mode, sharing)

public class Levenshtein {

  // Options -- more in LevenshteinParallel.java
  private static final boolean runPerfTests = false;
  private static final boolean runSamplePairs = false;

  private static final int wordLengthLimit = 30; // The longest word in words_alpha.txt is 29.

  private static final boolean buildFullMap = false;
  private static final boolean parallelFullBuild = true;
  private static final boolean parallelLazyBFS = true && !buildFullMap;
  private static final boolean letTheUserPlay = true;

  // First attempt based on the pseudo code at https://en.wikipedia.org/wiki/Levenshtein_distance.
  // This is the classic non-recursive version, implemented as obviously as possible.
  //
  // This algorithm is O(n^2) in the length of the strings, and the constants in it are pretty high,
  // too, making for an expensive function when processing a real dictionary of words.
  //
  // Runtime for this is ~0.5074µs
  private static int editDistanceBasic(String w1, String w2) {
    int[][] d = new int[w1.length() + 1][w2.length() + 1];

    for (int i = 1; i <= w1.length(); i++) {
      d[i][0] = i;
    }
    for (int i = 1; i <= w2.length(); i++) {
      d[0][i] = i;
    }

    for (int j = 1; j <= w2.length(); j++) {
      for (int i = 1; i <= w1.length(); i++) {
        int substitutionCost = 0;
        if (w1.charAt(i - 1) != w2.charAt(j - 1)) {
          substitutionCost = 1;
        }

        d[i][j] = Math.min(d[i - 1][j] + 1, // deletion
            Math.min(d[i][j - 1] + 1, // insertion
                d[i - 1][j - 1] + substitutionCost)); // substitution
      }
    }

    return d[w1.length()][w2.length()];
  }

  // This version replaces allocation of a temporary array with reuse of a static array. Memory
  // allocation and the initialization is expensive relative to the rest of the function, and
  // avoiding it cuts the execution time by 70%.
  //
  // Since the first row and column never change, we can initialize it once as well.
  //
  // Of course, we have to allocate this static array when the program starts. See
  // editDistanceStaticSetup().
  //
  // Runtime for this is ~0.1540µs
  private static int editDistanceNoAlloc(String w1, String w2) {
    int[][] d = StaticDistanceArray; // Pre-allocated, pre-initialized array.

    for (int j = 1; j <= w2.length(); j++) {
      for (int i = 1; i <= w1.length(); i++) {
        int substitutionCost = 0;
        if (w1.charAt(i - 1) != w2.charAt(j - 1)) {
          substitutionCost = 1;
        }

        d[i][j] = Math.min(d[i - 1][j] + 1, // deletion
            Math.min(d[i][j - 1] + 1, // insertion
                d[i - 1][j - 1] + substitutionCost)); // substitution
      }
    }

    return d[w1.length()][w2.length()];
  }

  // This version explores the cost of calling String::length(). "Hoisting" the calls out of the
  // loops to the top of the function helps a fair bit, surprisingly. It suggests the compiler isn't
  // doing a good job of this itself, even with immutable objects like String, which is a shame.
  //
  // There are other micro-optimizations like this that we can play with, but none of them really
  // give too much of a win, and all of them just make the code more and more complex.
  //
  // Runtime for this is ~0.1299µs
  private static int editDistanceHoistedLengths(String w1, String w2) {
    int[][] d = StaticDistanceArray; // Pre-allocated array.
    int w1l = w1.length();
    int w2l = w2.length();

    for (int j = 1; j <= w2l; j++) {
      for (int i = 1; i <= w1l; i++) {
        int substitutionCost = 0;
        if (w1.charAt(i - 1) != w2.charAt(j - 1)) {
          substitutionCost = 1;
        }

        d[i][j] = Math.min(d[i - 1][j] + 1, // deletion
            Math.min(d[i][j - 1] + 1, // insertion
                d[i - 1][j - 1] + substitutionCost)); // substitution
      }
    }

    return d[w1l][w2l];
  }

  // Static pre-allocated memory for various implementations of the edit
  // distance function.
  private static int[][] StaticDistanceArray = null;

  private static void editDistanceStaticSetup(int wordLengthLimit) {
    StaticDistanceArray = new int[wordLengthLimit + 1][wordLengthLimit + 1];
    for (int i = 1; i <= wordLengthLimit; i++) {
      StaticDistanceArray[i][0] = i;
    }
    for (int i = 1; i <= wordLengthLimit; i++) {
      StaticDistanceArray[0][i] = i;
    }
  }

  // The problem statement never said we have to actually compute the Levenshtein distance between
  // any words. It said we have to find all words within 1 distance of each other, and build a
  // neighbors map. That's a much easier problem, so why did we even implement the real thing?? :)
  //
  // To start, let's assume we're given two words of equal lengths. Can we determine quickly and
  // easily if they are distance 1 or not?
  //
  // Sure. Since they're the same length the only option to make them equal is substitution, thus we
  // just need to count the number of different letters, and stop after we find more than one.
  //
  // This is O(n) in the length of the strings, and a fraction of any of the methods above.
  //
  // Runtime for this is ~0.0099µs
  public static int editDistanceEqual(String w1, String w2) {
    int w1l = w1.length();
    int diffs = 0;
    for (int i = 0; i < w1l; i++) {
      if (w1.charAt(i) != w2.charAt(i)) {
        diffs++;
        if (diffs > 1) {
          break;
        }
      }
    }
    return diffs;
  }

  // Next, for words of different lenghts to be within 1 edit distance they need to be within 1
  // character in length of each other.
  //
  // Also, substitution is no longer an option. Since they're off by one, deletion and insertion are
  // the only options. Consider these examples, in order, to learn what the algorithm is doing.
  //
  // DOG & DOGO
  // DOG & DOOG
  // DOG & ADOG
  // DOG & ACAT
  //
  // We'll consider the shorter word first and the longer word second, and only allow deletion in
  // the longer word. Again, this is O(n) in the length of the strings and way faster than the real
  // algorithm.
  //
  // Runtime of this is ~0.0147µ
  public static int editDistanceOffByOne(String w1, String w2) {
    int diffs = 0;
    int w1l = w1.length(); // Requires w1 shorter than w2
    int w2l = w2.length();
    int w1i = 0;
    int w2i = 0;

    while (w1i < w1l && diffs < 2) {
      if (w1.charAt(w1i) != w2.charAt(w2i)) {
        diffs++;
        w2i++; // Same as deletion.
      } else {
        w1i++;
        w2i++;
      }
    }

    if (w2i < w2l) {
      diffs++; // Deletion of the last char of w2.
    }

    return diffs;
  }

  // So let's combine the two to work with words of any length in any order, because you need this
  // logic somewhere to make use of these.
  //
  // Runtime for this is ~0.0153µs
  //
  // Thus the original version is 33.16x slower than this. The best we could do with the classic
  // algorithm is 8.49x slower than this.
  private static int editDistanceCheater(String w1, String w2) {
    int w1l = w1.length();
    int w2l = w2.length();
    int ed = 0;
    if (w1l == w2l) {
      ed = editDistanceEqual(w1, w2);
    } else {
      int d = w2l - w1l;
      if (d == 1) {
        ed = editDistanceOffByOne(w1, w2);
      } else if (d == -1) {
        ed = editDistanceOffByOne(w2, w1);
      }
    }
    return ed;
  }

  // This builds the neighbor map. Here are the optimizations from the obvious version:
  //
  // 1. Only compute half the matrix, cuts the run time in half. Being a neighbor is "commutative",
  //    i.e., order doesn't matter. If w1 is a neighbor of w2, then w2 is a neighbor to w1.
  //
  // 2. We hoist everything we can.
  //
  // 3. We inline the "combined" cheater function to allow us to reuse the length computations.
  //
  // 4. Sort the dictionary of words by their length. (Miyoshi's idea.) It came sorted
  //    alphabetically, so we end up sorted by length then lexically. This allows us to stop looking
  //    for neighbors when we see a string 2 or more longer. This is a multiple bonus actually and
  //    cuts the runtime by a bit more than half:
  //
  //   a) We consider far fewer words than we otherwise would for a nice win.
  //
  //   b) There are cache and memory access effects we benefit from as we drag much less memory thru
  //      the cache.
  //
  //   c) Since we do all equal sizes, then all off by ones, then stop, we get much better branch
  //      prediction.
  //
  // Previous experiments gave the following times:
  //   - Unsorted words:           1,527.85s (25.46min)
  //   - Sorted words:               923.56s (15.39min)
  //   - Sorted words & stop early:  411.54s (6.86min)
  //
  // Current runtime of this is ~308.11s (5.13min)
  public static ArrayList<String> words = null;
  public static HashMap<String, List<String>> neighbors = new HashMap<>();

  private static void buildFullNeighborMap() {
    long totalChecks = 0;
    long skippedChecks = 0;
    long totalNeighbors = 0;
    int wordsLen = words.size();
    long totalCompsNeeded = (long) wordsLen * (long) wordsLen / 2;
    long startTime = System.nanoTime();
    long lastTime = startTime;
    for (int i = 0; i < wordsLen; i++) {
      String w1 = words.get(i);
      int w1l = w1.length();
      List<String> nl = neighbors.get(w1);
      for (int j = i + 1; j < wordsLen; j++) {
        totalChecks++;
        String w2 = words.get(j);
        int w2l = w2.length();
        int ed = 0;
        if (w1l == w2l) {
          ed = editDistanceEqual(w1, w2);
        } else {
          int d = w2l - w1l; // Positive, since we're sorted by length
          if (d == 1) {
            ed = editDistanceOffByOne(w1, w2);
          } else {
            // d > 1. Since words is sorted by length, then everything past j is too long.
            skippedChecks += wordsLen - j;
            break;
          }
        }
        if (ed == 1) {
          totalNeighbors++;
          if (nl == null) {
            nl = new ArrayList<>();
            neighbors.put(w1, nl);
          }
          nl.add(w2);
          List<String> nl2 = neighbors.get(w2);
          if (nl2 == null) {
            nl2 = new ArrayList<>();
            neighbors.put(w2, nl2);
          }
          nl2.add(w1);
        }
      }
      if (i % 10_000 == 0) {
        long currentTime = System.nanoTime();
        if (currentTime - lastTime > 1_000_000_000L) {
          long tandsChecks = totalChecks + skippedChecks;
          System.out.format("Finsihed '%s', %,.0fm/%,.0fm (%.2f%%), %,d neighbors\n", w1,
              tandsChecks / 1_000_000.0, totalCompsNeeded / 1_000_000.0,
              (double) tandsChecks / (double) totalCompsNeeded * 100.0, totalNeighbors);
          double elapsedMS = (currentTime - startTime) / 1_000_000.0;
          System.out.format("Elapsed time %,.2fs, average time %.04fµs, for %,d calls\n\n",
              elapsedMS / 1000,
              elapsedMS / totalChecks * 1000.0, totalChecks);
          lastTime = currentTime;
        }
      }
    }

    double elapsedMS = (System.nanoTime() - startTime) / 1_000_000.0;
    System.out.format("Skipped %,d checks\n", skippedChecks);
    System.out.format(
        "Done.\nElapsed time %,.2fs, average loop time %.04fµs, for %,d calls and %,d total neighbors\n\n",
        elapsedMS / 1000, elapsedMS / totalChecks * 1000.0, totalChecks, totalNeighbors);
  }

  // This builds the neighbor map "lazily", one row at a time and on-demand. This allows us to skip
  // building the full map in favor of only building the pieces we need to perform a given search.
  // The map is essentially a cache.
  public static int[] wordLengthStarts = null;

  public static void lazyBuildNeighborMap(String w1, List<String> nl) {
    int w1l = w1.length();
    int wordsLen = words.size();
    for (int j = wordLengthStarts[w1l - 1]; j < wordsLen; j++) {
      String w2 = words.get(j);
      int w2l = w2.length();
      int ed = 0;
      if (w1l == w2l) {
        ed = editDistanceEqual(w1, w2);
      } else {
        int d = w2l - w1l;
        if (d == 1) {
          ed = editDistanceOffByOne(w1, w2);
        } else if (d == -1) {
          ed = editDistanceOffByOne(w2, w1);
        } else if (d > 1) {
          // Since words is sorted by length, then everything past j is too long.
          break;
        }
      }
      if (ed == 1) {
        nl.add(w2);
      }
    }
  }

  // Build a map of where each group of words of a given length start. Used by
  // lazyBuildNeighborMap() to skip the left side of the matrix.
  private static int[] buildWordLengthStarts(int wordLengthLimit) {
    int[] a = new int[wordLengthLimit];

    int lastWordLength = 0;
    for (int i = 0; i < words.size(); i++) {
      int l = words.get(i).length();
      if (l > lastWordLength) {
        a[l] = i;
        lastWordLength = l;
      }
    }

    // Fill in gaps, e.g., there are no words 26 letters long.
    if (a[a.length - 1] == 0) {
      a[a.length - 1] = words.size() - 1;
    }
    for (int i = a.length - 1; i > 1; i--) {
      if (a[i] == 0) {
        a[i] = a[i + 1];
      }
    }

    return a;
  }

  // Used to print the paths we discover, so we keep parent references as we explore the graph.
  public static class Node {

    Node parent;
    String word;

    Node(Node parent, String word) {
      this.parent = parent;
      this.word = word;
    }
  }

  public static void printPath(List<String> path, String dest) {
    for (String s : path) {
      System.out.print(s + " -> ");
    }
    System.out.println(dest);
  }

  public static List<String> buildPath(Node n) {
    LinkedList<String> path = new LinkedList<>();
    while (n != null) {
      path.push(n.word);
      n = n.parent;
    }
    return path;
  }

  // Traverse the neighbor graph from w1 to w2, finding all shortest paths. Goes breadth-first.
  // Trims out loops to make it a tree a level at a time as it goes.
  private static void findPathBFS(String w1, String w2) {
    if (parallelLazyBFS) {
      new LevenshteinParallel.LazyFindPathBFS(w1, w2).findPath();
      return;
    }

    System.out.format("Find path from '%s' to '%s'\n", w1, w2);
    long startTime = System.nanoTime();

    int totalWords = 0;
    int totalMinPaths = 0;
    Queue<Node> leavesQueue = new ArrayDeque<>();
    HashSet<String> parents = new HashSet<>(); // To remove loops.
    Node levelSentinel = new Node(null, null); // Marks the end of each level.
    leavesQueue.add(levelSentinel);
    leavesQueue.add(new Node(null, w1));

    while (!leavesQueue.isEmpty()) {
      Node n = leavesQueue.remove();
      if (n == levelSentinel) {
        // Stop when we can't find anything, or if we found paths on the last level.
        if (leavesQueue.isEmpty() || totalMinPaths > 0) {
          break;
        }
        for (Node queuedNode : leavesQueue) {
          parents.add(queuedNode.word);
        }
        leavesQueue.add(levelSentinel);
        continue;
      }
      totalWords++;

      List<String> nl = getNeighborsWithLazyBuild(n.word);
      for (String w : nl) {
        if (w.equals(w2)) {
          List<String> p = buildPath(n);
          if (totalMinPaths == 0) {
            System.out.println("Shortest path lengths: " + p.size());
            // Copy-paste to http://www.webgraphviz.com/
            System.out.format("digraph %s_%s_%d{concentrate=true;\n", w1, w2, p.size());
          }
          totalMinPaths++;
          printPath(p, w);
        }
        if (!parents.contains(w)) {
          leavesQueue.add(new Node(n, w));
        }
      }
    }

    if (totalMinPaths > 0) {
      System.out.println("}");
    }
    double searchMS = (System.nanoTime() - startTime) / 1_000_000.0;
    System.out.format("Done %,.2fms, considered %,d words for %,d total minimum paths\n\n",
        searchMS,
        totalWords,
        totalMinPaths);
  }

  // A small helper to get us the neighbor list for a word, and build its neighbors lazily if
  // necessary. Used by findPathBFS() and the user input code down in main().
  private static List<String> getNeighborsWithLazyBuild(String w) {
    List<String> nl = neighbors.get(w);
    if (nl == null) {
      nl = new ArrayList<>();
      neighbors.put(w, nl);
      lazyBuildNeighborMap(w, nl);
    }
    return nl;
  }

  private static int emptyTestFunc(String w1, String w2) {
    return 0;
  }

  public static void main(String[] args) {
    editDistanceStaticSetup(8); // Static setup large enough for all functional and perf tests.

    Helpers.Test.Args[] commonTests = {new Helpers.Test.Args("dog", "dot", 1),
        new Helpers.Test.Args("dog", "dog", 0), new Helpers.Test.Args("Saturday", "Sunday", 3),
        new Helpers.Test.Args("sitting", "kitten", 3)};
    Helpers.Test.Args[] equalSizeTests = {new Helpers.Test.Args("dog", "dot", 1),
        new Helpers.Test.Args("dog", "dog", 0), new Helpers.Test.Args("dog", "cat", 2)};
    Helpers.Test.Args[] offByOneTests = {new Helpers.Test.Args("dog", "dogo", 1),
        new Helpers.Test.Args("dog", "doog", 1), new Helpers.Test.Args("dog", "adog", 1),
        new Helpers.Test.Args("dog", "acat", 3)};
    Helpers.Test.Args[] cheaterCombinedTests = {new Helpers.Test.Args("dog", "dot", 1),
        new Helpers.Test.Args("dog", "dog", 0), new Helpers.Test.Args("Saturday", "Sunday", 0),
        new Helpers.Test.Args("sitting", "kitten", 3)};
    Helpers.Test[] tests = new Helpers.Test[]{
        new Helpers.Test("Empty function", Levenshtein::emptyTestFunc, null,
            new Helpers.Test.Args("Empty", "Empty", 10_000_000_000L)),
        new Helpers.Test("Levenshtein basic", Levenshtein::editDistanceBasic, commonTests,
            new Helpers.Test.Args("Saturday", "Sunday", 4_000_000)),
        new Helpers.Test("Levenshtein no-alloc", Levenshtein::editDistanceNoAlloc, commonTests,
            new Helpers.Test.Args("Saturday", "Sunday", 20_000_000)),
        new Helpers.Test("Levenshtein hoisted string lengths",
            Levenshtein::editDistanceHoistedLengths,
            commonTests, new Helpers.Test.Args("Saturday", "Sunday", 20_000_000)),
        new Helpers.Test("Cheater equal lengths", Levenshtein::editDistanceEqual, equalSizeTests,
            new Helpers.Test.Args("Saturday", "Satuxday", 200_000_000)),
        new Helpers.Test("Cheater off by one", Levenshtein::editDistanceOffByOne, offByOneTests,
            new Helpers.Test.Args("Saturday", "Saturxday", 200_000_000)),
        new Helpers.Test("Cheater combined", Levenshtein::editDistanceCheater, cheaterCombinedTests,
            new Helpers.Test.Args("Saturxday", "Saturday", 200_000_000)),

    };

    if (!Helpers.runTests(tests)) {
      System.out.println("Some tests failed!");
      System.exit(-1);
    }

    if (runPerfTests) {
      Helpers.runPerfTests(tests);
    }

    // Found a dictionary at https://github.com/dwyl/english-words
    words = Helpers.loadDictionary("words_alpha.txt", wordLengthLimit);

    // Sort the words by length. See description of buildFullNeighborMap().
    long startTime = System.nanoTime();
    Collections.sort(words, new Comparator<String>() {
      @Override
      public int compare(String a, String b) {
        return a.length() - b.length();
      }
    });
    double sortMS = (System.nanoTime() - startTime) / 1_000_000.0;
    System.out.format("Sorted words in %,.2fms\n\n", sortMS);

    // Build the full neighbor map all at once (serial or parallel), or set up for lazy work.
    if (buildFullMap) {
      if (parallelFullBuild) {
        LevenshteinParallel.buildFullNeighborMapParallel();
      } else {
        buildFullNeighborMap();
      }
      Helpers.neighborAnalysis(neighbors, wordLengthLimit);
    } else {
      if (parallelLazyBFS) {
        LevenshteinParallel.LazyFindPathBFS.setupThreadPool();
      }
    }
    wordLengthStarts = buildWordLengthStarts(
        wordLengthLimit); // For lazy neighbor finding in all cases.

    // These are some sample pairs used to test and time our search algorithms.
    if (runSamplePairs) {
      long searchStartTime = System.nanoTime();

      findPathBFS("dog", "cat");
      findPathBFS("dog", "smart");
      findPathBFS("dog", "quack");

      // These were originally chosen randomly from the set of words with neighbors.
      // Turns our some are quite good test cases.
      findPathBFS("angerly", "invaded");
      findPathBFS("vulgates", "gumwood");
      findPathBFS("sweetly", "raddles");
      findPathBFS("lenten", "chiffonnieres");
      findPathBFS("cradlemen", "discreation");
      findPathBFS("blinkingly", "taupou");
      findPathBFS("protanopia", "interiorist");
      findPathBFS("outchid", "paramountly");
      findPathBFS("bldr", "rewrote");
      findPathBFS("evacuee", "fall");
      findPathBFS("monkey", "business");

      double searchMS = (System.nanoTime() - searchStartTime) / 1000000.0;
      System.out.format("Total search time: %,.2fms\n\n", searchMS);

      if (!buildFullMap) {
        Helpers.neighborAnalysis(neighbors, wordLengthLimit);
      }
    }

    if (letTheUserPlay) {
      Scanner scanner = new Scanner(System.in);
      while (true) {
        System.out.println("Enter two words separated by a space, or nothing to exit:");
        String line = scanner.nextLine();
        if (line.length() == 0) {
          break;
        }
        String[] pair = line.trim().split("\\s+");
        if (pair.length != 2) {
          System.out.println("You need to enter two words!");
          continue;
        }
        if (getNeighborsWithLazyBuild(pair[0]).size() == 0) {
          System.out.printf("'%s' has no neighbors, sorry!\n", pair[0]);
          continue;
        }
        if (getNeighborsWithLazyBuild(pair[1]).size() == 0) {
          System.out.printf("'%s' has no neighbors, sorry!\n", pair[1]);
          continue;
        }

        findPathBFS(pair[0], pair[1]);
      }
      scanner.close();
    }

    if (parallelLazyBFS) {
      LevenshteinParallel.LazyFindPathBFS.cleanupThreadPool();
    }

    System.out.println("Done.");
  }
}
