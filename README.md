# Distributed Graph Service

A distributed graph processing system using RMI (Remote Method Invocation) with batch and single-query processing modes.

## Quick Start

### Build
```bash
./build.sh
```

### Run
```bash
./run.sh
```

Or compile and run manually:
```bash
javac -d classes shared/*.java client/*.java server/*.java *.java
java -cp classes Start
```

## Directory Structure

```
.
├── README.md                    # This file
├── build.sh                     # Build script (compiles to classes/)
├── run.sh                        # Run script (executes from classes/)
├── system.properties            # Configuration file
├── Start.java                   # Main entry point
├── WorkloadGenerator.java       # Generates workload batches
│
├── classes/                     # ✓ Compiled .class files (auto-generated)
│   ├── Start.class
│   ├── WorkloadGenerator.class
│   ├── client/
│   ├── server/
│   └── shared/
│
├── client/                      # Client source code
│   └── Client.java
│
├── server/                      # Server source code
│   ├── Server.java
│   ├── GraphEngine.java         # Core graph engine implementation
│   └── README.md                # Server docs
│
├── shared/                      # Shared interfaces
│   └── GraphService.java        # RMI interface
│
├── data/                        # Data files
│   ├── intial_graph.txt         # Initial graph structure
│   ├── patch_queries.txt        # Query operations
│   └── output.txt               # Query results
│
├── log/                         # Log files (auto-generated)
│   ├── server_logs.csv
│   └── client_logs.csv
│
└── batch*.txt                   # Generated workload batches (auto-generated)
```

## Configuration

Edit `system.properties` to customize:

```properties
GSP.server=localhost                    # Server hostname
GSP.server.port=49053                   # Server port
GSP.numberOfnodes=3                     # Number of clients
GSP.rmiregistry.port=1099               # RMI registry port
GSP.writePercent=30                     # Write operation percentage (0-100)
GSP.operations.per.batch=50             # Operations per batch
GSP.serviceName=GraphEngine             # Service name
GSP.graph.file=data/intial_graph.txt    # Input graph file
GSP.server.log.file=log/server_logs.csv # Server log location
GSP.client.log.file=log/client_logs.csv # Client log location
GSP.server.verbose=true                 # Server logging enabled
GSP.client.verbose=true                 # Client logging enabled
```

## How It Works

1. **Build Phase**: Compiles all Java sources into the `classes/` directory
2. **Execution Phase**: 
   - Start.java initializes the system
   - Server starts and loads the graph from `data/intial_graph.txt`
   - WorkloadGenerator creates batch files with random operations
   - Clients execute operations against the server
3. **Logging**: Results written to CSV files in `log/` directory

## File Descriptions

- **GraphService.java**: RMI interface defining remote operations (query, addEdge, deleteEdge, processBatch)
- **GraphEngine.java**: Graph implementation with BFS, precomputed shortest paths, and thread-safe operations
- **Server.java**: RMI server setup and lifecycle management
- **Client.java**: RMI client for executing operations (batch or individual)
- **WorkloadGenerator.java**: Creates random workload batches from graph nodes
    

