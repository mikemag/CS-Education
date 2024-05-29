// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

// A very simple implementation of a neural network to recognize handwritten digits based upon
// Michael Nielsen's book Neural Networks and Deep Learning, Chapters 1 & 2.
// http://neuralnetworksanddeeplearning.com/index.html
//
// This is a reasonably faithful Java translation of the Python code in the book, kept simple to
// aid learning.

public class Main {

  public static void main(String[] args) throws IOException {
    var trainingData = loadMNIST("/Users/mike/dev/misc_personal/NNTest/train-images.idx3-ubyte",
        "/Users/mike/dev/misc_personal/NNTest/train-labels.idx1-ubyte", false);
    var testData = loadMNIST("/Users/mike/dev/misc_personal/NNTest/t10k-images.idx3-ubyte",
        "/Users/mike/dev/misc_personal/NNTest/t10k-labels.idx1-ubyte", true);

    NeuralNetwork nn = new NeuralNetwork(new int[]{784, 30, 10});
    nn.SGD(trainingData, 30, 10, 3.0, testData);
  }

  public static void NN1() {
    // Turn off sigmoid for this one
    NeuralNetwork nn = new NeuralNetwork(new int[]{1, 1});

    ArrayList<NeuralNetwork.Data> trainingData = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      trainingData.add(
          new NeuralNetwork.Data(new Matrix2D(new double[][]{{i}}).transpose(),
              new Matrix2D(new double[][]{{i * 2}}).transpose()));
    }

    ArrayList<NeuralNetwork.Data> testData = new ArrayList<>();
    for (int i = 1; i < 100; i += 5) {
      testData.add(
          new NeuralNetwork.Data(new Matrix2D(new double[][]{{i}}).transpose(),
              new Matrix2D(new double[][]{{i * 2}}).transpose()));
    }

    nn.SGD(trainingData, 10, 10, .0001, testData);
  }

  public static void NN2() {
    // Turn off sigmoid for this one
    NeuralNetwork nn = new NeuralNetwork(new int[]{2, 1});

    ArrayList<NeuralNetwork.Data> trainingData = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      trainingData.add(
          new NeuralNetwork.Data(new Matrix2D(new double[][]{{i, i * i}}).transpose(),
              new Matrix2D(new double[][]{{0.5 * i * i + i - 1}}).transpose()));
    }

    ArrayList<NeuralNetwork.Data> testData = new ArrayList<>();
    for (int i = 1; i < 100; i += 5) {
      testData.add(
          new NeuralNetwork.Data(new Matrix2D(new double[][]{{i, i * i}}).transpose(),
              new Matrix2D(new double[][]{{0.5 * i * i + i - 1}}).transpose()));
    }

    nn.SGD(trainingData, 100, 10, .000000002, testData);
  }

  public static List<NeuralNetwork.Data> loadMNIST(String dataPath, String labelPath,
      boolean isTestData) throws IOException {
    DataInputStream dataInputStream = new DataInputStream(
        new BufferedInputStream(new FileInputStream(dataPath)));
    int magicNumber = dataInputStream.readInt();
    int imageCount = dataInputStream.readInt();
    int rows = dataInputStream.readInt();
    int cols = dataInputStream.readInt();

    System.out.println("Loading " + dataPath);
    System.out.printf("Magic number %d, %d items, %dx%d\n", magicNumber, imageCount, rows, cols);

    DataInputStream labelInputStream = new DataInputStream(
        new BufferedInputStream(new FileInputStream(labelPath)));
    magicNumber = labelInputStream.readInt();
    int labelCount = labelInputStream.readInt();

    System.out.println("Loading " + labelPath);
    System.out.printf("Magic number %d, %d labels\n", magicNumber, labelCount);
    assert imageCount == labelCount;

    ArrayList<NeuralNetwork.Data> ret = new ArrayList<>(imageCount);

    for (int i = 0; i < imageCount; i++) {
      double[][] img = new double[rows * cols][1];
      for (int j = 0; j < rows * cols; j++) {
        img[j][0] = dataInputStream.readUnsignedByte() / 255.0;
      }
      int label = labelInputStream.readUnsignedByte();
      if (isTestData) {
        ret.add(new NeuralNetwork.Data(new Matrix2D(img), label));
      } else {
        double[][] lab = new double[10][1];
        lab[label][0] = 1.0;
        ret.add(new NeuralNetwork.Data(new Matrix2D(img), new Matrix2D(lab)));
      }
    }

    dataInputStream.close();
    labelInputStream.close();

    System.out.println("Done loading dataset.");

    return ret;
  }
}

