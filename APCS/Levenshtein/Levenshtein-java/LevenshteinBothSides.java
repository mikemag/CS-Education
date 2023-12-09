import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

// An implementation of the AP CS Levenshtein distance project, inspired by Tristen Yim's idea,
// from March 2023, of searching from both sides simultaneously.
//
// This builds upon the other methods shown here, using a BFS to find the end word, but searches
// from the start and end word at the same time, looking for where they meet in the middle. The
// challenging part is keeping track of the paths on each side and joining them once we've found
// an intersection.
//
// This is a port of the C# version, and makes for a fun comparison between the languages.
// https://github.com/mikemag/CS-Education/blob/master/APCS/Levenshtein/Levenshtein-cs/

public class LevenshteinBothSides {

  static class WordsToParents extends HashMap<String, ArrayList<String>> {
    // No "using" in Java. In C#, I can do: using WordsToParents = Dictionary<string, List<string>>;
  }

  record FindResult(int length, ArrayList<String> paths) {
    // Java can't return multiple values from a function, so compensate with this.
  }

  // --------------------------------------------------------------------------------------------------
  // Finding and building paths

  // Find paths between words, looking from both sides.
  //
  // On each side we have an explored area, and a frontier of neighbors of that explored area. We grow each
  // frontier alternatively, and stop when frontiers intersect. Then we join up paths from both sides.
  private static FindResult findPaths(String w1, String w2) {
    var leftFrontier = new WordsToParents();
    leftFrontier.put(w1, new ArrayList<String>());
    var leftExplored = new WordsToParents();

    var rightFrontier = new WordsToParents();
    rightFrontier.put(w2, new ArrayList<String>());
    var rightExplored = new WordsToParents();

    int length = 0;

    while (!leftFrontier.isEmpty() && !rightFrontier.isEmpty()) {
      System.out.format("Left frontier: %d, right frontier: %d\n", leftFrontier.size(),
          rightFrontier.size());

      // Have we found words on one side which match words on the other side?
      var intersection = new HashSet<String>(leftFrontier.keySet());
      intersection.retainAll(rightFrontier.keySet());
      if (!intersection.isEmpty()) {
        // We've found some words in the middle, so we're done! Let's collect the paths.
        return new FindResult(length,
            buildPaths(intersection, leftFrontier, leftExplored, rightFrontier, rightExplored));
      }

      // Advance the smaller frontier, favoring the starting word (left side).
      if (leftFrontier.size() <= rightFrontier.size()) {
        leftFrontier = advanceFrontier(leftFrontier, leftExplored);
      } else {
        rightFrontier = advanceFrontier(rightFrontier, rightExplored);
      }

      length++;
    }

    return new FindResult(0, new ArrayList<String>());
  }

  private static WordsToParents advanceFrontier(WordsToParents frontier, WordsToParents explored) {
    // Merge the current frontier into the explored space. This keeps history of where we've been, and
    // ensures we don't add any duplicates as we expand.
    explored.putAll(frontier);

    // Build a new frontier of the neighbors of the current frontier, and use that for the next round.
    var newFrontier = new WordsToParents();

    for (var item : frontier.entrySet()) {
      for (var n : findNeighbors(item.getKey())) {
        if (explored.containsKey(n)) {
          continue;
        }

        var parentList = newFrontier.computeIfAbsent(n, k -> new ArrayList<>());
        parentList.add(item.getKey());
      }
    }

    return newFrontier;
  }

  // Given a set of intersecting words, and all the words we've discovered on both sides, build all the paths
  // between our two words. I've chosen to simply build the paths from each side and join them at the
  // intersections.
  private static ArrayList<String> buildPaths(HashSet<String> intersection,
      WordsToParents leftFrontier, WordsToParents leftExplored, WordsToParents rightFrontier,
      WordsToParents rightExplored) {
    var paths = new ArrayList<String>();

    for (var w : intersection) {
      var pathsLeft = buildPathsForSide(leftFrontier.get(w), true, leftExplored);
      var pathsRight = buildPathsForSide(rightFrontier.get(w), false, rightExplored);

      if (pathsLeft.isEmpty()) {
        for (var pr : pathsRight) {
          paths.add(w + " -> " + pr);
        }
      } else if (pathsRight.isEmpty()) {
        for (var pl : pathsLeft) {
          paths.add(pl + " -> " + w);
        }
      } else {
        for (var pl : pathsLeft) {
          for (var pr : pathsRight) {
            paths.add(pl + " -> " + w + " -> " + pr);
          }
        }
      }
    }

    return paths;
  }

