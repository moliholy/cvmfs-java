#!/bin/bash
set -euo pipefail

JAVA_MOUNT="/tmp/bench_java"
CPP_MOUNT="/tmp/bench_cpp"
JAVA_CACHE="/tmp/bench_java_cache"
CPP_CACHE="/tmp/bench_cpp_cache"
REPO_URL="http://cvmfs-stratum-one.cern.ch/opt/boss"
REPO_FQRN="boss.cern.ch"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_JAR="$SCRIPT_DIR/target/cvmfs-0.3.0-jar-with-dependencies.jar"
JAVA_BIN="/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home/bin/java"
JFFI_LIB_DIR="/tmp/jffi_native/jni/Darwin"
ITERATIONS=50

cleanup() {
    umount "$JAVA_MOUNT" 2>/dev/null || diskutil unmount force "$JAVA_MOUNT" 2>/dev/null || true
    umount "$CPP_MOUNT" 2>/dev/null || diskutil unmount force "$CPP_MOUNT" 2>/dev/null || true
    kill "${JAVA_PID:-}" 2>/dev/null || true
    kill "${CPP_PID:-}" 2>/dev/null || true
    sleep 1
    rmdir "$JAVA_MOUNT" "$CPP_MOUNT" 2>/dev/null || true
    rm -rf "$JAVA_CACHE" "$CPP_CACHE"
}
trap cleanup EXIT

if [ ! -f "$JAVA_JAR" ]; then
    echo "Building Java jar..."
    (cd "$SCRIPT_DIR" && mvn package -q -DskipTests)
fi

if ! command -v cvmfs2 &>/dev/null; then
    echo "ERROR: cvmfs2 not found"
    exit 1
fi

if [ ! -f "$JFFI_LIB_DIR/libjffi-1.2.jnilib" ]; then
    echo "Extracting and signing jffi native library..."
    mkdir -p /tmp/jffi_native
    JFFI_JAR=$(find "$HOME/.m2/repository/com/github/jnr/jffi" -name "jffi-*-native.jar" 2>/dev/null | head -1)
    if [ -z "$JFFI_JAR" ]; then
        echo "ERROR: jffi native jar not found in maven cache"
        exit 1
    fi
    unzip -o "$JFFI_JAR" "jni/Darwin/*" -d /tmp/jffi_native
    codesign -s - /tmp/jffi_native/jni/Darwin/libjffi-1.2.jnilib
fi

cleanup 2>/dev/null || true
mkdir -p "$JAVA_MOUNT" "$CPP_MOUNT" "$JAVA_CACHE" "$CPP_CACHE"

echo "Mounting Java cvmfs-java..."
"$JAVA_BIN" --enable-native-access=ALL-UNNAMED \
    -Djava.library.path="$JFFI_LIB_DIR" \
    -jar "$JAVA_JAR" "$REPO_URL" "$JAVA_MOUNT" "$JAVA_CACHE" &
JAVA_PID=$!
sleep 5

if ! stat "$JAVA_MOUNT/testfile" &>/dev/null; then
    echo "ERROR: Java mount failed"
    kill $JAVA_PID 2>/dev/null || true
    exit 1
fi
echo "  OK: $JAVA_MOUNT"

echo "Mounting C++ cvmfs2..."
cat > /tmp/cvmfs_bench.local <<EOF
CVMFS_CACHE_BASE=$CPP_CACHE
CVMFS_HTTP_PROXY=DIRECT
CVMFS_SERVER_URL="http://cvmfs-stratum-one.cern.ch/cvmfs/@fqrn@"
CVMFS_KEYS_DIR=/etc/cvmfs/keys/cern.ch
EOF
cvmfs2 -o config=/tmp/cvmfs_bench.local "$REPO_FQRN" "$CPP_MOUNT" &
CPP_PID=$!
sleep 4

if ! stat "$CPP_MOUNT/testfile" &>/dev/null; then
    echo "ERROR: C++ mount failed"
    kill $JAVA_PID 2>/dev/null || true
    kill $CPP_PID 2>/dev/null || true
    exit 1
