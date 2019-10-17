"""
Copyright (c) Michael M. Magruder (https://github.com/mikemag)

This source code is licensed under the MIT license found in the
LICENSE file in the root directory of this source tree.


AP CS Levenshtein distance project.

Implementation of the project, which is to find a path between two words making a
single edit at each step only using valid words.

This is a port of the Java version of this project to Python for the purpose of showing
a bit of Python. This not a full port of everything in the Java version, and it kinda
requires that you've looked at the Java version first.

Python is quite a bit slower than Java and C++, even with some work to optimize it.
This runs faster with pypy, of course, but it's still a lot slower.

                         Java           C++            Python3          Pypy3
Full map build:          355.01s        175.40s        19,621.47s       1,702.41s
  - avg loop time:       0.0178us       0.0088us       0.9835us         0.0853us
  - total search time:   4,407.45ms     4,248.25ms     37,344.17ms      7,536.31ms
Lazy search and build:   3.13 min       1.74 min       181.52 min       15.17 min

Full map build:          2.0x           1x             112.5x           9.7x
  - avg loop time:       2.0x           1x             111.8x           9.7x
  - total search time:   1.04x          1x             8.8x             1.8x
Lazy search and build:   1.8x           1x             104.3x           8.7x

All tests and timings taken on my Mid-2012 MacBook Pro, macOS Mojave 10.14.5,
2.6 GHz Intel Core i7 (4 cores, hyperthreaded, 256 KB L2, 6 MB L3), 8 GB 1600 MHz DDR3,
plugged in, run from the command line with nothing else running.

$ python3 --version
Python 3.7.3

$ pypy3 --version
Python 3.6.1 (784b254d669919c872a505b807db8462b6140973, May 08 2019, 09:30:49)
[PyPy 7.1.1-beta0 with GCC 4.2.1 Compatible Clang 4.0.1 (tags/RELEASE_401/final)]

$ c++ --version
Apple LLVM version 10.0.1 (clang-1001.0.46.4)
Target: x86_64-apple-darwin18.6.0
clang++ -std=c++17 -stdlib=libc++ -O2 ...

$ java --version
java 12.0.1 2019-04-16
Java(TM) SE Runtime Environment (build 12.0.1+12)
Java HotSpot(TM) 64-Bit Server VM (build 12.0.1+12, mixed mode, sharing)

"""

import sys
import time
import timeit
from collections import deque

# Options
RUN_PERF_TESTS = True
RUN_SAMPLE_PAIRS = True

WORD_LENGTH_LIMIT = 30  # The longest word in words_alpha.txt is 29.

BUILD_FULL_MAP = False
LET_THE_USER_PLAY = False


# First attempt based on the pseudo code at
# https://en.wikipedia.org/wiki/Levenshtein_distance. This is the classic
# non-recursive version, implemented as obviously as possible.
#
# This algorithm is O(n^2) in the length of the strings, and the constants in
# it are pretty high, too, making for an expensive function when processing a
# real dictionary of words.
def edit_distance_basic(w1, w2):
    d = [[0 for y in range(len(w2) + 1)] for x in range(len(w1) + 1)]  # Note strange 2d array construction

    for i in range(1, len(w1) + 1):
        d[i][0] = i

    for i in range(1, len(w2) + 1):
        d[0][i] = i

    for j in range(1, len(w2) + 1):
        for i in range(1, len(w1) + 1):
            substitution_cost = 0
            if w1[i - 1] != w2[j - 1]:
                substitution_cost = 1

            d[i][j] = min(d[i - 1][j] + 1,  # deletion
                          d[i][j - 1] + 1,  # insertion
                          d[i - 1][j - 1] + substitution_cost)  # substitution

    return d[len(w1)][len(w2)]


# This version replaces allocation of a temporary array with reuse of a static
# array. Memory allocation is expensive relative to the rest of the function,
# and avoiding it cuts the execution time in half.
#
# Since the first row and column never change, we can initialize it once as
# well.

