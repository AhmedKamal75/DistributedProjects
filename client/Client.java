import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.List;

// directory structure
// DistributedProject
// |-- client
//     |-- Client.java
//     |-- GraphService.java
//     |-- Operation.java
// |-- server
//     |-- GraphEngine.java
//     |-- GraphService.java
//     |-- Server.java

// javac *.java
// java Client

public class Client {
    public static void main(String[] args) {
        try {
            GraphService stub = loadStup("localhost", 8080, "GraphEngine");
            List<Operation> operations = loadOperations("../data/patch_queries.txt");

            for (Operation op : operations) {
                switch (op.operationType()) {
                    case 'Q':
                        System.out.println(stub.query(op.u(), op.v()));
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
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static GraphService loadStup(String hostname, int port, String stubName) throws Exception {
        Registry registry = LocateRegistry.getRegistry(hostname, port);
        return (GraphService) registry.lookup(stubName);
    }

    private static List<Operation> loadOperations(String filepath) {
        List<Operation> operations = new ArrayList<>();
        try (BufferedReader bf = new BufferedReader(new FileReader(new File(filepath)))) {
            String line;
            while ((line = bf.readLine()) != null) {
                if (line.contains("F")) {
                    return operations;
                }
                String[] tokens = line.trim().split("\\s+");
                operations.add(
                        new Operation(
                                tokens[0].trim().charAt(0),
                                Integer.parseInt(tokens[1].trim()),
                                Integer.parseInt(tokens[2].trim())));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
