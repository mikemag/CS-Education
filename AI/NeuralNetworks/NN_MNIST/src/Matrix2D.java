// Copyright (c) Michael M. Magruder (https://github.com/mikemag)
//
// This source code is licensed under the MIT license found in the
// LICENSE file in the root directory of this source tree.

import java.util.Random;
import java.util.function.DoubleUnaryOperator;

// A trivial 2d matrix class, again kept as simple as possible. A new matrix is returned from
// every op.

public class Matrix2D {

  double data[][];
  int rows;
  int cols;

  public Matrix2D(int rows, int cols) {
    data = new double[rows][cols];
    this.rows = rows;
    this.cols = cols;
  }

  public Matrix2D(double[][] data) {
    this.data = data;
    this.rows = data.length;
    this.cols = data[0].length;
  }

  public Matrix2D gaussianFill(Random r) {
    for (int i = 0; i < data.length; i++) {
      for (int j = 0; j < data[i].length; j++) {
        data[i][j] = r.nextGaussian();
      }
    }
    return this;
  }

  public double[][] data() {
    return data;
  }

  public Matrix2D dotProduct(Matrix2D other) {
    if (this.cols != other.rows) {
      throw new IllegalArgumentException("Invalid matrix dimensions for multiplication.");
    }

    double[][] result = new double[this.rows][other.cols];
    for (int i = 0; i < this.rows; i++) {
      for (int j = 0; j < other.cols; j++) {
        for (int k = 0; k < this.cols; k++) {
          result[i][j] += this.data[i][k] * other.data[k][j];
        }
      }
    }
    return new Matrix2D(result);
  }

  public Matrix2D transpose() {
    double[][] result = new double[this.cols][this.rows];
    for (int i = 0; i < this.cols; i++) {
      for (int j = 0; j < this.rows; j++) {
        result[i][j] = this.data[j][i];
      }
    }
    return new Matrix2D(result);
  }

  public Matrix2D add(Matrix2D other) {
    if (this.rows != other.rows || this.cols != other.cols) {
      throw new IllegalArgumentException("Matrices dimensions do not match for addition.");
    }

    double[][] result = new double[this.rows][this.cols];
    for (int i = 0; i < this.rows; i++) {
      for (int j = 0; j < this.cols; j++) {
        result[i][j] = this.data[i][j] + other.data[i][j];
      }
    }
    return new Matrix2D(result);
  }

  public Matrix2D subtract(Matrix2D other) {
    if (this.rows != other.rows || this.cols != other.cols) {
      throw new IllegalArgumentException("Matrices dimensions do not match for addition.");
    }

    double[][] result = new double[this.rows][this.cols];
    for (int i = 0; i < this.rows; i++) {
      for (int j = 0; j < this.cols; j++) {
        result[i][j] = this.data[i][j] - other.data[i][j];
      }
    }
    return new Matrix2D(result);
  }

  public Matrix2D elementWiseProduct(Matrix2D other) {
    if (this.rows != other.rows || this.cols != other.cols) {
      throw new IllegalArgumentException(
          "Matrices must be of the same dimensions for element-wise product.");
    }

    double[][] result = new double[this.rows][this.cols];
    for (int i = 0; i < this.rows; i++) {
      for (int j = 0; j < this.cols; j++) {
        result[i][j] = this.data[i][j] * other.data[i][j];
      }
    }
    return new Matrix2D(result);
  }

  public Matrix2D scalarMultiply(double scalar) {
    double[][] result = new double[this.rows][this.cols];
    for (int i = 0; i < this.rows; i++) {
      for (int j = 0; j < this.cols; j++) {
        result[i][j] = this.data[i][j] * scalar;
      }
    }
    return new Matrix2D(result);
  }

  public Matrix2D applyFunction(DoubleUnaryOperator func) {
    double[][] result = new double[this.rows][this.cols];
    for (int i = 0; i < this.rows; i++) {
      for (int j = 0; j < this.cols; j++) {
        result[i][j] = func.applyAsDouble(this.data[i][j]);
      }
    }
    return new Matrix2D(result);
  }

  public int argMax() {
    if (this.cols != 1) {
      throw new IllegalArgumentException(
          "ArgMax only for single column vectors.");
    }
    double max = Double.NEGATIVE_INFINITY;
    int idx = 0;
    for (int i = 0; i < this.rows; i++) {
      if (data[i][0] > max) {
        idx = i;
        max = data[i][0];
      }
    }
    return idx;
  }
}
