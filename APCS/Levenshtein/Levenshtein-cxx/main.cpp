// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

// AP CS Levenshtein distance project in C++ instead of Java
//
// This is a companion to the Java version I implemented. It's got many of the
// exact same algorithms and implementations to show the perf difference between
// the two.
//
// See the comments in the Java version for an explanation of what's going on
// here in general. I'll add C++-specific notes here.
//
// The the sample_output_* files for timing results. Note however that direct
// comparison of the microbenchmarks to the Java version are inaccurate. I had
// to fight more with the C++ compiler to get it to not optimize away the perf
// tests, and the result is more overhead per call. Note the difference in time
// to call an empty function: 0.0031us for C++ and 0.0004us for Java.
//
// That said, right out of the gate the basic edit distance impl in C++ 0.0856us
// vs. 0.8586us for the same thing in Java, and 0.1742us for out best hand-tuned
// effort in Java.
//
// Instead, we have to look at the overall timings of building the maps and
// finding results.
//
//                          Java                C++
// Full map build:          355.01s             175.40s
//   - avg loop time:       0.0178us            0.0088us
//
// Lazy search and build:   187,745.10ms        104,423.13ms
//
// All tests and timings taken on my Mid-2012 MacBook Pro, macOS Mojave 10.14.5,
// 2.6 GHz Intel Core i7 (4 cores, hyperthreaded, 256 KB L2, 6 MB L3), 8 GB 1600
// MHz DDR3, plugged in, run from the command line with nothing else running.
//
// $ c++ --version
// Apple LLVM version 10.0.1 (clang-1001.0.46.4)
// Target: x86_64-apple-darwin18.6.0
// clang++ -std=c++17 -stdlib=libc++ -O2 ...

#include <stdio.h>

#include <locale.h>
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <deque>
#include <fstream>
#include <functional>
#include <iostream>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

using namespace std;

// Load the dictionary of words. There's just under 371k words in the file.
static vector<string> words;

static void loadDictionary(const string &filename, int lengthLimit) {
  ifstream input(filename);
  string s;
  while (getline(input, s)) {
    if (s.length() && s.back() == '\r') {
      s.pop_back();
    }
    if (s.size() <= lengthLimit) {
      words.push_back(s);
    }
  }
  printf("Loaded %'lu words from %s\n\n", words.size(), filename.c_str());
}

// First attempt based on the pseudo code at
// https://en.wikipedia.org/wiki/Levenshtein_distance. This is the classic
// non-recursive version, implemented as obviously as possible.
//
// This algorithm is O(n^2) in the length of the strings, and the constants in
// it are pretty high, too, making for an expensive function when processing a
// real dictionary of words.
//
// We get wins over the Java version immediately in two ways:
// 1) The array is stack allocated.
// 2) The C++ compiler does the obvious inlining and hoisting.
//
// If you wanna have some fun, toss some of these functions into
// Compiler Explorer at https://godbolt.org/
static int editDistanceBasic(const string &w1, const string &w2) {
  int d[w1.length() + 1][w2.length() + 1];

  for (int i = 0; i <= w1.length(); i++) {
    d[i][0] = i;
  }
  for (int i = 1; i <= w2.length(); i++) {
    d[0][i] = i;
  }

  for (int j = 1; j <= w2.length(); j++) {
    for (int i = 1; i <= w1.length(); i++) {
      int substitutionCost = 0;
      if (w1[i - 1] != w2[j - 1]) {
        substitutionCost = 1;
      }

      d[i][j] = min(d[i - 1][j] + 1,                           // deletion
                    min(d[i][j - 1] + 1,                       // insertion
                        d[i - 1][j - 1] + substitutionCost));  // substitution
    }
  }

  return d[w1.length()][w2.length()];
}

// Static pre-allocated memory for various implementations of the edit
// distance function.
static int StaticDistanceArray[30][30];

static void editDistanceStaticSetup(int wordLengthLimit) {
  for (int i = 0; i <= wordLengthLimit; i++) {
    StaticDistanceArray[i][0] = i;
  }
  for (int i = 1; i <= wordLengthLimit; i++) {
    StaticDistanceArray[0][i] = i;
  }
}

