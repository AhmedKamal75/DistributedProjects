# Distributed Graph Service: Incremental Shortest-Path Calculation
**Course:** CS432 – Distributed Systems and Net-Centric Computing  
**Institution:** Alexandria University, Faculty of Engineering  

---

## Abstract
This project implements a distributed RMI-based system for answering shortest-path queries on a dynamic, directed, unweighted graph. The system supports concurrent client operations while preserving strict sequential semantics within each workload batch. Two algorithmic variants are implemented and compared: a standard unidirectional BFS (`uni`) and a bidirectional BFS (`bi`). Performance is evaluated across request frequency, write-operation ratio, and concurrent client load (including stress testing up to 15 clients). Experimental results demonstrate that the bidirectional variant consistently reduces query latency and scales more effectively under high concurrency and write-heavy workloads, while the fair read-write locking strategy guarantees correct batch ordering at the cost of predictable contention spikes under heavy writes.

---
## Table of Content
1. [Introduction](#1-introduction)
2. [System Architecture](#2-system-architecture)
3. [Implementation Details](#3-implementation-details)
4. [Experimental Methodology](#4-experimental-methodology)
5. [Performance Analysis & Results](#5-performance-analysis--results)
6. [Design Decisions & Rationale](#6-design-decisions--rationale)
7. [Conclusion](#7-conclusion)
8. [Appendices](#appendix-a-configuration-reference)

---

## 1. Introduction
The shortest-path problem is a cornerstone of graph theory with applications in routing, navigation, and network analysis. In dynamic environments, graphs evolve continuously, requiring systems that can efficiently handle interleaved edge insertions/deletions and distance queries. This project addresses the challenge of maintaining correct shortest-path answers in a distributed setting using Java RMI.

The primary objectives are:
1. Implement a correct RMI-based client-server architecture for dynamic graph operations.
2. Ensure sequential consistency within operation batches while allowing concurrent execution.
3. Develop and compare two shortest-path algorithms (unidirectional vs. bidirectional BFS).
4. Conduct systematic performance analysis across frequency, write ratio, and concurrency dimensions.

---

## 2. System Architecture
The system follows a classic client-server model orchestrated by a central launcher.

```
┌──────────────────────────────────────────┐
│            Start.java (Orchestrator)     │
│  1. Load system.properties               │
│  2. Launch RMI server thread             │
│  3. Generate workload batches            │
│  4. Spawn client processes (local/SSH)   │
│  5. Wait for completion & shutdown       │
└───────┬──────────────────────┬───────────
        │                      │
        ▼                      ▼
┌──────────────┐    ┌─────────────────────┐
│  Server (RMI)│    │  Client Processes   │
│  GraphEngine │◄───│  (per-node JVMs)    │
│  (Remote Obj)│    └─────────────────────┘
└──────────────┘
        ▲
        │
┌──────────────┐
│  shared/     │
│ GraphService │ (Remote Interface)
└──────────────┘
```

### Core Components
| Component | Responsibility |
|-----------|----------------|
| `Start.java` | Reads configuration, launches server, generates batches, spawns clients, handles graceful shutdown. |
| `Server.java` | Bootstraps RMI registry, exports `GraphEngine`, prints `R\n` ready signal, manages lifecycle. |
| `GraphEngine.java` | Core graph logic. Maintains adjacency/reverse-adjacency maps, implements BFS variants, enforces concurrency control. |
| `Client.java` | Loads operation batches, communicates via RMI (batch or per-op mode), logs client-side metrics, exports results. |
| `GraphService.java` | Remote interface declaring `query`, `addEdge`, `deleteEdge`, `processBatch`, and serializable records. |
| `CorrectnessHarness.java` | Standalone stdin/stdout wrapper for automatic grading (reads graph → prints `R` → processes batches → outputs answers). |

---

## 3. Implementation Details

### 3.1 Data Structures & Graph Representation
- **Forward Adjacency List:** `Map<Integer, Set<Integer>> adj` stores outgoing edges.
- **Reverse Adjacency List:** `Map<Integer, Set<Integer>> reverseAdj` enables backward traversal for bidirectional search.
- Both maps are lazily populated during `loadFromFile()` and dynamically updated during `addEdge`/`deleteEdge`.

### 3.2 Shortest-Path Variants
| Variant | Algorithm | Complexity | Rationale |
|---------|-----------|------------|-----------|
| `uni` | Standard BFS from source until target is found | O(V + E) worst-case | Baseline; simple, correct for unweighted graphs. |
| `bi` | Bidirectional BFS (forward from source, backward from target) | O(b^(d/2)) average-case | Reduces search space significantly for long paths; requires reverse adjacency list. |

Path reconstruction uses parent maps. Distance is returned as `path.length - 1` (or `-1` if unreachable).

### 3.3 Concurrency Model & Batch Semantics
- **Locking:** `ReentrantReadWriteLock(true)` (fair mode) ensures threads are served in arrival order.
- **Read Operations:** `query()` acquires read lock; multiple queries execute concurrently.
- **Write Operations:** `addEdge()`/`deleteEdge()` acquire write lock; exclusive access guarantees graph consistency.
- **Batch Processing:** `processBatch()` iterates operations sequentially. While the spec allows concurrent execution within a batch, sequential processing with a fair lock naturally preserves the required ordering: each query sees all preceding modifications and none of the subsequent ones.

### 3.4 Correctness Harness
The harness (`CorrectnessHarness.java`) complies with the grading specification:
1. Reads edge list until `S` is encountered.
2. Prints `R\n` to signal readiness.
3. Reads operations until `F`. Processes batch, prints query results (one per line), waits for next batch.
4. Supports `uni`/`bi` modes via CLI argument.

---

## 4. Experimental Methodology

### 4.1 Configuration & Workload Generation
- **Graph:** 100 nodes, 1000 random directed edges (`gen_initial_graph.py`).
- **Workloads:** Generated via `WorkloadGenerator` with configurable write percentage (`GSP.writePercent`).
- **Sleep Units:** `GSP.client.operations.sleep` is specified in **nanoseconds** for `LockSupport.parkNanos()`.
- **Trials:** `NUM_RUNS = 5` per configuration to average out JVM warm-up and OS scheduling noise.

### 4.2 Metrics & Measurement
- **Response Time:** Server-side `duration` (ns) for `Q` operations, extracted from `server-log.txt`.
- **Aggregation:** Mean and standard deviation computed per configuration; converted to microseconds for plotting.
- **Throughput:** QPS calculated as `query_count / (t_max - t_min)`.

### 4.3 Automation
`run_experiments.py` automates property mutation, system execution, log collection, parsing, aggregation, and plotting. Ensures clean state between runs and handles subprocess timeouts.

---

## 5. Performance Analysis & Results

All experiments were conducted on a generated graph (100 nodes, 1000 edges) with 5 repetitions per configuration.

### 5.1 Response Time vs. Request Frequency
*Fixed: 3 clients, 30% writes, one-by-one mode.*  

![Response Time vs Frequency](plots/response_time_vs_frequency.png)

**Observation:** The `bi` variant consistently outperforms `uni` across all sleep intervals. `uni` exhibits high variance (large error bars) at low sleep values (high frequency), indicating latency spikes caused by fair-lock queueing and JVM JIT warm-up effects. `bi` remains stable due to reduced search depth.

### 5.2 Response Time vs. Write Percentage
*Fixed: 3 clients, high frequency, batch mode.*  

![Response Time vs Write Percentage](plots/response_time_vs_write_pct.png)

**Observation:** `bi` latency remains nearly flat (~100–180 μs) regardless of write ratio. `uni` shows a pronounced spike at 80% writes. This is a direct consequence of the fair read-write lock: as write operations dominate, reader threads are queued behind writers, causing starvation-like latency peaks. The behavior validates correct lock semantics but highlights a known throughput bottleneck under write-heavy loads.

### 5.3 Scalability: Number of Clients (Basic & Stress)
*Fixed: 30% writes, high frequency, batch mode.*  
![Response Time vs Number of Clients](plots/response_time_vs_num_clients.png)

![Response Time vs Number of Clients Stress](plots/response_time_vs_num_clients_stress.png)

**Observation:** 
- **Basic [1–5]:** Both variants scale sub-linearly initially. `bi` maintains a ~50–60% latency advantage. The dip at 2 clients is attributed to statistical variance across 5 runs and JVM optimization effects.
- **Stress [5–15]:** `uni` degrades linearly (reaching ~1500 μs at 15 clients), confirming algorithmic and lock contention bottlenecks. `bi` scales more gracefully (~1000 μs at 15 clients), demonstrating that reduced node exploration offsets concurrency overhead.

### 5.4 Summary Table (Median Query Response Time, μs)
| Configuration          | `uni` (μs) | `bi` (μs) | Improvement |
|------------------------|------------|-----------|-------------|
| Low Freq (100 ms sleep) | 310        | 125       | ~240%        |
| High Write (80%)       | 595        | 175       | ~340%        |
| Stress (15 clients)    | 1510       | 1040      | ~145%        |

---

## 6. Design Decisions & Rationale

| Decision | Rationale |
|----------|-----------|
| **Fair `ReentrantReadWriteLock`** | Guarantees sequential ordering within batches as required by the spec. Prevents writer starvation but introduces predictable latency under high write ratios. |
| **Bidirectional BFS** | Explores O(b^(d/2)) nodes instead of O(b^d). Requires reverse adjacency list but yields significant latency reduction for medium/long paths. |
| **Batch Processing via RMI** | Reduces network round-trips. Clients send `Operation[]` arrays, minimizing serialization overhead and aligning with the grader's batch semantics. |
| **Separate Correctness Harness** | Grading requires stdin/stdout interaction. Embedding RMI would complicate automated testing. The harness directly instantiates `GraphEngine`, ensuring identical logic without network overhead. |
| **Auto-flush Logger** | `PrintWriter` initialized with `autoFlush=true` prevents log loss on unexpected termination or mid-batch crashes. |

---

## 7. Conclusion
This project successfully implements a distributed, RMI-based dynamic shortest-path system that satisfies all functional and concurrency requirements. The bidirectional BFS variant consistently outperforms the unidirectional baseline in latency and scalability. Performance analysis reveals that while the fair locking strategy ensures correctness, it becomes a bottleneck under write-heavy or high-concurrency workloads—a trade-off explicitly acknowledged and documented.

---

## Appendix A. Configuration Reference
All parameters are stored in `system.properties`.

| Property | Description | Example |
|----------|-------------|---------|
| `GSP.server` | Host where RMI registry & server run | `localhost` |
| `GSP.server.port` | Server socket port (0 = any) | `49053` |
| `GSP.rmiregistry.port` | RMI registry port | `1099` |
| `GSP.serviceName` | Binding name in registry | `GraphEngine` |
| `GSP.graph.file` | Path to initial graph file | `graph/initial_graph.txt` |
| `GSP.numberOfnodes` | Number of client processes | `3` |
| `GSP.node<i>` | Host for client `i` | `localhost` |
| `GSP.writePercent` | Percentage of writes (0–100) | `30` |
| `GSP.operations.per.batch` | Operations per generated batch | `500` |
| `GSP.batchMode` | `true` = batch RMI, `false` = per-op | `true` |
| `GSP.bidirectionalMode` | `true` = Bi-BFS, `false` = Uni-BFS | `false` |
| `GSP.client.operations.sleep` | Max inter-request sleep (**ms**) | `0` |
| `GSP.server.operations.sleep` | Simulated server delay (**ms**) | `0` |
| `GSP.client.timeout.seconds` | Max wait for client processes | `180` |

---

## Appendix B. Build & Run Instructions

### Compilation
```bash
./compile.sh
# or manually:
javac -d classes shared/*.java client/*.java server/*.java *.java
```

### Distributed Execution
```bash
./start.sh
# or:
java -cp classes Start
```

### Grading Harness
```bash
./correctness.sh uni   < input.txt
./correctness.sh bi    < input.txt
```

### Automated Experiments
```bash
python3 run_experiments.py
# Outputs plots to ./plots/ and raw/aggregated CSVs to ./experiments/
```

---

## Appendix C. File Structure
```
.
├── README.md                  # Project report & documentation
├── Start.java                 # Orchestrator
├── WorkloadGenerator.java     # Batch generator
├── CorrectnessHarness.java    # Grading stdin/stdout wrapper
├── shared/GraphService.java   # RMI interface & records
├── server/
│   ├── GraphEngine.java       # Core logic & concurrency
│   └── Server.java            # RMI bootstrap
├── client/Client.java         # RMI client & logging
├── python_scripts/
│   ├── gen_initial_graph.py   # Graph generator
│   └── run_experiments.py     # Automation & plotting
── graph/initial_graph.txt    # Default graph
├── data/                      # Batch inputs/outputs
├── log/                       # Server & client logs
└── classes/                   # Compiled bytecode
```

---

## References
1. Oracle. *Java RMI Tutorial*. https://docs.oracle.com/javase/tutorial/rmi/
2. Cormen, T. H., et al. *Introduction to Algorithms*, 3rd ed. MIT Press, 2009. (Section 22.2: Breadth-First Search)
3. Java Platform SE 17 Documentation. `java.util.concurrent.locks.ReentrantReadWriteLock`.
4. Pohl, I. *Bidirectional Search*. Machine Intelligence, 1971. (Foundational work on frontier intersection)

--- 