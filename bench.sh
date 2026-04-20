#!/bin/bash
set -euo pipefail

JAVA_MOUNT="/tmp/bench_java"
RUST_MOUNT="/tmp/bench_rust"
JAVA_CACHE="/tmp/bench_java_cache"
RUST_CACHE="/tmp/bench_rust_cache"
REPO_URL="http://cvmfs-stratum-one.cern.ch/opt/boss"
REPO_FQRN="boss.cern.ch"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_JAR="$SCRIPT_DIR/target/cvmfs-0.3.0-jar-with-dependencies.jar"
RUST_BIN="${RUST_BIN:-$SCRIPT_DIR/../cvmfs-rust/target/release/cvmfs-cli}"
ITERATIONS=50

cleanup() {
    umount "$JAVA_MOUNT" 2>/dev/null || diskutil unmount force "$JAVA_MOUNT" 2>/dev/null || true
    umount "$RUST_MOUNT" 2>/dev/null || diskutil unmount force "$RUST_MOUNT" 2>/dev/null || true
    kill "${JAVA_PID:-}" 2>/dev/null || true
    kill "${RUST_PID:-}" 2>/dev/null || true
    sleep 1
    rmdir "$JAVA_MOUNT" "$RUST_MOUNT" 2>/dev/null || true
    rm -rf "$JAVA_CACHE" "$RUST_CACHE"
}
trap cleanup EXIT

if [ ! -f "$JAVA_JAR" ]; then
    echo "Building Java jar..."
    (cd "$SCRIPT_DIR" && mvn package -q -DskipTests)
fi

if [ ! -f "$RUST_BIN" ]; then
    echo "ERROR: Rust binary not found at $RUST_BIN"
    echo "Set RUST_BIN env var or build with: cd ../cvmfs-rust && cargo build --release"
    exit 1
fi

cleanup 2>/dev/null || true
mkdir -p "$JAVA_MOUNT" "$RUST_MOUNT" "$JAVA_CACHE" "$RUST_CACHE"

echo "Mounting Java cvmfs-java..."
java -jar "$JAVA_JAR" "$REPO_URL" "$JAVA_MOUNT" "$JAVA_CACHE" &
JAVA_PID=$!
sleep 5

if ! stat "$JAVA_MOUNT/testfile" &>/dev/null; then
    echo "ERROR: Java mount failed"
    kill $JAVA_PID 2>/dev/null || true
    exit 1
fi
echo "  OK: $JAVA_MOUNT"

echo "Mounting Rust cvmfs-cli..."
"$RUST_BIN" "$REPO_URL" "$RUST_MOUNT" "$RUST_CACHE" &
RUST_PID=$!
sleep 4

if ! stat "$RUST_MOUNT/testfile" &>/dev/null; then
    echo "ERROR: Rust mount failed"
    kill $JAVA_PID 2>/dev/null || true
    kill $RUST_PID 2>/dev/null || true
    exit 1
fi
echo "  OK: $RUST_MOUNT"

echo "Warming up..."
for p in / /testfile /database /pacman-3.29 /pacman-3.29/setup.csh /slc4_ia32_gcc34 /database/run.db /pacman-latest.tar.gz; do
    stat "${JAVA_MOUNT}${p}" &>/dev/null || true
    stat "${RUST_MOUNT}${p}" &>/dev/null || true
done
ls "$JAVA_MOUNT/" >/dev/null 2>&1
ls "$RUST_MOUNT/" >/dev/null 2>&1
cat "$JAVA_MOUNT/testfile" >/dev/null 2>&1
cat "$RUST_MOUNT/testfile" >/dev/null 2>&1

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
RUST_WINS=0
TOTAL=0

run_bench() {
    local label="$1"
    local java_cmd="$2"
    local rust_cmd="$3"

    local j_med r_med
    j_med=$(measure "$java_cmd" "$ITERATIONS")
    r_med=$(measure "$rust_cmd" "$ITERATIONS")

    local winner pct
    if [ "$j_med" -le "$r_med" ]; then
        winner="Java"
        JAVA_WINS=$((JAVA_WINS + 1))
        if [ "$j_med" -gt 0 ]; then
            pct=$(python3 -c "print(f'+{($r_med/$j_med - 1)*100:.0f}%')")
        else
            pct="inf"
        fi
    else
        winner="Rust"
        RUST_WINS=$((RUST_WINS + 1))
        if [ "$r_med" -gt 0 ]; then
            pct=$(python3 -c "print(f'+{($j_med/$r_med - 1)*100:.0f}%')")
        else
            pct="inf"
        fi
    fi
    TOTAL=$((TOTAL + 1))

    printf "  %-40s %12s %12s  %s %s\n" \
        "$label" "$(fmt_ns "$j_med")" "$(fmt_ns "$r_med")" "$winner" "$pct"
}

print_section() {
    echo ""
    echo "== $1 =="
    printf "  %-40s %12s %12s  %s\n" "Operation" "Java" "Rust" "Winner"
    printf "  %s\n" "$(printf -- '-%.0s' {1..85})"
}

# ── Benchmarks ──

JAVA_VERSION=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' || echo "unknown")

echo ""
echo "CVMFS Benchmark: Java ${JAVA_VERSION} (FUSE) vs Rust (FUSE)"
echo "Repository: $REPO_FQRN"
echo "Iterations: $ITERATIONS per operation (after warmup)"
echo "Java mount: $JAVA_MOUNT"
echo "Rust mount: $RUST_MOUNT"