// This version replaces local array with reuse of a static array. Since the
// first row and column never change, we can initialize it once as well.
//
// This is a nice win in the Java version, but is only a minor win here. We get
// a slight improvement from not having to init the stack allocated array each
// time.
//
// Of course, we have to allocate this static array when the program starts. See
// editDistanceStaticSetup().
static int editDistanceStaticArray(const string &w1, const string &w2) {
  for (int j = 1; j <= w2.length(); j++) {
    for (int i = 1; i <= w1.length(); i++) {
      int substitutionCost = 0;
      if (w1[i - 1] != w2[j - 1]) {
        substitutionCost = 1;
      }

      StaticDistanceArray[i][j] =
          min(StaticDistanceArray[i - 1][j] + 1,      // deletion
              min(StaticDistanceArray[i][j - 1] + 1,  // insertion
                  StaticDistanceArray[i - 1][j - 1] +
                      substitutionCost));  // substitution
    }
  }

  return StaticDistanceArray[w1.length()][w2.length()];
}

// This is the manually hoisted version from the Java impl, and shows quite
// clearly how unnecessary this is in C++. I didn't bother to bring over the
// other few versions of manually hoisting from Java since the result is the
// same.
static int editDistanceHoistedLengths(const string &w1, const string &w2) {
  int w1l = w1.length();
  int w2l = w2.length();
  for (int j = 1; j <= w2l; j++) {
    for (int i = 1; i <= w1l; i++) {
      int substitutionCost = 0;
      if (w1[i - 1] != w2[j - 1]) {
        substitutionCost = 1;
      }

      StaticDistanceArray[i][j] =
          min(StaticDistanceArray[i - 1][j] + 1,      // deletion
              min(StaticDistanceArray[i][j - 1] + 1,  // insertion
                  StaticDistanceArray[i - 1][j - 1] +
                      substitutionCost));  // substitution
    }
  }

  return StaticDistanceArray[w1l][w2l];
}

// The problem statement never said we have to actually compute the Levenshtein
// distance between any words. It said we have to find all words within 1
// distance of each other, and build a neighbors map. So why did we even
// implement the real thing?? :)
//
// To start, let's assume we're given two words of equal lengths. Can we
// determine quickly and easily if they are distance 1 or not?
//
// Sure. Since they're the same length the only option to make them equal
// is substitution, thus we just need to count the number of different letters,
// and stop after we find more than one.
//
// This is O(n) in the length of the strings, and a fraction of any of the
// methods above.
//
// This also shows nicely how fundamental algorithm changes can be a nice win no
// matter what language you use :)
static int editDistanceEqual(const string &w1, const string &w2) {
  int diffs = 0;
  for (int i = 0; i < w1.length(); i++) {
    if (w1[i] != w2[i]) {
      diffs++;
      if (diffs > 1) {
        break;
      }
    }
  }
  return diffs;
}

// Next, for words to be within 1 distance they need to be within 1 character in
// length of each other.
//
// Also, substitution is no longer an option. Since they're off by one, deletion
// and insertion are the only options. Consider these examples, in order, to
// learn what the algorithm is doing.
//
// DOG & DOGO
// DOG & DOOG
// DOG & ADOG
// DOG & ACAT
//
// We'll consider the shorter word first and the longer word second, and only
// allow deletion in the longer word. Again, this is O(n) in the length of the
// strings and way faster than the real algorithm.
static int editDistanceOffByOne(const string &w1, const string &w2) {
  int diffs = 0;
  int w1i = 0;
  int w2i = 0;
  while (w1i < w1.length() && diffs < 2) {
    if (w1[w1i] != w2[w2i]) {
      diffs++;
      w2i++;  // Same as deletion.
    } else {
      w1i++;
      w2i++;
    }
  }

  if (w2i < w2.length()) {
    diffs++;  // Deletion of the last char of w2.
  }

  return diffs;
}

// So let's combine the two to work with words of any length in any order,
// because you need this logic somewhere to make use of these.
static int editDistanceCheater(const string &w1, const string &w2) {
  int ed = 0;
  if (w1.length() == w2.length()) {
    ed = editDistanceEqual(w1, w2);
  } else {
    int d = w2.length() - w1.length();
    if (d == 1) {
      ed = editDistanceOffByOne(w1, w2);
    } else if (d == -1) {
      ed = editDistanceOffByOne(w2, w1);
    }
  }
  return ed;
}

