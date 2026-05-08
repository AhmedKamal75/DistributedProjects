# Distributed Graph Service

A distributed system for answering shortest‑path queries on a **dynamic, directed, unweighted graph**,
built with Java RMI. The system supports concurrent operations, two algorithm variants for performance
comparison, batch processing for automatic grading, and logging for performance analysis.

**Course:** CS432 – Distributed Systems and Net‑Centric Computing  
**Date:** Spring 2026  


---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Two Shortest‑Path Variants](#two-shortest-path-variants)
- [Graph Engine Internals](#graph-engine-internals)
- [Concurrency Model](#concurrency-model)
- [Batch Protocol & Correctness Harness](#batch-protocol--correctness-harness)
- [Distributed Deployment](#distributed-deployment)
- [Configuration](#configuration)
- [Build & Run](#build--run)
- [Logging & Performance Data](#logging--performance-data)
- [Performance Experiments](#performance-experiments)
- [Design Decisions & Rationale](#design-decisions--rationale)
- [File Structure](#file-structure)
- [References](#references)

---

## Overview

The system maintains a **directed graph** and supports three operations:

| Operation | Symbol | Description |
|-----------|--------|-------------|
| Query     | `Q u v`| Returns the shortest directed distance from `u` to `v` (or `-1` if no path). |
| Add edge  | `A u v`| Adds directed edge `u→v`. Creates nodes if they don’t exist. |
| Delete edge | `D u v` | Removes edge `u→v`. Does nothing if the edge doesn’t exist. |

The system works in two modes:

1. **Interactive Distributed Mode** – an RMI server receives batches from multiple remote clients.
2. **Batch Processing Mode** – a standalone harness (`CorrectnessHarness`) reads standard input and outputs query results, used for automatic grading.

---

## Architecture

```
┌──────────────────────────────────────────┐
│            Start.java (main)             │
│  1. Load system.properties               │
│  2. Start RMI server thread              │
│  3. Generate workload batches            │
│  4. Launch client processes              │
│  5. Wait for clients, then shut down     │
└───────┬──────────────────────┬───────────┘
        │                      │
        ▼                      ▼
┌──────────────┐    ┌─────────────────────┐
│  Server (RMI)│    │  Client Process(es) │
│  GraphEngine │◄───│  (separate JVMs)    │
│  (remote obj)│    └─────────────────────┘
└──────────────┘
        ▲
        │
┌──────────────┐
│  Shared      │
│  Interface   │
│ GraphService │
└──────────────┘
```

- **Server** – hosts the `GraphEngine` (the actual graph). It registers itself with an RMI registry.
- **Client** – a separate process (launched locally or via SSH) that reads batch files, connects to the RMI server, sends operations, and logs results.
- **Shared Interface** – `GraphService` declares the remote methods (`query`, `addEdge`, `deleteEdge`, `processBatch`). It also contains record classes `Operation` and `Pair`.
- **Start.java** – the coordinator: starts the server thread, generates random workload batches using `WorkloadGenerator`, spawns client processes, and manages shutdown.

---

## Two Shortest‑Path Variants

The project requires **two different methods** for answering shortest‑path queries to compare performance.

### Variant 1 – On‑demand BFS (baseline)
- **Mode identifier:** `ondemand`
- A classic Breadth‑First Search is run **for every query**.
- Time per query: O(V + E) where V, E are the current graph size.
- Used when `precomputeMode = false`.

### Variant 2 – Precomputed All‑Pairs Shortest Paths (APSP)
- **Mode identifier:** `all`
- After the initial graph is loaded (and before any batch processing), a **full distance matrix is recomputed**.
- Every query becomes an O(1) lookup in a `Map<Pair, Integer>`.
- Re‑computation happens at the beginning of each batch (if the graph changed). This amortises to O(V * (2 * V + E)) ~ O(V^2 + E) cost over the adjacency list.
- Used when `precomputeMode = true`.

**Switching between variants** is done via the configuration property `GSP.precomputeMode` or by passing `ondemand` / `all` to `CorrectnessHarness`.

---

## Graph Engine Internals

### Data Structures
- **Adjacency list**: `Map<Integer, Set<Integer>> adj` – the core graph.
- **Distance cache (APSP)**: `Map<GraphService.Pair, Integer> precomputedPaths`.
  *In APSP mode*, this map is replaced atomically after recomputation (using a new `HashMap`), and the field is `volatile` to ensure visibility without locks after the initial write.
- **Locks**: `ReentrantReadWriteLock(true)` (fair) guarantees that readers and writers are served in arrival order, essential for preserving operation order within a batch.

### Algorithms
- **BFS**: standard queue‑based traversal. Chosen because the graph is **unweighted** and BFS gives the exact shortest path in linear time.
- **APSP recomputation**: For each node `u`, a single BFS discovers distances to all reachable nodes (`BFSAllPairs`). This avoids running a full BFS for every pair separately.
- **Path reconstruction**: The BFS keeps a `parent` map and reconstructs the path when the target is found. Not strictly needed for distance only, but available for future use.

---

## Concurrency Model

The server must be **non‑blocking** and handle multiple RMI calls simultaneously while **preserving operation order** within each batch.

- **Fair Read‑Write Lock**:  
  - Queries acquire a **read lock**; many queries can run concurrently.
  - Add/Delete operations acquire a **write lock**; they are exclusive.
  - The fair mode ensures that threads are served in the order they requested the lock, which naturally preserves the sequential order of operations inside a batch when the batch is processed sequentially.
- **APSP recomputation**: In `processBatch`, if `precomputeMode` is on and the graph has changed, a **write lock** is acquired to safely recompute the distance matrix. This blocks all other operations until the recomputation finishes, which is acceptable because writes are infrequent.
- **Volatile flags**: `graphChanged` is `volatile` so that changes are immediately visible to all threads. `precomputedPaths` is also volatile because it is replaced entirely during recomputation.

---

## Batch Protocol & Correctness Harness

To comply with the grading specification, the class `CorrectnessHarness` reads **from standard input** and writes **to standard output** exactly as required:

1. Read the initial graph lines (pairs of integers) until a line containing `S`.
2. Print `R` (ready).
3. Then repeatedly read operation lines until `F`. When `F` is seen, process the entire batch (if on all mode) else it process the operations one at a time and store the queries results till `F` if encountered, print the query results (one per line), and wait for the next batch.

**Usage:**
```bash
./correctness.sh ondemand   < input.txt
./correctness.sh all        < input.txt
```

This harness does **not** use RMI; it instantiates the engine directly. It is the entry point for automatic grading.

---

## Distributed Deployment

### Remote Process Launch
`Start.java` launches a **separate JVM** for each client:
- **Local nodes** (host = `localhost` or `127.0.0.1`): the `java` command is called directly via `ProcessBuilder`.
- **Remote nodes** (any other host): an `ssh` command is built that changes to the project directory and executes the same Java command.

All client processes inherit the current classpath. The system waits up to `GSP.client.timeout.seconds` for each client to finish.

### Server Lifecycle
- The server runs in a **daemon‑like thread** inside `Start`’s JVM.
- After registering itself, the server thread enters an infinite sleep (`while(true) Thread.sleep(...)`) to keep the RMI registry alive.
- A shutdown hook unbinds the service on exit.
- When clients finish, `Start` calls `server.stop()` to cleanly unbind and unexport the remote object.

---

## Configuration

All parameters are stored in `system.properties`. The following table explains every key:

| Property | Description | Example |
|----------|-------------|---------|
| `GSP.server` | Host where the RMI registry & server run | `localhost` |
| `GSP.server.port` | Port on which the server socket listens (0 = any) | `49053` |
| `GSP.rmiregistry.port` | Port for the RMI registry | `1099` |
| `GSP.serviceName` | Name the server binds to | `GraphEngine` |
| `GSP.graph.file` | Path to the initial graph file | `data/intial_graph.txt` |
| `GSP.numberOfnodes` | Number of client processes to launch | `3` |
| `GSP.node0`, `GSP.node1`, … | Host for each client | `localhost` |
| `GSP.writePercent` | Percentage of writes (0–100) in generated batches | `30` |
| `GSP.operations.per.batch` | Number of operations per generated batch | `50` |
| `GSP.batchMode` | `true` → clients use `processBatch`; `false` → one-by-one | `true` |
| `GSP.precomputeMode` | `true` = APSP variant, `false` = BFS variant | `false` |
| `GSP.server.verbose` | Enable server logging | `true` |
| `GSP.client.verbose` | Enable client logging | `true` |
| `GSP.server.log.directory` | Server log directory prefix (inside append like `server-log.txt`) | `log/` |
| `GSP.client.log.directory` | Client log directory prefix | `log/` |
| `GSP.data.directory` | Where batch input/output files are stored | `data/` |
| `GSP.client.operations.sleep` | Maximum inter‑request sleep for clients (ms) | `0` |
| `GSP.server.operations.sleep` | Simulated processing delay inside server (ms) | `0` |
| `GSP.client.timeout.seconds` | Max wait time for each client process | `120` |

*Note: The original spec uses `miregistry`, we maintain `rmiregistry` for consistency.*

---

## Build & Run

### Quick Start
```bash
# Build and Run the Start system
./start.sh

# Or compile & run manually
javac -d classes shared/*.java client/*.java server/*.java *.java
java -cp classes Start
```

### Correctness Test (stdin/stdout)
```bash
./correctness.sh ondemand < test_input.txt
./correctness.sh all       < test_input.txt
```

### Clean Compilation
The scripts `correctness.sh` and `start.sh` clean the `classes/` directory before compiling.  
`log/` and `data/` directories will be created automatically if they didn't exist.

---

## Logging & Performance Data

### Server Log
Location: `log/server_logs.csv`.  
Format: `timestamp, threadId, operationType, u, v, startTime, duration` (CSV).  
Every remote call (even those inside a batch) is logged. A final flush happens after each batch.

### Client Logs
Location: `log/log0.log`, `log/log1.log`, …  
Format (batch mode): `BATCH_MODE,-,-,startTime,endTime,duration,Batch Size: N`  
Format (one‑by‑one): `operationType,u,v,startTime,endTime,duration`

These logs contain all the raw data needed for the performance analysis.

---

## Performance Experiments

The assignment requires three sets of experiments for **both** variants. The logged data (response time per operation) can be post‑processed.

1. **Response time vs. frequency of requests**  
   Change `GSP.client.operations.sleep` (lower → higher frequency). Run with a fixed number of clients and write percentage.

2. **Response time vs. percentage of add/delete operations**  
   Vary `GSP.writePercent` (0, 20, 40, 60, 80, 100). Keep other parameters constant.

3. **Response time vs. number of client nodes [1,5]** (basic) and **[5,15]** (stress)  
   Change `GSP.numberOfnodes` and set `GSP.node<i>` accordingly.

For each configuration, run multiple trials and compute the **average or median query response time** (excluding write operations). The server logs also allow measuring throughput.

*TODO:* Write a small python script to parse the logs and compute summary statistics.

---

## Design Decisions & Rationale

1. **Why BFS?**  
   The graph is unweighted. BFS finds exact shortest paths in O(V+E) without the complexity of Dijkstra or bidirectional search, making it both simple and efficient.

2. **Why fair ReadWriteLock?**  
   Fairness guarantees that within a batch, operations are applied in the order they arrive from the sequencer (the single client thread). This ensures each query sees all preceding updates, satisfying the specification.

3. **Why volatile `graphChanged` and `precomputedPaths`?**  
   `graphChanged` is a simple flag, `volatile` provides visibility across threads without a lock. `precomputedPaths` is replaced entirely during recomputation, so `volatile` ensures clients see the new map immediately.

4. **Why `ConcurrentHashMap` for `precomputedPaths`?**  
   Although we only read after recomputation (no concurrent writes), the map is created as `ConcurrentHashMap` to allow safe reads if a thread accesses it before the volatile assignment is seen – extra safety at minimal cost.

5. **Why recompute APSP at the start of a batch, not after every write?**  
   A batch may contain multiple writes. Computing once at the beginning of the batch (before any query is answered) correctly reflects all modifications, reduces recomputation overhead, and still produces correct results.

6. **Why use `processBatch` for clients by default?**  
   It reduces the number of RMI round‑trips, which improves performance and more closely models the batch semantics required by the grader.

7. **Why separate `CorrectnessHarness` from the RMI system?**  
   The grader expects a single program reading stdin/writing stdout. Embedding a full RMI stack would complicate deployment. The harness directly uses the engine, providing identical logic without network overhead.

---

## File Structure

```
.
├── README.md
├── start.sh
├── correctness.sh
├── system.properties
├── Start.java
├── WorkloadGenerator.java
├── CorrectnessHarness.java
├── shared/
│   └── GraphService.java
├── server/
│   ├── GraphEngine.java
│   └── Server.java
├── client/
│   └── Client.java
├── data/
│   ├── intial_graph.txt
│   ├── patch_queries.txt    (sample)
│   └── output*.txt
├── log/
│   ├── server_logs.csv
│   └── log*.log
└── classes/                  (compiled .class files)
```

---

## References

- Java RMI Tutorial: [Oracle RMI Overview](http://docs.oracle.com/javase/tutorial/rmi/overview.html)
// page 594 of book 22.2 from introduction to Algorithms 3rd edition 
- BFS algorithm: standard introduction (Cormen, Leiserson, Rivest, Stein) (3rd ed.) [link](https://www.cs.mcgill.ca/~akroit/math/compsci/Cormen%20Introduction%20to%20Algorithms.pdf)
- `ReentrantReadWriteLock` fairness: Java Platform SE 17 documentation.

---