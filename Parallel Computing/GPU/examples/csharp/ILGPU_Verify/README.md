# Does ILGPU run on my GPU?

Install the .NET 10 SDK. If you downloaded the ZIP, extract it first. Open a terminal in the folder containing `ILGPU_Verify.csproj` and run

```sh
dotnet run -c Release
```

In Rider, open `ILGPU_Verify.slnx` and run the project. In VS Code, open the `ILGPU_Verify` folder with C# Dev Kit and press F5. The `feed/` folder supplies the packages for offline restore.

[Kernel.cs](Kernel.cs) contains one C# kernel for every device. Each of 65,536 threads writes its index plus one. [Program.cs](Program.cs) zeroes the buffer before launch, waits for the device, copies the results back, and checks every value. It runs the CPU check last.

## Tested hardware

Each entry below matched all 65,536 values using the bundled classroom packages.

| Machine | Device | Passing backend | Configuration |
| --- | --- | --- | --- |
| Windows 11 | Intel UHD Graphics 770 | OpenCL | Release |
| Windows 11 | NVIDIA GeForce RTX 4090 | CUDA (bundled PTX) | Release |
| Same Windows PC | Host CPU | CPU (SIMD) | Release |
| Mac with Apple M4 Max | Apple GPU | Metal | Debug and Release |
| Same Mac | Apple CPU | CPU (SIMD) | Debug and Release |

## If you have a Radeon

Radeon hardware is unverified. Run the same command with your Radeon graphics driver installed. Look for `FOUND OpenCL` and then `RUN   OpenCL` with your Radeon’s name. If the **next line** reads `PASS  OpenCL | 65,536 values matched`, your Radeon ran the kernel and returned every expected result.

- **Only a CPU pass, or zero GPU runs.** The CPU check succeeded. Look through the `FOUND` lines to see which GPUs ILGPU discovered.
- **`SKIP  ROCm`.** This sample leaves native ROCm disabled. The same Radeon can still pass through OpenCL if the driver exposes it to ILGPU.
- **`FAIL  OpenCL` after your Radeon’s name.** The sample attempted to use that GPU. Keep the exception and stack trace.

When reporting a Radeon result, include your GPU model, OS and driver version, and the output from `FOUND` through `Summary`. A passing run helps us add your model to the tested list.

## Build details

The bundled ILGPU and ILGPUC packages (`2.0.0-classroom.b6439a32.3`) were built from the [classroom-gpu branch of mikemag/ILGPU](https://github.com/mikemag/ILGPU/tree/classroom-gpu) at [commit 3af89d6b](https://github.com/mikemag/ILGPU/tree/3af89d6b557a105e9cace98e51965e2c70817645).

Required build inputs live in `tooling/`. ILGPUC generates the backend launchers under `obj/` and gives each set its own namespace. For OpenCL, the driver compiles the generated OpenCL C 1.2 source when the kernel loads.

The CUDA path targets SM 8.0 or newer. The bundled PTX lets students run it without the CUDA Toolkit. If you change the kernel, the build checks its generated CUDA source against `tooling/Stamp.cu`. If they differ, copy the emitted `Stamp.cu` named in the build error to `tooling/` and regenerate the PTX with a CUDA compiler:

```sh
nvcc -ptx -arch=compute_80 tooling/Stamp.cu -o tooling/Stamp.ptx
```

Update the expected result in `Program.cs` if you change the computation. The original package license notices are in the NuGet archives.

## Build the student ZIP

From `Parallel Computing/GPU/examples/csharp` in the course checkout, run the packaging script on macOS or Linux.

```sh
./build-student-zip.sh
```

Distribute `build/ILGPU_Verify.zip`. The script checks the new ZIP before replacing the previous one. It packages the source, offline `feed/`, required `tooling/`, and VS Code launch configuration under one `ILGPU_Verify/` folder. The C# ignore rules exclude generated build output.

To test what students receive, run the following from the same directory with the .NET 10 SDK installed. This uses a fresh extraction and an empty NuGet package cache.

```sh
test_dir=$(mktemp -d)
unzip -q build/ILGPU_Verify.zip -d "$test_dir"
(cd "$test_dir/ILGPU_Verify" && NUGET_PACKAGES="$test_dir/packages" dotnet run -c Release)
```