// This builds the neighbor map. Here are the optimizations from the obvious
// version:
//
// 1. Only compute half the matrix, cuts the run time in half. Being a neighbor
// is "commutative", i.e., order doesn't matter. If w1 is a neighbor of w2, then
// w2 is a neighbor to w1.
//
// 2. We hoist everything we can.
//
// 3. We inline the "combined" cheater function to allow us to reuse the length
// computations.
//
// 4. Sort the dictionary of words by their length. (Miyoshi's idea.) It came
// sorted alphabetically, so we end up sorted by length then lexically. This
// allows us to stop looking for neighbors when we see a string 2 or more
// longer. This is a multiple bonus actually and cuts the runtime by a bit
// more than half:
//
// a) We consider far fewer words than we otherwise would for a nice win.
//
// b) There are cache and memory access effects we benefit from as we drag much
// less memory thru the cache.
//
// c) Since we do all equal sizes, then all off by ones, then stop, we get much
// better branch prediction.
typedef const string *StringHandle;
static unordered_map<string, vector<StringHandle>> neighbors;

static void buildFullNeighborMap(const vector<string> &words) {
  long totalChecks = 0;
  long skippedChecks = 0;
  long totalNeighbors = 0;
  long totalCompsNeeded = (long)words.size() * (long)words.size() / 2;
  auto startTime = chrono::high_resolution_clock::now();
  auto lastTime = startTime;
  for (int i = 0; i < words.size(); i++) {
    auto &w1 = words[i];
    for (int j = i + 1; j < words.size(); j++) {
      totalChecks++;
      auto &w2 = words[j];
      int ed = 0;
      if (w1.length() == w2.length()) {
        ed = editDistanceEqual(w1, w2);
      } else {
        int d = w2.length() - w1.length();
        if (d == 1) {
          ed = editDistanceOffByOne(w1, w2);
        } else {
          // d > 1. If words is sorted by length, then everything past j is too
          // long.
          skippedChecks += words.size() - j;
          break;
        }
      }
      if (ed == 1) {
        totalNeighbors++;
        auto &nl = neighbors[w1];
        nl.push_back(&(words[j]));
        auto &nl2 = neighbors[w2];
        nl2.push_back(&(words[i]));
      }
    }
    if (i % 1000 == 0) {
      auto currentTime = chrono::high_resolution_clock::now();
      chrono::duration<float, milli> elapsedMS = currentTime - lastTime;
      chrono::duration<float, micro> elapsedUS = elapsedMS;
      if (elapsedMS > 1s) {
        long tandsChecks = totalChecks + skippedChecks;
        printf("Finsihed '%s', %'.0fm/%'.0fm (%.2f%%), %'ld neighbors\n",
               w1.c_str(), tandsChecks / 1000000.0,
               totalCompsNeeded / 1000000.0,
               (double)tandsChecks / (double)totalCompsNeeded * 100.0,
               totalNeighbors);
        elapsedMS = currentTime - startTime;
        chrono::duration<float, micro> elapsedUS = elapsedMS;
        chrono::duration<float> elapsedS = elapsedMS;
        printf("Elapsed time %'.2fs, average time %'.04fus, for %'ld calls\n",
               elapsedS.count(), elapsedUS.count() / totalChecks, totalChecks);
        long callsLeft = totalCompsNeeded - tandsChecks;
        // The ETA is rough since we're skipping an unknown number of checks per
        // row.
        auto etaS = (elapsedS.count() / totalChecks) * callsLeft;
        printf("Estimate %'.2f minutes to go...\n\n", etaS / 60);
        lastTime = currentTime;
      }
    }
  }

  auto currentTime = chrono::high_resolution_clock::now();
  chrono::duration<float, micro> elapsedUS = currentTime - startTime;
  chrono::duration<float> elapsedS = elapsedUS;
  printf("Skipped %'ld checks\n", skippedChecks);
  printf(
      "Done.\nElapsed time %'.2fs, average loop time %'.04fus, for %'ld calls "
      "and %'ld total neighbors\n\n",
      elapsedS.count(), elapsedUS.count() / totalChecks, totalChecks,
      totalNeighbors);
}

