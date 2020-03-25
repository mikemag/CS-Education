# Mazes

Generating good, random mazes is a classic problem in computer science. There are a lot of maze generation algorithms out there, and the [Wikipedia page on maze generation](https://en.wikipedia.org/wiki/Maze_generation_algorithm) has a good overview of many. However, most of them are pretty confusing to read about. Prim, Kruskal, Minimum Spanning Trees, recursion, etc. You usually need a few years of CS in college to understand that stuff. But there are some easier ones out there that are very good!

A good one is Wilson's algorithm. I think it's pretty understandable without having to learn a bunch of new, complex topics. If you look at the [Wikipedia entry for Wilson's algorithm](https://en.wikipedia.org/wiki/Maze_generation_algorithm#Wilson), it has a pretty short description and the only confusing part is this "loop-erased random walk" thing. [This page on maze algorithms](https://professor-l.github.io/mazes/) has a decent explanation of both Wilson's algorithm and the loop-erased random walk. And [this page has some awesome animations of many algorithms](https://bost.ocks.org/mike/algorithms/#maze-generation), including Wilson's which are just fun to watch. 

Wilson's should work well on small mazes, and I bet with some time you'll be able to see how to implement it with what you already know.

Finally, if you want to learn more about lots of maze generation algorithms in a pretty intuitive way, check out [this presentation which is really good](http://www.jamisbuck.org/presentations/rubyconf2011/index.html#title-page).

## Wilson's Algorithm in C++

This directory contains a random maze generator in C++. I chose Wilson's algorithm because it is easy to understand intuitively, without any  background in graphs, trees, or recursion. It is also easy to implement with basic arrays/vectors.

 This is written to use a limited set of C++. It assumes you only know the following:
 - Basic syntax
 - Functions 
 - Simple structs: a few fields, and a default constructor.
 - `vector<>`, including vectors of simple structs, and passing them by reference.

Example output:

```
5 x 5: done in 13 passes.

+--+--+--+--+--+
|        |     |
+  +--+--+  +  +
|     |     |  |
+  +--+  +  +--+
|        |  |  |
+  +--+  +--+  +
|  |  |  |     |
+  +  +--+  +--+
|              |
+--+--+--+--+--+
```
