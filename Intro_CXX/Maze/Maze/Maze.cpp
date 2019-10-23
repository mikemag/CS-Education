// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.


// Generate random 2D mazes using Wilson's algorithm.
// https://en.wikipedia.org/wiki/Maze_generation_algorithm#Wilson's_algorithm
// 
// This performs "random walks" from random cells not in the maze to any cell in the maze.
// These walks are added to the maze, and this is repeated until all cells are in the maze.
// This produces nice, unbiased mazes in a reasonable amount of time.
//
// I chose Wilson's algorithm because it is easy to understand intuitively, without any 
// background in graphs, trees, or recursion. It is also easy to implement with basic
// arrays/vectors.
//
// This is written to use a limited set of C++. It assumes you only know the following:
// - Basic syntax
// - Functions 
// - Simple structs: a few fields, and a default constructor.
// - vector<>, including vectors of simple structs, and passing them by refernce.
//
// Example output:
// 5 x 5: done in 13 passes.
//
// +--+--+--+--+--+
// |        |     |
// +  +--+--+  +  +
// |     |     |  |
// +  +--+  +  +--+
// |        |  |  |
// +  +--+  +--+  +
// |  |  |  |     |
// +  +  +--+  +--+
// |              |
// +--+--+--+--+--+

#include <iostream>
#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <vector>
#include <time.h>
#include <Windows.h>
using namespace std;

// A maze is made of cells, and each cell has four walls. 
// The wall are represented by a vector of bools, in the order N, E, S, W.
// Each cell starts as closed, and not part of the maze.
//
// A maze is represented as a one-dimensional vector of cells. This means that
// every cell has a unique ID, which is just its index into the 1D vector.

struct Cell {
  vector<bool> walls;
  bool inMaze;

  Cell() {
    walls = { true, true, true, true };
    inMaze = false;
  }
};

// Add a cell to the maze. This doesn't change any walls, but it does update 
// our list of cells which aren't in the maze.
void addCellToMaze(int cellId, vector<Cell>& maze,
  vector<int>& cellsNotInMaze) {
  maze[cellId].inMaze = true;

  // Remove this cell from the list of cells not in the maze. This is a simple way to
  // do it with a loop and shrinking the size of the list by one. If you want to have some
  // fun, look up vector::erase() and std::remove().
  for (int i = 0; i < cellsNotInMaze.size(); i++) {
    if (cellsNotInMaze[i] == cellId) { // Find the cell in the list
      std::swap(cellsNotInMaze[i], cellsNotInMaze[cellsNotInMaze.size() - 1]); // Swap it with the end
      cellsNotInMaze.pop_back(); // Throw away the end
      break;
    }
  }
}

// Find the direction between two adjacent cells.
// Returns a move direction as a number 0-3 => NESW
int directionFromCellToCell(int fromCell, int toCell, int width) {
  int d = toCell - fromCell;
  if (d == -width) {
    return 0;
  }
  else if (d == 1) {
    return 1;
  }
  else if (d == width) {
    return 2;
  }
  else if (d == -1) {
    return 3;
  }
  return -1;
}

// Remove the walls between two adjacent cells.
void removeWalls(int cellA, int cellB, vector<Cell>& maze, int width) {
  maze[cellA].walls[directionFromCellToCell(cellA, cellB, width)] = false;
  maze[cellB].walls[directionFromCellToCell(cellB, cellA, width)] = false;
}

// Find a random direction to go from a cell, staying within the bounds of the maze,
// and also possibly avoiding going in a particular direction.
//
// This really needs to be random to avoid introducing bias into the overall algorithm.
//
// Returns a move direction as a number 0-3 => NESW
int randomValidDirection(int cellId, int avoidDirection, int width, int height) {
  vector<bool> moves = { true, true, true, true }; // NESW, all moves possible.

  // Remove any moves we're not allowed to make.
  if (avoidDirection >= 0) {
    moves[avoidDirection] = false;
  }
  if (cellId % width == 0) { // on the left edge
    moves[3] = false;
  }
  if ((cellId + 1) % width == 0) { // on the right edge
    moves[1] = false;
  }
  if (cellId < width) { // on the top edge
    moves[0] = false;
  }
  if (cellId >= (width * height) - width) { // on the bottom edge
    moves[2] = false;
  }

  // Pack the possible moves into a small vector.
  vector<int> possibleDirections;
  for (int i = 0; i < moves.size(); i++) {
    if (moves[i]) {
      possibleDirections.push_back(i);
    }
  }

  // Pick a possible move at random and return it.
  return possibleDirections[rand() % possibleDirections.size()];
}