# Static pre-allocated memory for various implementations of the edit
# distance function.
STATIC_DISTANCE_ARRAY = None  # global


def edit_distance_static_setup(word_length_limit):
    global STATIC_DISTANCE_ARRAY
    STATIC_DISTANCE_ARRAY = [[0 for y in range(
        word_length_limit + 1)] for x in range(word_length_limit + 1)]

    for i in range(1, word_length_limit + 1):
        STATIC_DISTANCE_ARRAY[i][0] = i

    for i in range(1, word_length_limit + 1):
        STATIC_DISTANCE_ARRAY[0][i] = i


def edit_distance_no_alloc(w1, w2):
    for j in range(1, len(w2) + 1):
        for i in range(1, len(w1) + 1):
            substitution_cost = 0
            if w1[i - 1] != w2[j - 1]:
                substitution_cost = 1

            STATIC_DISTANCE_ARRAY[i][j] = min(
                STATIC_DISTANCE_ARRAY[i - 1][j] + 1,
                STATIC_DISTANCE_ARRAY[i][j - 1] + 1,
                STATIC_DISTANCE_ARRAY[i - 1][j - 1] + substitution_cost)

    return STATIC_DISTANCE_ARRAY[len(w1)][len(w2)]


# The problem statement never said we have to actually compute the Levenshtein
# distance between any words. It said we have to find all words within 1
# distance of each other, and build a neighbors map. So why did we even
# implement the real thing?? :)
#
# To start, let's assume we're given two words of equal lengths. Can we
# determine quickly and easily if they are distance 1 or not?
#
# Sure. Since they're the same length the only option to make them equal
# is substitution, thus we just need to count the number of different letters,
# and stop after we find more than one.
#
# This is O(n) in the length of the strings, and a fraction of any of the
# methods above.
def edit_distance_equal(w1, w2, w1l):
    diffs = 0
    i = 0
    while i < w1l:
        if w1[i] != w2[i]:
            diffs += 1
            if diffs > 1:
                break
        i += 1
    return diffs


# Next, for words to be within 1 distance they need to be within 1 character in
# length of each other.
#
# Also, substitution is no longer an option. Since they're off by one, deletion
# and insertion are the only options. Consider these examples, in order, to
# learn what the algorithm is doing.
#
# DOG & DOGO
# DOG & DOOG
# DOG & ADOG
# DOG & ACAT
#
# We'll consider the shorter word first and the longer word second, and only
# allow deletion in the longer word. Again, this is O(n) in the length of the
# strings and way faster than the real algorithm.
def edit_distance_off_by_one(w1, w2, w1l, w2l):
    # Requires w1 shorter than w2
    diffs = 0
    w1i = 0
    w2i = 0

    while w1i < w1l and diffs < 2:
        if w1[w1i] != w2[w2i]:
            diffs += 1
            w2i += 1  # Same as deletion.
        else:
            w1i += 1
            w2i += 1

    if w2i < w2l:
        diffs += 1  # Deletion of the last char of w2.

    return diffs


# So let's combine the two to work with words of any length in any order,
# because you need this logic somewhere to make use of these.
def edit_distance_cheater(w1, w2):
    w1l = len(w1)
    w2l = len(w2)
    ed = 0
    if w1l == w2l:
        ed = edit_distance_equal(w1, w2, w1l)
    else:
        d = w2l - w1l
        if d == 1:
            ed = edit_distance_off_by_one(w1, w2, w1l, w2l)
        elif d == -1:
            ed = edit_distance_off_by_one(w2, w1, w2l, w1l)
    return ed


# Load the dictionary of words. There's just under 371k words in the file.
def load_dictionary(filename, length_limit):
    words = []
    with open(filename) as f:
        for line in f:
            line = line.strip()
            if len(line) <= length_limit:
                words.append(line)
    print("Loaded {} words from {}\n\n".format(len(words), filename))
    return words


