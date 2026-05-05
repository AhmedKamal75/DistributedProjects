import java.io.File;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

// rmiregistry 8080
// javac *.java
// java Server

public class Server {
    private String serverName;
    private String logFilePath;
    private boolean verbose;

    public Server() {
        this("server", "../log/server-logs.txt", false);
    }

    public Server(String serverName, String logFilePath, boolean verbose) {
        this.serverName = serverName;
        this.logFilePath = logFilePath;
        this.verbose = verbose;

        if (logFilePath != null) {
            this.logFilePath = "log/" + this.serverName + "_" + "logs.txt";
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

    public static void main(String[] args) {
        Server server = new Server();
        server.run("../data/intial_graph.txt", 0, 8080, "GraphEngine", "localhost");
    }


    /**
     * Runs the server
     * @param filename the file containing the graph
     * @param serverPort the port on which the server is running
     * @param rmiPort the port on which the rmiregistry is running
     * @param stubName the name of the stub
     * @param serverAddress the address of the server
     */
    public void run(String filename, int serverPort, int rmiPort, String stubName, String serverAddress) {
        try {
            // the server on which the stup is running
            System.setProperty("java.rmi.server.hostname", serverAddress);

            GraphEngine engine = GraphEngine.loadFromFile(filename);
            if (engine == null) {
                System.out.println("Failed to load graph from file");
                return;
            }

            // this server stup is registered with rmiregistry on port serverPort.
            GraphService stub = (GraphService) UnicastRemoteObject.exportObject(engine, serverPort);
            // when client connects to this server, it will use rmiregistry on port rmiPort 
            // to lookup the stub and the rmi return the stub at port serverPort
            // Registry registry = LocateRegistry.getRegistry("localhost", rmiPort);
            Registry registry = LocateRegistry.createRegistry(rmiPort);
            registry.bind(stubName, stub);
            System.out.print("R\n");  // as required

        } catch (Exception e) {
            System.err.println("GraphEngine server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    // private void logger() {
        // if (verbose){
            // TODO 4: implement later & figure out the format

        // }
    // }
}
