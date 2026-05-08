"""
Automated performance experiment runner for Graph Service.
Modifies system.properties, runs ./start.sh, collects logs, aggregates results,
and plots the required performance graphs.

Usage:
  python run_experiments.py
"""

import os
import shutil
import subprocess
import time
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path
from typing import Dict, List, Optional
import itertools

# ---------- Configuration ----------
BASE_DIR = Path(os.path.dirname(os.path.abspath(__file__)))  # where this script is
PROJECT_DIR = BASE_DIR.parent  # DistributedProjects root 
PROPERTIES_FILE = PROJECT_DIR.joinpath("system.properties")  
START_SCRIPT = PROJECT_DIR.joinpath("start.sh")  
LOG_DIR = PROJECT_DIR.joinpath("log")  
EXPERIMENTS_DIR = PROJECT_DIR.joinpath("experiments")
PLOTS_DIR = PROJECT_DIR.joinpath("plots")

# number of repetitions per configuration (to average)
NUM_RUNS = 3 

WRITE_PERCENTAGES = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 99]#[0, 20, 50, 80, 99]
CLIENT_COUNTS_BASIC = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
CLIENT_COUNTS_STRESS = [11, 12, 13, 14, 15, 16, 17, 18, 19, 20]   # for extra credit
FREQ_LABELS = {
    "t1": 0, 
    "t2": 50,
    "t3": 200,
    "t4": 500,
    "t5": 1000,
    "t6": 5000,
    "t7": 10000,
    "t8": 50000,
    "t9": 100000,
    "t10": 500000,
    "t11": 1000000

    # "high": 0,      # 0 ms sleep = max frequency
    # "medium": 50,
    # "low": 200
}
VARIANTS = ["ondemand", "all"]

# ---------- 1. Helper Functions ----------
def write_properties(properties: Dict[str, str], path: Path):
    """Write key=value pairs to a properties file."""
    with open(path, 'w') as f:
        for key, value in properties.items():
            f.write(f"{key}={value}\n")
            
            
def base_properties() -> Dict[str, str]:
    """Return the default properties dict with fixed values."""
    return {
        "GSP.server": "localhost",
        "GSP.server.port": "49053",
        "GSP.rmiregistry.port": "1099",
        "GSP.serviceName": "GraphEngine",
        "GSP.graph.file": "data/intial_graph.txt",
        "GSP.data.directory": "data/",
        "GSP.server.log.directory": "log/",
        "GSP.client.log.directory": "log/",
        "GSP.server.verbose": "true",
        "GSP.client.verbose": "true",
        "GSP.batchMode": "true",
        "GSP.operations.per.batch": "50",
        "GSP.client.timeout.seconds": "120",
        "GSP.server.operations.sleep": "0",
        "GSP.client.operations.sleep": "0"
    }
    
def apply_config(props: dict, variant: str, write_pct: int,
                 num_clients: int, sleep_ms: int):
    """Modify properties dict for a specific configuration."""
    props["GSP.precomputeMode"] = "true" if variant == "all" else "false"
    props["GSP.writePercent"] = str(write_pct)
    props["GSP.numberOfnodes"] = str(num_clients)
    props["GSP.client.operations.sleep"] = str(sleep_ms)

    for i in range(num_clients):
        key = f"GSP.node{i}"
        if i < num_clients:
            props[key] = "localhost"
        elif key in props:
            del props[key]
            
            
def run_system_and_wait():
    """Execute the start script and wait for it to finish.
    Returns True if successful.
    """
    try:
        result = subprocess.run(
            ["bash", str(START_SCRIPT)],
            cwd=PROJECT_DIR,
            capture_output=True,
            text=True,
            timeout=300  # 5 minutes max per run
        )
        if result.returncode != 0:
            print(f"ERROR running start.sh: {result.stderr}")
            return False
        # print(result.stdout)
        return True
    except subprocess.TimeoutExpired:
        print("ERROR: start.sh timed out")
        return False
    
    
