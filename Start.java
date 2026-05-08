
/**
 * Distributed Graph Service - Main Entry Point
 * 
 * Orchestrates the initialization and execution of the distributed graph processing system:
 * 1. Loads configuration from system.properties
 * 2. Starts the RMI server
 * 3. Generates workload batches
 * 4. Spawns client processes
 * 5. Cleanly shuts down the server
 */

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import server.Server;

public class Start {

    public static void main(String[] args) {
        try {
            // Step 1: Load configuration
            Properties properties = loadConfiguration();

            // Step 2: Start server
            Server server = startServer(properties);

            // Step 3: Wait for server initialization
            waitForServerStartup();

            // Step 4: Generate workload batches
            String[] batchFiles = generateWorkloads(properties);

            // Step 5: Execute clients
            executeClients(properties, batchFiles);

            // Step 6: Cleanup and shutdown
            shutdownServer(server, properties);

            System.exit(0); // Ensure clean JVM termination

        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void executeClients(Properties properties, String[] batchFiles)
            throws IOException, InterruptedException {
        String classpath = System.getProperty("java.class.path");
        String serverHost = properties.getProperty("GSP.server");
        int rmiPort = Integer.parseInt(properties.getProperty("GSP.rmiregistry.port"));
        String serviceName = properties.getProperty("GSP.serviceName");
        int numNodes = Integer.parseInt(properties.getProperty("GSP.numberOfnodes"));
        boolean batchMode = Boolean.parseBoolean(properties.getProperty("GSP.batchMode"));
        boolean verbose = Boolean.parseBoolean(properties.getProperty("GSP.client.verbose"));
        int clientSleepMs = Integer.parseInt(properties.getProperty("GSP.client.operations.sleep"));

        List<Process> processes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            String nodeHost = properties.getProperty("GSP.node" + i);
            final String batchFile = batchFiles[i];
            final String outputFile = properties.getProperty("GSP.data.directory") + "output" + i + ".txt";
            final String logFile = properties.getProperty("GSP.client.log.directory") + "log" + i + ".txt";

            List<String> cmd = new ArrayList<>();
            if (!nodeHost.equals("localhost") && !nodeHost.equals("127.0.0.1")) {
                cmd.add("ssh");
                cmd.add(nodeHost);
                // Quote the whole remote command to protect spaces (simplistic but works for
                // typical paths)
                String remoteCmd = String.format(
                        "cd \"%s\" && java -cp \"%s\" client.Client %s %d %s %s %s %b %b %s %d",
                        System.getProperty("user.dir"), classpath,
                        serverHost, rmiPort, serviceName,
                        batchFile, outputFile, batchMode, verbose,
                        logFile, clientSleepMs);
                cmd.add(remoteCmd);
            } else {
                cmd.add("java");
                cmd.add("-cp");
                cmd.add(classpath);
                cmd.add("client.Client");
                cmd.add(serverHost);
                cmd.add(String.valueOf(rmiPort));
                cmd.add(serviceName);
                cmd.add(batchFile);
                cmd.add(outputFile);
                cmd.add(String.valueOf(batchMode));
                cmd.add(String.valueOf(verbose));
                cmd.add(logFile);
                cmd.add(String.valueOf(clientSleepMs));
            }

            System.out.println("Executing client: " + String.join(" ", cmd));
            Process p = new ProcessBuilder(cmd).inheritIO().start();
            processes.add(p);
        }

        int clientTimeoutSeconds = Integer.parseInt(properties.getProperty("GSP.client.timeout.seconds"));
        for (Process p : processes) {
            boolean finished = p.waitFor(clientTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                System.err.println("Client process " + p.pid() + " timed out, destroying it.");
                p.destroyForcibly();
            }
        }
    }

    /**
     * Loads configuration from system.properties file
     */
    private static Properties loadConfiguration() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = new FileInputStream("system.properties")) {
            properties.load(in);
        } catch (Exception e) {
            System.err.println("Failed to load properties: " + e.getMessage());
            throw e;
        }
        return properties;
    }

    /**
     * Starts the RMI server in a background thread
     */
    private static Server startServer(Properties properties) throws InterruptedException {
        String serverHost = properties.getProperty("GSP.server");
        int serverPort = Integer.parseInt(properties.getProperty("GSP.server.port"));
        int rmiPort = Integer.parseInt(properties.getProperty("GSP.rmiregistry.port"));
        String graphFile = properties.getProperty("GSP.graph.file");
        String serviceName = properties.getProperty("GSP.serviceName");
        String serverLogFile = properties.getProperty("GSP.server.log.directory") + "server-log.txt";
        boolean serverVerbose = Boolean.parseBoolean(properties.getProperty("GSP.server.verbose"));
        boolean precomputeMode = Boolean.parseBoolean(properties.getProperty("GSP.precomputeMode"));
        int serverDelayMs = Integer.parseInt(properties.getProperty("GSP.server.operations.sleep"));

        Server server = new Server("server1", serverLogFile, serverVerbose, serverDelayMs);

        Thread serverThread = new Thread(() -> {
            try {
                server.run(graphFile, serverHost, serverPort, rmiPort, serviceName, precomputeMode);
            } catch (Exception e) {
                System.err.println("Failed to start server thread: " + e.getMessage());
            }
        }, "server-thread");

        serverThread.setDaemon(false); // Server should block until explicitly stopped
        serverThread.start();

        return server;
    }

    /**
     * Waits for server to fully initialize
     */
    private static void waitForServerStartup() {
        try {
            Thread.sleep(2000); // Give server time to start and register RMI service
        } catch (InterruptedException e) {
            System.err.println("Failed to sleep: " + e.getMessage());
        }
    }

    /**
     * Generates workload batch files for clients
     */
    private static String[] generateWorkloads(Properties properties) {
        String graphFile = properties.getProperty("GSP.graph.file");
        int numberOfClients = Integer.parseInt(properties.getProperty("GSP.numberOfnodes"));
        int opsPerBatch = Integer.parseInt(properties.getProperty("GSP.operations.per.batch"));
        int writePercent = Integer.parseInt(properties.getProperty("GSP.writePercent"));
        String outputDir = properties.getProperty("GSP.data.directory");

        WorkloadGenerator gen = new WorkloadGenerator(graphFile);
        String[] batchFiles = new String[numberOfClients];
        for (int i = 0; i < numberOfClients; i++) {
            String batchFile = outputDir + "batch_inputs" + i + ".txt";
            batchFiles[i] = batchFile;
            gen.generateBatch(batchFile, opsPerBatch, writePercent);
        }

        return batchFiles;
    }

    /**
     * Gracefully shuts down the server
     */
    private static void shutdownServer(Server server, Properties properties) throws InterruptedException {
        int rmiPort = Integer.parseInt(properties.getProperty("GSP.rmiregistry.port"));
        String serviceName = properties.getProperty("GSP.serviceName");

        server.stop(rmiPort, serviceName);
    }

}