// This builds the neighbor map "lazily", one row at a time and on-demand. This
// allows us to skip building the full map in favor of only building the pieces
// we need to perform a given search. The map is essentially a cache.
//
// I did not port over the version with the wordLengthStarts array to shorten
// the start of each row.
static void lazyBuildNeighborMap(const string &w1, vector<StringHandle> &nl) {
  for (int j = 0; j < words.size(); j++) {
    const auto w2 = &(words[j]);
    int ed = 0;
    if (w1.length() == w2->length()) {
      ed = editDistanceEqual(w1, *w2);
    } else {
      int d = w2->length() - w1.length();
      if (d == 1) {
        ed = editDistanceOffByOne(w1, *w2);
      } else if (d == -1) {
        ed = editDistanceOffByOne(*w2, w1);
      } else if (d > 1) {
        // If words is sorted by length, then everything past j is too long.
        break;
      }
    }
    if (ed == 1) {
      nl.push_back(w2);
    }
  }
}

// Helper to learn about the properties of the neighbor maps.
static void neighborAnalysis(int wordLengthLimit) {
  printf("%lu total words with any neighbors\n", neighbors.size());

  int kmin = INT_MAX;
  int kmax = 0;
  int ktotal = 0;
  int nmin = INT_MAX;
  int nmax = 0;
  int ntotal = 0;
  int m[wordLengthLimit + 1][wordLengthLimit + 1];
  for (int i = 0; i <= wordLengthLimit; i++) {
    for (int j = 0; j <= wordLengthLimit; j++) {
      m[i][j] = 0;
    }
  }
  for (const auto &e : neighbors) {
    int klen = e.first.length();
    kmin = min(kmin, klen);
    kmax = max(kmax, klen);
    ktotal += klen;

    int nlen = e.second.size();
    nmin = min(nmin, nlen);
    nmax = max(nmax, nlen);
    ntotal += nlen;

    for (const auto &s : e.second) {
      m[klen][s->length()]++;
    }
  }

  printf("Min/avg/max word size: %d/%.2f/%d\n", kmin,
         (double)ktotal / neighbors.size(), kmax);
  printf("Min/avg/max neighbor count: %d/%.2f/%d\n\n", nmin,
         (double)ntotal / neighbors.size(), nmax);

  // for (int i = 0; i <= wordLengthLimit; i++) {
  //   for (int j = 0; j <= wordLengthLimit; j++) {
  //     printf("%7d ", m[i][j]);
  //   }
  //   printf("\n");
  // }
  // printf("\n");
}

// Used to print the paths we discover, so we keep parent references as we
// explore the graph.
class Node {
 public:
  Node(StringHandle word) : parent(0), word(word) {}
  Node(int parentIdx, StringHandle word) : parent(parentIdx), word(word) {}

  StringHandle getWord() const { return word; }
  int getParentIdx() const { return parent; }

 private:
  StringHandle word;
  int parent;
};

static vector<StringHandle> buildPath(const vector<Node> &nodes, int i) {
  vector<StringHandle> path;
  while (i > 0) {
    path.insert(path.begin(), nodes[i].getWord());
    i = nodes[i].getParentIdx();
  }
  return path;
}

static void printPath(const vector<StringHandle> &path, const string &dest) {
  for (const auto &s : path) {
    printf("%s -> ", s->c_str());
  }
  printf("%s\n", dest.c_str());
}

// Helper to find the address of a given word in the global word list.
static StringHandle findWordAddr(const string &w) {
  auto it = find(words.begin(), words.end(), w);
  if (it == words.end()) {
    printf("Couldn't find '%s' in the words list!\n", w.c_str());
    exit(-1);
  }
  return &*it;
}

