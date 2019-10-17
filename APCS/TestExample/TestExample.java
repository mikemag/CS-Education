// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.util.*;

// A short example of how one might consider making test cases for assignments and projects.
//
// I highly recommend taking a moment to make a tiny bit of infrastructure to test your algorithms, 
// and always running those tests even if the assignment asks you to get input from the user and to 
// print your output. 
//
// There are two great reasons for this:
//
// 1) It allows you to iterate very quickly on your algorithm without having to type input and read
//    output. And it ensures that you can tell quickly if you break something you already had working.
//
// 2) You'll have to do this for real one day :) These days every software engineer is required to 
//    write their own tests for their work. Your colleagues wont let you check in your cool new code
//    without tests to prove that it works, and which ensure that it keeps working when others make
//    changes later. This is a good thing, and it's good to give it a try with your own work now.
//
// This stuff isn't too hard, and you'll find that you can copy-paste it from one project to another
// and tweak it as you go. 

// Here's the output from this example:

// Single element:	**FAILED**	([a]) -> [Hi, null], expected [Hi]
// Two:	PASSED
// More than two:	PASSED
// First try:	**FAILED**	(5, []) -> 0, expected 5
// Single element list:	PASSED
// Multi-element list:	**FAILED**	(5, [a, b, c]) -> 15, expected 14
//
//
// **** Some tests failed!! ****
//

public class TestExample {

	// Here's a silly algorithm which you might be working on for an assignment.
	public static String[] algo1(String[] stuff) {
		String[] result = Arrays.copyOf(stuff, 2);
		result[0] = "Hi";
		return result;
	}

	// Here's a happy function to run a test for algo1(). By giving your test a name, and passing in
	// the input for your function and your expected output you'll be able to see if the function worked,
	// and print a decent message one way or the other.
	//
	// One of the tricks to making this output short and useful is learning about some of the ways to 
	// print things like arrays. Arrays.toString() is pretty useful. A Google search helps for this kind 
	// of stuff.
	private static boolean runAlgo1Test(String name, String[] input, String[] expected) {
		String[] result = algo1(input);
		if (Arrays.equals(result, expected)) {
			System.out.printf("%s:\tPASSED\n", name);
			return true;
		} else {
			System.out.printf("%s:\t**FAILED**\t(%s) -> %s, expected %s\n", name, Arrays.toString(input),
					Arrays.toString(result), Arrays.toString(expected));
			return false;
		}
	}

	// Here's another small function as an example.
	public static int algo2(int n, List<String> l) {
		return n * l.size();
	}

	// And here's a version of the test function for algo2(). It's basically the same as the previous one, 
	// with the arguments, success test, and print failure print slightly modified.
	private static boolean runAlgo2Test(String name, int inputN, List<String> inputL, int expected) {
		int result = algo2(inputN, inputL);
		if (result == expected) {
			System.out.printf("%s:\tPASSED\n", name);
			return true;
		} else {
			System.out.printf("%s:\t**FAILED**\t(%d, %s) -> %d, expected %d\n", name, inputN, inputL.toString(), result,
					expected);
			return false;
		}
	}

	public static void main(String[] args) {
		boolean runTests = true;

		if (runTests) {
			// When you get this set up well, you can just add a single line for each new test case as you go.
			// You usually start with just one, and add more as you work on and improve your algorithm.
			//
			// Often the secret to making these one-liners is figuring out how to construct things like arrays 
			// and lists in a single statement. Here are some examples for arrays and ArrayLists.
			boolean allPassed = true;
			allPassed &= runAlgo1Test("Single element", new String[] { "a" }, new String[] { "Hi" });
			allPassed &= runAlgo1Test("Two", new String[] { "a", "b" }, new String[] { "Hi", "b" });
			allPassed &= runAlgo1Test("More than two", new String[] { "a", "b", "c" }, new String[] { "Hi", "b" });

			allPassed &= runAlgo2Test("First try", 5, new ArrayList<String>(), 5);
			allPassed &= runAlgo2Test("Single element list", 5, new ArrayList<String>(Arrays.asList("a")), 5);
			allPassed &= runAlgo2Test("Multi-element list", 5, new ArrayList<String>(Arrays.asList("a", "b", "c")), 14);
			
			if (!allPassed) {
				System.out.println("\n\n**** Some tests failed!! ****\n\n");
				System.exit(-1);
			}
		}

		// Do your usual user input stuff here, to satisfy the book/goalsheet.
		// I.e., "get array from user" then "call algo w/ array" then "print result".
	}
}
