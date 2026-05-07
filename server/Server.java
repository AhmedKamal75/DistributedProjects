package server;

import java.io.File;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import client.Client;
import shared.GraphService;

// rmiregistry 8080
// javac *.java
// java Server

public class Server {
    private GraphEngine engine;
    private String serverName;
    private String logFilePath;
    private boolean verbose;

    public Server() {
        this("server", null, true);
    }

    public Server(String serverName, String logFilePath, boolean verbose) {
        this.serverName = serverName;
        this.verbose = verbose;

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
     * @param graphFile   the file containing the graph
     * @param hostname    the address of the server on which the stub (service
     *                    provider) is running
     * @param port        the port on which the server is running
     * @param rmiPort     the port on which the rmiregistry is running
     * @param serviceName the name of the stub on the rmiregistry
     */
    public void run(String graphFile, String hostname, int port, int rmiPort, String serviceName) {
        try {
            // the server on which the stup is running
            System.setProperty("java.rmi.server.hostname", hostname);

            this.engine = new GraphEngine(graphFile, this.logFilePath, this.verbose);

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


    public static void main(String[] args) {
        if (args.length < 6) {
            System.err.println(
                "Usage: Client <clientName> <serverHost> <rmiPort> <serviceName> <opsFile> <outFile> [batchMode] [verbose] [logFilePath]");
            System.exit(1);
        }
        
        String clientName = args[0];
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        String serviceName = args[3];
        String opsFile = args[4];
        String outFile = args[5];
        boolean batchMode = args.length > 6 ? Boolean.parseBoolean(args[6]) : true;
        boolean verbose = args.length > 7 ? Boolean.parseBoolean(args[7]) : true;
        String logFile = args.length > 8 ? args[8] : null;

        Client client = new Client(clientName, logFile, batchMode, verbose);
        client.run(host, port, serviceName, opsFile, outFile);
    }

    // public static void main(String[] args) {
    //     Server server = new Server("server", "../log/server_logs.csv", true);
    //     server.run("../data/intial_graph.txt", "localhost", 0, 8080, "GraphEngine");
    // }

}