// Traverse the neighbor graph from w1 to w2, finding all shortest paths. Goes
// breadth-first. Trims out loops to make it a tree a level at a time as it
// goes.
//
// This has an interesting optimization which was necessary to get it to run
// faster than the Java version. The obvious port was slower due to the high
// degree of memory management for the Node objects. Java's GC and memory
// allocator gloss over this problem, but it was obvious with heap allocated
// Nodes and shared pointers to them here.
//
// Instead, we make an arena for the Nodes in the form of a simple vector<Node>,
// and all node references are simply indexes into this vector. So we have a
// NodeIndex type to represent this, and to enqueue a node we first create it in
// the vector and then take its index. The arena is destroyed all at once on
// return from the function.
//
// The other optimization is to not copy std::strings into things like the
// parents set, the nodes, etc. You can't use a std::string& in things like
// unordered_set<> so I made a StringHandle type which is just a std::string*.
static void findPathBFS(const string &w1, const string &w2) {
  printf("Find path from '%s' to '%s'\n", w1.c_str(), w2.c_str());
  auto startTime = chrono::high_resolution_clock::now();

  auto pw1 = findWordAddr(w1);
  auto pw2 = findWordAddr(w2);
  int totalWords = 0;
  int minPathLength = INT_MAX;
  int totalMinPaths = 0;

  typedef size_t NodeIndex;
  vector<Node> nodes;
  deque<NodeIndex> leavesQueue;
  unordered_set<StringHandle> parents;  // To remove loops.

  string emptyString = "";
  nodes.emplace_back(&emptyString);
  NodeIndex levelSentinel = nodes.size() - 1;  // Marks the end of each level.
  leavesQueue.push_back(levelSentinel);

  nodes.emplace_back(pw1);
  leavesQueue.push_back(nodes.size() - 1);

  while (!leavesQueue.empty()) {
    auto nidx = leavesQueue.front();
    leavesQueue.pop_front();
    if (nidx == levelSentinel) {
      // Stop when we can't find anything, or if we found paths on the last
      // level.
      if (leavesQueue.empty() || totalMinPaths > 0) {
        break;
      }
      for (auto i : leavesQueue) {
        parents.insert(nodes[i].getWord());
      }
      leavesQueue.push_back(levelSentinel);
      continue;
    }
    totalWords++;

    const auto &n = nodes[nidx];
    auto &nl = neighbors[*n.getWord()];
    if (nl.size() == 0) {
      lazyBuildNeighborMap(*n.getWord(), nl);
    }

    for (const auto &w : nl) {
      if (w == pw2) {
        auto path = buildPath(nodes, nidx);
        if (path.size() < minPathLength) {
          minPathLength = path.size();
          printf("Shortest path lengths: %d\n", minPathLength);
          // Copy-paste to http://www.webgraphviz.com/
          printf("digraph %s_%s_%d{concentrate=true;\n", w1.c_str(), w2.c_str(),
                 minPathLength);
        }
        totalMinPaths++;
        printPath(path, *w);
      }
      if (parents.find(w) == parents.end()) {
        nodes.emplace_back(nidx, w);
        leavesQueue.push_back(nodes.size() - 1);
      }
    }
  }

  if (totalMinPaths > 0) {
    printf("}\n");
  }

  auto currentTime = chrono::high_resolution_clock::now();
  chrono::duration<float, milli> searchMS = currentTime - startTime;
  printf("Done %'.2fms, considered %'d words for %'d total minimum paths\n\n",
         searchMS.count(), totalWords, totalMinPaths);
}

// Some helpers for tunning functional and perf tests.
struct Test {
  struct Args {
    Args(const string &w1, const string &w2, long i) : w1(w1), w2(w2), i(i) {}
    const string w1;
    const string w2;
    const long i;
  };

  Test(const string &name,
       const function<int(const string &, const string &)> func,
       vector<Args> tests, const Args perftest)
      : name(name), func(func), tests(tests), perftest(perftest) {}

  const string name;
  const function<int(const string &, const string &)> func;
  const vector<Args> tests;
  const Args perftest;
};

static bool runTests(const vector<Test> &tests) {
  bool passed = true;
  for (const auto &t : tests) {
    if (!t.tests.empty()) {
      for (const auto &a : t.tests) {
        auto d = t.func(a.w1, a.w2);
        printf("'%s': '%s' -> '%s' = %d", t.name.c_str(), a.w1.c_str(),
               a.w2.c_str(), d);
        if (d != a.i) {
          printf(" -- failed, expected %ld", a.i);
          passed = false;
        }
        printf("\n");
      }
    }
  }
  printf("\n");
  return passed;
}

