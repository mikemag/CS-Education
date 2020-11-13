#include <cstdint>
#include <iostream>
#include <vector>
#include <iomanip>
#include <chrono>
#include <random>
#include <immintrin.h> // For SSE/AVX support
#include <unordered_map>

using namespace std;

enum Algo {
  // Pick the first of the remaining choices.
  // 6/4 game, ~2m comps, 5.0216 avg turns, 8 turns max
  FirstOne,

  // Pick any of the remaining choices.
  // ~2m comps, ~4.6-4.7 avg turns, 7 turns max
  Random,

  // Pick the one that will eliminate the most remaining choices.
  // ~392m comps, 4.4761 avg turns, 5 turns max
  Knuth
};

// Pick which algo to run. If I was cool I'd either make this a command line arg, or have it run
// thru all of them.
static Algo algo = Algo::Knuth;

// Represents the result of the Codeword scoring function: number of black and white pegs.
class Score {
 public:
  uint8_t result;

 public:
  Score() { result = 0xFFu; }
  Score(uint8_t b, uint8_t w) noexcept {
    result = (b << 4u) | w;
  }

  bool isInvalid() const { return result == 0xFFu; }

  bool operator==(const Score &other) const { return result == other.result; }
  bool operator!=(const Score &other) const { return !operator==(other); }

  ostream &dump(ostream &stream) const {
    ios state(nullptr);
    state.copyfmt(stream);
    stream << hex << setfill('0') << setw(2) << (uint32_t) result;
    stream.copyfmt(state);
    return stream;
  }
};

template<>
struct std::hash<Score> {
  std::size_t operator()(const Score &s) const { return std::hash<int>()(s.result); }
};

// Class to hold a codeword for the Mastermind game.
//
// This is represented as a packed group of 4-bit digits, up to 8 digits, along with the ordinal of the codeword for use
// as a compact cache key.
class Codeword {
 private:
  uint32_t codeword;
  uint32_t ordinal;
  uint64_t colorCounts4; // Room for 16 4-bit counters
  unsigned __int128 __attribute__((aligned(32))) colorCounts8; // Room for 16 8-bit counters

 public:
  constexpr static uint pinCount = 4; // 1-8
  constexpr static uint colorCount = 6; // 1-15

  Codeword() noexcept: codeword(-1), ordinal(-1), colorCounts4(0), colorCounts8(0) {}

  explicit Codeword(uint32_t codeword, uint32_t ordinal) noexcept
      : codeword(codeword), ordinal(ordinal), colorCounts4(0), colorCounts8(0) {
    computeColorCounts();
  }

  explicit Codeword(uint32_t codeword) noexcept: codeword(codeword), ordinal(-1), colorCounts4(0), colorCounts8(0) {
    computeColorCounts();
  }

  bool isInvalid() const { return ordinal == -1; }

  bool operator==(const Codeword other) const { return codeword == other.codeword; }

  static uint64_t scoreCounter;
  static uint64_t totalCodewords;
  static const Score winningScore; // "0x40" for a 4-pin game.

  static vector<vector<Score>> *scoreCache;

  static void initScoreCache() {
    auto cacheSizeGB = (totalCodewords * totalCodewords) / 1'073'741'824.0;
    if (cacheSizeGB < 64.0) {
      printf("Setup score cache of %0.2fGiB\n", cacheSizeGB);
      scoreCache = new vector<vector<Score>>(totalCodewords);
      for (auto &v : *scoreCache) {
        v = vector<Score>(totalCodewords);
      }
    } else {
      printf("Skipping score cache of %0.2fGiB, too big!\n", cacheSizeGB);
    }
  }

  // Pre-compute color counts for all Codewords. Building this two ways right now for experimentation. The packed 4-bit
  // counters are good for the scalar versions and overall memory usage, while hte 8-bit counters are needed for SSE/AVX
  // vectorization, both auto and by-hand.
  void computeColorCounts() {
    uint32_t s = this->codeword;
    for (int i = 0; i < pinCount; i++) {
      colorCounts4 += 1lu << ((s & 0xFu) * 4);
      colorCounts8 += ((unsigned __int128) 1) << ((s & 0xFu) * 8);
      s >>= 4u;
    }
  }