// Perform a random walk from a cell not in the maze and try to find any cell in the maze.
// Adds the result of the walk to the maze.
//
// The walk is a 1D vector of cell ids and tells us what cell we came from to get to a particular cell.
// So if walk[5] is 12, it says that to get to cell #5 we came from cell #12.
void doRandomWalk(vector<Cell>& maze, vector<int>& cellsNotInMaze, int width, int height) {
  vector<int> walk(maze.size(), -1);
  int start = cellsNotInMaze[rand() % cellsNotInMaze.size()]; // Start at a random cell.
  walk[start] = start; // Starting cell points to itself, a useful sentinel. 
  int current = start;
  int lastDirection = -1;
  vector<int> moves = { -width, 1, width, -1 }; // NESW

  while (!maze[current].inMaze) {
    // Pick random direction != inverse of last direction, so we don't try to walk backwards.
    int direction = randomValidDirection(current, (lastDirection + 2) % 4, width, height);
    int newCellId = current + moves[direction];
    if (walk[newCellId] == -1) {
      // New cell isn't in the walk. Is it in the maze?
      if (maze[newCellId].inMaze) {
        // Hit the maze, stop the walk. Follow the path backwards, adding cells
        // to the maze and adjusting walls as we go.
        removeWalls(newCellId, current, maze, width); // Open the wall to the maze
        while (current != start) {
          addCellToMaze(current, maze, cellsNotInMaze);
          removeWalls(current, walk[current], maze, width); // Open walls along the walk
          current = walk[current];
        }
        addCellToMaze(current, maze, cellsNotInMaze); // Don't forget to add the start.
        break;
      }

      // Add the cell to the walk.
      walk[newCellId] = current;
      current = newCellId;
      lastDirection = direction;
    }
    else {
      // Oops, we ran into our own path! Remove the loop and keep going. 
      // Follow the path backwards and reset the walk cells to -1, leave us back at the
      // collision cell.
      while (current != newCellId) {
        int t = walk[current];
        walk[current] = -1;
        current = t;
      }
      lastDirection = directionFromCellToCell(walk[current], current, width);
    }
  }
}

// Move the console cursor to a specific position, on Windows only.
void moveCursorToPos(int x, int y) {
  COORD pos = { x, y };
  HANDLE output = GetStdHandle(STD_OUTPUT_HANDLE);
  SetConsoleCursorPosition(output, pos);
}

// Print our maze, with markers for cells not in the maze.
void printMaze(vector<Cell>& maze, int width, int height) {
  for (int i = 0; i < maze.size(); i += width) {
    if (i == 0) {
      for (int j = 0; j < width; j++) {
        if (j == 0) printf("+");
        if (maze[i + j].walls[0]) printf("--"); else printf("  ");
        printf("+");
      }
      printf("\n");
    }
    for (int j = 0; j < width; j++) {
      if (j == 0) if (maze[i + j].walls[3]) printf("|"); else printf(" ");
      if (maze[i + j].inMaze) printf("  "); else printf("::");
      if (maze[i + j].walls[1]) printf("|"); else printf(" ");
    }
    printf("\n");
    for (int j = 0; j < width; j++) {
      if (j == 0) printf("+");
      if (maze[i + j].walls[2]) printf("--"); else printf("  ");
      printf("+");
    }
    printf("\n");
  }
  printf("\n");
}

// Build a maze via Wilson's algorithm.
vector<Cell> buildMaze(int width, int height) {
  vector<Cell> maze(width * height);

  // List of all cell ids that aren't yet in the maze.
  vector<int> cellsNotInMaze;
  for (int i = 0; i < width * height; i++) {
    cellsNotInMaze.push_back(i);
  }

  // Pick the first cell to be in the maze.
  addCellToMaze(0, maze, cellsNotInMaze);

  // Add random walks until the maze is complete.
  system("cls");
  int passCount = 1;

  while (cellsNotInMaze.size() > 0) {
    moveCursorToPos(0, 0);
    printf("%d x %d: pass #%d, %d cells to go (%0.2f%%)...\n\n", width, height, passCount, cellsNotInMaze.size(),
      100.0 - ((float)cellsNotInMaze.size() / maze.size()) * 100.0);

    doRandomWalk(maze, cellsNotInMaze, width, height);

    printMaze(maze, width, height);
    if (cellsNotInMaze.size() > maze.size() / 2) { // Speed up when we're halfway done.
      Sleep(max(0, 1000 * ((float)cellsNotInMaze.size() / maze.size())));
    }
    passCount++;
  }

  moveCursorToPos(0, 0);
  printf("%d x %d: done in %d passes.                     \n\n", width, height, passCount);
  printMaze(maze, width, height);
  Sleep(2000);

  return maze;
}

int main(int, char**) {
  srand(42);
  //srand(time(NULL));

  // Build a few sample sizes
  vector<Cell> maze = buildMaze(5, 5);
  maze = buildMaze(10, 10);
  maze = buildMaze(20, 5);
  maze = buildMaze(30, 2);

  // Build a bunch of random sizes, just for fun.
  for (int i = 0; i < 100; i++) {
    int width = rand() % 30 + 2;
    int height = rand() % 10 + 2;
    maze = buildMaze(width, height);
  }

  cout << "Done.\n";
  return 0;
}
