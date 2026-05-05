import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;


// javac *.java
// java Client

public class Client {
    private String clientName;
    private String logFilePath;
    private boolean batchMode;
    private boolean verbose;


    public Client() {
        this("client", "../log/client-logs.txt", false, false);
    }

    public Client(String clientName) {
        // this.clientName = clientName;
        this(clientName, "../log/client-logs.txt",false, false);
    }

    public Client(String clientName, String logFilePath, boolean batchMode, boolean verbose) {
        this.clientName = clientName;
        this.batchMode = batchMode;
        this.verbose = verbose;

        if (logFilePath != null) {
            this.logFilePath = "log/" + this.clientName + "_" + "logs.txt";
        } else {
            this.logFilePath = logFilePath;
        }

        File logFile = new File(this.logFilePath);
        try {
            // check parent first
            if (logFile.getParentFile() != null && !logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            if (!logFile.exists()) {
                logFile.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Client client = new Client("Client", "../data/log.txt", false, true);
        client.run("localhost", 8080, "GraphEngine", "../data/patch_queries.txt", "../data/output.txt");
    }

    public void run(String rmiHostname, int rmiPort, String stupName, String operationsFilePath,
            String outputFilePath) {
        try {
            GraphService stub = loadStup(rmiHostname, rmiPort, stupName);
            List<GraphService.Operation> operations = loadOperations(operationsFilePath);

            if (this.batchMode) {
                double startTime = System.currentTimeMillis();
                Integer[] results = stub.processBatch(operations.toArray(new GraphService.Operation[0]));
                logger(null, startTime, startTime, "Batch size: " + operations.size(), ',');
                exportResults(outputFilePath, results);
            } else {
                List<Integer> results = new ArrayList<>(operations.size());
                for (int i = 0; i < operations.size(); i++) {
                    GraphService.Operation op = operations.get(i);
                    double startTime = System.currentTimeMillis();
                    switch (op.operationType()) {
                        case 'Q':
                            results.add(stub.query(op.u(), op.v()));
                            break;
                        case 'A':
                            stub.addEdge(op.u(), op.v());
                            break;
                        case 'D':
                            stub.deleteEdge(op.u(), op.v());
                            break;
                        default:
                            break;
                    }
                    // Log the progress
                    // format: <operation type> <u> <v> <start-timestamp> <end-timestamp> 
                    logger(op, startTime, System.currentTimeMillis(), null, ',');
                    
                }
                exportResults(outputFilePath, results.toArray(new Integer[0]));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 
     * Loads the stub from the rmiregistry 
     * 
     * @param rmiHostname : hostname of the rmiregistry
     * @param port : port on which the rmiregistry is running
     * @param stubName : name of the stub
     * @return GraphService : stub of the GraphEngine 
     * @throws Exception 
     */
    private GraphService loadStup(String rmiHostname, int port, String stubName) throws Exception {
        Registry registry = LocateRegistry.getRegistry(rmiHostname, port);
        return (GraphService) registry.lookup(stubName);
    }

    /**
     * Loads the operations from the file
     * formated as:
     * Q u v --> to get the shortest path from u to v
     * A u v --> to add an edge from u to v
     * D u v --> to delete an edge from u to v
     * ...
     * F --> to end the operations
     * @param filepath
     * @return
     */
    private List<GraphService.Operation> loadOperations(String filepath) {
        List<GraphService.Operation> operations = new ArrayList<>();
        try (BufferedReader bf = new BufferedReader(new FileReader(new File(filepath)))) {
            String line;
            while ((line = bf.readLine()) != null) {
                if (line.contains("F")) {
                    return operations;
                }
                String[] tokens = line.trim().split("\\s+");
                operations.add(
                        new GraphService.Operation(
                                tokens[0].trim().charAt(0),
                                Integer.parseInt(tokens[1].trim()),
                                Integer.parseInt(tokens[2].trim())));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void exportResults(String filepath, Integer[] results) {
        try {
            if (filepath != null) {
                for (Integer result : results) {
                    File outputFile = new File(filepath);
                    if (!outputFile.exists()) {
                        outputFile.createNewFile();
                    }
                    try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(filepath), true))) {
                        bw.write(result + "\n");
                    }
                }
            } else {
                for (Integer result : results) {
                    System.out.println(result);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void logger(GraphService.Operation op, double startTime, double endTime, String message, char delimiter) {

        if (verbose) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
                StringBuilder sb = new StringBuilder();

                if (op != null) {
                    sb.append(op.operationType())
                    .append(delimiter)
                    .append(op.u())
                    .append(delimiter)
                    .append(op.v());
                } else { // batchMode
                    sb.append("BatchMode");
                }
                sb.append(delimiter)
                .append(startTime)
                .append(delimiter)
                .append(endTime)
                .append(delimiter)
                .append(endTime - startTime);
                if (message != null) sb.append(delimiter + message);
                sb.append('\n');

                writer.write(sb.toString());

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
