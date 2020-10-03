public class Main {

    // This is a very short example of how to do very, very basic performance testing in Java.
    //
    // Basically, you time many iterations of the thing you want to measure using System.nanoTime(), then compute the
    // total elapsed time and the per-iteration time.
    //
    // When you do this for small functions, this is called "microbenchmarking". Try it with some of the functions you
    // write for your AP CS class, and try seeing if you can make them faster by coding them differently.
    //
    // To get the best measurements try to do the following:
    //   1. Make sure your benchmark runs for many seconds on your computer.
    //   2. Close all other programs.
    //   3. Run from the command line, not the IDE.

    // There are two important things for you to remember:
    //
    // 1. Microbenchmarking is hard, and this sample only shows the smallest beginning of it. I have left out a lot
    // already, but it's good to start simply. You _will_ get strange results sometimes. To do this very well you need a
    // deep understanding of the JVM, JIT compilation, and machine code. And even then, professionals get this stuff
    // wrong all the time!!
    //
    // 2. Don't waste too much time doing this. There is a very famous quote from one of the most famous computer
    // scientists of all time:
    //
    //     "Premature optimization is the root of all evil." -- Donald Knuth, 1974
    //
    // That's important to keep in mind. Don't go crazy. Note that the short quote is taken a little bit out of context.
    // Here's the full quote:
    //
    //     "Programmers waste enormous amounts of time thinking about, or worrying about, the speed of noncritical parts
    //     of their programs, and these attempts at efficiency actually have a strong negative impact when debugging and
    //     maintenance are considered. We should forget about small efficiencies, say about 97% of the time: premature
    //     optimization is the root of all evil. Yet we should not pass up our opportunities in that critical 3%."

    public static void main(String[] args) {
        System.out.println("Basic performance measurement in Java");
        testAdd(5, 6);
    }

    // Write a little helper function to test your real function
    public static void testAdd(int a, int b) {
        long iterations = 10_000_000_000L; // Iterate until this runs for a few seconds on your computer
        int sum = 0;
        long s = System.nanoTime(); // Start measuring just before your loop
        for (long i = 0; i < iterations; i++) {
             sum += add(a, b); // Call your interesting function in the loop and do something with the result
        }
        long e = System.nanoTime(); // Stop measuring right after the loop
        System.out.println(sum); // Do something with the result
        printPerfResult(iterations, s, e); // Print your perf data
    }

    public static int add(int a, int b) {
        return a + b;
    }

    // System.nanoTime() let's you measure how many nanoseconds (ns) elapsed. That's one billionth of a second.
    // Very small functions will take ns or us (microseconds, one millionth of a second). Larger functions will be
    // measured in ms (thoushands of a second).
    //
    // Given a time delta in ns, you can conver to us by dividing by 1_000.0, and to ms by dividing by 1_000_000.0.
    //
    // I always print the total elapsed time in ms, and for this small add() function I printed the per-iteration
    // time in ns.
    public static void printPerfResult(long iterations, long start, long end) {
        double totalMS = (end - start) / 1_000_000.0;
        double iterNS = (end - start) / (double)iterations;
        System.out.printf("Elapsed time: %,.2fms, time per item: %.4fns\n", totalMS, iterNS);
    }
 }
