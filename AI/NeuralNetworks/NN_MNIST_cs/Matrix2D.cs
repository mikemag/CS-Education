namespace NN_MNIST;

using System;

public class Matrix2D
{
    private readonly double[,] _values;

    public int Rows { get; }
    public int Cols { get; }

    // Constructor from a 2D array
    public Matrix2D(double[,] values)
    {
        Rows = values.GetLength(0);
        Cols = values.GetLength(1);
        _values = values;
    }

    // Constructor from row and column count (initialized to zeros)
    public Matrix2D(int rows, int cols)
    {
        Rows = rows;
        Cols = cols;
        _values = new double[rows, cols];
    }

    // Dot product
    public Matrix2D Dot2(Matrix2D other)
    {
        var rows = _values.GetLength(0);
        var cols = _values.GetLength(1);
        var otherRows = other._values.GetLength(0);
        var otherCols = other._values.GetLength(1);

        if (cols != otherRows)
            throw new InvalidOperationException("Invalid matrix dimensions for dot product.");

        var result = new double[rows, otherCols];

        for (int i = 0; i < rows; i++) // rows
        {
            for (int j = 0; j < otherCols; j++) // other.cols
            {
                double sum = 0;
                for (int k = 0; k < cols && k < otherRows; k++) // cols
                {
                    sum += _values[i, k] * other._values[k, j];
                }

                result[i, j] = sum;
            }
        }

        return new Matrix2D(result);
    }

    public unsafe Matrix2D Dot(Matrix2D other)
    {
        if (Cols != other.Rows)
            throw new InvalidOperationException("Invalid matrix dimensions for dot product.");

        var result = new double[Rows, other.Cols];

        fixed (double* pValues = _values, pOtherValues = other._values, pResult = result)
        {
            for (int i = 0; i < Rows; i++)
            {
                double* rowStartA = pValues + (i * Cols);  // Start of the current row in matrix A
                double* resultRowStart = pResult + (i * other.Cols);  // Start of the current row in result

                for (int j = 0; j < other.Cols; j++)
                {
                    double sum = 0;
                    double* columnStartB = pOtherValues + j;  // Start of the column in matrix B

                    for (int k = 0; k < Cols; k++)
                    {
                        sum += *(rowStartA + k) * *(columnStartB + k * other.Cols);
                    }

                    // Store result without bounds checks
                    *(resultRowStart + j) = sum;
                }
            }
        }

        return new Matrix2D(result);
    }

    // Transpose
    public Matrix2D Transpose()
    {
        var transposed = new double[Cols, Rows];

        for (int i = 0; i < Rows; i++)
        {
            for (int j = 0; j < Cols; j++)
            {
                transposed[j, i] = _values[i, j];
            }
        }

        return new Matrix2D(transposed);
    }

    // Add two matrices
    public Matrix2D Add(Matrix2D other)
    {
        if (Rows != other.Rows || Cols != other.Cols)
            throw new InvalidOperationException("Matrix dimensions must match for addition.");

        var result = new double[Rows, Cols];

        for (int i = 0; i < Rows; i++)
        {
            for (int j = 0; j < Cols; j++)
            {
                result[i, j] = _values[i, j] + other._values[i, j];
            }
        }

        return new Matrix2D(result);
    }

    // Subtract another matrix
    public Matrix2D Subtract(Matrix2D other)
    {
        if (Rows != other.Rows || Cols != other.Cols)
            throw new InvalidOperationException("Matrix dimensions must match for subtraction.");

        var result = new double[Rows, Cols];

        for (int i = 0; i < Rows; i++)
        {
            for (int j = 0; j < Cols; j++)
            {
                result[i, j] = _values[i, j] - other._values[i, j];
            }
        }

        return new Matrix2D(result);
    }

    // Element-wise product
    public Matrix2D ElementWiseProduct(Matrix2D other)
    {
        if (Rows != other.Rows || Cols != other.Cols)
            throw new InvalidOperationException("Matrix dimensions must match for element-wise multiplication.");

        var result = new double[Rows, Cols];

        for (int i = 0; i < Rows; i++)
        {
            for (int j = 0; j < Cols; j++)
            {
                result[i, j] = _values[i, j] * other._values[i, j];
            }
        }

        return new Matrix2D(result);
    }

    // Scalar multiply
    public Matrix2D ScalarMultiply(double scalar)
    {
        var result = new double[Rows, Cols];

        for (int i = 0; i < Rows; i++)
        {
            for (int j = 0; j < Cols; j++)
            {
                result[i, j] = _values[i, j] * scalar;
            }
        }

        return new Matrix2D(result);
    }

    // Apply a lambda function to every element
    public Matrix2D Apply(Func<double, double> func)
    {
        var result = new double[Rows, Cols];

        for (int i = 0; i < Rows; i++)
        {
            for (int j = 0; j < Cols; j++)
            {
                result[i, j] = func(_values[i, j]);
            }
        }

        return new Matrix2D(result);
    }

    public Matrix2D GaussianFill(Random random, double mean, double standardDeviation)
    {
        for (int i = 0; i < Rows; i++)
        {
            for (int j = 0; j < Cols; j++)
            {
                // Box-Muller transform for generating Gaussian-distributed values
                double u1 = 1.0 - random.NextDouble(); // Uniform(0,1] random doubles
                double u2 = 1.0 - random.NextDouble();
                double randStdNormal =
                    Math.Sqrt(-2.0 * Math.Log(u1)) * Math.Cos(2.0 * Math.PI * u2); // Standard normal (0,1)
                _values[i, j] =
                    mean + standardDeviation * randStdNormal; // Scale to the desired mean and standard deviation
            }
        }

        return this;
    }

    public int ArgMax()
    {
        if (Cols != 1)
            throw new InvalidOperationException("ArgMax only for single column vectors.");

        double max = Double.MinValue;
        int idx = 0;
        for (var i = 0; i < this.Rows; i++)
        {
            if (_values[i, 0] > max)
            {
                idx = i;
                max = _values[i, 0];
            }
        }

        return idx;
    }
}