print_section "stat"
run_bench "stat / (root)" \
    "stat $JAVA_MOUNT/" \
    "stat $RUST_MOUNT/"
run_bench "stat /testfile" \
    "stat $JAVA_MOUNT/testfile" \
    "stat $RUST_MOUNT/testfile"
run_bench "stat /database" \
    "stat $JAVA_MOUNT/database" \
    "stat $RUST_MOUNT/database"
run_bench "stat symlink" \
    "stat $JAVA_MOUNT/pacman-3.29/setup.csh" \
    "stat $RUST_MOUNT/pacman-3.29/setup.csh"

print_section "ls (readdir)"
run_bench "ls / (root)" \
    "ls $JAVA_MOUNT/" \
    "ls $RUST_MOUNT/"
run_bench "ls /database" \
    "ls $JAVA_MOUNT/database" \
    "ls $RUST_MOUNT/database"
run_bench "ls /pacman-3.29" \
    "ls $JAVA_MOUNT/pacman-3.29" \
    "ls $RUST_MOUNT/pacman-3.29"
run_bench "ls /slc4_ia32_gcc34 (nested catalog)" \
    "ls $JAVA_MOUNT/slc4_ia32_gcc34" \
    "ls $RUST_MOUNT/slc4_ia32_gcc34"

print_section "readlink"
run_bench "readlink symlink" \
    "readlink $JAVA_MOUNT/pacman-3.29/setup.csh" \
    "readlink $RUST_MOUNT/pacman-3.29/setup.csh"

print_section "cat (file read)"
run_bench "cat /testfile (50 bytes)" \
    "cat $JAVA_MOUNT/testfile" \
    "cat $RUST_MOUNT/testfile"
run_bench "head -c 16 offlinedb.db (chunked)" \
    "head -c 16 $JAVA_MOUNT/database/offlinedb.db" \
    "head -c 16 $RUST_MOUNT/database/offlinedb.db"
run_bench "head -c 2 pacman-latest.tar.gz" \
    "head -c 2 $JAVA_MOUNT/pacman-latest.tar.gz" \
    "head -c 2 $RUST_MOUNT/pacman-latest.tar.gz"

print_section "dd (seek + read)"
run_bench "dd skip into offlinedb.db" \
    "dd if=$JAVA_MOUNT/database/offlinedb.db bs=64 count=1 skip=100" \
    "dd if=$RUST_MOUNT/database/offlinedb.db bs=64 count=1 skip=100"

print_section "find (recursive traversal)"
run_bench "find /pacman-3.29 -maxdepth 1" \
    "find $JAVA_MOUNT/pacman-3.29 -maxdepth 1" \
    "find $RUST_MOUNT/pacman-3.29 -maxdepth 1"
run_bench "find /database -type f" \
    "find $JAVA_MOUNT/database -type f" \
    "find $RUST_MOUNT/database -type f"

print_section "wc (full file read + count)"
run_bench "wc -c /testfile" \
    "wc -c $JAVA_MOUNT/testfile" \
    "wc -c $RUST_MOUNT/testfile"
run_bench "wc -c /pacman-latest.tar.gz" \
    "wc -c $JAVA_MOUNT/pacman-latest.tar.gz" \
    "wc -c $RUST_MOUNT/pacman-latest.tar.gz"

print_section "md5 (hash full file)"
run_bench "md5 /testfile" \
    "md5 -q $JAVA_MOUNT/testfile" \
    "md5 -q $RUST_MOUNT/testfile"

print_section "full file read"
run_bench "cat pacman-latest.tar.gz (full)" \
    "cat $JAVA_MOUNT/pacman-latest.tar.gz" \
    "cat $RUST_MOUNT/pacman-latest.tar.gz"

print_section "recursive traversal"
run_bench "find / -maxdepth 3 (all entries)" \
    "find $JAVA_MOUNT -maxdepth 3" \
    "find $RUST_MOUNT -maxdepth 3"

print_section "du (recursive stat)"
run_bench "du -s / -maxdepth 2" \
    "du -d 2 -s $JAVA_MOUNT" \
    "du -d 2 -s $RUST_MOUNT"

print_section "hash large files"
run_bench "md5 run.db (chunked, 410MB)" \
    "md5 -q $JAVA_MOUNT/database/run.db" \
    "md5 -q $RUST_MOUNT/database/run.db"
run_bench "md5 pacman-latest.tar.gz" \
    "md5 -q $JAVA_MOUNT/pacman-latest.tar.gz" \
    "md5 -q $RUST_MOUNT/pacman-latest.tar.gz"

# ── Heavy benchmarks (1 iteration, >5s operations) ──
ITERATIONS=1

print_section "heavy (1 iteration)"
run_bench "cat run.db (chunked, 410MB)" \
    "cat $JAVA_MOUNT/database/run.db" \
    "cat $RUST_MOUNT/database/run.db"
run_bench "find / -maxdepth 2 -type f" \
    "find $JAVA_MOUNT -maxdepth 2 -type f" \
    "find $RUST_MOUNT -maxdepth 2 -type f"

echo ""
printf '=%.0s' {1..87}; echo ""
echo "Java wins: $JAVA_WINS/$TOTAL"
echo "Rust wins: $RUST_WINS/$TOTAL"
if [ "$JAVA_WINS" -gt "$RUST_WINS" ]; then
    echo "Result: Java is faster overall."
elif [ "$RUST_WINS" -gt "$JAVA_WINS" ]; then
    echo "Result: Rust is faster overall."
else
    echo "Result: Tied."
fi
