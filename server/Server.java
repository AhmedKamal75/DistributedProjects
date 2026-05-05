import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

// rmiregistry 8080
// javac *.java
// java Server

public class Server {
    public static void main(String[] args) {
        try {
            System.setProperty("java.rmi.server.hostname", "localhost");

            GraphEngine engine = GraphEngine.loadFromFile("../data/intial_graph.txt");
            if (engine == null) {
                System.out.println("Failed to load graph from file");
                return;
            }


            GraphService stub = (GraphService) UnicastRemoteObject.exportObject(engine, 0);
            Registry registry = LocateRegistry.getRegistry("localhost", 8080);
            registry.bind("GraphEngine", stub);
            System.out.print("R\n");  // as required

        } catch (Exception e) {
            System.err.println("GraphEngine server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
