using System.Buffers.Binary;

namespace NN_MNIST;

// http://neuralnetworksanddeeplearning.com/chap1.html

class Program
{
    static void Main(string[] args)
    {
        var trainingData = LoadMnist("/Users/mike/dev/misc_personal/NN_MNIST/train-images.idx3-ubyte",
            "/Users/mike/dev/misc_personal/NN_MNIST/train-labels.idx1-ubyte", false);
        var testData = LoadMnist("/Users/mike/dev/misc_personal/NN_MNIST/t10k-images.idx3-ubyte",
            "/Users/mike/dev/misc_personal/NN_MNIST/t10k-labels.idx1-ubyte", false);

        // Chapter 1 results:
        // 784, 30, 10 -- 30, 10, 3.0 -- 95.44
        // 784, 100, 10 -- 30, 10, 3.0 -- 96.69
        // 784, 10 -- 30, 10, 3.0 -- 84.16
        // 784, 30, 30, 10 -- 30, 10, 3.0 -- 95.33
        Random rng = new Random(42);
        var nn = new NeuralNetwork([784, 30, 10], rng);
        nn.Sgd(trainingData, 30, 10, 3.0, testData, rng);
    }

    // https://yann.lecun.com/exdb/mnist/
    private static NNData[] LoadMnist(string imagePath, string labelPath, bool testData)
    {
        Console.WriteLine($"Loading from {imagePath} and {labelPath}...");
        using var imageStream = new FileStream(imagePath, FileMode.Open, FileAccess.Read);
        using var imageReader = new BinaryReader(imageStream);
        var magicNumber = ReadBigEndianInt(imageReader);
        if (magicNumber != 2051) throw new Exception($"Invalid magic number {magicNumber} in file {imageStream.Name}");
        var imageCount = ReadBigEndianInt(imageReader);
        var rows = ReadBigEndianInt(imageReader);
        var cols = ReadBigEndianInt(imageReader);

        using var labelStream = new FileStream(labelPath, FileMode.Open, FileAccess.Read);
        using var labelReader = new BinaryReader(labelStream);
        magicNumber = ReadBigEndianInt(labelReader);
        if (magicNumber != 2049) throw new Exception($"Invalid magic number {magicNumber} in file {labelStream.Name}");
        var labelCount = ReadBigEndianInt(labelReader);
        if (labelCount != imageCount)
            throw new Exception($"Invalid label count {labelCount} vs {imageCount} in file {labelStream.Name}");

        var data = new NNData[imageCount];
        for (var i = 0; i < imageCount; i++)
        {
            var img = new double[rows * cols, 1];
            for (var j = 0; j < rows * cols; j++)
            {
                img[j, 0] = imageReader.ReadByte() / 255.0;
            }

            var label = labelReader.ReadByte();
            data[i] = new NNData(img, label);
        }

        Console.WriteLine($"Loaded {imageCount:N0} images");

        return data;
    }

    private static int ReadBigEndianInt(BinaryReader reader)
    {
        Span<byte> b = stackalloc byte[4];
        // ReSharper disable once MustUseReturnValue
        reader.Read(b);
        return BinaryPrimitives.ReadInt32BigEndian(b);
    }
}

public struct NNData
{
    public Matrix2D Image { get; }
    public int Label { get; set; }
    public Matrix2D ExpectedOutput { get; }

    public NNData(double[,] image, int label)
    {
        Image = new Matrix2D(image);
        Label = label;
        var i = new double[10, 1];
        i[Label, 0] = 1.0;
        ExpectedOutput = new Matrix2D(i);
    }
}