/*
Some sample results


NeuralNetwork nn = new NeuralNetwork(new int[]{784, 30, 10});
nn.SGD(trainingData, 30, 10, 3.0, testData);
Normalize inputs w/ 256.0
gaussianFill both biases and weights w/ a Random(42)
Best: 9544

/Users/mike/Library/Java/JavaVirtualMachines/openjdk-21.0.1/Contents/Home/bin/java -javaagent:/Users/mike/Applications/IntelliJ IDEA Ultimate.app/Contents/lib/idea_rt.jar=63578:/Users/mike/Applications/IntelliJ IDEA Ultimate.app/Contents/bin -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -classpath /Users/mike/dev/misc_personal/NNTest/out/production/NNTest Main
Loading /Users/mike/dev/misc_personal/NNTest/train-images.idx3-ubyte
Magic number 2051, 60000 items, 28x28
Loading /Users/mike/dev/misc_personal/NNTest/train-labels.idx1-ubyte
Magic number 2049, 60000 labels
Done loading dataset.
Loading /Users/mike/dev/misc_personal/NNTest/t10k-images.idx3-ubyte
Magic number 2051, 10000 items, 28x28
Loading /Users/mike/dev/misc_personal/NNTest/t10k-labels.idx1-ubyte
Magic number 2049, 10000 labels
Done loading dataset.
Start: 863 / 10000
Epoch 0: 9109 / 10000
Epoch 1: 9302 / 10000
Epoch 2: 9365 / 10000
Epoch 3: 9386 / 10000
Epoch 4: 9431 / 10000
Epoch 5: 9448 / 10000
Epoch 6: 9462 / 10000
Epoch 7: 9455 / 10000
Epoch 8: 9475 / 10000
Epoch 9: 9464 / 10000
Epoch 10: 9480 / 10000
Epoch 11: 9488 / 10000
Epoch 12: 9530 / 10000
Epoch 13: 9511 / 10000
Epoch 14: 9509 / 10000
Epoch 15: 9518 / 10000
Epoch 16: 9540 / 10000
Epoch 17: 9492 / 10000
Epoch 18: 9524 / 10000
Epoch 19: 9544 / 10000
Epoch 20: 9529 / 10000
Epoch 21: 9521 / 10000
Epoch 22: 9539 / 10000
Epoch 23: 9510 / 10000
Epoch 24: 9510 / 10000
Epoch 25: 9515 / 10000
Epoch 26: 9541 / 10000
Epoch 27: 9542 / 10000
Epoch 28: 9536 / 10000
Epoch 29: 9528 / 10000

Process finished with exit code 0

NeuralNetwork nn = new NeuralNetwork(new int[]{784, 30, 10});
nn.SGD(trainingData, 30, 10, 3.0, testData);
Normalize inputs w/ 255.0
gaussianFill both biases and weights w/ a Random(42)
Best: 9545

/Users/mike/Library/Java/JavaVirtualMachines/openjdk-21.0.1/Contents/Home/bin/java -javaagent:/Users/mike/Applications/IntelliJ IDEA Ultimate.app/Contents/lib/idea_rt.jar=63601:/Users/mike/Applications/IntelliJ IDEA Ultimate.app/Contents/bin -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -classpath /Users/mike/dev/misc_personal/NNTest/out/production/NNTest Main
Loading /Users/mike/dev/misc_personal/NNTest/train-images.idx3-ubyte
Magic number 2051, 60000 items, 28x28
Loading /Users/mike/dev/misc_personal/NNTest/train-labels.idx1-ubyte
Magic number 2049, 60000 labels
Done loading dataset.
Loading /Users/mike/dev/misc_personal/NNTest/t10k-images.idx3-ubyte
Magic number 2051, 10000 items, 28x28
Loading /Users/mike/dev/misc_personal/NNTest/t10k-labels.idx1-ubyte
Magic number 2049, 10000 labels
Done loading dataset.
Start: 863 / 10000
Epoch 0: 9112 / 10000
Epoch 1: 9279 / 10000
Epoch 2: 9345 / 10000
Epoch 3: 9399 / 10000
Epoch 4: 9419 / 10000
Epoch 5: 9423 / 10000
Epoch 6: 9464 / 10000
Epoch 7: 9466 / 10000
Epoch 8: 9470 / 10000
Epoch 9: 9506 / 10000
Epoch 10: 9485 / 10000
Epoch 11: 9490 / 10000
Epoch 12: 9484 / 10000
Epoch 13: 9527 / 10000
Epoch 14: 9508 / 10000
Epoch 15: 9491 / 10000
Epoch 16: 9494 / 10000
Epoch 17: 9510 / 10000
Epoch 18: 9514 / 10000
Epoch 19: 9517 / 10000
Epoch 20: 9512 / 10000
Epoch 21: 9539 / 10000
Epoch 22: 9530 / 10000
Epoch 23: 9508 / 10000
Epoch 24: 9520 / 10000
Epoch 25: 9528 / 10000
Epoch 26: 9523 / 10000
Epoch 27: 9536 / 10000
Epoch 28: 9537 / 10000
Epoch 29: 9545 / 10000

Process finished with exit code 0


NeuralNetwork nn = new NeuralNetwork(new int[]{784, 30, 10});
nn.SGD(trainingData, 30, 10, 3.0, testData);
Raw inputs
gaussianFill both biases and weights w/ a Random(42)
Best: 2280 -- just terrible

/Users/mike/Library/Java/JavaVirtualMachines/openjdk-21.0.1/Contents/Home/bin/java -javaagent:/Users/mike/Applications/IntelliJ IDEA Ultimate.app/Contents/lib/idea_rt.jar=63617:/Users/mike/Applications/IntelliJ IDEA Ultimate.app/Contents/bin -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -classpath /Users/mike/dev/misc_personal/NNTest/out/production/NNTest Main
Loading /Users/mike/dev/misc_personal/NNTest/train-images.idx3-ubyte
Magic number 2051, 60000 items, 28x28
Loading /Users/mike/dev/misc_personal/NNTest/train-labels.idx1-ubyte
Magic number 2049, 60000 labels
Done loading dataset.
Loading /Users/mike/dev/misc_personal/NNTest/t10k-images.idx3-ubyte
Magic number 2051, 10000 items, 28x28
Loading /Users/mike/dev/misc_personal/NNTest/t10k-labels.idx1-ubyte
Magic number 2049, 10000 labels
Done loading dataset.
Start: 893 / 10000
Epoch 0: 1391 / 10000
Epoch 1: 1492 / 10000
Epoch 2: 1762 / 10000
Epoch 3: 2314 / 10000
Epoch 4: 1303 / 10000
Epoch 5: 1591 / 10000
Epoch 6: 1754 / 10000
Epoch 7: 2280 / 10000
Epoch 8: 2105 / 10000
Epoch 9: 1873 / 10000
Epoch 10: 2529 / 10000
Epoch 11: 1863 / 10000
Epoch 12: 1781 / 10000
Epoch 13: 1927 / 10000
Epoch 14: 1951 / 10000
Epoch 15: 1896 / 10000
Epoch 16: 1975 / 10000
Epoch 17: 1990 / 10000
Epoch 18: 1910 / 10000
Epoch 19: 1887 / 10000
Epoch 20: 1793 / 10000
Epoch 21: 1956 / 10000
Epoch 22: 2058 / 10000
Epoch 23: 2025 / 10000
Epoch 24: 2031 / 10000
Epoch 25: 2155 / 10000
Epoch 26: 2049 / 10000
Epoch 27: 1891 / 10000
Epoch 28: 1946 / 10000
Epoch 29: 2048 / 10000

Process finished with exit code 0


NeuralNetwork nn = new NeuralNetwork(new int[]{784, 30, 10});
nn.SGD(trainingData, 30, 10, 0.03, testData);
Raw inputs
gaussianFill both biases and weights w/ a Random(42)
Best: 8556

/Users/mike/Library/Java/JavaVirtualMachines/openjdk-21.0.1/Contents/Home/bin/java -javaagent:/Users/mike/Applications/IntelliJ IDEA Ultimate.app/Contents/lib/idea_rt.jar=63663:/Users/mike/Applications/IntelliJ IDEA Ultimate.app/Contents/bin -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -classpath /Users/mike/dev/misc_personal/NNTest/out/production/NNTest Main
Loading /Users/mike/dev/misc_personal/NNTest/train-images.idx3-ubyte
Magic number 2051, 60000 items, 28x28
Loading /Users/mike/dev/misc_personal/NNTest/train-labels.idx1-ubyte
Magic number 2049, 60000 labels
Done loading dataset.
Loading /Users/mike/dev/misc_personal/NNTest/t10k-images.idx3-ubyte
Magic number 2051, 10000 items, 28x28
Loading /Users/mike/dev/misc_personal/NNTest/t10k-labels.idx1-ubyte
Magic number 2049, 10000 labels
Done loading dataset.
Start: 893 / 10000
Epoch 0: 2708 / 10000
Epoch 1: 4501 / 10000
Epoch 2: 5506 / 10000
Epoch 3: 6215 / 10000
Epoch 4: 6700 / 10000
Epoch 5: 6975 / 10000
Epoch 6: 7109 / 10000
Epoch 7: 7415 / 10000
Epoch 8: 7529 / 10000
Epoch 9: 7584 / 10000
Epoch 10: 7743 / 10000
Epoch 11: 7819 / 10000
Epoch 12: 7876 / 10000
Epoch 13: 8018 / 10000
Epoch 14: 8093 / 10000
Epoch 15: 8129 / 10000
Epoch 16: 8164 / 10000
Epoch 17: 8211 / 10000
Epoch 18: 8182 / 10000
Epoch 19: 8236 / 10000
Epoch 20: 8316 / 10000
Epoch 21: 8304 / 10000
Epoch 22: 8381 / 10000
Epoch 23: 8397 / 10000
Epoch 24: 8460 / 10000
Epoch 25: 8470 / 10000
Epoch 26: 8443 / 10000
Epoch 27: 8535 / 10000
Epoch 28: 8556 / 10000
Epoch 29: 8518 / 10000

Process finished with exit code 0

 */