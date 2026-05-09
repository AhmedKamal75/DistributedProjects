import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import server.GraphEngine;
import shared.GraphService;

public class CorrectnessHarness {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println(
                    "Usage: BatchServer <variant>\n<variant> could be 'uni' or 'bi'.\nuni is for the uni-directional BFS mode.\nbi is for the bi-directional BFS mode.");
            System.exit(1);
        }
        boolean bidirectionalMode = args.length > 0 && args[0].equalsIgnoreCase("bi");

        GraphEngine engine = new GraphEngine(null, "log/correctness_logs.csv", true, 10);
        engine.setBidirectionalMode(bidirectionalMode);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;

        while ((line = in.readLine()) != null) {
            if (line.trim().equals("S"))
                break;
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length < 2)
                continue;
            engine.addEdge(Integer.parseInt(tokens[0]), Integer.parseInt(tokens[1]));
        }

        System.out.println("R"); // ready signal

        List<GraphService.Operation> batch = new ArrayList<>();
        while ((line = in.readLine()) != null) {
            if (line == null || line.trim().equals("")) continue;
            if (line.trim().equalsIgnoreCase("quit") || line.trim().equalsIgnoreCase("exit")) {
                System.exit(0);
            }
            if (line.trim().equalsIgnoreCase("print-adj")) {
                System.out.println(engine.toString());
                continue;
            }
            if (line.trim().equalsIgnoreCase("flush")) {
                Integer[] results = engine.processBatch(
                        batch.toArray(new GraphService.Operation[0]));
                for (Integer result : results)
                    System.out.println(result);
                batch.clear();
                continue;
            }
            if (line.trim().equals("F")) {
                Integer[] results = engine.processBatch(
                        batch.toArray(new GraphService.Operation[0]));
                for (Integer result : results)
                    System.out.println(result);
                batch.clear();
                continue;
            }
            String[] tokens = line.trim().split("\\s+");
            if (tokens.length < 3)
                continue;
            batch.add(new GraphService.Operation(tokens[0].trim().charAt(0),
                    Integer.parseInt(tokens[1].trim()), Integer.parseInt(tokens[2].trim())));
        }
    }
}