fi
echo "  OK: $CPP_MOUNT"

echo "Warming up..."
for p in / /testfile /database /pacman-3.29 /pacman-3.29/setup.csh /slc4_ia32_gcc34 /database/run.db /pacman-latest.tar.gz; do
    stat "${JAVA_MOUNT}${p}" &>/dev/null || true
    stat "${CPP_MOUNT}${p}" &>/dev/null || true
done
ls "$JAVA_MOUNT/" >/dev/null 2>&1
ls "$CPP_MOUNT/" >/dev/null 2>&1
cat "$JAVA_MOUNT/testfile" >/dev/null 2>&1
cat "$CPP_MOUNT/testfile" >/dev/null 2>&1

# ── Helpers ──

measure() {
    local cmd="$1"
    local n="$2"
    python3 -c "
import subprocess, time, statistics
times = []
for _ in range($n):
    start = time.monotonic_ns()
    subprocess.run('$cmd', shell=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    times.append(time.monotonic_ns() - start)
print(int(statistics.median(times)))
"
}

fmt_ns() {
    python3 -c "
ns = $1
if ns < 1000:
    print(f'{ns}ns')
elif ns < 1_000_000:
    print(f'{ns/1000:.1f}us')
elif ns < 1_000_000_000:
    print(f'{ns/1_000_000:.2f}ms')
else:
    print(f'{ns/1_000_000_000:.3f}s')
"
}

JAVA_WINS=0
CPP_WINS=0
TOTAL=0

run_bench() {
    local label="$1"
    local java_cmd="$2"
    local cpp_cmd="$3"

    local j_med c_med
    j_med=$(measure "$java_cmd" "$ITERATIONS")
    c_med=$(measure "$cpp_cmd" "$ITERATIONS")

    local winner pct
    if [ "$j_med" -le "$c_med" ]; then
        winner="Java"
        JAVA_WINS=$((JAVA_WINS + 1))
        if [ "$j_med" -gt 0 ]; then
            pct=$(python3 -c "print(f'+{($c_med/$j_med - 1)*100:.0f}%')")
        else
            pct="inf"
        fi
    else
        winner="C++ "
        CPP_WINS=$((CPP_WINS + 1))
        if [ "$c_med" -gt 0 ]; then
            pct=$(python3 -c "print(f'+{($j_med/$c_med - 1)*100:.0f}%')")
        else
            pct="inf"
        fi
    fi
    TOTAL=$((TOTAL + 1))

    printf "  %-40s %12s %12s  %s %s\n" \
        "$label" "$(fmt_ns "$j_med")" "$(fmt_ns "$c_med")" "$winner" "$pct"
}

print_section() {
    echo ""
    echo "== $1 =="
    printf "  %-40s %12s %12s  %s\n" "Operation" "Java" "C++ cvmfs2" "Winner"
    printf "  %s\n" "$(printf -- '-%.0s' {1..85})"
}

# ── Benchmarks ──

CVMFS2_VERSION=$(cvmfs2 --version 2>&1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' || echo "unknown")
JAVA_VERSION=$("$JAVA_BIN" -version 2>&1 | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' || echo "unknown")

echo ""
echo "CVMFS Benchmark: Java ${JAVA_VERSION} (FUSE) vs C++ cvmfs2 v${CVMFS2_VERSION} (FUSE)"
echo "Repository: $REPO_FQRN"
echo "Iterations: $ITERATIONS per operation (after warmup)"
echo "Java mount: $JAVA_MOUNT"
echo "C++ mount:  $CPP_MOUNT"

print_section "stat"
run_bench "stat / (root)" \
    "stat $JAVA_MOUNT/" \
    "stat $CPP_MOUNT/"
run_bench "stat /testfile" \
    "stat $JAVA_MOUNT/testfile" \
    "stat $CPP_MOUNT/testfile"
run_bench "stat /database" \
    "stat $JAVA_MOUNT/database" \
    "stat $CPP_MOUNT/database"
run_bench "stat symlink" \
    "stat $JAVA_MOUNT/pacman-3.29/setup.csh" \
    "stat $CPP_MOUNT/pacman-3.29/setup.csh"

print_section "ls (readdir)"
run_bench "ls / (root)" \
    "ls $JAVA_MOUNT/" \
    "ls $CPP_MOUNT/"
run_bench "ls /database" \
    "ls $JAVA_MOUNT/database" \
    "ls $CPP_MOUNT/database"
run_bench "ls /pacman-3.29" \
    "ls $JAVA_MOUNT/pacman-3.29" \
    "ls $CPP_MOUNT/pacman-3.29"
run_bench "ls /slc4_ia32_gcc34 (nested catalog)" \
    "ls $JAVA_MOUNT/slc4_ia32_gcc34" \
    "ls $CPP_MOUNT/slc4_ia32_gcc34"

print_section "readlink"
run_bench "readlink symlink" \
    "readlink $JAVA_MOUNT/pacman-3.29/setup.csh" \
    "readlink $CPP_MOUNT/pacman-3.29/setup.csh"

print_section "cat (file read)"
run_bench "cat /testfile (50 bytes)" \
    "cat $JAVA_MOUNT/testfile" \
    "cat $CPP_MOUNT/testfile"
run_bench "head -c 16 offlinedb.db (chunked)" \
    "head -c 16 $JAVA_MOUNT/database/offlinedb.db" \
    "head -c 16 $CPP_MOUNT/database/offlinedb.db"
run_bench "head -c 2 pacman-latest.tar.gz" \
    "head -c 2 $JAVA_MOUNT/pacman-latest.tar.gz" \
    "head -c 2 $CPP_MOUNT/pacman-latest.tar.gz"

print_section "find (recursive traversal)"
run_bench "find /pacman-3.29 -maxdepth 1" \
    "find $JAVA_MOUNT/pacman-3.29 -maxdepth 1" \
    "find $CPP_MOUNT/pacman-3.29 -maxdepth 1"
run_bench "find /database -type f" \
    "find $JAVA_MOUNT/database -type f" \
    "find $CPP_MOUNT/database -type f"

print_section "wc (full file read + count)"
run_bench "wc -c /testfile" \
    "wc -c $JAVA_MOUNT/testfile" \
    "wc -c $CPP_MOUNT/testfile"
run_bench "wc -c /pacman-latest.tar.gz" \
    "wc -c $JAVA_MOUNT/pacman-latest.tar.gz" \
    "wc -c $CPP_MOUNT/pacman-latest.tar.gz"

print_section "md5 (hash full file)"
run_bench "md5 /testfile" \
    "md5 -q $JAVA_MOUNT/testfile" \
    "md5 -q $CPP_MOUNT/testfile"

print_section "full file read"
run_bench "cat pacman-latest.tar.gz (full)" \
    "cat $JAVA_MOUNT/pacman-latest.tar.gz" \
    "cat $CPP_MOUNT/pacman-latest.tar.gz"

print_section "recursive traversal"
run_bench "find / -maxdepth 3 (all entries)" \
    "find $JAVA_MOUNT -maxdepth 3" \
    "find $CPP_MOUNT -maxdepth 3"

print_section "du (recursive stat)"
run_bench "du -s / -maxdepth 2" \
    "du -d 2 -s $JAVA_MOUNT" \
    "du -d 2 -s $CPP_MOUNT"

# ── Heavy benchmarks (1 iteration) ──
ITERATIONS=1

print_section "heavy (1 iteration)"
run_bench "find / -maxdepth 2 -type f" \
    "find $JAVA_MOUNT -maxdepth 2 -type f" \
    "find $CPP_MOUNT -maxdepth 2 -type f"

echo ""
printf '=%.0s' {1..87}; echo ""
echo "Java wins: $JAVA_WINS/$TOTAL"
echo "C++ wins:  $CPP_WINS/$TOTAL"
if [ "$JAVA_WINS" -gt "$CPP_WINS" ]; then
    echo "Result: Java is faster overall."
elif [ "$CPP_WINS" -gt "$JAVA_WINS" ]; then
    echo "Result: C++ cvmfs2 is faster overall."
else
    echo "Result: Tied."
fi
