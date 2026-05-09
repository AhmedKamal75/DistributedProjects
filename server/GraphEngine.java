package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import shared.GraphService;

public class GraphEngine implements GraphService {
    private Map<Integer, Set<Integer>> adj;
    private Map<Integer, Set<Integer>> reverseAdj;
    private final ReentrantReadWriteLock lock;
    private final PrintWriter logger;
    private final boolean verbose;
    private boolean BidirectionalMode;
    private String filename;
    private int simulatedDelayMs;

    public GraphEngine() {
        this(null, null, false, 0);
    }

    public GraphEngine(String filename, String logfilePath, boolean verbose, int simulatedDelayMs) {
        this.filename = filename;
        this.adj = new HashMap<>();
        this.reverseAdj = new HashMap<>();
        this.lock = new ReentrantReadWriteLock(true);
        this.verbose = verbose;
        this.BidirectionalMode = false;
        PrintWriter tempLogger = null;
        this.simulatedDelayMs = simulatedDelayMs;

        if (logfilePath != null) {
            try {
                File logFile = new File(logfilePath);
                if (logFile.getParentFile() != null && !logFile.getParentFile().exists()) {
                    logFile.getParentFile().mkdirs();
                }

                tempLogger = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)), true);
            } catch (IOException e) {
                System.err.println("Failed to create log file: " + e.getMessage());
            }
        }

        this.logger = tempLogger;
    }

    @Override
    public int query(int u, int v) throws RemoteException {
        long startTime = System.nanoTime();
        this.lock.readLock().lock();
        try {
            Integer[] path = this.BidirectionalMode ? bidirectionalBFS(u, v) : this.BFS(u, v);
            return (path == null) ? -1 : path.length - 1;
        } finally {
            long duration = System.nanoTime() - startTime;
            this.lock.readLock().unlock();
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
        final long startNano = System.nanoTime();
        this.lock.writeLock().lock();
        try {
            addEdgeInternal(u, v);
        } finally {
            long duration = System.nanoTime() - startNano;
            this.lock.writeLock().unlock();
            simulateDelay();
            log("A", u, v, startNano, duration);
        }
    }

    private void addEdgeInternal(int u, int v) {
        adj.putIfAbsent(u, new HashSet<>());
        adj.putIfAbsent(v, new HashSet<>());
        adj.get(u).add(v);

        reverseAdj.putIfAbsent(v, new HashSet<>());
        reverseAdj.putIfAbsent(u, new HashSet<>());
        reverseAdj.get(v).add(u);

    }

    /**
     * Removes a directed edge from node u to node v from the graph.
     * 
     * @param u the starting node
     * @param v the target node
     */
    @Override
    public void deleteEdge(int u, int v) throws RemoteException {
        final long startNano = System.nanoTime();
        this.lock.writeLock().lock();
        try {
            deleteEdgeInternal(u, v);
        } finally {
            long duration = System.nanoTime() - startNano;
            this.lock.writeLock().unlock();
            simulateDelay();
            log("D", u, v, startNano, duration);
        }
    }

    private void deleteEdgeInternal(int u, int v) {
        if (this.adj.containsKey(u)) {
            this.adj.get(u).remove(v);
        }
        if (this.reverseAdj.containsKey(v)) {
            this.reverseAdj.get(v).remove(u);
        }
    }

    /**
     * Processes a batch of operations and returns the results.
     * 
     * @param operations the operations to process
     * @return an array of integers representing the results of query operations
     */
    @Override
    public Integer[] processBatch(Operation[] operations) throws RemoteException {
        List<Integer> result = new ArrayList<>(operations.length);

        for (Operation op : operations) {
            switch (op.operationType()) {
                case 'Q' -> result.add(query(op.u(), op.v()));
                case 'A' -> addEdge(op.u(), op.v());
                case 'D' -> deleteEdge(op.u(), op.v());
                default -> System.err.println("Invalid operation: " + op.operationType());
            }
        }
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

    private Integer[] bidirectionalBFS(int u, int v) {
        if (u == v)
            return new Integer[] { u };
        if (!adj.containsKey(u) || !adj.containsKey(v))
            return null;

        Set<Integer> forwardVisited = new HashSet<>();
        Set<Integer> backwardVisited = new HashSet<>();
        Queue<Integer> forwardQueue = new LinkedList<>();
        Queue<Integer> backwardQueue = new LinkedList<>();
        Map<Integer, Integer> forwardParent = new HashMap<>();
        Map<Integer, Integer> backwardParent = new HashMap<>();

        forwardVisited.add(u);
        forwardQueue.add(u);
        backwardVisited.add(v);
        backwardQueue.add(v);

        Integer meeting = null;

        while (!forwardQueue.isEmpty() && !backwardQueue.isEmpty()) {
            meeting = expandFrontier(forwardQueue, forwardVisited, backwardVisited, adj, forwardParent);
            if (meeting != null)
                break;
            meeting = expandFrontier(backwardQueue, backwardVisited, forwardVisited, reverseAdj, backwardParent);
            if (meeting != null)
                break;
        }

        if (meeting == null)
            return null;
        return reconstructBidirectionalPath(forwardParent, backwardParent, u, v, meeting);
    }

    private Integer expandFrontier(Queue<Integer> queue, Set<Integer> visitedOwn,
        Set<Integer> visitedOther, Map<Integer, Set<Integer>> edges, Map<Integer, Integer> parents) {
        int levelSize = queue.size();
        for (int i = 0; i < levelSize; i++) {
            int curr = queue.poll();
            for (Integer neighbor:edges.getOrDefault(curr, new HashSet<>())) {
                if (!visitedOwn.contains(neighbor)) {
                    visitedOwn.add(neighbor);
                    parents.put(neighbor, curr);
                    queue.add(neighbor);
                }
                if (visitedOther.contains(neighbor)) {
                    return neighbor;
                }
            }
        }
        return null;
    }

    private Integer[] reconstructBidirectionalPath(Map<Integer, Integer> forwardParent,
                Map<Integer, Integer> backwardParent, int u, int v, Integer meeting) {
        LinkedList<Integer> path = new LinkedList<>();

        // meeting (curr) -> u
        Integer curr = meeting;
        while (curr != null) {
            path.addFirst(curr);
            if (curr == u) break;
            curr = forwardParent.get(curr);
        }

        // meeting (curr) -> v
        curr = backwardParent.get(meeting);
        while (curr != null) {
            path.addLast(curr);
            if (curr == v) break;
            curr = backwardParent.get(curr);
        }

        return path.toArray(new Integer[0]);
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
        Map<Integer, Set<Integer>> tempRev = new HashMap<>();

        try (BufferedReader bf = new BufferedReader(new FileReader(new File(filename)))) {
            String line;
            while ((line = bf.readLine()) != null) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }

                if (line.trim().equals("S")) {
                    this.adj = tempAdj;
                    this.reverseAdj = tempRev;
                    return true;
                }

                String[] tokens = line.trim().split("\\s+");
                if (tokens.length < 2) continue;

                int u = Integer.parseInt(tokens[0]);
                int v = Integer.parseInt(tokens[1]);

                tempAdj.putIfAbsent(u, new HashSet<>());
                tempAdj.putIfAbsent(v, new HashSet<>());
                
                tempRev.putIfAbsent(u, new HashSet<>());
                tempRev.putIfAbsent(v, new HashSet<>());
                
                tempAdj.get(u).add(v);
                
                tempRev.get(v).add(u);
                
            }
        } catch (IOException | NumberFormatException e) {
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
     * @param startTime the start time of the operation in nanoseconds
     * @param duration  the duration of the operation in nanoseconds
     */
    private void log(String method, int u, int v, long startTime, long duration) {
        if (!verbose || this.logger == null)
            return;

        String line = String.format("%d,%d,%s,%d,%d,%d,%d,%n",
                System.nanoTime(),
                Thread.currentThread().threadId(),
                method, u, v, startTime, duration);
        logger.print(line);
    }

    public void setBidirectionalMode(boolean bidirectionalMode) {
        this.BidirectionalMode = bidirectionalMode;
        if (this.filename != null && this.adj.isEmpty()) {
            loadFromFile(this.filename);
        }
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