# ---------- 2. Log Parsing  ----------
def parse_server_log(filepath: str) -> pd.DataFrame: # works and tested
    """Parse server log CSV into a DataFrame.
    Expected format: timestamp,threadId,method,u,v,startTime,duration,
    """
    
    col_names = ["timestamp", "threadId", "method", "u", "v", "startTime", "duration"]
    df = pd.read_csv(filepath, header=None, names=col_names, usecols=range(7), engine="python", skip_blank_lines=True);
    
    df = df[df["method"].isin(["Q", "A", "D"])].copy()
    for col in ["u", "v", "timestamp", "startTime", "duration"]:
        df[col] = pd.to_numeric(df[col], errors="raise")
    df = df.dropna(subset=["duration"])
    df["duration"] = df["duration"].astype(int)
    return df

def compute_query_stats(server_df: pd.DataFrame) -> Dict[str, float]:
    """From server DataFrame (all operations), extract query (Q) statistics.
    Expected format: timestamp,threadId,method,u,v,startTime,duration
    Returns: query_count -> number of queries
             avg_duration -> average duration
             median_duration -> median duration
             p95_duration -> percentile of 95% (95% of queries took less than this duration)
             qps -> queries per second
    """
    queries = server_df[server_df["method"] == "Q"]
    if len(queries) == 0:
        return {
            "query_count": 0,
            "avg_duration": 0,
            "median_duration": 0,
            "p95_duration": 0,
            "qps": 0
        }
    durations = queries["duration"]
    avg = durations.mean()
    med = durations.median()
    p95 = durations.quantile(0.95)
    # Time span in seconds
    t_min = queries["timestamp"].min()
    t_max = queries["timestamp"].max()
    time_span = (t_max - t_min) / 1_000_000_000.0
    qps = len(queries) / time_span if time_span > 0 else 0
    return {
        "query_count": len(queries),
        "avg_duration_ns": avg,
        "median_duration_ns": med,
        "p95_duration_ns": p95,
        "qps": qps
    }


# ---------- 3. Single Experiment Run ----------
def run_single_experiment(variant: str, write_pct: int,
                          num_clients: int, sleep_ms: int,
                          run_id: int) -> Optional[Dict]:
    """
    Run one instance of the system with given config.
    Saves logs to experiments/<variant>/<config_name>/run<run_id>/
    Returns computed stats or None if failed.
    """
    # create config name
    freq_label = [k for k, v in FREQ_LABELS.items() if v == sleep_ms][0] if sleep_ms in FREQ_LABELS.values() else "custom"
    config_name = f"freq_{freq_label}_write{write_pct}_nodes{num_clients}"
    # run_dir = EXPERIMENTS_DIR / variant / config_name / f"run{run_id}"
    run_dir = EXPERIMENTS_DIR.joinpath(variant, config_name, f"run{run_id}")
    run_dir.mkdir(parents=True, exist_ok=True)

    # 1. Write properties
    props = base_properties()
    apply_config(props, variant, write_pct, num_clients, sleep_ms)
    write_properties(props, PROPERTIES_FILE)

    # 2. Clean old logs
    for f in LOG_DIR.glob("*"):
        if f.is_file():
            f.unlink()

    # 3. Run system
    print(f"Running: {variant} write={write_pct}% nodes={num_clients} sleep={sleep_ms}ns run={run_id} ...")
    success = run_system_and_wait()
    if not success:
        print("  Run failed, skipping.")
        return None

    # 4. Move logs to run_dir
    # (system may have written server-log.txt and log*.txt to LOG_DIR)
    for f in LOG_DIR.glob("*"):
        if f.is_file():
            shutil.move(str(f), str(run_dir.joinpath(f.name)))

    # 5. Parse server log
    server_log = run_dir.joinpath("server-log.txt")
    if not server_log.exists():
        print("  No server log found!")
        return None
    df = parse_server_log(server_log)
    stats = compute_query_stats(df)

    # add metadata
    stats["variant"] = variant
    stats["write_pct"] = write_pct
    stats["num_clients"] = num_clients
    stats["freq_label"] = freq_label
    stats["sleep_ms"] = sleep_ms
    stats["run"] = run_id
    return stats


