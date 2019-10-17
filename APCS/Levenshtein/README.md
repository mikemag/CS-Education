# Levenshtein Distance Samples

This is a collection of samples that show how to compute the [Levenshtein Distance](https://en.wikipedia.org/wiki/Levenshtein_distance) between two words. There is an AP CS project to find if any path exists between two words keeping the edit distance to 1 with each change.

Ex: cat -> cot -> cog -> dog

This is a pretty fun project because the obvious way to do it takes many hours to compute if you use a whole dictionary and allow words of any length. (A good way to start is by using only 3-letter words.)

The code collected here shows different ways to implement the edit distance computation itself, comparing them for simplicity vs. speed, and also shows a reasonable way to find not just if any path exists, but to find and show all shortest paths between given words. There's also a parallel version of the algorithm in Java, and ports of the simpler way in C++ and Python.

This started in Java, so that version has the most detail. See the options in the top of Levenshtein.java for configuration details:

```
	// Options -- more in LevenshteinParallel.java
	private static final boolean runPerfTests = false;
	private static final boolean runSamplePairs = false;

	private static final int wordLengthLimit = 30; // The longest word in words_alpha.txt is 29.

	private static final boolean buildFullMap = false;
	private static final boolean parallelFullBuild = true;
	private static final boolean parallelLazyBFS = true && !buildFullMap;
	private static final boolean letTheUserPlay = true;
```