  // This is a relatively simple O(2p) scoring method. First we count black hits and unused colors in O(p) time, then
  // we consume colors in O(p) time and count white hits. This is quite efficient for a rather simple scoring method,
  // with the only real complexity being the packing of pins and colors to reduce space used.
  // https://godbolt.org/z/sEEjcY
  //
  // Elapsed time 4.4948s, average search 3.4682ms
  Score scoreSimpleLoops(const Codeword guess) const {
    uint8_t b = 0;
    uint8_t w = 0;
    uint64_t unusedColorCounts = 0; // Room for 16 4-bit counters

    uint32_t s = this->codeword;
    uint32_t g = guess.codeword;
    for (int i = 0; i < pinCount; i++) {
      if ((g & 0xFu) == (s & 0xFu)) {
        b++;
      } else {
        unusedColorCounts += 1lu << ((s & 0xFu) * 4);
      }
      s >>= 4u;
      g >>= 4u;
    }

    s = this->codeword;
    g = guess.codeword;
    for (int i = 0; i < pinCount; i++) {
      if ((g & 0xFu) != (s & 0xFu) && (unusedColorCounts & (0xFlu << ((g & 0xFu) * 4))) > 0) {
        w++;
        unusedColorCounts -= 1lu << ((g & 0xFu) * 4);
      }
      s >>= 4u;
      g >>= 4u;
    }
    return Score(b, w);
  }

  // This uses the full counting method from Knuth, plus some fun bit twiddling hacks and SWAR action. This is O(c),
  // with constant time to get black hits, and often quite a bit less than O(c) time to get the total hits (and thus the
  // white hits.)
  //
  // Find black hits with xor, which leaves zero nibbles on matches, then count the zeros in the result. This is a
  // variation on determining if a word has a zero byte from https://graphics.stanford.edu/~seander/bithacks.html. This
  // part ends with using std::popcount() to count the zero nibbles, and when compiled with C++ 20 and -march=native we
  // get a single popcountl instruction generated. Codegen example: https://godbolt.org/z/MofY33
  //
  // Next, Codewords now carry their color counts with them, and we can run over them and add up total hits per Knuth by
  // aggregating min color counts between the secret and guess.
  //
  // Overall this method is much faster than the previous version by ~40% on the 4p6c game with no score cache. It's a
  // big improvement for larger games and surprisingly efficient overall.
  //
  // Elapsed time 3.1218s, average search 2.4088ms
  Score scoreCountingScalar(const Codeword guess) const {
    constexpr static uint32_t unusedPinsMask = 0xFFFFFFFFu & ~((1lu << pinCount * 4u) - 1);
    uint32_t v = this->codeword ^guess.codeword; // Matched pins are now 0.
    v |= unusedPinsMask; // Ensure that any unused pin positions are non-zero.
    uint32_t r = ~((((v & 0x77777777u) + 0x77777777u) | v) | 0x77777777u); // Yields 1 bit per matched pin
    uint8_t b = std::popcount(r);

    int allHits = 0;
    uint64_t scc = this->colorCounts4;
    uint64_t gcc = guess.colorCounts4;
    do { // colorCounts are never 0, so a do-while is solid win
      allHits += min(scc & 0xFlu, gcc & 0xFlu); // cmp/cmovb, no branching
      scc >>= 4u;
      gcc >>= 4u;
    } while (scc != 0 && gcc != 0); // Early out for many combinations

    return Score(b, allHits - b);
  }

  // This uses the full counting method from Knuth, but is organized to allow auto-vectorization of the second part.
  // When properly vectorized by the compiler, this method is O(1) time and space.
  //
  // See scoreCountingScalar() for an explanation of how hits are computed.
  //
  // Clang's auto-vectorizer will pick up on the modified loop and use vpminub to compute all minimums in a single
  // vector op, then use a fixed sequence of shuffles and adds to sum the minimums. Overall perf is very sensitive to
  // alignment of the colorCounts. Unaligned fields will make this slower than the scalar version. The auto-vectorizer
  // is also pretty sensitive to how the code is structured, and the code it generates for adding up the minimums is
  // pretty large and sub-optimal. https://godbolt.org/z/arcE5e
  //
  // Elapsed time 2.2948s, average search 1.7707ms
  Score scoreCountingAutoVec(const Codeword guess) const {
    constexpr static uint32_t unusedPinsMask = 0xFFFFFFFFu & ~((1lu << pinCount * 4u) - 1);
    uint32_t v = this->codeword ^guess.codeword; // Matched pins are now 0.
    v |= unusedPinsMask; // Ensure that any unused pin positions are non-zero.
    uint32_t r = ~((((v & 0x77777777u) + 0x77777777u) | v) | 0x77777777u); // Yields 1 bit per matched pin
    uint8_t b = std::popcount(r);

    int allHits = 0;
    auto *scc = (uint8_t *) &(this->colorCounts8);
    auto *gcc = (uint8_t *) &(guess.colorCounts8);
    for (int i = 0; i < 16; i++) {
      allHits += min(scc[i], gcc[i]);
    }

    return Score(b, allHits - b);
  }

