package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import shared.GraphService;

// javac *.java
// java Client

public class Client {
    private String logFilePath;
    private boolean batchMode;
    private boolean verbose;
    private PrintWriter logger;
    private int maxSleep;

    public Client(String logFilePath, boolean batchMode, boolean verbose, int maxSleep) {
        this.batchMode = batchMode;
        this.verbose = verbose;

        if (logFilePath == null) {
            System.err.println("Client logger initialization failed: logFilePath is null");
        } else {
            this.logFilePath = logFilePath;
        }

        if (verbose && logFilePath != null) {
            initLogger();
        } else {
            this.logger = null;
            this.verbose = false;
        }

        this.maxSleep = maxSleep;
    }

    /**
     * Runs the client on the given rmiregistry and stub
     * 
     * @param host        the hostname of the rmiregistry
     * @param port        the port on which the rmiregistry is running
     * @param serviceName the name of the stub (server) on the rmiregistry
     * @param opPath      the file containing the operations
     * @param outPath     the file to which the results are written
     */
    public void run(String host, int port, String serviceName, String opPath, String outPath) {
        try {
            GraphService stub = (GraphService) LocateRegistry.getRegistry(host, port).lookup(serviceName);
            List<GraphService.Operation> ops = this.loadOperations(opPath);

            if (ops == null || ops.isEmpty())
                return;

            List<Integer> results = new ArrayList<>();

            if (this.batchMode) {
                long startTime = System.currentTimeMillis();
                try {
                    Integer[] batchResults = stub.processBatch(ops.toArray(new GraphService.Operation[0]));
                    results.addAll(List.of(batchResults));
                } catch (RemoteException e ) {
                    System.err.println("Batch processing failed: " + e.getMessage());
                    long queryCount = ops.stream().filter(op->op.operationType() == 'Q').count();
                    for (int i = 0; i < queryCount; i++) results.add(-1);
                }
                
                this.log(null, startTime, System.currentTimeMillis(), "Batch Size: " + ops.size());
            } else {
                for (GraphService.Operation op : ops) {
                    long startTime = System.currentTimeMillis();
                    int res = -1;
                    try {
                        switch (op.operationType()) {
                            case 'Q' -> res = stub.query(op.u(), op.v());
                            case 'A' -> stub.addEdge(op.u(), op.v());
                            case 'D' -> stub.deleteEdge(op.u(), op.v());
                            default -> System.err.println("Invalid operation type: " + op.operationType());
                        }
                    } catch (RemoteException e) {
                        System.err.println("Remote call failed for operation: " + op.operationType() + " " + op.u() + " " + op.v() + ":" + e.getMessage());
                    }
                    if (op.operationType() == 'Q')
                        results.add(res);
                    this.log(op, startTime, System.currentTimeMillis(), null);
                    if (this.maxSleep > 0) {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(this.maxSleep + 1));
                    }
                }
            }

            this.exportResults(outPath, results);
        } catch (Exception e) {
            System.err.println("Client Exception: " + e.toString());
            e.printStackTrace();
        } finally {
            if (this.logger != null) {
                this.logger.flush();
                this.logger.close();
            }
        }
    }

    private void initLogger() {
        try {
            File logFile = new File(this.logFilePath);
            if (logFile.getParentFile() != null && !logFile.getParentFile().exists()) {
                logFile.getParentFile().mkdirs();
            }
            if (!logFile.exists()) {
                logFile.createNewFile();
            }

            this.logger = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
        } catch (IOException e) {
            System.err.println("Client logger initialization failed: " + e.getMessage());
        }
    }

    private List<GraphService.Operation> loadOperations(String filepath) {
        List<GraphService.Operation> ops = new ArrayList<>();
        try (BufferedReader bf = new BufferedReader(new FileReader(new File(filepath)))) {
            String line;
            while ((line = bf.readLine()) != null) {
                if (line.trim().equals("F"))
                    break;
                String[] tokens = line.trim().split("\\s+");
                if (tokens.length < 3)
                    continue;
                ops.add(new GraphService.Operation(tokens[0].trim().charAt(0),
                        Integer.parseInt(tokens[1].trim()), Integer.parseInt(tokens[2].trim())));
            }
            return ops;
        } catch (IOException e) {
            System.err.println("Load operations failed: " + e.getMessage());
        }
        return ops;
    }

    private void exportResults(String path, List<Integer> results) {
        if (path == null) {
            results.forEach(System.out::println);
            return;
        }
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(path)))) {
            for (Integer i : results)
                out.println(i);
        } catch (IOException e) {
            System.err.println("Failed to export to file: " + e.getMessage());
        }
    }

    public void log(GraphService.Operation op, long startTime, long endTime, String message) {
        if (!verbose || logger == null)
            return;

        String type = (op != null) ? String.valueOf(op.operationType()) : "BATCH_MODE";
        String u = (op != null) ? String.valueOf(op.u()) : "-";
        String v = (op != null) ? String.valueOf(op.v()) : "-";
        logger.printf("%s,%s,%s,%d,%d,%d%s%n",
                type, u, v, startTime, endTime, (endTime - startTime), (message != null) ? "," + message : "");
    }

    public static void main(String[] args) {
        if (args.length < 6) {
            System.err.println(
                    "Usage: Client <serverHost> <rmiPort> <serviceName> <opsFile> <outFile> [batchMode] [verbose] [logFilePath] [maxSleep]");
            System.exit(1);
        }

        String host = args[1];
        int port = Integer.parseInt(args[2]);
        String serviceName = args[3];
        String opsFile = args[4];
        String outFile = args[5];
        boolean batchMode = args.length > 6 ? Boolean.parseBoolean(args[6]) : true;
        boolean verbose = args.length > 7 ? Boolean.parseBoolean(args[7]) : true;
        String logFile = args.length > 8 ? args[8] : null;
        int maxSleep = args.length > 9 ? Integer.parseInt(args[9]) : 0;

        Client client = new Client(logFile, batchMode, verbose, maxSleep);
        client.run(host, port, serviceName, opsFile, outFile);
    }
}