  // Recursively build a list of paths for one side, given a list of starting words in the explored area.
  private static ArrayList<String> buildPathsForSide(List<String> l, boolean left,
      WordsToParents explored) {
    var paths = new ArrayList<String>();

    for (var s : l) {
      var parents = explored.get(s);
      if (parents.isEmpty()) {
        paths.add(s);
      } else {
        for (var p : buildPathsForSide(parents, left, explored)) {
          if (left) {
            paths.add(p + " -> " + s);
          } else {
            paths.add(s + " -> " + p);
          }
        }
      }
    }

    return paths;
  }

  // --------------------------------------------------------------------------------------------------
  // Running pairs of words and showing results

  private static boolean doPair(String w1, String w2, int expectedLength, int expectedPathCount) {
    System.out.format("Finding all shortest paths from '%s' to '%s'\n", w1, w2);
    long startTime = System.nanoTime();
    var result = findPaths(w1, w2);
    double findMS = (System.nanoTime() - startTime) / 1_000_000.0;

    boolean correct = true;
    System.out.format("Found %d paths of length %d for '%s' to '%s' in %,.2fms\n",
        result.paths.size(), result.length, w1, w2, findMS);
    if (result.length != expectedLength || result.paths.size() != expectedPathCount) {
      correct = false;
      System.out.format("Whoa!!!! This isn't the right answer! Expected %d paths of %d length.\n",
          expectedPathCount, expectedLength);
    }

    if (!result.paths.isEmpty()) {
      // Copy-paste to http://www.webgraphviz.com/
      System.out.format("digraph %s_%s_%d{{concentrate=true;\n", w1, w2, result.length);
      System.out.println(String.join("\n", result.paths));
      System.out.println("}");
    }

    System.out.println();
    return correct;
  }

  public static List<String> words = null;
  public static int[] wordLengthStarts = null;

  public static void main(String[] args) throws IOException {
    // Load our dictionary of English words. Dictionary from https://github.com/dwyl/english-words
    final String dictionaryFilename = "./words_alpha.txt";
    words = Files.readAllLines(Paths.get(dictionaryFilename));
    System.out.format("Loaded %d words from %s\n\n", words.size(), dictionaryFilename);

    // Sort the dictionary by word length
    words.sort(Comparator.comparingInt(String::length));

    // Find where each word length starts in our dictionary. Now we can quickly find, say, words of length 4.
    wordLengthStarts = buildWordLengthStarts(32);

    boolean correct = true;
    correct &= doPair("dog", "dog", 0, 0);
    correct &= doPair("dog", "dot", 1, 1);
    correct &= doPair("dog", "cat", 3, 6);
    correct &= doPair("dog", "smart", 5, 51);
    correct &= doPair("dog", "quack", 7, 107);
    correct &= doPair("monkey", "business", 13, 1);
    correct &= doPair("vulgates", "gumwood", 0, 0);

    System.out.println(correct ? "Everything worked." : "Oops!! At least one test failed :(");
  }

  // --------------------------------------------------------------------------------------------------
  // Edit distance between two words

  // To start, let's assume we're given two words of equal lengths. Can we determine quickly and
  // easily if they are distance 1 or not?
  //
  // Sure. Since they're the same length the only option to make them equal is substitution, thus we
  // just need to count the number of different letters, and stop after we find more than one.
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

  // Next, for words of different lengths to be within 1 edit distance they need to be within 1
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
  // the longer word.
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
  private static int editDistance(String w1, String w2) {
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

  // --------------------------------------------------------------------------------------------------
  // Finding neighbors

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


  private static ArrayList<String> findNeighbors(String w) {
    var neighbors = new ArrayList<String>();
    var end = words.size();
    if (w.length() + 2 < wordLengthStarts.length) {
      end = wordLengthStarts[w.length() + 2];
    }

    for (int j = wordLengthStarts[w.length() - 1]; j < end; j++) {
      if (editDistance(w, words.get(j)) == 1) {
        neighbors.add(words.get(j));
      }
    }

    return neighbors;
  }
}
