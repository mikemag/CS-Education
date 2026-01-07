using System.Diagnostics;

namespace Levenshtein_cs
{
    // To remember and build paths we need to know the parents of each word we discover. We'll keep them in a simple
    // dictionary, with a list of parents for each word.
    using WordsToParents = Dictionary<string, List<string>>;

    public class Levenshtein
    {
        // --------------------------------------------------------------------------------------------------
        // Finding and building paths

        // Find paths between words looking from both sides.
        //
        // On each side we have an explored area, and a frontier of neighbors of that explored area. We grow each
        // frontier alternatively, and stop when frontiers intersect. Then we join up paths from both sides.
        private static (int, List<string> paths) FindPaths(string w1, string w2)
        {
            var leftFrontier = new WordsToParents() { { w1, new List<string>() } };
            var leftExplored = new WordsToParents();

            var rightFrontier = new WordsToParents() { { w2, new List<string>() } };
            var rightExplored = new WordsToParents();

            int length = 0;

            while (leftFrontier.Count > 0 && rightFrontier.Count > 0)
            {
                Console.WriteLine("Left frontier: {0}, right frontier: {1}", leftFrontier.Count, rightFrontier.Count);

                // Have we found words on one side which match words on the other side?
                var intersection = leftFrontier.Keys.Intersect(rightFrontier.Keys).ToList();
                if (intersection.Count > 0)
                {
                    // We've found some words in the middle, so we're done! Let's collect the paths.
                    return (length, BuildPaths(intersection, leftFrontier, leftExplored, rightFrontier, rightExplored));
                }

                // Advance the smaller frontier, favoring the starting word (left side).
                if (leftFrontier.Count <= rightFrontier.Count)
                {
                    leftFrontier = AdvanceFrontier(leftFrontier, leftExplored);
                }
                else
                {
                    rightFrontier = AdvanceFrontier(rightFrontier, rightExplored);
                }

                length++;
            }

            return (0, new List<string>());
        }

        private static WordsToParents AdvanceFrontier(WordsToParents frontier, WordsToParents explored)
        {
            // Merge the current frontier into the explored space. This keeps history of where we've been, and
            // ensures we don't add any duplicates as we expand.
            foreach (var item in frontier)
            {
                explored.Add(item.Key, item.Value); // Throws if there are dups, which indicates a mistake!
            }

            // Build a new frontier of the neighbors of the current frontier, and use that for the next round.
            var newFrontier = new WordsToParents();

            foreach (var item in frontier)
            {
                foreach (var n in FindNeighbors(item.Key))
                {
                    if (explored.ContainsKey(n)) continue;

                    if (newFrontier.TryGetValue(n, out var parentList))
                    {
                        parentList.Add(item.Key);
                    }
                    else
                    {
                        newFrontier[n] = new List<string>() { item.Key };
                    }
                }
            }

            return newFrontier;
        }

        // Given a set of intersecting words, and all of the words we've discovered on both sides, build all the paths
        // between our two words. I've chosen to simply build the paths from each side and join them at the
        // intersections.
        private static List<String> BuildPaths(List<string> intersection, WordsToParents leftFrontier,
            WordsToParents leftExplored, WordsToParents rightFrontier, WordsToParents rightExplored)
        {
            var paths = new List<string>();

            foreach (var w in intersection)
            {
                var pathsLeft = BuildPathsForSide(leftFrontier[w], true, leftExplored);
                var pathsRight = BuildPathsForSide(rightFrontier[w], false, rightExplored);

                if (pathsLeft.Count == 0)
                {
                    foreach (var pr in pathsRight)
                    {
                        paths.Add(w + " -> " + pr);
                    }
                }
                else if (pathsRight.Count == 0)
                {
                    foreach (var pl in pathsLeft)
                    {
                        paths.Add(pl + " -> " + w);
                    }
                }
                else
                {
                    foreach (var pl in pathsLeft)
                    {
                        foreach (var pr in pathsRight)
                        {
                            paths.Add(pl + " -> " + w + " -> " + pr);
                        }
                    }
                }
            }

            return paths;
        }

        // Recursively build a list of paths for one side, given a list of starting words in the explored area.
        private static List<String> BuildPathsForSide(List<string> l, bool left, WordsToParents explored)
        {
            var paths = new List<string>();

            foreach (var s in l)
            {
                var parents = explored[s];
                if (parents.Count == 0)
                {
                    paths.Add(s);
                }
                else
                {
                    foreach (var p in BuildPathsForSide(parents, left, explored))
                    {
                        if (left)
                        {
                            paths.Add(p + " -> " + s);
                        }
                        else
                        {
                            paths.Add(s + " -> " + p);
                        }
                    }
                }
            }

            return paths;
        }

        // --------------------------------------------------------------------------------------------------
        // Running pairs of words and showing results

