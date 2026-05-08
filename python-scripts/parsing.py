import pandas as pd
import numpy as np
from typing import Dict, List, Optional
import matplotlib.pyplot as plt
import os
import re
from pprint import pprint


# ---------------------------
# 1. Parsing Functions
# ---------------------------

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

def parse_client_log(filepath: str) -> pd.DataFrame: # works and tested
    """Parse client log file into a DataFrame.
    Expected format:
      In batch mode: BATCH_MODE,-,-,start,end,duration,Batch Size: N
      In per-op mode: type,u,v,start,end,duration
    Returns DataFrame with columns: [type, u, v, startTime, endTime, duration, message]
    """
    rows = []
    with open(filepath, "r") as f:
        for line in f:
            line = line.strip()
            if not line: 
                continue
            
            parts = line.split(",")
            if len(line) < 6:
                continue
            
            op_type = parts[0]
            u = parts[1] if parts[1] != '-' else None
            v = parts[2] if parts[2] != '-' else None
            
            start = int(parts[3])
            end = int(parts[4])
            duration = int(parts[5])
            message = parts[6] if len(parts) > 6 else None
            
            rows.append({
                'type': op_type,
                'u': int(u) if u is not None else None,
                'v': int(v) if v is not None else None,
                'startTime': start,
                'endTime': end,
                'duration': duration,
                'message': message
            })
    
    return pd.DataFrame(rows)


# ---------------------------
# 2. Statistics Computation
# ---------------------------
        
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
        "avg_duration": avg,
        "median_duration": med,
        "p95_duration": p95,
        "qps": qps
    }


# ---------------------------
# 3. Configuration Parsing
# ---------------------------


if __name__ == "__main__":
    df_server = parse_server_log("../log/server-log.txt")
    # df_client = parse_client_log("../log/log0.txt")
    print(df_server.head(10))
    pprint(compute_query_stats(df_server))
    # print(df_client.head(10))