  // This uses the full counting method from Knuth, but computing the sum of all hits is vectorized by-hand. This is
  // O(1) for both parts, guaranteed no matter what the compiler decides to do. https://godbolt.org/z/KvPf1Y
  //
  // See scoreCountingScalar() for an explanation of how hits are computed.
  //
  // Elapsed time 0.9237s, average search 0.7127ms
  Score scoreCountingHandVec(const Codeword guess) const {
    constexpr static uint32_t unusedPinsMask = 0xFFFFFFFFu & ~((1lu << pinCount * 4u) - 1);
    uint32_t v = this->codeword ^guess.codeword; // Matched pins are now 0.
    v |= unusedPinsMask; // Ensure that any unused pin positions are non-zero.
    uint32_t r = ~((((v & 0x77777777u) + 0x77777777u) | v) | 0x77777777u); // Yields 1 bit per matched pin
    uint8_t b = std::popcount(r);

    // Load the 128-bit color counts into vector registers. Each one is 16 8-bit counters.
    __m128i_u secretColorsVec = _mm_loadu_si128((__m128i_u *) &this->colorCounts8);
    __m128i_u guessColorsVec = _mm_loadu_si128((__m128i_u *) &guess.colorCounts8);

    // Find the minimum of each pair of 8-bit counters in one instruction.
    __m128i_u minColorsVec = _mm_min_epu8(secretColorsVec, guessColorsVec);

    // Add up all of the 8-bit counters into two 16-bit sums in one instruction.
    __m128i vsum = _mm_sad_epu8(minColorsVec, _mm_setzero_si128());

    // Pull out the two 16-bit sums and add them together normally to get our final answer. 3 instructions.
    int allHits = _mm_extract_epi16(vsum, 0) + _mm_extract_epi16(vsum, 4);

    return Score(b, allHits - b);
  }

  // Wrapper for the real scoring function which provides a cache.
  Score score(const Codeword guess) const {
    Score result;
    scoreCounter++;

    if (scoreCache != nullptr) {
      result = (*scoreCache)[ordinal][guess.ordinal];
      if (result.isInvalid()) {
        result = scoreCountingHandVec(guess);
        (*scoreCache)[ordinal][guess.ordinal] = result;
      }
    } else {
      result = scoreCountingHandVec(guess);
    }

    return result;
  }

  // Make a list of all codewords for a given number of "colors". Colors are represented by the
  // digits 1 thru n. This figures out how many codewords there are, which is colorCount ^ pinCount,
  // then converts the base-10 number of each codeword to it's base-colorCount representation.
  static vector<Codeword> allCodewords;
  constexpr static uint32_t onePins = 0x11111111u & ((1lu << pinCount * 4u) - 1);

  static void makeAllCodewords() {
    allCodewords.reserve(totalCodewords);

    for (uint i = 0; i < totalCodewords; i++) {
      int w = i;
      uint32_t cw = 0;
      int di = 0;
      do {
        cw |= (w % colorCount) << (4u * di++);
        w /= colorCount;
      } while (w > 0);

      cw += onePins; // Colors start at 1, not 0.
      allCodewords.emplace_back(cw, i);
    }
  }

  static const Codeword &findByValue(uint32_t x) {
    return *find_if(allCodewords.cbegin(), allCodewords.cend(),
                    [x](Codeword w) { return w.codeword == x; });
  }

  ostream &dump(ostream &stream) const {
    ios state(nullptr);
    state.copyfmt(stream);
    stream << hex << setw(pinCount) << setfill('0') << codeword;
    stream.copyfmt(state);
    return stream;
  }
};

