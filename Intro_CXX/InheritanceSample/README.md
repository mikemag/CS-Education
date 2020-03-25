# Basic Inheritance in C++

This sample shows basic inheritance in C++ using simple classes and shared pointers.

This assumes:
- you know how to define a class and make a local variable of that class in a function.
- you know how to make a subclass and make a local with that, too.

This is for students that already understand the basics of classes and can use them successfully
as locals in a function, or even in a `vector<>`. 

This sample will help them extend their knowledge to be able to store subclass instances in a 
`vector<>` of the
base class type. It will also allow them to return subclass instances from a function and store them into a 
variable of the base type.

This glosses over memory allocation and leans on `shared_ptr<>` to allow students to work with instances
without having to worry about lifetime, nor worry about whether they have the space to store a subclass
into a base class variable. 

This is presented as a portion of a RPG game since that is where students in the class I help out with
first encounter structs and classes.
