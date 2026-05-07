package shared;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GraphService extends Remote {

    public record Operation(char operationType, int u, int v) implements Serializable {}
    public record Pair(int x, int y) {}


    public int query(int u, int v) throws RemoteException;
 
    public void addEdge(int u, int v) throws RemoteException;
 
    public void deleteEdge(int u, int v) throws RemoteException;

    Integer[] processBatch(Operation[] operations) throws RemoteException; // returns only Q answers in order

    public void setSimulatedDelayMs(int simulatedDelayMs) throws RemoteException;
}