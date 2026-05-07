import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class WorkloadGenerator {
    private final Random rand = new Random();
    private final Set<Integer> nodes = new HashSet<>();
    private final int MAX_NODES = 10000;

    /**
     * Loads the graph from a file
     * 
     * @param graphFile the file containing the graph
     */
    public WorkloadGenerator(String graphFile) {
        try (BufferedReader bf = new BufferedReader(new FileReader(graphFile))) {
            String line;
            while ((line = bf.readLine()) != null) {
                if (line.trim().equals("S"))
                    break;
                String[] tokens = line.trim().split("\\s+");
                int u = Integer.parseInt(tokens[0]);
                int v = Integer.parseInt(tokens[1]);
                this.nodes.add(u);
                this.nodes.add(v);
            }
        } catch (IOException e) {
            System.err.println("Failed to load graph: " + e.getMessage());
        }
    }

    public void generateBatch(String outputFile, int numOperations, float writePercentage) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(outputFile))) {
            // Convert writePercentage (0-100) to fraction (0-1)
            float writeRatio = writePercentage / 100.0f;

            for (int i = 0; i < numOperations; i++) {
                int u = randomNode();
                int v = randomNode();
                char op;
                if (rand.nextFloat() < writeRatio) {
                    op = rand.nextBoolean() ? 'A' : 'D';
                } else {
                    op = 'Q';
                }
                pw.printf("%c %d %d%n", op, u, v);
            }
            pw.printf("F");
        } catch (IOException e) {
            System.err.println("Failed to write to file: " + e.getMessage());
        }
    }

    /**
     * Picks a random node from the graph. if graph is empty it will generate a
     * random node
     * and add it to the graph.
     *
     * @return a random node
     */
    private Integer randomNode() {
        if (nodes.isEmpty()) {
            int id = rand.nextInt(this.MAX_NODES) + 1;
            this.nodes.add(id);
            return id;
        }
        int id = rand.nextInt(nodes.size());
        return nodes.toArray(new Integer[0])[id];
    }
}
