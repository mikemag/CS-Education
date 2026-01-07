// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.util.Collections;
import java.util.List;
import java.util.Random;

// This should look very, very similar to the code in http://neuralnetworksanddeeplearning.com/chap1.html

public class NeuralNetwork {

  int numLayers;
  int[] layerSizes;
  Matrix2D[] biases;
  Matrix2D[] weights;
  Matrix2D[] deltaNablaB;
  Matrix2D[] deltaNablaW;

  public static class Data {

    Matrix2D inputs;
    Matrix2D outputs;
    int testLabel;

    public Data(Matrix2D inputs, Matrix2D outputs) {
      this.inputs = inputs;
      this.outputs = outputs;
    }

    public Data(Matrix2D inputs, int testLabel) {
      this.inputs = inputs;
      this.testLabel = testLabel;
    }
  }


  public NeuralNetwork(int[] layerSizes) {
    numLayers = layerSizes.length;
    this.layerSizes = layerSizes;

    Random rand = new Random(42);
    biases = new Matrix2D[numLayers - 1]; // no biases for the input layer
    for (int i = 0; i < biases.length; i++) {
      biases[i] = new Matrix2D(layerSizes[i + 1], 1).gaussianFill(rand);
    }

    weights = new Matrix2D[numLayers - 1]; // no weights for the input layer
    for (int i = 0; i < weights.length; i++) {
      weights[i] = new Matrix2D(layerSizes[i + 1], layerSizes[i]).gaussianFill(rand);
    }
  }

  public Matrix2D feedForward(Matrix2D a) {
    for (int i = 0; i < biases.length; i++) {
      var b = biases[i];
      var w = weights[i];
      var z = w.dotProduct(a).add(b);
      a = z.applyFunction(this::sigmoid);
    }
    return a;
  }

  public void SGD(List<Data> trainingData, int epochs, int miniBatchSize, double eta,
      List<Data> testData) {
    System.out.printf("Start: %d / %d\n", evaluate(testData), testData.size());

    var n = trainingData.size();
    for (int e = 0; e < epochs; e++) {
      Collections.shuffle(trainingData);
      for (int miniBatchOffset = 0; miniBatchOffset < n; miniBatchOffset += miniBatchSize) {
        var miniBatch = trainingData.subList(miniBatchOffset, miniBatchOffset + miniBatchSize);
        updateMiniBatch(miniBatch, eta);
      }
      if (testData != null) {
        System.out.printf("Epoch %d: %d / %d\n", e, evaluate(testData), testData.size());
      } else {
        System.out.printf("Epoch %d complete\n", e);
      }
    }
  }

  private void updateMiniBatch(List<Data> miniBatch, double eta) {
    var nablaB = new Matrix2D[biases.length];
    for (int i = 0; i < nablaB.length; i++) {
      nablaB[i] = Matrix2D.zerosLike(biases[i]);
    }
    var nablaW = new Matrix2D[weights.length];
    for (int i = 0; i < nablaW.length; i++) {
      nablaW[i] = Matrix2D.zerosLike(weights[i]);
    }

    for (var d : miniBatch) {
      backprop(d.inputs, d.outputs);
      for (int i = 0; i < nablaB.length; i++) {
        nablaB[i] = nablaB[i].add(deltaNablaB[i]);
        nablaW[i] = nablaW[i].add(deltaNablaW[i]);
      }
    }
    double etaByBatch = eta / miniBatch.size();
    for (int i = 0; i < nablaB.length; i++) {
      biases[i] = biases[i].subtract(nablaB[i].scalarMultiply(etaByBatch));
      weights[i] = weights[i].subtract(nablaW[i].scalarMultiply(etaByBatch));
    }
  }

  // Compute nabla_b and nabla_w representing the gradient for the cost function C_x.
  // nabla_b and nabla_w are layer-by-layer lists of matrices, similar biases and weights
  public void backprop(Matrix2D x, Matrix2D y) {
    deltaNablaB = new Matrix2D[biases.length];
    for (int i = 0; i < deltaNablaB.length; i++) {
      deltaNablaB[i] = Matrix2D.zerosLike(biases[i]);
    }
    deltaNablaW = new Matrix2D[weights.length];
    for (int i = 0; i < deltaNablaW.length; i++) {
      deltaNablaW[i] = Matrix2D.zerosLike(weights[i]);
    }

    // feedforward
    var activation = x;
    //  list to store all the activations, layer by layer
    Matrix2D activations[] = new Matrix2D[biases.length + 1];
    activations[0] = x;

    // list to store all the z vectors, layer by layer
    Matrix2D zs[] = new Matrix2D[biases.length];

    for (int i = 0; i < biases.length; i++) {
      var z = weights[i].dotProduct(activation).add(biases[i]);
      zs[i] = z;
      activation = z.applyFunction(this::sigmoid);
      activations[i + 1] = activation;
    }

    // backward pass
    var delta = costDerivative(activations[activations.length - 1], y).elementWiseProduct(
        zs[zs.length - 1].applyFunction(this::sigmoidPrime));
    deltaNablaB[deltaNablaB.length - 1] = delta;
    deltaNablaW[deltaNablaW.length - 1] = delta.dotProduct(
        activations[activations.length - 2].transpose());

    for (int l = 2; l < numLayers; l++) {
      var z = zs[zs.length - l];
      var sp = z.applyFunction(this::sigmoidPrime);
      delta = weights[weights.length - l + 1].transpose().dotProduct(delta)
          .elementWiseProduct(sp);
      deltaNablaB[deltaNablaB.length - l] = delta;
      deltaNablaW[deltaNablaW.length - l] = delta.dotProduct(
          activations[activations.length - l - 1].transpose());
    }

  }

  private int evaluate(List<Data> testData) {
    int totalCorrect = 0;
    for (var d : testData) {
      var p = feedForward(d.inputs);
      var predictedLabel = p.argMax();
      if (predictedLabel == d.testLabel) {
        totalCorrect++;
      }
    }

    return totalCorrect;

  }

  // TODO: old eval func for learning simple polynomials
//  private int evaluateSimple(List<Data> testData) {
//    int totalCorrect = 0;
//    for (var d : testData) {
//      var p = feedForward(d.inputs);
//      double err = 0.0;
//      for (int i = 0; i < d.outputs.rows; i++) {
//        var delta = p.data[i][0] - d.outputs.data[i][0];
//        System.out.printf("%f -> %f, d=%f\n", d.inputs.data[i][0], p.data[i][0], delta);
//        err += delta * delta;
//      }
//      err /= d.inputs.rows;
//      if (err < 0.5) {
//        totalCorrect++;
//      }
//    }
//
//    return totalCorrect;
//  }

  private Matrix2D costDerivative(Matrix2D outputActivation, Matrix2D y) {
    return outputActivation.subtract(y);
  }

  private double sigmoid(double z) {
    return 1 / (1 + Math.exp(-z));
  }

  private double sigmoidPrime(double z) {
    return sigmoid(z) * (1 - sigmoid(z));
  }

}
