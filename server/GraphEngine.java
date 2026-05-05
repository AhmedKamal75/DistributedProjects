import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class GraphEngine implements GraphService {
    private final Map<Integer, Set<Integer>> adj; // adjacency list
    private final Map<GraphService.Pair, Integer> precomputedPaths;
    private boolean precomputed;

    /**
     * Initializes the graph engine with an empty graph.
     */
    public GraphEngine() {
        this.adj = new HashMap<>();
        this.precomputedPaths = new HashMap<>();
        this.precomputed = false;
    }

    /**
     * Initializes the graph engine with the given adjacency list.
     * @param adj
     */
    public GraphEngine(Map<Integer, Set<Integer>> adj) {
        this.adj = adj;
        this.precomputedPaths = new HashMap<>();
        this.precomputed = false;
    }

    /**
     * Queries the shortest distance between two nodes in the graph.
     * 
     * @param u the starting node
     * @param v the target node
     * @return the shortest distance between u and v, or -1 if no path exists, or if u or v is not in the graph, or 0 if u == v.
     */
    @Override
    public int query(int u, int v) {
        if (!precomputed) {
            if (u == v) return 0;
            Integer[] path = this.BreadthFirstSearch(u, v);
            if (path == null) return -1;
            return path.length - 1;
        } else {
            // TODO 1: later use the precomputedPaths map to get the distance
            return -1;
        }
    }

    /**
     * Adds a directed edge from node u to node v to the graph.
     * 
     * @param u the starting node
     * @param v the target node
    */
    @Override
    public void addEdge(int u, int v) {
        this.precomputed = false;
        adj.putIfAbsent(u, new HashSet<>());
        adj.putIfAbsent(v, new HashSet<>());
        adj.get(u).add(v);
    }

    /**
     * Removes a directed edge from node u to node v from the graph.
     * 
     * @param u the starting node
     * @param v the target node
     */
    @Override
    public void deleteEdge(int u, int v) {
        this.precomputed = false;
        if (!adj.containsKey(u)) return;
        adj.get(u).remove(v);
    }

    @Override
    public Integer[] processBatch(Operation[] operations) {
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
            // System.out.println(op.toString() + " " + this.adj.toString());
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
        if (!adj.containsKey(u)) return null;
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
    public void computeAllPaths() {
        for (Integer u: this.adj.keySet()) {
            for (Integer v: this.adj.keySet()) {
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
     * @return
     */
    public static GraphEngine loadFromFile(String filename) {
        Map<Integer, Set<Integer>> tempAdj = new HashMap<>();
        try (BufferedReader bf = new BufferedReader(new FileReader(new File(filename)))) {
            String line;
            while ((line  = bf.readLine()) != null) {
                if (line.contains("S")) {
                    return new GraphEngine(tempAdj);
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
        return null;
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
