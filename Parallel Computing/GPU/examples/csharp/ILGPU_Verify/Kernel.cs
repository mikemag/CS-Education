using ILGPU;
using ILGPU.Runtime;

static class Probe
{
    // ILGPU supplies a different index to each thread.
    static void Stamp(Index1D index, ArrayView1D<int, Stride1D.Dense> output)
    {
        output[index] = index.X + 1;
    }

    public static void Launch(AcceleratorStream stream, ArrayView1D<int, Stride1D.Dense> output, int count)
    {
        stream.Launch((Index1D)count, index => Stamp(index, output));
    }
}
