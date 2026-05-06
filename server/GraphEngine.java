import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GraphEngine implements GraphService {
    private Map<Integer, Set<Integer>> adj;
    private Map<GraphService.Pair, Integer> precomputedPaths;
    private boolean precomputed;
    private final ReentrantReadWriteLock lock;
    
    
    public GraphEngine() {
        this(new HashMap<>());
    }

    public GraphEngine(String filename) {
        this(new HashMap<>());
        if (this.loadFromFile(filename)) {
            this.computeAllPaths();
        } else {
            System.out.println("Failed to load graph from file");
        }
    }


    public GraphEngine(Map<Integer, Set<Integer>> adj) {
        this.adj = adj;
        this.precomputedPaths = new HashMap<>();
        this.precomputed = false;
        this.lock = new ReentrantReadWriteLock(true);
    }

    /**
     * Queries the shortest distance between two nodes in the graph.
     * 
     * @param u the starting node
     * @param v the target node
     * @return      the shortest distance between u and v,
     *              or -1 if no path exists, or if u or v is not in the graph,
     *              or 0 if u == v.
     */
    @Override
    public int query(int u, int v) throws RemoteException {
        this.lock.readLock().lock(); // acquire read lock
        try {
            if (!precomputed) {
                Integer[] path = this.BreadthFirstSearch(u, v);
                if (path == null) return -1;
                return path.length - 1;
            } else {
                return this.precomputedPaths.getOrDefault(new GraphService.Pair(u, v), -1);
            }

        } finally {
            this.lock.readLock().unlock(); // release read lock
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
        this.lock.writeLock().lock();
        try {
            this.precomputed = false;
            adj.putIfAbsent(u, new HashSet<>());
            adj.putIfAbsent(v, new HashSet<>());
            adj.get(u).add(v);
        } finally {
            this.lock.writeLock().unlock();
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
        this.lock.writeLock().lock();
        try {
            this.precomputed = false;
            if (!adj.containsKey(u)) return;
            adj.get(u).remove(v);
        } finally {
            this.lock.writeLock().unlock();
        }

    }


    /**
     * Processes a batch of operations and returns the results.
     * 
     * parallization:
     * any number of client threads can call processBatch() simultaneously.
     * this is safe because the locks are inside query, addEdge, and deleteEdge.
     * this allows for:
     *     1. only one write at a time.
     *     2. concurrent reads when no write is happening.
     *     3. each client operations are processed in order. 
     * 
     * 
     * @param operations the operations to process
     * @return an array of integers representing the results of the query operations
     */

    @Override
    public Integer[] processBatch(Operation[] operations) throws RemoteException {
        if (!this.precomputed) this.computeAllPaths();

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
        return result.toArray(new Integer[0]);
    }
    
    /**
     * Performs a breadth-first search (BFS) to find the shortest path from node u to node v in the graph.
     * ( On‑demand BFS )
     * 
     * @param u the starting node
     * @param v the target node
     * @return an array of integers representing the shortest path from u to v, or null if no path exists
     */
    private Integer[] BreadthFirstSearch(int u, int v) {
        if (u == v ) return new Integer[]{u};
        if (!adj.containsKey(u) || !adj.containsKey(v)) return null;
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

            for (Integer neighbor:this.adj.getOrDefault(curr, new HashSet<>())) {
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
            if (curr == u) break;
            curr = parent.get(curr);
        }

        return path.toArray(new Integer[0]);
    }

    /**
     * Computes the shortest path between all pairs of nodes in the graph, and stores the results in the precomputedPaths private map.
     */
    private void computeAllPaths() {
        this.precomputedPaths.clear();

        Set<Integer> allNodes = new HashSet<>(adj.keySet());
        for (Set<Integer> neighbors : adj.values()) {
            allNodes.addAll(neighbors);
        }

        for (Integer u: allNodes) {
            for (Integer v: allNodes) {
                Integer[] path = this.BreadthFirstSearch(u, v);
                if (path != null) {
                    this.precomputedPaths.put(new GraphService.Pair(u, v), path.length - 1);
                }
            }
        }
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
     * @param filename
     * @return true if the graph was successfully loaded, false otherwise
     */
    private boolean loadFromFile(String filename) {
        Map<Integer, Set<Integer>> tempAdj = new HashMap<>();
        try (BufferedReader bf = new BufferedReader(new FileReader(new File(filename)))) {
            String line;
            while ((line  = bf.readLine()) != null) {
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
        } catch (IOException  e) {
            e.printStackTrace();
        }
        return false;
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
