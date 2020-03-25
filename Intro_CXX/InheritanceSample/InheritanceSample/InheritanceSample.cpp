//
// A short example to show inheritance and placing subclasses into a vector.
//
// This assumes:
// - you know how to define a class and make a local variable of that class in a function.
// - you know how to make a subclass and make a local with that, too.

#include <iostream>
#include <string>
#include <vector>

using namespace std;

// A basic "enemy" class, with some of the usual methods.
class Enemy {
public:
  Enemy(int _hp, int _speed, string _name) {
    hp = _hp;
    speed = _speed;
    name = _name;
  }
  bool isAlive() { return hp > 0; }
  virtual void attack(int damage) { hp -= damage; }
  string getName() { return name; }

  // This looks weird, and I'm not gonna explain the method definition. Feel free to cut-and-paste
  // this and adapt it for any class. The intent shoud be clear, and the syntax on the return line
  // should also be very familiar. 
  virtual ostream& dump(ostream& stream) const {
    return stream << "Enemy: hp=" << hp << ", speed= " << speed << ", name='" << name << "'";
  }

protected:
  int hp;
  int speed;
  string name;
};

// This typedef will make it super-easy to make instances of our Enemy class on "the heap" rather than
// on the stack. We'll make a "Ptr" typedef for each of our classes. A shared_ptr<> is a bit of C++ magic
// which makes it easy to make objects on the heap, automatically clean them up, and they are safe to use
// without worrying about the many issues that usually come up with allocating and releasing dynamic memory.
typedef shared_ptr<Enemy> EnemyPtr;

// Again, I know this looks strange. This is what makes it easy to use an Enemy with cout. Also feel 
// free to cut-and-paste this for your own classes.
ostream& operator<<(ostream& stream, const Enemy& e) {
  return e.dump(stream);
}

// A simple subclass of Enemy that adds a new variable.
class Orc : public Enemy {
public:
  Orc(int _hp, int _speed, string _name, string _clan) : Enemy(_hp, _speed, _name) { clan = _clan; }

  virtual ostream& dump(ostream& stream) const {
    return Enemy::dump(stream) << ", clan=" << clan;
  }

private:
  string clan;
};

// Again, we make our Ptr type.
typedef shared_ptr<Orc> OrcPtr;

ostream& operator<<(ostream& stream, const Orc& o) {
  return o.dump(stream);
}

// Another subclass with a different new variable.
class Elf : public Enemy {
public:
  Elf(int _hp, int _speed, string _name) : Enemy(_hp, _speed, _name) { damage_resist = 2; }
  virtual void attack(int damage) { hp -= (damage - damage_resist); }

  virtual ostream& dump(ostream& stream) const {
    return Enemy::dump(stream) << ", resist=" << damage_resist;
  }

private:
  int damage_resist;
};

typedef shared_ptr<Elf> ElfPtr;

ostream& operator<<(ostream& stream, const Elf& e) {
  return e.dump(stream);
}


int main() {
  // This should look familiar... we just make some objects on the stack and do things with them.
  Enemy e(20, 10, "Enemy One");
  e.attack(5);
  cout << e << endl;

  Orc o(20, 5, "Head Orc", "Clan A");
  o.attack(5);
  cout << o << endl;

  Elf elf(20, 15, "Elf One");
  elf.attack(5);
  cout << elf << endl;

  // This is a bit different. We make an Elf object on the heap with make_shared<Elf> and keep a ElfPtr to it.
  // make_shared<Elf> takes the same parameters as any other Elf constructor. The shared_ptr<> is a bit of C++
  // magic which takes care of cleaning up the memory for you later, and ensuring you don't access the wrong
  // memory with your ElfPtr.
  ElfPtr ep = make_shared<Elf>(20, 15, "Elf Two");

  // The ElfPtr is just an "address" of the Elf object we made on the heap, so this prints a hex number like 016D20B4.
  cout << ep << endl;

  // We have to use -> to call methods given an ElfPtr instead of .
  ep->attack(5);

  // We can get the Elf out of the ElfPtr by using *ep. This copies the Elf off of the heap and into 
  // the local variable on the stack. You probably don't want to do this, but it's useful to know you can.
  elf = *ep;

  // Likewise, we can use cout like above by using *ep instead of ep. 
  cout << *ep << endl;

  cout << endl;

  // Let's make a list of enemies to fight.
  vector<EnemyPtr> enemies;
  enemies.push_back(make_shared<Orc>(20, 5, "Meat Shield 1", "Clan A"));
  enemies.push_back(make_shared<Elf>(20, 5, "Bowman 1"));
  enemies.push_back(make_shared<Orc>(20, 5, "Meat Shield 2", "Clan A"));
  enemies.push_back(make_shared<Orc>(20, 5, "Meat Shield 3", "Clan B"));
  enemies.push_back(make_shared<Elf>(20, 5, "Bowman 2"));

  // Now let's fight them all. Notice that the only thing that seems strange in the following
  // code is the -> instead of a . to call methods.
  cout << "Attacking all enemies until dead with AOE attack, damage=5..." << endl;
  bool anyAlive = true;
  int round = 0;

  while (anyAlive) {
    cout << "Round " << ++round << endl;
    anyAlive = false;

    for (int i = 0; i < enemies.size(); i++) {
      EnemyPtr ep = enemies.at(i);

      if (ep->isAlive()) {
        cout << "Attacking " << ep->getName() << " with 5 damage." << endl;
        ep->attack(5);

        if (!ep->isAlive()) {
          cout << "    " << ep->getName() << " dies!!" << endl;
        }
        else {
          cout << "    " << *ep << endl;
          anyAlive = true;
        }
      }
    }
    cout << endl;
  }
  cout << "All enimies killed." << endl;

  return 0;
}
