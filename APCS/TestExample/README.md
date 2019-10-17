# Levenshtein Distance Samples

A short example of how one might consider making test cases for assignments and projects.

I highly recommend taking a moment to make a tiny bit of infrastructure to test your algorithms, 
and always running those tests even if the assignment asks you to get input from the user and to 
print your output. 

There are two great reasons for this:

1. It allows you to iterate very quickly on your algorithm without having to type input and read output. And it ensures that you can tell quickly if you break something you already had working.

2. You'll have to do this for real one day :) These days every software engineer is required to 
   write their own tests for their work. Your colleagues wont let you check in your cool new code
   without tests to prove that it works, and which ensure that it keeps working when others make
   changes later. This is a good thing, and it's good to give it a try with your own work now.

This stuff isn't too hard, and you'll find that you can copy-paste it from one project to another and tweak it as you go. 

Here's the output from this example:

```
Single element:	**FAILED**	([a]) -> [Hi, null], expected [Hi]
Two:	PASSED
More than two:	PASSED
First try:	**FAILED**	(5, []) -> 0, expected 5
Single element list:	PASSED
Multi-element list:	**FAILED**	(5, [a, b, c]) -> 15, expected 14


**** Some tests failed!! ****
```

# JUnit

You're probably using Eclipse, and that makes it easy to also do some JUnit tests and run them. You can do this instead, but I find it helpful to make a bit of test code yourself to start out. You may find this easier than learning about JUnit, especially if you're still new to Java.
