#!/usr/bin/env python3
"""Build and package the LumaCurve ARM64 C engine."""
import argparse, hashlib, json, os, shutil, subprocess, sys, tempfile, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / "tools")]
from source_lists import DEFAULT_TARGET, sources_for  # noqa: E402

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--ndk", type=Path, help="Android NDK directory")
parser.add_argument("--target", choices=("highlevel",), default=DEFAULT_TARGET)
parser.add_argument("--api", type=int, default=26)
parser.add_argument("--package", action="store_true")
parser.add_argument("--sanitize", action="store_true", help="trap undefined behavior in a validation ELF")
parser.add_argument("--output-dir", type=Path, help="isolated build output directory")
args = parser.parse_args()

if args.api < 26:
    parser.error("requires Android API 26+")


def find_ndk():
    if args.ndk:
        return args.ndk
    for key in ("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT"):
        if os.environ.get(key):
            return Path(os.environ[key])
    roots = []
    for key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(key):
            roots.append(Path(os.environ[key]) / "ndk")
    if os.name == "nt":
        roots += [
            Path("D:/App/SDK/ndk"),
            Path("C:/Users") / os.environ.get("USERNAME", "") / "AppData/Local/Android/Sdk/ndk",
        ]
    else:
        roots += [Path.home() / "Android/Sdk/ndk", Path.home() / "Library/Android/sdk/ndk"]
    for root in roots:
        if root.is_dir():
            versions = sorted(
                root.iterdir(),
                key=lambda p: tuple(int(n) for n in p.name.split(".") if n.isdigit()),
                reverse=True,
            )
            if versions:
                return versions[0]
    parser.error("NDK not found; pass --ndk or set ANDROID_NDK_HOME")


ndk = find_ndk().resolve()
host = {"win32": "windows-x86_64", "linux": "linux-x86_64", "darwin": "darwin-x86_64"}[sys.platform]
prebuilt = ndk / "toolchains/llvm/prebuilt" / host
bin_dir = prebuilt / "bin"
suffix = ".exe" if os.name == "nt" else ""
clang = bin_dir / ("clang" + suffix)
if not clang.is_file():
    parser.error(f"clang not found: {clang}")

mode = args.target
sources = sources_for(args.target)

out = args.output_dir.resolve() if args.output_dir else ROOT / "build" / mode
out.mkdir(parents=True, exist_ok=True)
target = out / "luma_curve_daemon"

command = [
    str(clang),
    f"--target=aarch64-linux-android{args.api}",
    f"--sysroot={prebuilt / 'sysroot'}",
    "-O2",
    "-g",
    f"-ffile-prefix-map={ROOT}=.",
    "-fPIE",
    "-pie",
    "-ffp-contract=off",
    "-fno-strict-aliasing",
    "-std=c11",
    "-D_DEFAULT_SOURCE",
    "-Wall",
    "-Wextra",
    "-Werror",
    "-Wno-unused-label",
]
if args.target == "region":
    command.append("-DIOS_REGION")
if args.target == "highlevel":
    command += ["-DIOS_PRODUCTION", "-DIOS_BUSINESS_MAIN"]
if args.sanitize:
    command += ["-fsanitize=undefined", "-fsanitize-trap=undefined"]
command += [str(p) for p in sources]
command += [
    "-lm",
    "-ldl",
    "-Wl,--no-relax",
    "-Wl,-z,max-page-size=16384",
    "-Wl,--build-id=sha1",
    "-Wl,-z,relro",
    "-Wl,-z,now",
    "-o",
    str(target),
]
subprocess.run(command, check=True)
print(f"Built {target} ({mode})")

metadata = dict(
    mode=mode,
    undefined_behavior_traps=args.sanitize,
    target=args.target,
    api=args.api,
    ndk=str(ndk),
    inputs=[str(p.relative_to(ROOT)) for p in sources],
    input_sha256={str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources + sorted((ROOT / "csrc").glob("*.h"))},
    module_properties=(ROOT / "module/module.prop").read_text(encoding="utf-8"),
    sha256=hashlib.sha256(target.read_bytes()).hexdigest(),
    upstream_original_sha256="5a2650c26bb2218edff673e4a8ddb09bed6e5f6eb4dda48014c194cc24f1db9a",
    project="LumaCurve",
    license="GPL-3.0",
)
(out / "build.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")

if args.package:
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    # Stage into an empty directory so removed UI assets cannot leak into a later ZIP.
    module = Path(tempfile.mkdtemp(prefix="module-", dir=out))
    shutil.copytree(ROOT / "module", module, dirs_exist_ok=True)
    daemon = module / "system/bin/luma_curve_daemon"
    daemon.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(target, daemon)
    subprocess.run([str(bin_dir / ("llvm-strip" + suffix)), "--strip-debug", str(daemon)], check=True)
    metadata["packaged_daemon_sha256"] = hashlib.sha256(daemon.read_bytes()).hexdigest()
    metadata["module_files_sha256"] = {p.relative_to(module).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(module.rglob("*")) if p.is_file() and p.name != "build-info.json"}
    (out / "build.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    (module / "build-info.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    properties = dict(line.split("=", 1) for line in metadata["module_properties"].splitlines() if "=" in line and not line.startswith("#"))
    archive = dist / f"{properties['id']}-{properties['version']}.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as z:
        for path in sorted(module.rglob("*")):
            if not path.is_file():
                continue
            name = path.relative_to(module).as_posix()
            info = zipfile.ZipInfo(name)
            info.compress_type = zipfile.ZIP_DEFLATED
            mode_bits = (
                0o755
                if name.endswith(".sh") or name.endswith("update-binary") or name.endswith("luma_curve_daemon")
                else 0o644
            )
            info.create_system = 3
            info.external_attr = (0o100000 | mode_bits) << 16
            z.writestr(info, path.read_bytes())
    print(f"Packaged {archive}")
