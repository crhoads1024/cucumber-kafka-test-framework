#!/usr/bin/env python3
"""
JMeter Results Threshold Validator

Parses JMeter JTL results files and fails the CI/CD pipeline if
performance thresholds are breached.

USAGE:
    python3 check-perf-thresholds.py results.jtl [--config thresholds.json]

HOW TO CUSTOMIZE THRESHOLDS:
1. Edit the DEFAULT_THRESHOLDS dict below, OR
2. Pass a JSON config file with --config flag

THRESHOLD DEFINITIONS:
- max_avg_response_ms:   Maximum average response time across all samplers
- max_p95_response_ms:   Maximum 95th percentile response time
- max_p99_response_ms:   Maximum 99th percentile response time
- max_error_rate_pct:    Maximum error rate percentage
- min_throughput_rps:    Minimum requests per second

EXIT CODES:
- 0: All thresholds passed
- 1: One or more thresholds breached (fails pipeline)
- 2: Error parsing results file
"""

import csv
import json
import sys
import statistics
from pathlib import Path
from collections import defaultdict

DEFAULT_THRESHOLDS = {
    "max_avg_response_ms": 500,
    "max_p95_response_ms": 1500,
    "max_p99_response_ms": 3000,
    "max_error_rate_pct": 2.0,
    "min_throughput_rps": 50,
    # Per-sampler overrides (optional)
    "samplers": {
        "Health Check": {
            "max_avg_response_ms": 100,
            "max_p95_response_ms": 200,
        },
        "Create Order": {
            "max_avg_response_ms": 800,
            "max_p95_response_ms": 2000,
        }
    }
}


def parse_jtl(filepath: str) -> list[dict]:
    """Parse a JMeter JTL (CSV format) results file."""
    results = []
    with open(filepath, 'r') as f:
        reader = csv.DictReader(f)
        for row in reader:
            results.append({
                'timestamp': int(row.get('timeStamp', 0)),
                'elapsed': int(row.get('elapsed', 0)),
                'label': row.get('label', ''),
                'responseCode': row.get('responseCode', ''),
                'success': row.get('success', 'false').lower() == 'true',
                'bytes': int(row.get('bytes', 0)),
                'latency': int(row.get('Latency', 0)),
                'connect': int(row.get('Connect', 0)),
            })
    return results


def compute_metrics(results: list[dict]) -> dict:
    """Compute aggregate performance metrics."""
    if not results:
        return {}

    # Group by sampler label
    by_label = defaultdict(list)
    for r in results:
        by_label[r['label']].append(r)

    metrics = {}
    for label, samples in by_label.items():
        elapsed_times = sorted([s['elapsed'] for s in samples])
        error_count = sum(1 for s in samples if not s['success'])
        total = len(samples)

        # Calculate time range for throughput
        timestamps = [s['timestamp'] for s in samples]
        duration_sec = (max(timestamps) - min(timestamps)) / 1000.0 if len(timestamps) > 1 else 1.0

        metrics[label] = {
            'count': total,
            'avg_ms': statistics.mean(elapsed_times),
            'min_ms': min(elapsed_times),
            'max_ms': max(elapsed_times),
            'median_ms': statistics.median(elapsed_times),
            'p90_ms': percentile(elapsed_times, 90),
            'p95_ms': percentile(elapsed_times, 95),
            'p99_ms': percentile(elapsed_times, 99),
            'error_count': error_count,
            'error_rate_pct': (error_count / total * 100) if total > 0 else 0,
            'throughput_rps': total / duration_sec if duration_sec > 0 else 0,
        }

    # Overall metrics
    all_elapsed = sorted([r['elapsed'] for r in results])
    all_errors = sum(1 for r in results if not r['success'])
    all_timestamps = [r['timestamp'] for r in results]
    total_duration = (max(all_timestamps) - min(all_timestamps)) / 1000.0 if len(all_timestamps) > 1 else 1.0

    metrics['__overall__'] = {
        'count': len(results),
        'avg_ms': statistics.mean(all_elapsed),
        'p95_ms': percentile(all_elapsed, 95),
        'p99_ms': percentile(all_elapsed, 99),
        'error_count': all_errors,
        'error_rate_pct': (all_errors / len(results) * 100),
        'throughput_rps': len(results) / total_duration,
    }

    return metrics


