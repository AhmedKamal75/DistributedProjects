package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import shared.GraphService;

public class GraphEngine implements GraphService {
    private Map<Integer, Set<Integer>> adj;
    private volatile Map<GraphService.Pair, Integer> precomputedPaths;
    private boolean precomputed;
    private final ReentrantReadWriteLock lock;
    private final PrintWriter logger;
    private final boolean verbose;
    private boolean precomputeMode; // true = ASPPS, false = BFS
    private volatile boolean graphChanged; // volatile is for thread safety that makes variable value visible to all
                                           // threads
    private String filename;
    private int simulatedDelayMs;

    public GraphEngine() {
        this(null, null, false, 0);
    }

    public GraphEngine(String filename, String logfilePath, boolean verbose, int simulatedDelayMs) {
        this.filename = filename;
        this.adj = new HashMap<>();
        this.precomputedPaths = new ConcurrentHashMap<>();// HashMap<>();
        this.precomputed = false;
        this.lock = new ReentrantReadWriteLock(true);
        this.verbose = verbose;
        this.precomputeMode = false;
        this.graphChanged = false;
        PrintWriter tempLogger = null;
        this.simulatedDelayMs = simulatedDelayMs;

        if (logfilePath != null) {
            try {
                tempLogger = new PrintWriter(new BufferedWriter(new FileWriter(new File(logfilePath), true)));
            } catch (IOException e) {
                System.out.println("Failed to create log file: " + e);
            }
        }

        this.logger = tempLogger;
    }

    /**
     * Queries the shortest distance between two nodes in the graph.
     * 
     * @param u the starting node
     * @param v the target node
     * @return the shortest distance between u and v,
     *         or -1 if no path exists, or if u or v is not in the graph,
     *         or 0 if u == v.
     */
    @Override
    public int query(int u, int v) throws RemoteException {
        long startTime = System.currentTimeMillis();
        this.lock.readLock().lock(); // acquire read lock
        try {
            if (precomputeMode && !this.graphChanged) {
                return this.precomputedPaths.getOrDefault(new GraphService.Pair(u, v), -1);
            } else {
                Integer[] path = this.BFS(u, v);
                if (path == null)
                    return -1;
                return path.length - 1;
            }
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            this.lock.readLock().unlock(); // release read lock
            simulateDelay();
            log("Q", u, v, startTime, duration);
        }
    }

    /**
     * Adds a directed edge from node u to node v to the graph.
     * 
     * @param u the starting node
     * @param v the target node
     */
    @Override
    public void addEdge(int u, int v) throws RemoteException {
        long startTime = System.currentTimeMillis();
        this.lock.writeLock().lock();
        try {
            this.precomputed = false;
            adj.putIfAbsent(u, new HashSet<>());
            adj.putIfAbsent(v, new HashSet<>());
            adj.get(u).add(v);
            if (this.precomputeMode)
                this.graphChanged = true;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            this.lock.writeLock().unlock();
            simulateDelay();
            log("A", u, v, startTime, duration);
        }
    }

    /**
     * Removes a directed edge from node u to node v from the graph.
     * 
     * @param u the starting node
     * @param v the target node
     */
    @Override
    public void deleteEdge(int u, int v) throws RemoteException {
        long startTime = System.currentTimeMillis();
        this.lock.writeLock().lock();
        try {
            this.precomputed = false;
            if (!adj.containsKey(u))
                return;
            adj.get(u).remove(v);
            if (this.precomputeMode)
                this.graphChanged = true;
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            this.lock.writeLock().unlock();
            simulateDelay();
            log("D", u, v, startTime, duration);
        }

    }

    /**
     * Processes a batch of operations and returns the results.
     * 
     * <p>
     * Thread-safe: Multiple client threads can call this method simultaneously.
     * Lock granularity within individual operations (query, addEdge, deleteEdge)
     * ensures:
     * <ul>
     * <li>Only one write operation at a time</li>
     * <li>Concurrent reads when no write is in progress</li>
     * <li>In-order processing of each client's operations</li>
     * </ul>
     * 
     * @param operations the operations to process
     * @return an array of integers representing the results of query operations
     */
    @Override
    public Integer[] processBatch(Operation[] operations) throws RemoteException {
        if (this.precomputeMode) {
            this.lock.writeLock().lock();
            try {
                if (this.graphChanged) {
                    computeAllPaths();
                    this.graphChanged = false;
                }
            } finally {
                this.lock.writeLock().unlock();
            }
        }

        List<Integer> result = new ArrayList<>(operations.length);

        for (Operation op : operations) {
            switch (op.operationType()) {
                case 'Q':
                    result.add(query(op.u(), op.v()));
                    break;
                case 'A':
                    addEdge(op.u(), op.v());
                    break;
                case 'D':
                    deleteEdge(op.u(), op.v());
                    break;
                default:
                    break;
            }
        }

        this.logger.flush();

        return result.toArray(new Integer[0]);
    }

