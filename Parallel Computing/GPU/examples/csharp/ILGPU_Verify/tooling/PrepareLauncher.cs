using System;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Microsoft.Build.Utilities;

public sealed class PrepareLauncher : Task
{
    public string DirectoryPath { get; set; }
    public string Backend { get; set; }
    public string Assets { get; set; }

    public override bool Execute()
    {
        var output = Path.Combine(DirectoryPath, "compiled");
        Directory.CreateDirectory(output);
        foreach (var file in new[] { "generated", "rewritten" }
            .SelectMany(dir => Directory.GetFiles(Path.Combine(DirectoryPath, dir), "*.cs")))
        {
            var source = File.ReadAllText(file);
            if (Backend == "Cuda" && file.EndsWith("Stamp_CompiledKernel.cs"))
            {
                // Stable entry-point name across host platforms and incremental builds.
                source = Regex.Replace(source, @"method_Stamp_\d+", "ClassroomStamp");
                var literal = Regex.Match(source, "KernelSource = @\"((?:[^\"]|\"\")*)\";", RegexOptions.Singleline);
                if (!literal.Success) throw new InvalidDataException("CUDA source was absent from ILGPUC output.");
                string cu = literal.Groups[1].Value.Replace("\"\"", "\"").Replace("\r\n", "\n").Trim();
                // The probe has one ArrayView structure; normalize its compiler-assigned ID.
                if (Regex.Matches(cu, @"struct struct_\d+\b").Count == 1)
                    cu = Regex.Replace(cu, @"\bstruct_\d+\b", "ProbeView");
                // Normalize the one block label and any references without changing control flow.
                if (Regex.Matches(cu, @"\bblock_\d+:").Count == 1)
                    cu = Regex.Replace(cu, @"\bblock_\d+\b", "probe_block");
                var emitted = Path.Combine(DirectoryPath, "Stamp.cu");
                File.WriteAllText(emitted, cu + "\n");
                var cached = Path.Combine(Assets, "Stamp.cu");
                if (!File.Exists(cached) || File.ReadAllText(cached).Replace("\r\n", "\n").Trim() != cu)
                {
                    Log.LogError("CUDA kernel changed. Refresh the bundled PTX from " + emitted + " (see README).");
                    return false;
                }
                var ptx = File.ReadAllText(Path.Combine(Assets, "Stamp.ptx"));
                var bytes = System.Text.Encoding.UTF8.GetBytes(ptx.TrimEnd() + "\0");
                source = source.Replace("public override string GetSourceAsString()",
                    "public override ReadOnlyMemory<byte> GetCompiledBinary() => Convert.FromBase64String(\"" +
                    Convert.ToBase64String(bytes) + "\");\n    public override string GetSourceAsString()");
            }
            File.WriteAllText(Path.Combine(output, Path.GetFileName(file)), "namespace " + Backend + ";\n" + source);
        }
        return true;
    }
}
