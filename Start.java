
// compile: javac server/*.java client/*.java *.java
// run: java Start

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Start {
    public static void main(String[] args) {
        Properties properties = new Properties();
        try(InputStream in = new FileInputStream("system.properties")) {
            properties.load(in);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String serverHost = properties.getProperty("GSP.server");
        int serverPort = Integer.parseInt(properties.getProperty("GSP.server.port"));
        int numberOfNodes = Integer.parseInt(properties.getProperty("GSP.numberOfnodes"));
        int rmiPort = Integer.parseInt(properties.getProperty("GSP.rmiregistry.port"));

        List<String> clientHosts = new ArrayList<>();
        for (int i = 0; i < numberOfNodes; i++) {
            clientHosts.add(properties.getProperty("GSP.node" + i));
        }

        // TODO 2: start the server,and start the clients


        // TODO 3: add performace measurement


    }
    
}
