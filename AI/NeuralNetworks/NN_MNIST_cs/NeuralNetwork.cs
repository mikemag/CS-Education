using System.Diagnostics;
using System.Runtime.InteropServices;

namespace NN_MNIST;

public class NeuralNetwork
{
    private readonly int _numLayers;
    private int[] _layerSizes;
    Matrix2D[] _biases;
    Matrix2D[] _weights;

    public NeuralNetwork(int[] layerSizes, Random rng)
    {
        _numLayers = layerSizes.Length;
        _layerSizes = layerSizes;

        _biases = new Matrix2D[_numLayers - 1]; // no biases for the input layer
        for (int i = 0; i < _biases.Length; i++)
        {
            _biases[i] = new Matrix2D(layerSizes[i + 1], 1).GaussianFill(rng, 0, 1);
        }

        _weights = new Matrix2D[_numLayers - 1]; // no weights for the input layer
        for (int i = 0; i < _weights.Length; i++)
        {
            _weights[i] = new Matrix2D(layerSizes[i + 1], layerSizes[i]).GaussianFill(rng, 0, 1);
        }
    }

    public Matrix2D FeedForward(Matrix2D a)
    {
        for (var i = 0; i < _biases.Length; i++)
        {
            var b = _biases[i];
            var w = _weights[i];
            var z = w.Dot(a).Add(b);
            a = z.Apply(Sigmoid);
        }

        return a;
    }

    public void Sgd(Span<NNData> trainingData, int epochs, int miniBatchSize, double eta, ReadOnlySpan<NNData> testData, Random rng)
    {
        var sw = Stopwatch.StartNew();
        int best = Evaluate(testData);
        Console.WriteLine($"Start: {best} / {testData.Length} in {sw.ElapsedMilliseconds}ms");
        sw.Restart();

        var n = trainingData.Length;
        for (var e = 0; e < epochs; e++)
        {
            rng.Shuffle(trainingData);
            for (int miniBatchOffset = 0; miniBatchOffset < n; miniBatchOffset += miniBatchSize)
            {
                var miniBatch = trainingData.Slice(miniBatchOffset, miniBatchSize);
                UpdateMiniBatch(miniBatch, eta);
            }

            if (!testData.IsEmpty)
            {
                var c = Evaluate(testData);
                if (c > best) best = c;
                Console.WriteLine($"Epoch {e}: {c} / {testData.Length} (best is {best}) in {sw.ElapsedMilliseconds}ms");
            }
            else
            {
                Console.WriteLine($"Epoch {e} complete in {sw.ElapsedMilliseconds}ms");
            }

            sw.Restart();
        }
    }

    private void UpdateMiniBatch(ReadOnlySpan<NNData> miniBatch, double eta)
    {
        var nablaB = new Matrix2D[_biases.Length];
        for (var i = 0; i < nablaB.Length; i++)
        {
            nablaB[i] = new Matrix2D(_biases[i].Rows, _biases[i].Cols);
        }

        var nablaW = new Matrix2D[_weights.Length];
        for (var i = 0; i < nablaW.Length; i++)
        {
            nablaW[i] = new Matrix2D(_weights[i].Rows, _weights[i].Cols);
        }

        foreach (var d in miniBatch)
        {
            var (deltaNablaB, deltaNablaW) = Backprop(d.Image, d.ExpectedOutput);
            for (var i = 0; i < nablaB.Length; i++)
            {
                nablaB[i] = nablaB[i].Add(deltaNablaB[i]);
                nablaW[i] = nablaW[i].Add(deltaNablaW[i]);
            }
        }

        var etaByBatch = eta / miniBatch.Length;
        for (var i = 0; i < nablaB.Length; i++)
        {
            _biases[i] = _biases[i].Subtract(nablaB[i].ScalarMultiply(etaByBatch));
            _weights[i] = _weights[i].Subtract(nablaW[i].ScalarMultiply(etaByBatch));
        }
    }

    // Compute nabla_b and nabla_w representing the gradient for the cost function C_x.
    // nabla_b and nabla_w are layer-by-layer lists of matrices, similar biases and weights
    public (Matrix2D[], Matrix2D[]) Backprop(Matrix2D x, Matrix2D y)
    {
        var nablaB = new Matrix2D[_biases.Length];
        for (var i = 0; i < nablaB.Length; i++)
        {
            nablaB[i] = new Matrix2D(_biases[i].Rows, _biases[i].Cols);
        }

        var nablaW = new Matrix2D[_weights.Length];
        for (var i = 0; i < nablaW.Length; i++)
        {
            nablaW[i] = new Matrix2D(_weights[i].Rows, _weights[i].Cols);
        }

        // feedforward
        var activation = x;
        //  list to store all the activations, layer by layer
        var activations = new Matrix2D[_biases.Length + 1];
        activations[0] = x;

        // list to store all the z vectors, layer by layer
        var zs = new Matrix2D[_biases.Length];

        for (var i = 0; i < _biases.Length; i++)
        {
            var z = _weights[i].Dot(activation).Add(_biases[i]);
            zs[i] = z;
            activation = z.Apply(Sigmoid);
            activations[i + 1] = activation;
        }

        // backward pass
        var delta = CostDerivative(activations[^1], y).ElementWiseProduct(zs[^1].Apply(SigmoidPrime));
        nablaB[^1] = delta;
        nablaW[^1] = delta.Dot(activations[^2].Transpose());

        for (var l = 2; l < _numLayers; l++)
        {
            var z = zs[^l];
            var sp = z.Apply(SigmoidPrime);
            delta = _weights[^(l - 1)].Transpose().Dot(delta).ElementWiseProduct(sp);
            nablaB[^l] = delta;
            nablaW[^l] = delta.Dot(activations[^(l + 1)].Transpose());
        }

        return (nablaB, nablaW);
    }

    private int Evaluate(ReadOnlySpan<NNData> testData)
    {
        int totalCorrect = 0;
        foreach (var d in testData)
        {
            var p = FeedForward(d.Image);
            var predictedLabel = p.ArgMax();
            if (predictedLabel == d.Label)
            {
                totalCorrect++;
            }
        }

        return totalCorrect;
    }

    // // TODO: old eval func for learning simple polynomials
    // private int evaluateSimple(List<Data> testData) {
    //   int totalCorrect = 0;
    //   for (var d : testData) {
    //     var p = feedForward(d.inputs);
    //     double err = 0.0;
    //     for (int i = 0; i < d.outputs.rows; i++) {
    //       var delta = p.data[i][0] - d.outputs.data[i][0];
    //       System.out.printf("%f -> %f, d=%f\n", d.inputs.data[i][0], p.data[i][0], delta);
    //       err += delta * delta;
    //     }
    //     err /= d.inputs.rows;
    //     if (err < 0.5) {
    //       totalCorrect++;
    //     }
    //   }
    //   return totalCorrect;
    // }

    private static Matrix2D CostDerivative(Matrix2D outputActivation, Matrix2D y)
    {
        return outputActivation.Subtract(y);
    }

    private static double Sigmoid(double z)
    {
        return 1 / (1 + Math.Exp(-z));
    }

    private static double SigmoidPrime(double z)
    {
        return Sigmoid(z) * (1 - Sigmoid(z));
    }
}