# ---------- 4. Run Full Experiment Set ----------
def run_all_experiments():
    """Run all experiments as defined by the project requirements."""
    all_stats = []

    # --- Plot 1: Response time vs frequency ---
    # Fix write% and nodes, vary frequency
    for variant in VARIANTS:
        for label, sleep_ms in FREQ_LABELS.items():
            for run in range(1, NUM_RUNS+1):
                stats = run_single_experiment(variant, write_pct=30,
                                              num_clients=3, sleep_ms=sleep_ms,
                                              run_id=run)
                if stats:
                    all_stats.append(stats)

    # --- Plot 2: Response time vs write percentage ---
    for variant in VARIANTS:
        for wp in WRITE_PERCENTAGES:
            for run in range(1, NUM_RUNS+1):
                stats = run_single_experiment(variant, write_pct=wp,
                                              num_clients=3, sleep_ms=0,  # high freq
                                              run_id=run)
                if stats:
                    all_stats.append(stats)

    # --- Plot 3: Response time vs number of clients [1,5] ---
    for variant in VARIANTS:
        for nc in CLIENT_COUNTS_BASIC:
            for run in range(1, NUM_RUNS+1):
                stats = run_single_experiment(variant, write_pct=30,
                                              num_clients=nc, sleep_ms=0,
                                              run_id=run)
                if stats:
                    all_stats.append(stats)

    # --- Extra credit: stress test (5-15) ---
    for variant in VARIANTS:
        for nc in CLIENT_COUNTS_STRESS:
            for run in range(1, NUM_RUNS+1):
                stats = run_single_experiment(variant, write_pct=30,
                                              num_clients=nc, sleep_ms=0,
                                              run_id=run)
                if stats:
                    all_stats.append(stats)

    # Save all raw stats
    df_all = pd.DataFrame(all_stats)
    df_all.to_csv(EXPERIMENTS_DIR.joinpath("raw_stats.csv"), index=False)
    print(f"All raw stats saved to {EXPERIMENTS_DIR.joinpath('raw_stats.csv')}")
    return df_all