# This builds the neighbor map. Here are the optimizations from the obvious
# version:
#
# 1. Only compute half the matrix, cuts the run time in half. Being a neighbor
# is "commutative", i.e., order doesn't matter. If w1 is a neighbor of w2, then
# w2 is a neighbor to w1.
#
# 2. We hoist everything we can.
#
# 3. We inline the "combined" cheater function to allow us to reuse the length
# computations.
#
# 4. Sort the dictionary of words by their length. (Miyoshi's idea.) It came
# sorted alphabetically, so we end up sorted by length then lexically. This
# allows us to stop looking for neighbors when we see a string 2 or more
# longer. This is a multiple bonus actually and cuts the runtime by a bit
# more than half:
#
# a) We consider far fewer words than we otherwise would for a nice win.
#
# b) There are cache and memory access effects we benefit from as we drag much
# less memory thru the cache.
#
# c) Since we do all equal sizes, then all off by ones, then stop, we get much
# better branch prediction.

NEIGHBORS = {}  # global
WORDS = []  # global


def build_full_neighbor_map():
    total_checks = 0
    skipped_checks = 0
    total_neighbors = 0
    words_len = len(WORDS)
    total_comps_needed = words_len * words_len / 2
    start_time = time.perf_counter()
    last_time = start_time

    for i, w1 in enumerate(WORDS):
        w1l = len(w1)
        nl = NEIGHBORS.get(w1, None)
        j = i + 1
        while j < words_len:
            total_checks += 1
            w2 = WORDS[j]
            j += 1
            w2l = len(w2)
            ed = 0
            if w1l == w2l:
                ed = edit_distance_equal(w1, w2, w1l)
            else:
                d = w2l - w1l
                if d == 1:
                    ed = edit_distance_off_by_one(w1, w2, w1l, w2l)
                else:
                    # d > 1. If words is sorted by length, then everything past j is too long.
                    skipped_checks += words_len - j
                    break
            if ed == 1:
                total_neighbors += 1
                if nl is None:
                    nl = []
                    NEIGHBORS[w1] = nl
                nl.append(w2)
                nl2 = NEIGHBORS.get(w2, None)
                if nl2 is None:
                    nl2 = []
                    NEIGHBORS[w2] = nl2
                nl2.append(w1)

        if i % 1000 == 0:
            current_time = time.perf_counter()
            if current_time - last_time > 1:
                t_and_s_checks = total_checks + skipped_checks
                print("Finished '{}', {:.0f}m/{:.0f}m ({:.2f}%), {:,} neighbors".format(
                    w1, t_and_s_checks / 1000000.0, total_comps_needed / 1000000.0, t_and_s_checks /
                    total_comps_needed * 100.0, total_neighbors))
                elapsed_ms = (current_time - start_time) * 1000.0
                print("Elapsed time {:,.2f}s, average time {:.04f}us, for {:,} calls".format(
                    elapsed_ms / 1000, elapsed_ms / total_checks * 1000.0, total_checks))
                calls_left = total_comps_needed - t_and_s_checks
                # The ETA is rough since we're skipping an unknown number of checks per row.
                eta_ms = (elapsed_ms / total_checks) * calls_left
                print("Estimate {:.2f} minutes to go...\n".format(eta_ms / 1000 / 60))
                last_time = current_time

    elapsed_ms = (time.perf_counter() - start_time) * 1000
    print("Skipped {:,} checks".format(skipped_checks))
    print("Done.\n"
          "Elapsed time {:,.2f}s, average loop time {:.04f}us, for {:,} calls "
          "and {:,} total neighbors\n".format(
              elapsed_ms / 1000, elapsed_ms / total_checks * 1000.0, total_checks, total_neighbors))


# This builds the neighbor map "lazily", one row at a time and on-demand. This
# allows us to skip building the full map in favor of only building the pieces
# we need to perform a given search. The map is essentially a cache.
WORD_LENGTH_STARTS = []  # global