// Statics for Codeword
uint64_t Codeword::scoreCounter = 0;
uint64_t Codeword::totalCodewords = pow(Codeword::colorCount, Codeword::pinCount);
const Score Codeword::winningScore(Codeword::pinCount, 0); // "0x40" for a 4-pin game.
vector<Codeword> Codeword::allCodewords;
vector<vector<Score>> *Codeword::scoreCache = nullptr;

// Annoying placement of stream overloads for our types.
ostream &operator<<(ostream &stream, const Codeword &c) {
  return c.dump(stream);
}

ostream &operator<<(ostream &stream, const Score &r) {
  return r.dump(stream);
}

// Gameplay Strategy
//
// This is used to build a tree of plays to make based on previous plays and results. All games
// start with the same guess, which makes the root of the tree. The score received is used to find
// what to play next via the nextMoves map. If there is no entry in the map, then the gameplay
// engine will do whatever work is necessary (possibly large) to find the next play, then add it to
// the tree. As games are played, the tree gets filled in and playtime decreases.
class Strategy {
 private:
  // The strategy is made up of the next guess to play, and a map of where to go based on the result
  // of that play.
  Codeword guess;
  unordered_map<Score, shared_ptr<Strategy>> nextMoves;

  // These extra members are to allow us to build the strategy lazily, as we play games using any
  // algorithm. nb: these are copies.
  vector<Codeword> possibleSolutions;
  vector<Codeword> unguessedCodewords;

 public:
  Strategy(Codeword guess, vector<Codeword> &possibleSolutions,
           vector<Codeword> &unguessedCodewords) {
    this->guess = guess;
    this->possibleSolutions = std::move(possibleSolutions);
    this->unguessedCodewords = std::move(unguessedCodewords);
  }

  shared_ptr<Strategy> addMove(Score score,
                               Codeword nextGuess,
                               vector<Codeword> &possibleSolutions,
                               vector<Codeword> &unguessedCodewords) {
    auto n = make_shared<Strategy>(nextGuess, possibleSolutions, unguessedCodewords);
    nextMoves[score] = n;
    return n;
  }

  shared_ptr<Strategy> getNextMove(Score score) {
    return nextMoves[score];
  }

  Codeword getGuess() {
    return guess;
  }

  vector<Codeword> &getPossibleSolutions() {
    return possibleSolutions;
  }

  vector<Codeword> &getUnguessedCodewords() {
    return unguessedCodewords;
  }
};

// Storage for the hit count data for findKnuthGuess, kept static and reused for an easy way to have them zero'd for
// each use. Also flat and sparse, but that's okay, it's faster to use it in the inner loop than using a 2D array.
static int altHitCounts[(Codeword::pinCount << 4u) + 1];

static void initHitCounts() {
  for (auto &h : altHitCounts) {
    h = 0;
  }
}

// The core of Knuth's algorithm: find the remaining solution which will eliminate the most
// possibilities on the next round, favoring, but not requiring, any choice which may still be the
// final answer.
static Codeword findKnuthGuess(const Codeword lastGuess,
                               vector<Codeword> &allCodewords,
                               vector<Codeword> &possibleSolutions,
                               bool log) {
  // Pull out the last guess from the list of all remaining candidates.
  allCodewords.erase(remove(allCodewords.begin(), allCodewords.end(), lastGuess), allCodewords.end());

  Codeword bestGuess;
  size_t bestScore = 0;
  bool bestIsPossibleSolution = false;
  for (const auto g : allCodewords) {
    // Compute a score for this guess based on how many possible solutions it will remove.
    int highestHitCount = 0;
    bool isPossibleSolution = false;
    for (const auto p : possibleSolutions) {
      Score r = g.score(p);
      altHitCounts[r.result]++;
      if (r == Codeword::winningScore) {
        isPossibleSolution = true; // Remember if this guess is in the set of possible solutions
      }
    }

    for (auto &h : altHitCounts) {
      if (h > highestHitCount) {
        highestHitCount = h;
      }
      h = 0;
    }

    size_t score = possibleSolutions.size() - highestHitCount; // Minimum codewords eliminated
    if (score > bestScore) {
      bestScore = score;
      bestGuess = g;
      bestIsPossibleSolution = isPossibleSolution;
    } else if (!bestIsPossibleSolution && isPossibleSolution && score == bestScore) {
      bestGuess = g;
      bestIsPossibleSolution = isPossibleSolution;
    }
  }

  if (log) {
    cout << "Selecting Knuth's best guess: " << bestGuess << "\tscore: " << bestScore << endl;
  }
  return bestGuess;
}

