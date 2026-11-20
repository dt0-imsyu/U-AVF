"""
Compile and run the android-app Java test suite off-device.

The suite was previously unrunnable without hand-assembling a classpath that
included android.jar at runtime and the generated BundledPlatformAssets class.
Without it, "13 tests pass" could not be reproduced by anyone else, so real
regressions stayed invisible. This script makes the baseline measurable:

    python tools/release/run-java-tests.py --apk <release.apk>

android.jar is supplied by the SDK, and BundledPlatformAssets.java is generated
from the packaged APK exactly as the release build does.
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'android-app'
SRC = APP / 'src'
TESTS = APP / 'tests'

# Tests that require external fixtures and are skipped without them.
NEEDS_ARGS = {
    'com.example.winavf.InstalledEspRecoveryTest': 'needs an empty sparse fixture directory',
    'com.example.winavf.Lz4NativeFixtureTest': 'needs hex/size/hash arguments',
    'com.example.winavf.PersistentDiskGrowthTest': 'needs an empty growth-test.raw fixture',
    'com.example.winavf.WindowsKdWireTest': 'needs a Windows KD capture',
    'com.example.winavf.WindowsSetupLayoutTest': 'needs a WinPE template and geometry file',
}


def find_tool(name, *candidates):
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            return str(Path(candidate) / name)
    found = shutil.which(name)
    if found:
        return found
    raise SystemExit(f'Missing required tool: {name}')


def generate_assets(apk, output):
    script = Path(__file__).with_name('generate-platform-integrity.py')
    subprocess.run([sys.executable, str(script), '--apk', str(apk),
                    '--generated', str(output)], check=True, stdout=subprocess.DEVNULL)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path,
                        help='Packaged APK used to generate BundledPlatformAssets.java')
    parser.add_argument('--sdk', type=Path,
                        default=Path(os.environ.get('LOCALAPPDATA', '')) / 'Android/Sdk')
    parser.add_argument('--java-home', type=Path,
                        default=Path(os.environ.get('JAVA_HOME')) if os.environ.get('JAVA_HOME')
                        else Path(r'C:\Program Files\Android\Android Studio\jbr'))
    parser.add_argument('--keep', action='store_true', help='Keep the temporary build directory')
    args = parser.parse_args()

    android_jar = args.sdk / 'platforms/android-36/android.jar'
    if not android_jar.is_file():
        raise SystemExit(f'Missing {android_jar}')
    if not args.apk.is_file():
        raise SystemExit(f'Missing {args.apk}')

    javac = find_tool('javac.exe' if os.name == 'nt' else 'javac', args.java_home / 'bin')
    java = find_tool('java.exe' if os.name == 'nt' else 'java', args.java_home / 'bin')

    work = Path(tempfile.mkdtemp(prefix='uavf-java-tests-'))
    classes = work / 'classes'
    generated = work / 'generated'
    classes.mkdir()

    generate_assets(args.apk, generated)

    sources = sorted(str(p) for p in SRC.rglob('*.java'))
    sources += sorted(str(p) for p in TESTS.rglob('*.java'))
    sources += sorted(str(p) for p in generated.rglob('*.java'))
    if not sources:
        raise SystemExit('No Java sources found')

    argfile = work / 'sources.txt'
    argfile.write_text('\n'.join(sources), encoding='utf-8')

    compile_cmd = [javac, '-nowarn', '-encoding', 'UTF-8', '-d', str(classes),
                   '-cp', str(android_jar), f'@{argfile}']
    result = subprocess.run(compile_cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(result.stdout)
        print(result.stderr)
        raise SystemExit('Compilation failed')

    # android.jar is required at runtime too: several tests touch Android types
    # in their very first statement.
    classpath = os.pathsep.join([str(classes), str(android_jar)])

    test_classes = sorted(
        p.relative_to(classes).with_suffix('').as_posix().replace('/', '.')
        for p in classes.rglob('*Test.class') if '$' not in p.name)

    passed, skipped, failed = [], [], []
    for name in test_classes:
        run = subprocess.run([java, '-cp', classpath, name], capture_output=True, text=True)
        if run.returncode == 0:
            passed.append(name)
        elif name in NEEDS_ARGS and 'ArrayIndexOutOfBoundsException' in run.stderr:
            skipped.append((name, NEEDS_ARGS[name]))
        else:
            failed.append((name, run.stderr.strip().splitlines()[:4]))

    print(f'COMPILED={len(sources)} files')
    print(f'PASSED={len(passed)} SKIPPED={len(skipped)} FAILED={len(failed)}')
    for name in passed:
        print(f'  PASS {name}')
    for name, reason in skipped:
        print(f'  SKIP {name} ({reason})')
    for name, lines in failed:
        print(f'  FAIL {name}')
        for line in lines:
            print(f'       {line}')

    if not args.keep:
        shutil.rmtree(work, ignore_errors=True)

    if failed:
        raise SystemExit(f'{len(failed)} test(s) failed')
    print('JAVA_TESTS=PASS' if passed else 'JAVA_TESTS=EMPTY')


if __name__ == '__main__':
    main()
