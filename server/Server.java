package server;

import java.io.File;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import shared.GraphService;

// rmiregistry 8080
// javac *.java
// java Server

public class Server {
    private GraphEngine engine;
    private String serverName;
    private String logFilePath;
    private boolean verbose;
    private int simulatedDelayMs;

    public Server() {
        this("server", null, true, 0);
    }

    public Server(String serverName, String logFilePath, boolean verbose, int simulatedDelayMs) {
        this.serverName = serverName;
        this.verbose = verbose;
        this.simulatedDelayMs = simulatedDelayMs;

        if (logFilePath == null) {
            this.logFilePath = "log/" + this.serverName + "_" + "logs.txt";
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
                // create file if it doesn't exist
                logFile.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Runs the server
     * 
     * @param graphFile      the file containing the graph
     * @param hostname       the address of the server on which the stub (service
     *                       provider) is running
     * @param port           the port on which the server is running
     * @param rmiPort        the port on which the rmiregistry is running
     * @param serviceName    the name of the stub on the rmiregistry
     * @param BidirectionalMode true if bidirectional mode is enabled, false for vanilla BFS
     */
    public void run(String graphFile, String hostname, int port, int rmiPort, String serviceName,
            boolean BidirectionalMode) {
        try {
            // the server on which the stup is running
            System.setProperty("java.rmi.server.hostname", hostname);

            this.engine = new GraphEngine(graphFile, this.logFilePath, this.verbose, this.simulatedDelayMs);
            this.engine.setBidirectionalMode(BidirectionalMode);

            // this server stup is registered with rmiregistry on port serverPort.
            GraphService stub = (GraphService) UnicastRemoteObject.exportObject(this.engine, port);

            // when client connects to this server, it will use rmiregistry on port rmiPort
            // to lookup the stub and the rmi return the stub at port serverPort
            Registry registry = LocateRegistry.createRegistry(rmiPort);

            registry.bind(serviceName, stub);

            // shutdown hook for rmiregistry to unregister the stub
            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> {
                        try {
                            LocateRegistry.getRegistry(rmiPort).unbind(serviceName);
                            System.out.println("GraphEngine server [" + serverName + "] stopped");
                        } catch (Exception ignoredException) {
                        }
                    }));

            System.out.print("R\n"); // as required

            while (true) {
                Thread.sleep(Long.MAX_VALUE);
            }

        } catch (Exception e) {
            System.err.println("GraphEngine server [" + this.serverName + "] exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop(int rmiPort, String serviceName) {
        try {
            Registry registry = LocateRegistry.getRegistry(rmiPort);
            registry.unbind(serviceName);
            UnicastRemoteObject.unexportObject(this.engine, true);

            System.out.println("GraphEngine server [" + this.serverName + "] stopped");
        } catch (Exception e) {
            System.err.println(
                    "GraphEngine server [" + this.serverName + "] exception during shutdown: " + e.getMessage());
        }
    }
}