def lazy_build_neighbor_map(w1, nl):
    w1l = len(w1)
    if w1l >= len(WORD_LENGTH_STARTS):
        print("Word '{}' is longer than the max length word loaded, skipping.".format(w1))
        return
    words_len = len(WORDS)
    j = WORD_LENGTH_STARTS[w1l - 1]
    while j < words_len:
        w2 = WORDS[j]
        j += 1
        w2l = len(w2)
        ed = 0
        if w1l == w2l:
            ed = edit_distance_equal(w1, w2, w1l)
        else:
            d = w2l - w1l
            if d == 1:
                ed = edit_distance_off_by_one(w1, w2, w1l, w2l)
            elif d == -1:
                ed = edit_distance_off_by_one(w2, w1, w2l, w1l)
            elif d > 1:
                # If words is sorted by length, then everything past j is too long.
                break

        if ed == 1:
            nl.append(w2)


# Build a map of where each group of words of a given length start.
# Used by lazy_build_neighbor_map() to skip the left side of the matrix.
def build_word_length_starts(words, word_length_limit):
    a = [0] * (word_length_limit + 1)

    last_word_length = 0
    for i, w in enumerate(words):
        l = len(w)
        if l > last_word_length:
            a[l] = i
            last_word_length = l

    # Fill in gaps, e.g., there are no words 26 letters long.
    if a[len(a) - 1] == 0:
        a[len(a) - 1] = len(words) - 1
    i = len(a) - 1
    while i > 1:
        if a[i] == 0:
            a[i] = a[i + 1]
        i -= 1

    return a


# Used to print the paths we discover, so we keep parent references as we
# explore the graph.
class Node:
    def __init__(self, parent, word):
        self.parent = parent
        self.word = word


def print_path(path, dest):
    for s in path:
        print(s, " -> ", end="")
    print(dest)


def build_path(n):
    path = []
    while n is not None:
        path.insert(0, n.word)
        n = n.parent
    return path


# Traverse the neighbor graph from w1 to w2, finding all shortest paths. Goes
# breadth-first. Trims out loops to make it a tree a level at a time as it
# goes.
def find_path_bfs(w1, w2):
    print("Find path from '{0}' to '{1}'".format(w1, w2))
    start_time = time.perf_counter()

    total_words = 0
    total_min_paths = 0
    leaves_queue = deque()
    parents = set()  # To remove loops.
    level_sentinel = Node(None, None)  # Marks the end of each level.
    leaves_queue.append(level_sentinel)
    leaves_queue.append(Node(None, w1))

    while leaves_queue:
        n = leaves_queue.popleft()
        if n == level_sentinel:
            # Stop when we can't find anything, or if we found paths on the last level.
            if not leaves_queue or total_min_paths > 0:
                break
            for n in leaves_queue:
                parents.add(n.word)

            leaves_queue.append(level_sentinel)
            continue

        total_words += 1
        nl = get_neighbors_with_lazy_build(n.word)
        for w in nl:
            if w == w2:
                p = build_path(n)
                if total_min_paths == 0:
                    print("Shortest path lengths:", len(p))
                    # Copy-paste to http://www.webgraphviz.com/
                    print("digraph {}_{}_{}{{concentrate=true;".format(
                        w1, w2, len(p)))
                total_min_paths += 1
                print_path(p, w)
            if w not in parents:
                leaves_queue.append(Node(n, w))

    if total_min_paths > 0:
        print("}")

    search_ms = (time.perf_counter() - start_time) * 1000.0
    print("Done {:,.2f}ms, considered {:,} words for {:,} total "
          "minimum paths\n".format(search_ms, total_words, total_min_paths))


# A small helper to get us the neighbor list for a word, and build its
# neighbors lazily if necessary. Used by findPathBFS() and the user input code
# down in main().
def get_neighbors_with_lazy_build(w):
    nl = NEIGHBORS.get(w, None)
    if nl is None:
        nl = []
        NEIGHBORS[w] = nl
        lazy_build_neighbor_map(w, nl)

    return nl