# ---------- 5. Aggregate and Plot ----------
def aggregate_and_plot(df: pd.DataFrame):
    """Average over runs and produce the three required plots."""
    # Group by configuration, compute mean of metrics
    group_cols = ["variant", "write_pct", "num_clients", "freq_label", "sleep_ms"]

    agg_df = df.groupby(group_cols).agg(
        avg_duration_ns=("avg_duration_ns", "mean"),
        median_duration_ns=("median_duration_ns", "mean"),
        p95_duration_ns=("p95_duration_ns", "mean"),
        qps=("qps", "mean"),
    ).reset_index()

    # Save aggregated table
    # agg_df.to_csv(EXPERIMENTS_DIR / "aggregated_stats.csv", index=False)
    agg_df.to_csv(EXPERIMENTS_DIR.joinpath("aggregated_stats.csv"), index=False)

    # Plotting
    PLOTS_DIR.mkdir(parents=True, exist_ok=True)
    plt.style.use('ggplot')

    # --- Plot 1: Response time vs frequency ---
    freq_order = list(FREQ_LABELS.keys())  # ["high", "medium", "low"]
    df_freq = agg_df[(agg_df["write_pct"] == 30) & (agg_df["num_clients"] == 3)]
    fig, ax = plt.subplots()
    for variant in VARIANTS:
        sub = df_freq[df_freq["variant"] == variant]
        if sub.empty: continue
        sub.loc[:, "freq_cat"] = pd.Categorical(sub["freq_label"], categories=freq_order, ordered=True)
        sub = sub.sort_values("freq_cat")
        ax.plot(sub["freq_cat"], sub["avg_duration_ns"], marker='o', label=variant)
    ax.set_xlabel("Request Frequency (sleep ms)")
    ax.set_ylabel("Avg Query Response Time (ns)")
    ax.set_title("Response Time vs. Frequency")
    ax.legend()
    fig.tight_layout()
    # fig.savefig(PLOTS_DIR / "response_time_vs_frequency.png")
    fig.savefig(PLOTS_DIR.joinpath("response_time_vs_frequency.png"))
    plt.close(fig)
    print("Saved response_time_vs_frequency.png")

    # --- Plot 2: Response time vs write percentage ---
    df_write = agg_df[(agg_df["num_clients"] == 3) & (agg_df["sleep_ms"] == 0)]  # high freq
    fig, ax = plt.subplots()
    for variant in VARIANTS:
        sub = df_write[df_write["variant"] == variant].sort_values("write_pct")
        if sub.empty: continue
        ax.plot(sub["write_pct"], sub["avg_duration_ns"], marker='s', label=variant)
    ax.set_xlabel("Write Percentage (%)")
    ax.set_ylabel("Avg Query Response Time (ns)")
    ax.set_title("Response Time vs. Write Percentage")
    ax.legend()
    fig.tight_layout()
    # fig.savefig(PLOTS_DIR / "response_time_vs_write_pct.png")
    fig.savefig(PLOTS_DIR.joinpath("response_time_vs_write_pct.png"))
    plt.close(fig)
    print("Saved response_time_vs_write_pct.png")

    # --- Plot 3: Response time vs number of clients (basic) ---
    df_clients = agg_df[(agg_df["write_pct"] == 30) & (agg_df["sleep_ms"] == 0)]
    fig, ax = plt.subplots()
    for variant in VARIANTS:
        sub = df_clients[df_clients["variant"] == variant].sort_values("num_clients")
        if sub.empty: continue
        ax.plot(sub["num_clients"], sub["avg_duration_ns"], marker='D', label=variant)
    ax.set_xlabel("Number of Clients")
    ax.set_ylabel("Avg Query Response Time (ns)")
    ax.set_title("Response Time vs. Number of Clients")
    ax.legend()
    fig.tight_layout()
    # fig.savefig(PLOTS_DIR / "response_time_vs_num_clients.png")
    fig.savefig(PLOTS_DIR.joinpath("response_time_vs_num_clients.png"))
    plt.close(fig)
    print("Saved response_time_vs_num_clients.png")

    # Optional: stress plot
    df_stress = df_clients[df_clients["num_clients"] >= 5]
    if not df_stress.empty:
        fig, ax = plt.subplots()
        for variant in VARIANTS:
            sub = df_stress[df_stress["variant"] == variant].sort_values("num_clients")
            if sub.empty: continue
            ax.plot(sub["num_clients"], sub["avg_duration_ns"], marker='D', label=variant)
        ax.set_xlabel("Number of Clients (Stress)")
        ax.set_ylabel("Avg Query Response Time (ns)")
        ax.set_title("Response Time vs. Number of Clients (Stress Test)")
        ax.legend()
        fig.tight_layout()
        # fig.savefig(PLOTS_DIR / "response_time_vs_num_clients_stress.png")
        fig.savefig(PLOTS_DIR.joinpath("response_time_vs_num_clients_stress.png"))
        plt.close(fig)
        print("Saved stress plot.")
        
# ---------- Main ----------
if __name__ == "__main__":
    # Run experiments (this takes a long time)
    df_raw = run_all_experiments()

    # Aggregate and plot
    if not df_raw.empty:
        aggregate_and_plot(df_raw)
    else:
        print("No data collected. Check errors above.")

    print("Done.")