        private static bool DoPair(string w1, string w2, int expectedLength, int expectedPathCount)
        {
            Console.WriteLine($"Finding all shortest paths from '{w1}' to '{w2}'");
            var start = Stopwatch.GetTimestamp();
            var (length, paths) = FindPaths(w1, w2);
            var end = Stopwatch.GetTimestamp();

            bool correct = true;
            Console.WriteLine(
                $"Found {paths.Count} paths of length {length} for '{w1}' to '{w2}' in {(end - start) / (Stopwatch.Frequency / 1000)}ms");
            if (length != expectedLength || paths.Count != expectedPathCount)
            {
                correct = false;
                Console.WriteLine(
                    $"Whoa!!!! This isn't the right answer! Expected {expectedPathCount} paths of {expectedLength} length.");
            }

            if (paths.Count > 0)
            {
                // Copy-paste to http://www.webgraphviz.com/
                Console.WriteLine($"digraph {w1}_{w2}_{length}{{concentrate=true;");
                const int maxPaths = 120;
                if (paths.Count > maxPaths)
                {
                    Console.WriteLine(string.Join("\n", paths.Take(maxPaths)));
                    Console.WriteLine("...");
                }
                else
                {
                    Console.WriteLine(string.Join("\n", paths));
                }

                Console.WriteLine("}");
            }

            Console.WriteLine();
            return correct;
        }

        private static string[] _words = null!;
        private static int[] _wordLengthStarts = null!;

        public static void Main(string[] args)
        {
            // Load our dictionary of English words. Dictionary from https://github.com/dwyl/english-words
            const string dictionaryFilename = "../../../words_alpha.txt";
            _words = File.ReadAllLines(dictionaryFilename);
            Console.WriteLine("Loaded {0} words from {1}\n", _words.Length, dictionaryFilename);

            // Sort the dictionary by word length
            Array.Sort(_words, (x, y) => x.Length.CompareTo(y.Length));

            // Find where each word length starts in our dictionary. Now we can quickly find, say, words of length 4.
            _wordLengthStarts = BuildWordLengthStarts(_words);

            bool correct = true;
            correct &= DoPair("dog", "dog", 0, 0);
            correct &= DoPair("dog", "dot", 1, 1);
            correct &= DoPair("dog", "cat", 3, 6);
            correct &= DoPair("dog", "smart", 5, 51);
            correct &= DoPair("dog", "quack", 7, 107);
            correct &= DoPair("monkey", "business", 13, 1);
            correct &= DoPair("vulgates", "gumwood", 0, 0);
            correct &= DoPair("underditch", "toppingly", 55, 4);
            correct &= DoPair("headwards", "rifflers", 31, 68244);

            Console.WriteLine(correct ? "Everything worked." : "Oops!! At least one test failed :(");
        }

        // --------------------------------------------------------------------------------------------------
        // Edit distance between two words

        // To start, let's assume we're given two words of equal lengths. Can we determine quickly and
        // easily if they are distance 1 or not?
        //
        // Sure. Since they're the same length the only option to make them equal is substitution, thus we
        // just need to count the number of different letters, and stop after we find more than one.
        private static bool IsEditDistanceOneEqual(string w1, string w2)
        {
            var w1L = w1.Length;
            var diffs = false;
            for (var i = 0; i < w1L; i++)
            {
                if (w1[i] == w2[i]) continue;
                if (diffs) return false;
                diffs = true;
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
        private static bool IsEditDistanceOneOffByOne(string w1, string w2)
        {
            var diffs = false;
            var w1I = 0;
            var w2I = 0;

            while (w1I < w1.Length)
            {
                if (w1[w1I] != w2[w2I])
                {
                    if (diffs) return false;
                    diffs =  true;
                    w2I++; // Same as deletion.
                }
                else
                {
                    w1I++;
                    w2I++;
                }
            }

            if (w2I < w2.Length)
            {
                diffs = true; // Deletion of the last char of w2.
            }

            return diffs;
        }

        // --------------------------------------------------------------------------------------------------
        // Finding neighbors

        // Build a map of where each group of words of a given length start.
        private static int[] BuildWordLengthStarts(string[] words)
        {
            // Reserve extra entries past the longest word so we can ask for indices for the next longest word size.
            var wordLengthLimit = words[^1].Length + 3;
            int[] a = new int[wordLengthLimit];

            int lastWordLength = 0;
            for (int i = 0; i < words.Length; i++)
            {
                int l = words[i].Length;
                if (l > lastWordLength)
                {
                    a[l] = i;
                    lastWordLength = l;
                }
            }

            // Fill in gaps, e.g., there are no words 26 letters long.
            if (a[^1] == 0)
            {
                a[^1] = words.Length;
            }

            for (int i = a.Length - 1; i > 1; i--)
            {
                if (a[i] == 0)
                {
                    a[i] = a[i + 1];
                }
            }

            return a;
        }

        private static List<string> FindNeighbors(string w)
        {
            var neighbors = new List<string>();
            for (var j = _wordLengthStarts[w.Length - 1]; j < _wordLengthStarts[w.Length]; j++)
            {
                if (IsEditDistanceOneOffByOne(_words[j], w)) // Shorter vs. w
                {
                    neighbors.Add(_words[j]);
                }
            }

            for (var j = _wordLengthStarts[w.Length]; j < _wordLengthStarts[w.Length + 1]; j++)
            {
                if (IsEditDistanceOneEqual(_words[j], w)) // Same length as w
                {
                    neighbors.Add(_words[j]);
                }
            }

            for (var j = _wordLengthStarts[w.Length + 1]; j < _wordLengthStarts[w.Length + 2]; j++)
            {
                if (IsEditDistanceOneOffByOne(w, _words[j])) // w vs. longer
                {
                    neighbors.Add(_words[j]);
                }
            }

            return neighbors;
        }
    }
}