def main():
    global WORD_LENGTH_STARTS, WORDS, NEIGHBORS

    edit_distance_static_setup(30)

    print(edit_distance_basic("saturday", "sunday"))
    print(edit_distance_no_alloc("saturday", "sunday"))
    print(edit_distance_equal("saturday", "satuxday", 8))
    print(edit_distance_off_by_one("saturday", "satuxrday", 8, 9))
    print(edit_distance_cheater("saturday", "satuxrday"))

    if RUN_PERF_TESTS:
        print(min(timeit.repeat(lambda: edit_distance_basic(
            "saturday", "sunday"), number=10000)))
        print(min(timeit.repeat(lambda: edit_distance_no_alloc(
            "saturday", "sunday"), number=10000)))
        print(min(timeit.repeat(lambda: edit_distance_equal(
            "saturday", "satuxday", 8), number=1000000)))
        print(min(timeit.repeat(lambda: edit_distance_off_by_one(
            "saturday", "satuxrday", 8, 9), number=100000)))
        print(min(timeit.repeat(lambda: edit_distance_cheater(
            "saturday", "satuxrday"), number=100000)))

    # Found a dictionary at https://github.com/dwyl/english-words
    WORDS = load_dictionary("words_alpha.txt", WORD_LENGTH_LIMIT)

    # Sort the words by length. See description of buildFullNeighborMap().
    start_time = time.perf_counter()
    WORDS.sort(key=len)
    sort_ms = (time.perf_counter() - start_time) * 1000
    print("Sorted words in {:,.2f}ms\n".format(sort_ms))

    # Build the full neighbor map all at once (serial or parallel), or set up for
    # lazy work.
    if BUILD_FULL_MAP:
        build_full_neighbor_map()
        # Helpers.neighborAnalysis(neighbors, word_length_limit);

    # For lazy neighbor finding in all cases.
    WORD_LENGTH_STARTS = build_word_length_starts(WORDS, WORD_LENGTH_LIMIT)

    # These are some sample pairs used to test and time our search algorithms.
    if RUN_SAMPLE_PAIRS:
        search_start_time = time.perf_counter()

        find_path_bfs("dog", "cat")
        find_path_bfs("dog", "smart")
        find_path_bfs("dog", "quack")

        # These were originally chosen randomly from the set of words with neighbors.
        # Turns our some are quite good test cases.
        find_path_bfs("angerly", "invaded")
        find_path_bfs("vulgates", "gumwood")
        find_path_bfs("sweetly", "raddles")
        find_path_bfs("lenten", "chiffonnieres")
        find_path_bfs("cradlemen", "discreation")
        find_path_bfs("blinkingly", "taupou")
        find_path_bfs("protanopia", "interiorist")
        find_path_bfs("outchid", "paramountly")
        find_path_bfs("bldr", "rewrote")
        find_path_bfs("evacuee", "fall")

        search_ms = (time.perf_counter() - search_start_time) * 1000.0
        print("Total search time: {:,.2f}ms\n".format(search_ms))

    if LET_THE_USER_PLAY:
        while True:
            line = input("Enter two words separated by a space, or nothing to exit: ")
            if not line:
                break

            pair = line.split()
            if len(pair) != 2:
                print("You need to enter two words!")
                continue

            w1, w2 = pair
            if w1 not in WORDS:
                print("'{}' is not a word, sorry!".format(w1))
                continue

            if not get_neighbors_with_lazy_build(w1):
                print("'{}' has no neighbors, sorry!".format(w1))
                continue

            if w2 not in WORDS:
                print("'{}' is not a word, sorry!".format(w2))
                continue

            if not get_neighbors_with_lazy_build(w2):
                print("'{}' has no neighbors, sorry!".format(w2))
                continue

            find_path_bfs(w1, w2)


if __name__ == "__main__":
    sys.exit(main())
