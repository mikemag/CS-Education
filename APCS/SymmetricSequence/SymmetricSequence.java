// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

public class SymmetricSequence {

	// Exercise 12.3, but without the IllegalArgumentException if n < 1.
	//
	// That little requirement tacked on the end of the problem is annoying... see
	// the other impl.
	//
	// They key is to maintain the "oddness" of the original input all the way down
	// the recursion by simply subtracting 2 each time. Then we can use that to
	// decide what to print. Originally, I had if statements and printed n/2 or
	// n/2+1 based on whether n was odd or even, but then I "cheated" by folding the
	// ifs into a single, unconditional expression exploiting the fact that for even
	// numbers n % 2 = 0.
	private static void writeSequenceSlim(int n) {
		if (n > 0) {
			System.out.print(n / 2 + n % 2);
			writeSequenceSlim(n - 2);
			if (n > 1) {
				System.out.print(n / 2 + n % 2);
			}
		}
	}

	// Exercise 12.3, complete with IllegalArgumentException if n < 1.
	private static void writeSequence(int n) {
		if (n == 1) {
			System.out.print('1');
		} else if (n == 2) {
			System.out.print("11");
		} else if (n > 0) {
			System.out.print(n / 2 + n % 2);
			writeSequence(n - 2);
			System.out.print(n / 2 + n % 2);
		} else {
			throw new IllegalArgumentException();
		}
	}

	// Exercise 12.7. I mean, honestly, why have this exercise after 12.3?? Am I
	// missing something?
	private static void writeChars(int n) {
		if (n == 1) {
			System.out.print('*');
		} else if (n == 2) {
			System.out.print("**");
		} else if (n > 0) {
			System.out.print('<');
			writeChars(n - 2);
			System.out.print('>');
		} else {
			throw new IllegalArgumentException();
		}
	}

	public static void main(String[] args) {
		for (int i = 0; i <= 10; i++) {
			System.out.format("%d\t", i);
			try {
				writeSequenceSlim(i);
			} catch (IllegalArgumentException e) {
				System.out.print(e);
			}
			System.out.println();
		}
		System.out.println();

		for (int i = 0; i <= 10; i++) {
			System.out.format("%d\t", i);
			try {
				writeSequence(i);
			} catch (IllegalArgumentException e) {
				System.out.print(e);
			}
			System.out.println();
		}
		System.out.println();

		for (int i = 0; i <= 10; i++) {
			System.out.format("%d\t", i);
			try {
				writeChars(i);
			} catch (IllegalArgumentException e) {
				System.out.print(e);
			}
			System.out.println();
		}
		System.out.println();
	}
}