def percentile(sorted_data: list, pct: int) -> float:
    """Calculate percentile from sorted data."""
    if not sorted_data:
        return 0
    idx = int(len(sorted_data) * pct / 100)
    return sorted_data[min(idx, len(sorted_data) - 1)]


def check_thresholds(metrics: dict, thresholds: dict) -> list[str]:
    """Check metrics against thresholds. Returns list of violations."""
    violations = []
    overall = metrics.get('__overall__', {})

    # Global thresholds
    checks = [
        ('max_avg_response_ms', 'avg_ms', 'Average response time'),
        ('max_p95_response_ms', 'p95_ms', '95th percentile response time'),
        ('max_p99_response_ms', 'p99_ms', '99th percentile response time'),
    ]

    for threshold_key, metric_key, label in checks:
        limit = thresholds.get(threshold_key)
        actual = overall.get(metric_key, 0)
        if limit and actual > limit:
            violations.append(
                f"FAIL: {label} = {actual:.1f}ms (threshold: {limit}ms)"
            )

    # Error rate
    max_error = thresholds.get('max_error_rate_pct', 100)
    actual_error = overall.get('error_rate_pct', 0)
    if actual_error > max_error:
        violations.append(
            f"FAIL: Error rate = {actual_error:.2f}% (threshold: {max_error}%)"
        )

    # Throughput
    min_tps = thresholds.get('min_throughput_rps', 0)
    actual_tps = overall.get('throughput_rps', 0)
    if min_tps and actual_tps < min_tps:
        violations.append(
            f"FAIL: Throughput = {actual_tps:.1f} rps (minimum: {min_tps} rps)"
        )

    # Per-sampler thresholds
    sampler_thresholds = thresholds.get('samplers', {})
    for sampler_name, sampler_limits in sampler_thresholds.items():
        sampler_metrics = metrics.get(sampler_name, {})
        if not sampler_metrics:
            continue

        for threshold_key, metric_key, label in checks:
            limit = sampler_limits.get(threshold_key)
            actual = sampler_metrics.get(metric_key, 0)
            if limit and actual > limit:
                violations.append(
                    f"FAIL: [{sampler_name}] {label} = {actual:.1f}ms (threshold: {limit}ms)"
                )

    return violations


def print_report(metrics: dict):
    """Print a formatted performance report."""
    print("\n" + "=" * 70)
    print("PERFORMANCE TEST RESULTS")
    print("=" * 70)

    for label, m in sorted(metrics.items()):
        if label == '__overall__':
            print(f"\n{'OVERALL':}")
        else:
            print(f"\n{label}:")

        print(f"  Requests:    {m['count']}")
        print(f"  Avg:         {m['avg_ms']:.1f}ms")
        if 'median_ms' in m:
            print(f"  Median:      {m['median_ms']:.1f}ms")
        print(f"  P95:         {m['p95_ms']:.1f}ms")
        print(f"  P99:         {m['p99_ms']:.1f}ms")
        print(f"  Errors:      {m['error_count']} ({m['error_rate_pct']:.2f}%)")
        print(f"  Throughput:  {m['throughput_rps']:.1f} req/s")

    print("\n" + "=" * 70)


def main():
    if len(sys.argv) < 2:
        print("Usage: check-perf-thresholds.py <results.jtl> [--config thresholds.json]")
        sys.exit(2)

    jtl_file = sys.argv[1]

    # Load thresholds
    thresholds = DEFAULT_THRESHOLDS
    if '--config' in sys.argv:
        config_idx = sys.argv.index('--config') + 1
        if config_idx < len(sys.argv):
            with open(sys.argv[config_idx]) as f:
                thresholds = json.load(f)

    # Parse and analyze
    try:
        results = parse_jtl(jtl_file)
    except Exception as e:
        print(f"ERROR: Failed to parse JTL file: {e}")
        sys.exit(2)

    if not results:
        print("WARNING: No results found in JTL file")
        sys.exit(2)

    metrics = compute_metrics(results)
    print_report(metrics)

    # Check thresholds
    violations = check_thresholds(metrics, thresholds)

    if violations:
        print("\nTHRESHOLD VIOLATIONS:")
        for v in violations:
            print(f"  {v}")
        print(f"\n{len(violations)} threshold(s) breached. Pipeline FAILED.")
        sys.exit(1)
    else:
        print("\nAll thresholds PASSED.")
        sys.exit(0)


if __name__ == '__main__':
    main()