// Knuth's initial guess for 4-pin 6-color Mastermind is 1122. Generalize this to any pin count
// by using half 1's and half 2's.
static Codeword knuthInitialGuess;

static Codeword getKnuthInitialGuess() {
  if (knuthInitialGuess.isInvalid()) {
    uint32_t g = (Codeword::onePins >> Codeword::pinCount / 2 * 4) + Codeword::onePins;
    knuthInitialGuess = Codeword::findByValue(g);
    cout << "Knuth's initial guess: " << knuthInitialGuess << endl;
  }
  return knuthInitialGuess;
}

// A good random number generator for Algo::Random
static std::random_device randDevice;
static std::mt19937 randGenerator(randDevice());

// This is the gameplay strategy we build up as we play. There are a lot of common plays, and this
// allows us to reuse them almost instantly for greatly increased speed.
static shared_ptr<Strategy> gameStrategy = nullptr;

// Play the game to find the given secret codeword and return how many turns it took.
static uint findSecret(const Codeword secret, bool log = true) {
  vector<Codeword> possibleSolutions;
  vector<Codeword> allCodewords;

  if (gameStrategy == nullptr) {
    possibleSolutions = Codeword::allCodewords;
    if (algo == Algo::Knuth) {
      allCodewords = Codeword::allCodewords;
    }

    // Start w/ Knuth's first guess for all algorithms.
    gameStrategy = make_shared<Strategy>(getKnuthInitialGuess(), possibleSolutions, allCodewords);
  }

  shared_ptr<Strategy> strategy = gameStrategy;
  Codeword guess = strategy->getGuess();

  if (log) {
    cout << "Starting with secret " << secret << endl;
    cout << "Solution space contains " << strategy->getPossibleSolutions().size() << " possibilities." << endl;
    cout << "Initial guess is " << guess << endl;
  }

  uint turns = 0;

  while (true) {
    Score r = secret.score(guess); // Is our guess the winner?
    turns++;
    if (log) {
      cout << endl << "Tried guess " << guess << " against secret " << secret << " => " << r << endl;
    }

    if (r == Codeword::winningScore) {
      if (log) {
        cout << "Solution found after " << turns << " tries" << endl;
      }
      break;
    }

    // Try to pull the next move from the strategy we're building, and use that when available.
    shared_ptr<Strategy> nextMove = strategy->getNextMove(r);
    if (nextMove != nullptr) {
      strategy = nextMove;
      guess = strategy->getGuess();
      if (log) {
        cout << "Using next guess from strategy: " << guess << endl;
        cout << "Solution space now contains " << strategy->getPossibleSolutions().size() << " possibilities." << endl;
      }
      continue;
    }

    possibleSolutions = vector<Codeword>(strategy->getPossibleSolutions());

    // "5. Otherwise, remove from S any code that would not give the same response if it (the
    // guess) were the code (secret)." -- from the description of Knuth's algorithm at
    // https://en.wikipedia.org/wiki/Mastermind_(board_game)
    //
    // This describes something common to all good solutions: since the scoring function is
    // commutative, and since we know the secret remains in our set of possible solutions, we can
    // quickly eliminate lots and lots of solutions on every iteration.
    if (log) {
      cout << "Removing solutions that have no chance of being correct..." << endl;
    }
    possibleSolutions.erase(remove_if(possibleSolutions.begin(), possibleSolutions.end(),
                                      [&](Codeword c) { return c.score(guess) != r; }),
                            possibleSolutions.end());
    if (log) {
      cout << "Solution space now contains " << possibleSolutions.size() << " possibilities." << endl;
    }

    if (possibleSolutions.empty()) {
      // This is only possible if there is a bug in our scoring function.
      cout << "Failed to find solution with secret " << secret << endl;
      exit(-1);
    } else if (possibleSolutions.size() == 1) {
      guess = possibleSolutions.front();
      possibleSolutions.clear();
      if (log) {
        cout << "Only remaining solution must be correct: " << guess << endl;
      }
    } else if (algo == Algo::FirstOne) {
      guess = possibleSolutions.front();
      possibleSolutions.erase(possibleSolutions.begin());
      if (log) {
        cout << "Selecting the first possibility blindly: " << guess << endl;
      }
    } else if (algo == Algo::Random) {
      std::uniform_int_distribution<> distrib(0, possibleSolutions.size() - 1);
      guess = possibleSolutions[distrib(randGenerator)];
      possibleSolutions.erase(remove(possibleSolutions.begin(), possibleSolutions.end(), guess),
                              possibleSolutions.end());
      if (log) {
        cout << "Selecting a random possibility: " << guess << endl;
      }
    } else if (algo == Algo::Knuth) {
      allCodewords = vector<Codeword>(strategy->getUnguessedCodewords());
      guess = findKnuthGuess(guess, allCodewords, possibleSolutions, log);
    }

    strategy = strategy->addMove(r, guess, possibleSolutions, allCodewords);
  }

  if (log) {
    cout << "Done with secret " << secret << endl;
  }

  return turns;
}

