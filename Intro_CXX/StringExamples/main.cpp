// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

//
// Examples of interesting things we can do with strings. These might be useful for the RPG project.
//
// - building strings
// - converting numbers to strings
// - finding things in strings
// - taking parts of or replacing parts of strings
// - trimming or padding strings
//

#include <algorithm> // only for std::transform
#include <iostream>
#include <sstream>
#include <string>

using namespace std;

int main() {
  // Build up a string a bit at a time with + and +=.
  {
    string s = "AB";
    s = "CD" + s + "EF";
    cout << s << endl;  // CDABEF
  }


  // Build up a string a bit at a time with <<. Be sure to #include <sstream>
  // You can build strings just like using cout for printing output!!
  stringstream ss;
  ss << "My health: " << 42 << ", damage: " << 20.20;
  cout << ss.str() << endl;  // My health: 42, damage: 20.2


  // Convert a string to all lower case, requires #include <algorithm>
  {
    string s = "ABCDEFG";
    cout << "[" << s << "]\n";
    transform(begin(s), end(s), begin(s), ::tolower);
    cout << "[" << s << "]\n";  // [abcdefg]
  }


  // Is there a specific character anywhere in a string?
  // http://www.cplusplus.com/reference/string/string/find/
  {
    string s = "Hi there! Have fun.";
    cout << s << endl;
    if (s.find('!') != string::npos) {
      cout << "String has a '!' in it!" << endl;
    }
  }


  // Convert a number to a string
  // http://www.cplusplus.com/reference/string/to_string/
  {
    string s = to_string(4.2);
    cout << "[" << s << "]\n";  // [4.200000]
  }


  // Does a string start with a number (and so might work with stoi() or stof())?
  // http://www.cplusplus.com/reference/cctype/isdigit/
  {
    string s = "42.2";
    cout << "[" << s << "]\n";
    if (isdigit(s[0])) {
      float f = stof(s);
      cout << "Float: " << f << endl;  // Float: 42.2
    }
  }


  // Find a string within a string.
  {
    string s = "Hi there! Have fun.";
    cout << s << endl;
    if (s.find("ere") != string::npos) {
      cout << "String has 'ere' in it!" << endl;
    }
  }


  // Take the first 2 characters of a string.
  // http://www.cplusplus.com/reference/string/string/substr/
  {
    string s = "ABCDEFG";
    cout << "[" << s << "]\n";
    s = s.substr(0, 2);
    cout << "[" << s << "]\n";  // [AB]
  }


  // Take the middle n of a string, in the case the middle 3, skipping 2.
  {
    string s = "ABCDEFG";
    cout << "[" << s << "]\n";
    s = s.substr(2, 3);
    cout << "[" << s << "]\n";  // [CDE]
  }


  // Take the last n of a string, the last 2 in the case.
  {
    string s = "ABCDEFG";
    cout << "[" << s << "]\n";
    s = s.substr(s.length() - 2);
    cout << "[" << s << "]\n";  // [FG]
  }


  // Get first character, last character.
  // http://www.cplusplus.com/reference/string/string/front/
  // http://www.cplusplus.com/reference/string/string/back/
  {
    string s = "abcd";
    cout << "First: [" << s.front() << "]\n";  // First: [a]
    cout << "Last: [" << s.back() << "]\n";  // Last: [d]
  }


  // Insert into a string.
  // http://www.cplusplus.com/reference/string/string/insert/
  {
    string s = "ABCDEFG";
    cout << "[" << s << "]\n";
    s.insert(2, "***");
    cout << "[" << s << "]\n";  // [AB***CDEFG]
  }


  // Replace part of a string.
  // http://www.cplusplus.com/reference/string/string/replace/
  {
    string s = "ABCDEFG";
    cout << "[" << s << "]\n";
    s.replace(3, 2, "***");  // Replace 2 characters starting at index 3 with ***.
    cout << "[" << s << "]\n";  // [ABC***FG]
  }


  // Trim spaces off the end of a string, i.e., rtrim.
  // http://www.cplusplus.com/reference/string/string/erase/
  // http://www.cplusplus.com/reference/string/string/find_last_not_of/
  // http://www.cplusplus.com/reference/string/string/find_first_not_of/
  {
    string s = "Hello    ";
    cout << "[" << s << "]\n";
    s.erase(s.find_last_not_of(' ') + 1);
    cout << "[" << s << "]\n";  // [Hello]
  }


  // Trim spaces off the front of a string, i.e., ltrim.
  {
    string s = "    Hello";
    cout << "[" << s << "]\n";
    s.erase(0, s.find_first_not_of(' '));
    cout << "[" << s << "]\n";  // [Hello]
  }


  // Trim spaces off the front and back of a string, i.e., trim.
  {
    string s = "    Hello   ";
    cout << "[" << s << "]\n";
    s.erase(0, s.find_first_not_of(' '));
    s.erase(s.find_last_not_of(' ') + 1);
    cout << "[" << s << "]\n";  // [Hello]
  }


  // Pad out a string with more spaces. This will clip a long string, or extend a short one.
  {
    string s = "Hi";
    cout << "[" << s << "]\n";
    s.resize(10, ' ');
    cout << "[" << s << "]\n";  // [Hi        ]
  }


  // Pad out a string of a float with more spaces or 0s.
  {
    string s = "42.123";
    cout << "[" << s << "]\n";
    s.resize(s.find('.') + 1 + 6, '0');  // Ensure 6 positions beyond the decimal point.
    cout << "[" << s << "]\n";  // [42.123000]
  }

  return 0;
}