    /**
     * Performs a breadth-first search (BFS) to find the shortest path from node u
     * to node v in the graph.
     * ( On‑demand BFS)
     * 
     * BFS was choosen for point-to-point queries because it is a simple and
     * efficient algorithm O(V+E)that can be
     * implemented without the need to build a reverse adjacency list (as in
     * Bidirectional).
     * 
     * @param u the starting node
     * @param v the target node
     * @return an array of integers representing the shortest path from u to v, or
     *         null if no path exists
     */
    private Integer[] BFS(int u, int v) {
        if (u == v)
            return new Integer[] { u };
        if (!adj.containsKey(u) || !adj.containsKey(v))
            return null;
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        Map<Integer, Integer> parent = new HashMap<>();

        visited.add(u);
        queue.add(u);

        while (!queue.isEmpty()) {
            int curr = queue.poll();

            if (curr == v) {
                return reconstructPath(parent, u, v);
            }

            for (Integer neighbor : this.adj.getOrDefault(curr, new HashSet<>())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                    parent.put(neighbor, curr);
                }
            }
        }
        return null;
    }

    private Integer[] reconstructPath(Map<Integer, Integer> parent, int u, int v) {
        LinkedList<Integer> path = new LinkedList<>();
        Integer curr = v;

        while (curr != null) {
            path.addFirst(curr);
            if (curr == u)
                break;
            curr = parent.get(curr);
        }

        return path.toArray(new Integer[0]);
    }

    /**
     * Runs BFS from node u on all nodes in the graph, and returns a map of the
     * shortest distances from u to all nodes in the graph.
     * 
     * @param u        the starting node
     * @param allNodes the set of all nodes in the graph
     * @return a map of the shortest distances from u to all nodes in the graph
     */
    private Map<Integer, Integer> BFSAllPairs(int u, Set<Integer> allNodes) {
        Map<Integer, Integer> distances = new HashMap<>();

        distances.put(u, 0);

        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();

        visited.add(u);
        queue.add(u);

        while (!queue.isEmpty()) {
            int curr = queue.poll();
            int currDistance = distances.get(curr);

            for (Integer neighbor : this.adj.getOrDefault(curr, new HashSet<>())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    distances.put(neighbor, currDistance + 1); // distace = parent distance + 1
                    queue.add(neighbor);
                }
            }
        }

        return distances;
    }

    /**
     * Computes the shortest path between all pairs of nodes in the graph, and
     * stores the results in the precomputedPaths private map.
     */
    private void computeAllPaths() {

        Set<Integer> allNodes = new HashSet<>(adj.keySet());
        for (Set<Integer> neighbors : adj.values()) {
            allNodes.addAll(neighbors);
        }

        Map<GraphService.Pair, Integer> temp = new HashMap<>(allNodes.size() * allNodes.size());

        for (Integer u : allNodes) {
            Map<Integer, Integer> distances = this.BFSAllPairs(u, allNodes);
            for (Integer v : allNodes) {
                Integer distance = distances.get(v);
                if (distance != null) {
                    temp.put(new GraphService.Pair(u, v), distance);
                }
            }
        }

        this.precomputedPaths = temp;
        this.precomputed = true;
    }

    /**
     * Loads a graph from a file.
     * formated as:
     * u1 v1 --> to add an edge from u1 to v1
     * u2 v2
     * u3 v3
     * ...
     * S --> to end the graph
     * and values are space separated, and are integers
     * 
     * @param filename
     * @return true if the graph was successfully loaded, false otherwise
     */
    public boolean loadFromFile(String filename) throws IllegalArgumentException {
        Map<Integer, Set<Integer>> tempAdj = new HashMap<>();
        try (BufferedReader bf = new BufferedReader(new FileReader(new File(filename)))) {
            String line;
            while ((line = bf.readLine()) != null) {
                if (line == null || line.trim().equals("")) {
                    throw new IllegalArgumentException("Empty line in graph file or does not end with 'S'");
                }

                if (line.trim().equals("S")) {
                    this.adj = tempAdj;
                    return true;
                }

                String[] tokens = line.trim().split("\\s+");
                int u = Integer.parseInt(tokens[0]);
                int v = Integer.parseInt(tokens[1]);
                tempAdj.putIfAbsent(u, new HashSet<>());
                tempAdj.putIfAbsent(v, new HashSet<>());
                tempAdj.get(u).add(v);

            }
        } catch (IOException | NullPointerException e) {
            System.err.println("Failed to load graph: " + e.getMessage());
        }
        return false;
    }

    /**
     * Logs the execution time of a method call
     * format:
     * timestamp, thread id, method name, operation type, u, v, start time, duration
     * 
     * @param method    the method name ('Q', 'A', 'D')
     * @param u         the source node
     * @param v         the target node
     * @param startTime the start time of the operation in milliseconds
     * @param duration  the duration of the operation in milliseconds
     */
    private void log(String method, int u, int v, long startTime, long duration) {
        if (!verbose || this.logger == null)
            return;

        String line = String.format("%d,%d,%s,%d,%d,%d,%d,%n",
                Instant.now().toEpochMilli(),
                Thread.currentThread().threadId(),
                method, u, v, startTime, duration);
        logger.print(line);
    }

    public void setPrecomputedMode(boolean precomputed) {
        this.precomputeMode = precomputed;
        if (precomputed) {
            loadFromFile(this.filename);
            computeAllPaths();
            graphChanged = false;
        }
    }

    public boolean getPrecomputeMode() {
        return this.precomputed;
    }

    private void simulateDelay() {
        if (this.simulatedDelayMs > 0) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(this.simulatedDelayMs + 1));
            } catch (InterruptedException e) {
                System.err.println("Failed to simulate sleep: " + e.getMessage());
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Set<Integer>> entry : adj.entrySet()) {
            sb.append(entry.getKey());
            sb.append(": ");
            for (Integer neighbor : entry.getValue()) {
                sb.append(neighbor);
                sb.append(" ");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