static void runPerfTests(const vector<Test> &tests) {
  for (const auto &t : tests) {
    const auto pt = t.perftest;
    auto start = chrono::high_resolution_clock::now();
    for (long i = 0; i < pt.i; i++) {
      t.func(pt.w1, pt.w2);
    }
    auto end = chrono::high_resolution_clock::now();
    chrono::duration<float, milli> elapsedMS = end - start;
    chrono::duration<float, micro> elapsedUS = elapsedMS;
    printf(
        "Avg time %'.04fus -- %s, '%s' -> '%s', elapsed time %'.4fms, "
        "%'ld calls\n",
        elapsedUS.count() / pt.i, t.name.c_str(), pt.w1.c_str(), pt.w2.c_str(),
        elapsedMS.count(), pt.i);
  }
  printf("\n");
}

static int editDistanceEmpty(const string &w1, const string &w2) { return 0; }

int main() {
  setlocale(LC_NUMERIC, "");  // For commas in printf via '

  // Options
  bool buildFullMap = false;
  bool doPerfTests = false;
  bool runSamplePairs = true;
  int wordLengthLimit = 30;  // The longest word in words_alpha.txt is 29.
  editDistanceStaticSetup(wordLengthLimit);

  vector<Test::Args> commonTests = {{"dog", "dot", 1},
                                    {"dog", "dog", 0},
                                    {"Saturday", "Sunday", 3},
                                    {"sitting", "kitten", 3}};
  vector<Test::Args> equalSizeTests = {
      {"dog", "dot", 1}, {"dog", "dog", 0}, {"dog", "cat", 2}};
  vector<Test::Args> offByOneTests = {{"dog", "dogo", 1},
                                      {"dog", "doog", 1},
                                      {"dog", "adog", 1},
                                      {"dog", "acat", 3}};
  vector<Test::Args> cheaterCombinedTests = {{"dog", "dot", 1},
                                             {"dog", "dog", 0},
                                             {"Saturday", "Sunday", 0},
                                             {"sitting", "kitten", 3}};

  vector<Test> tests = {
      {"Empty func", editDistanceEmpty, vector<Test::Args>(),
       Test::Args("Saturday", "Sunday", 500000000L)},

      {"editDistanceBasic", editDistanceBasic, commonTests,
       Test::Args("Saturday", "Sunday", 30000000L)},
      {"editDistanceStaticArray", editDistanceStaticArray, commonTests,
       Test::Args("Saturday", "Sunday", 30000000L)},
      {"editDistanceHoistedLengths", editDistanceHoistedLengths, commonTests,
       Test::Args("Saturday", "Sunday", 30000000L)},

      {"Cheater equal lengths", editDistanceEqual, equalSizeTests,
       Test::Args("Saturday", "Satuxday", 80000000L)},
      {"Cheater off by one", editDistanceOffByOne, offByOneTests,
       Test::Args("Saturday", "Saturxday", 80000000L)},
      {"Cheater combined", editDistanceCheater, cheaterCombinedTests,
       Test::Args("Saturxday", "Saturday", 80000000L)},
  };

  if (!runTests(tests)) {
    printf("Some tests failed!\n");
    exit(-1);
  }

  if (doPerfTests) {
    runPerfTests(tests);
  }

  loadDictionary("words_alpha.txt", wordLengthLimit);

  // Sort the words by length. See description of buildFullNeighborMap().
  auto startTime = chrono::high_resolution_clock::now();
  std::stable_sort(begin(words), end(words), [](const auto &a, const auto &b) {
    return a.length() < b.length();
  });
  auto endTime = chrono::high_resolution_clock::now();
  chrono::duration<float, milli> sortMS = endTime - startTime;
  printf("Sorted words in %'.2fms\n\n", sortMS.count());

  if (buildFullMap) {
    buildFullNeighborMap(words);
    neighborAnalysis(wordLengthLimit);
  }

  if (runSamplePairs) {
    auto searchStartTime = chrono::high_resolution_clock::now();

    findPathBFS("dog", "cat");
    findPathBFS("dog", "smart");
    findPathBFS("dog", "quack");

    // The same tests from the Java version.
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

    auto searchEndTime = chrono::high_resolution_clock::now();
    chrono::duration<float, milli> searchMS = searchEndTime - searchStartTime;
    printf("Total search time: %'.2fms\n\n", searchMS.count());

    if (!buildFullMap) {
      neighborAnalysis(wordLengthLimit);
    }
  }

  printf("Done.\n");
}
