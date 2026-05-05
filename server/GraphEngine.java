import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class GraphEngine implements GraphService {
    private final Map<Integer, Set<Integer>> adj; // adjacency list

    /**
     * Initializes the graph engine with an empty graph.
     */
    public GraphEngine() {
        this.adj = new HashMap<>();
    }

    /**
     * Initializes the graph engine with the given adjacency list.
     * @param adj
     */
    public GraphEngine(Map<Integer, Set<Integer>> adj) {
        this.adj = adj;
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
        Integer[] path = this.BreadthFirstSearch(u, v);
        if (path == null) return -1;
        return path.length - 1;
    }


    /**
     * Adds a directed edge from node u to node v to the graph.
     * 
     * @param u the starting node
     * @param v the target node
     */
    public void addEdge(int u, int v) {
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
    public void deleteEdge(int u, int v) {
        if (!adj.containsKey(u)) return;
        adj.get(u).remove(v);
    }

    
    /**
     * Performs a breadth-first search (BFS) to find the shortest path from node u to node v in the graph.
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
}