int main() {
  if (Codeword::pinCount == 4) {
    // Test cases from Miyoshi
    Codeword testSecret(0x6684);
    bool success = true;
    success &= (testSecret.score(Codeword(0x0000)) == Score(0, 0));
    success &= (testSecret.score(Codeword(0x6666)) == Score(2, 0));
    success &= (testSecret.score(Codeword(0x0123)) == Score(0, 0));
    success &= (testSecret.score(Codeword(0x4567)) == Score(0, 2));
    success &= (testSecret.score(Codeword(0x4589)) == Score(1, 1));
    success &= (testSecret.score(Codeword(0x6700)) == Score(1, 0));
    success &= (testSecret.score(Codeword(0x0798)) == Score(0, 1));
    success &= (testSecret.score(Codeword(0x6484)) == Score(3, 0));
    success &= (testSecret.score(Codeword(0x6480)) == Score(2, 1));
    success &= (testSecret.score(Codeword(0x6884)) == Score(3, 0));
    success &= (testSecret.score(Codeword(0x6684)) == Score(4, 0));

    // Three extra tests to detect subtly broken scoring functions.
    success &= (testSecret.score(Codeword(0x8468)) == Score(0, 3));
    success &= (testSecret.score(Codeword(0x8866)) == Score(0, 3));
    success &= (testSecret.score(Codeword(0x8466)) == Score(0, 4));

    if (success) {
      printf("Tests pass\n");
    } else {
      printf("Some tests failed!\n");
      exit(-1);
    }
  }

  Codeword::initScoreCache();
  Codeword::makeAllCodewords();
  initHitCounts();

  if (Codeword::pinCount == 4 && Codeword::colorCount == 6) {
    printf("Run the example from Knuth's paper to compare with his results.\n");
    Codeword::scoreCounter = 0;
    findSecret(Codeword::findByValue(0x3632));
    printf("\nCodeword comparisons: %llu\n\n", Codeword::scoreCounter);
  }

  // Reset the game strategy so we start fresh after testing.
  gameStrategy = nullptr;

  // Run thru all possible secret codewords and keep track of the maximum number of turns it
  // takes to find them.
  printf("Playing %d pins %d colors game for every possible secret...\n", Codeword::pinCount, Codeword::colorCount);
  int maxTurns = 0;
  int totalTurns = 0;
  Codeword maxSecret;
  Codeword::scoreCounter = 0;
  auto startTime = chrono::high_resolution_clock::now();

  for (const auto &secret : Codeword::allCodewords) {
    uint turns = findSecret(secret, false);
    totalTurns += turns;
    if (turns > maxTurns) {
      maxTurns = turns;
      maxSecret = secret;
    }
  }

  auto endTime = chrono::high_resolution_clock::now();
  double averageTurns = (double) totalTurns / Codeword::allCodewords.size();
  printf("Average number of turns was %.4f\n", averageTurns);
  cout << "Maximum number of turns over all possible secrets was " << maxTurns << " with secret " << maxSecret << endl;
  cout << "Codeword comparisons: " << Codeword::scoreCounter << endl;
  chrono::duration<float, milli> elapsedMS = endTime - startTime;
  printf("Elapsed time %.4fs, average search %.04fms\n", elapsedMS.count() / 1000,
         elapsedMS.count() / Codeword::allCodewords.size());
  cout << "Done" << endl;
  return 0;
}
