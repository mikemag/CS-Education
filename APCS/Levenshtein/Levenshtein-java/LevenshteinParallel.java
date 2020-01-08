// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LevenshteinParallel {

	// Options
	private static final int parallelBuildChunkSize = 5000;
	private static final int parallelThreadCount = Runtime.getRuntime().availableProcessors();

	private static final int lazyBFSBuildMapChunkSize = 500;
	private static final int lazyBFSBuildLevelChunkSize = 5000;

	// Build the full neighbor map in parallel. Same algorithm as the previous
	// version, but the work is done in chunks via Runnable tasks enqueued to a
	// thread pool. Access to the map is protected with a lock to protect mutation
	// of both the map and the lists contained within.
	//
	// There are a variety of ways to do this, some which would add less
	// synchronization overhead, but I've kept this simple so you can see how
	// similar it can be to the original. In particular the shared HashMap is an
	// obvious point of contention, but honestly the ratio of neighbors to the total
	// search space is low enough that it doesn't make that much of a difference.
	//
	// The major concepts to explore here are: Runnable (used to make "tasks" to do
	// the work), CountDownLatch (used to know when all the work is done),
	// ExecutorService (used to get a fixed size thread pool), and the
	// "synchronized" keyword (for locking).
	//
	// Some kinds of problems are very, very easy to parallelize. This is a great
	// example of one, and just how easy it is to do.

	// First, we need a "task" object to contain the work we want to farm out to
	// each thread. This is a "Runnable", and the run() method will be executed on a
	// different thread.
	private static class NeighborMapTask implements Runnable {
		private final int startIndex;
		private final int endIndex;
		private final CountDownLatch doneLatch;

		public NeighborMapTask(int startIndex, int endIndex, CountDownLatch doneLatch) {
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.doneLatch = doneLatch;
		}

		// There are just 4 small changes to the code vs. the serial version, each
		// marked with a comment. Put them side-by-side and compare.
		@Override
		public void run() {
			ArrayList<String> words = Levenshtein.words;
			HashMap<String, List<String>> neighbors = Levenshtein.neighbors;
			int wordsLen = words.size();
			for (int i = startIndex; i < endIndex; i++) { // Limited range
				String w1 = words.get(i);
				int w1l = w1.length();
				List<String> nl;
				synchronized (neighbors) { // Protect the map
					nl = neighbors.get(w1);
				}
				for (int j = i + 1; j < wordsLen; j++) {
					String w2 = words.get(j);
					int w2l = w2.length();
					int ed = 0;
					if (w1l == w2l) {
						ed = Levenshtein.editDistanceEqual(w1, w2);
					} else {
						int d = w2l - w1l;
						if (d == 1) {
							ed = Levenshtein.editDistanceOffByOne(w1, w2);
						} else {
							// d > 1. If words is sorted by length, then everything past j is too long.
							break;
						}
					}
					if (ed == 1) {
						synchronized (neighbors) { // Protect the map **and its contents**.
							if (nl == null) {
								nl = neighbors.get(w1); // Retry... it might have been added by another thread.
								if (nl == null) {
									nl = new ArrayList<>();
									neighbors.put(w1, nl);
								}
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
				}
			}
			doneLatch.countDown(); // Let buildFullNeighborMapParallel() know this task is done.
			System.out.printf("%s: done with [%,d , %,d), latch is %,d\n", Thread.currentThread().getName(), startIndex,
					endIndex, doneLatch.getCount());
		}
	}

	// This partitions the list of words into equal sized chunks, one per task, and
	// enqueues all of the tasks to a thread pool. It then waits for all the threads
	// to finish.
	public static void buildFullNeighborMapParallel() {
		long startTime = System.nanoTime();

		ExecutorService pool = Executors.newFixedThreadPool(parallelThreadCount);

		int taskCount = Levenshtein.words.size() / parallelBuildChunkSize + 1;
		CountDownLatch doneLatch = new CountDownLatch(taskCount);
		System.out.printf("Spawning up %,d tasks to build the map in parallel...\n", doneLatch.getCount());
		int size = 0;
		for (int i = 0; i < Levenshtein.words.size(); i += parallelBuildChunkSize) {
			size = Math.min(parallelBuildChunkSize, Levenshtein.words.size() - i);
			pool.execute(new NeighborMapTask(i, i + size, doneLatch));
		}

		System.out.printf("Waiting for %,d tasks to build the map...\n", taskCount);
		try {
			doneLatch.await();
			pool.shutdown();
		} catch (Exception e) {
			System.out.println(e); // Pretty poor error handling ;)
		}

		double elapsedMS = (System.nanoTime() - startTime) / 1000000.0;
		System.out.printf(
				"Done building neighbor map in parallel, elapsed time: %,.02fms, chunk size %,d, thread count %d\n\n",
				elapsedMS, parallelBuildChunkSize, parallelThreadCount);
	}

	// A parallel version of finding all paths between two words while building the
	// neighbor map lazily.
	//
	// Whereas the above algorithm to build the full map in parallel is very, very
	// close to the serial version, this one is quite a bit different. This required
	// us to think about the problem in a different way to allow us to see a nice
	// way to parallelize the work.
	//
	// The serial version is a classic BFS traversal thru a graph.
	//
	// The parallel version treats the search more like expanding the frontier of a
	// search space one step at a time.
	//
	// The search starts with a single word as our "explored area". We compute all
	// neighbors of that explored area (to start, just that one word), then we take
	// all of those neighbors and add them to the explored area. Then we rinse and
	// repeat until we find our target word on the edge of the explored area, where
	// we stop.
	//
	// It's the same algorithm really, but thinking of it like this allows us to
	// realize that we have two large chunks of work to do:
	//
	// 1. Prepare the edge of the explored area by computing any missing neighbors.
	//
	// 2. Expand the edge of the explored area by adding in those neighbors, while
	// checking for our target word.
	//
	// Each of these operations can be done in parallel similar to the way we build
	// the full map. So you'll see two parallel phases per expansion of the explored
	// area: a "parallel prepare phase", and a "parallel expand phase".

	// We need an object to represent each search because some of our tasks need
	// share most of this state.
	public static final class LazyFindPathBFS {
		private static ExecutorService lazyBFSPool = null;

		private final String startWord;
		private final String targetWord;
		private Integer totalMinPaths;
		private ArrayList<Levenshtein.Node> edgeOfExploredArea;
		private ArrayList<Levenshtein.Node> neighborsOnEdgeOfExploredArea;
		private final HashSet<String> exploredArea;

		public LazyFindPathBFS(String startWord, String targetWord) {
			this.startWord = startWord;
			this.targetWord = targetWord;
			this.exploredArea = new HashSet<>();
			this.totalMinPaths = 0;
			this.edgeOfExploredArea = new ArrayList<>();
			this.neighborsOnEdgeOfExploredArea = new ArrayList<>();
		}

		public static void setupThreadPool() {
			lazyBFSPool = Executors.newFixedThreadPool(parallelThreadCount);
		}

		public static void cleanupThreadPool() {
			lazyBFSPool.shutdown();
		}

		public void findPath() {
			System.out.format("Find path from '%s' to '%s'\n", startWord, targetWord);
			long startTime = System.nanoTime();
			HashMap<String, List<String>> neighbors = Levenshtein.neighbors;
			int totalWords = 0;
			edgeOfExploredArea.add(new Levenshtein.Node(null, startWord));

			while (!edgeOfExploredArea.isEmpty() && totalMinPaths == 0) {
				// Add the edge into the explored area set.
				HashSet<String> uniqueWordsOnEdge = new HashSet<>();
				for (Levenshtein.Node edgeNode : edgeOfExploredArea) {
					uniqueWordsOnEdge.add(edgeNode.word);
				}
				exploredArea.addAll(uniqueWordsOnEdge);
				totalWords += edgeOfExploredArea.size();

				// Prepare the new edge by lazily building any missing pieces of the neighbor
				// map in parallel.
				uniqueWordsOnEdge.removeIf((w) -> neighbors.get(w) != null); // Filter out words with neighbors already.
				if (uniqueWordsOnEdge.size() == 0) {
					System.out.println("Prepare edge:    no work");
				} else {
					// Farm out chunks of work to tasks. Note that we do this even if there's only a
					// small bit of work to do. A common optimization is to just do that work
					// directly rather than start a single task for it. I haven't done that here
					// because it just makes the code more complex for minimal benefit.
					String[] uniqueWords = uniqueWordsOnEdge.stream().toArray(String[]::new);
					int taskCount = uniqueWords.length / lazyBFSBuildMapChunkSize + 1;
					CountDownLatch doneLatch = new CountDownLatch(taskCount);
					System.out.printf("Prepare edge: %, 4d tasks for %, 9d words\n", taskCount, uniqueWords.length);
					int size = 0;
					for (int i = 0; i < uniqueWords.length; i += lazyBFSBuildMapChunkSize) {
						size = Math.min(lazyBFSBuildMapChunkSize, uniqueWords.length - i);
						lazyBFSPool.execute(new LazyBuildMapTask(uniqueWords, i, i + size, doneLatch));
					}
					try {
						doneLatch.await();
					} catch (Exception e) {
						System.out.println(e); // Pretty poor error handling ;)
					}
				}

				// Expand the edge by pulling in all neighbors of the current edge, checking for
				// our target word as we go.
				int taskCount = edgeOfExploredArea.size() / lazyBFSBuildLevelChunkSize + 1;
				CountDownLatch doneLatch = new CountDownLatch(taskCount);
				System.out.printf("Expand edge:  %, 4d tasks for %, 9d words\n", taskCount, edgeOfExploredArea.size());
				int size = 0;
				for (int i = 0; i < edgeOfExploredArea.size(); i += size) {
					size = Math.min(lazyBFSBuildLevelChunkSize, edgeOfExploredArea.size() - i);
					lazyBFSPool.execute(new LazyBuildNextLevelTask(this, i, i + size, doneLatch));
				}
				try {
					doneLatch.await();
				} catch (Exception e) {
					System.out.println(e); // Pretty poor error handling ;)
				}

				// Swap in the new level, clear the old level
				ArrayList<Levenshtein.Node> tmp = edgeOfExploredArea;
				edgeOfExploredArea = neighborsOnEdgeOfExploredArea;
				neighborsOnEdgeOfExploredArea = tmp;
				neighborsOnEdgeOfExploredArea.clear();
			}

			if (totalMinPaths > 0) {
				System.out.println("}");
			}
			double searchMS = (System.nanoTime() - startTime) / 1000000.0;
			System.out.format("Done %,.2fms, considered %,d words for %,d total minimum paths\n\n", searchMS,
					totalWords, totalMinPaths);
		}

		// A task to build up the neighbor map for a chunk of words. This part ought to
		// look pretty similar to the regular version.
		private final class LazyBuildMapTask implements Runnable {
			private final String[] uniqueWords;
			private final int startIndex;
			private final int endIndex;
			private final CountDownLatch doneLatch;

			public LazyBuildMapTask(String[] uniqueWords, int startIndex, int endIndex, CountDownLatch doneLatch) {
				this.uniqueWords = uniqueWords;
				this.startIndex = startIndex;
				this.endIndex = endIndex;
				this.doneLatch = doneLatch;
			}

			@Override
			public void run() {
				for (int i = startIndex; i < endIndex; i++) { // Limited range
					String w = uniqueWords[i];
					List<String> nl;
					boolean needToBuild = false;
					synchronized (Levenshtein.neighbors) { // Protect the map
						nl = Levenshtein.neighbors.get(w);
						if (nl == null) {
							nl = new ArrayList<>();
							Levenshtein.neighbors.put(w, nl);
							needToBuild = true;
						}
					}
					if (needToBuild) {
						Levenshtein.lazyBuildNeighborMap(w, nl); // This thread is the only one working on w.
					}
				}

				doneLatch.countDown(); // Let findPathBFSLazyParallel() know this task is done.
			}
		}

		// A task to build up the new edge of the explored area, given a chunk of the current edge. 
		private final class LazyBuildNextLevelTask implements Runnable {
			private final LazyFindPathBFS data;
			private final int startIndex;
			private final int endIndex;
			private final CountDownLatch doneLatch;

			public LazyBuildNextLevelTask(LazyFindPathBFS data, int startIndex, int endIndex,
					CountDownLatch doneLatch) {
				this.data = data;
				this.startIndex = startIndex;
				this.endIndex = endIndex;
				this.doneLatch = doneLatch;
			}

			@Override
			public void run() {
				for (int i = startIndex; i < endIndex; i++) { // Limited range
					Levenshtein.Node n = data.edgeOfExploredArea.get(i);
					List<String> nl = Levenshtein.neighbors.get(n.word); // Read-only
					for (String w : nl) {
						if (w.equals(data.targetWord)) {
							List<String> p = Levenshtein.buildPath(n);
							synchronized (data) {
								if (data.totalMinPaths == 0) {
									System.out.println("Shortest path lengths: " + p.size());
									// Copy-paste to http://www.webgraphviz.com/
									System.out.format("digraph %s_%s_%d{concentrate=true;\n", data.startWord,
											data.targetWord, p.size());
								}
								data.totalMinPaths++;
								Levenshtein.printPath(p, w); // Print under the lock, so the output isn't interleaved.
							}
						}
						if (!data.exploredArea.contains(w)) { // Read only
							// TODO: coarsen, or use a concurrent list.
							synchronized (data.neighborsOnEdgeOfExploredArea) {
								data.neighborsOnEdgeOfExploredArea.add(new Levenshtein.Node(n, w));
							}
						}
					}

				}

				doneLatch.countDown(); // Let findPathBFSLazyParallel() know this task is done.
			}
		}
	}
}
