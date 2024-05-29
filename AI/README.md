# AI

I'll clean this up and add more resources and guidance over time. My goal is to have a reasonable
starting place for students to learn about AI at various levels.

## TODOs

* write a good guide for setting up Unity ML-Agents on PC and Mac for the 24/25 school year.
* Use Karpathy's minigrad as a basis for some good intro lessons for AP CS students into NN's. I
  feel like I could make some short lessons/projects which would lead in nicely to Nielsen's book.

## Handwriting Recognition

A great introduction to Neural Networks is to recognize handwritten digits. There are tons of
examples all around the internet on this and students can pick and choose from a bunch of different
guides.

* Michael Nielsen's online book is excellent. Chapter 1 is approachable for AP CS students. Chapter
  2 requires too much math, honestly, but you can build some intuition if you power through it. Even
  though it is Python AP CS students can still learn from it and translate it to Java, and get the
  same results as the book shows.
    * http://neuralnetworksanddeeplearning.com/index.html
    * Repo with current Python code: https://github.com/MichalDanielDobrzanski/DeepLearningPython
* The MNIST dataset is the gold standard for training and testing data
    * https://www.kaggle.com/datasets/hojjatk/mnist-dataset
    * https://www.tensorflow.org/datasets/catalog/mnist
* 3Blue1Brown lessons
    * These videos are excellent, as are the blog pages I link
      here: https://www.3blue1brown.com/topics/neural-networks
    * The first 3 videos are a great way to start
    * Videos 4 & 5 are optional for more advanced students who want to get a feel for some of the
      math behind it.
* Some example walkthroughs
    * These are all in Python, with reasonable explanations of the concepts.
    * https://machinelearningmastery.com/how-to-develop-a-convolutional-neural-network-from-scratch-for-mnist-handwritten-digit-classification/
    * https://learner-cares.medium.com/handwritten-digit-recognition-using-convolutional-neural-network-cnn-with-tensorflow-2f444e6c4c31
    * https://www.geeksforgeeks.org/handwritten-digit-recognition-using-neural-network/
    * w/
      TensorFlow: https://www.digitalocean.com/community/tutorials/how-to-build-a-neural-network-to-recognize-handwritten-digits-with-tensorflow
        * https://www.tensorflow.org/learn

## Unity

The Unity ML-Agents package gives you the ability to use PyTorch directly from Unity to train and
run models. Setup is pretty rough right now, but once completed it's pretty amazing.

* https://unity.com/products/machine-learning-agents
* https://github.com/Unity-Technologies/ml-agents
* The first video in this series is quite
  good: https://www.youtube.com/watch?v=zPFU30tbyKs&list=PLzDRvYVwl53vehwiN_odYJkPBzcqFw110

## General Resources

* Karpathy vids: https://www.youtube.com/playlist?list=PLAqhIrjkxbuWI23v9cThsA9GvCAUhRvKZ
* 9600dev: https://www.9600.dev/posts/machine-learning-developer-notes/
* https://colah.github.io/ Not specifically MNIST, just interesting things
