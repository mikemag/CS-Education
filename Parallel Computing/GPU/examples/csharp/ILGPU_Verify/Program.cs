using System;
using System.Linq;
using ILGPU;
using ILGPU.Runtime;
using ILGPU.Runtime.OpenCL;

using var context = Context.CreateDefault();
var devices = context.Devices.ToArray();
foreach (var device in devices)
{
    var version = device is CLDevice cl ? $" | OpenCL C {cl.CVersion}" : "";
    Console.WriteLine($"FOUND {device.AcceleratorType} | {device.Name}{version}");
}

int gpuPasses = 0, failures = 0;
bool cpuPassed = false;
foreach (var device in devices.OrderBy(d => d.AcceleratorType == AcceleratorType.CPU))
{
    var type = device.AcceleratorType;
    if (device is CLDevice cl && (cl.DeviceType & CLDeviceType.CL_DEVICE_TYPE_GPU) == 0)
    {
        Console.WriteLine($"SKIP  OpenCL | {device.Name} is not a GPU");
        continue;
    }
    if (type == AcceleratorType.ROCm)
    {
        Console.WriteLine($"SKIP  ROCm | {device.Name} | no native AMD image in this build");
        continue;
    }

    Console.WriteLine($"RUN   {type} | {device.Name}");
    try
    {
        using var accelerator = device.CreateAccelerator(context);
        Verify(accelerator);
        if (type == AcceleratorType.CPU)
            cpuPassed = true;
        else
            gpuPasses++;
    }
    catch (Exception error)
    {
        Console.WriteLine($"FAIL  {type} | {device.Name}\n{error}");
        failures++;
    }
}
Console.WriteLine($"Summary: {gpuPasses} GPU run(s) passed, CPU {(cpuPassed ? "passed" : "did not pass")}, {failures} failed run(s).");
return failures == 0 && cpuPassed ? 0 : 1;

static void Verify(Accelerator accelerator)
{
    const int count = 65_536;
    var stream = accelerator.DefaultStream;
    using var buffer = stream.Allocate1D<int>(count);
    buffer.MemSetToZero();
    switch (accelerator.AcceleratorType)
    {
        case AcceleratorType.Cuda: Cuda.Probe.Launch(stream, buffer.View, count); break;
        case AcceleratorType.OpenCL: OpenCL.Probe.Launch(stream, buffer.View, count); break;
        case AcceleratorType.Metal: Metal.Probe.Launch(stream, buffer.View, count); break;
        case AcceleratorType.CPU: CPU.Probe.Launch(stream, buffer.View, count); break;
        default: throw new NotSupportedException(accelerator.AcceleratorType.ToString());
    }
    stream.Synchronize();
    int[] actual = buffer.GetAsArray1D();
    for (int i = 0; i < count; i++)
        if (actual[i] != i + 1)
            throw new InvalidOperationException($"At index {i}: expected {i + 1}, got {actual[i]}.");
    Console.WriteLine($"PASS  {accelerator.AcceleratorType} | {count:N0} values matched");
}
