import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GraphService extends Remote {

    public int query(int u, int v) throws RemoteException;
 
    public void addEdge(int u, int v) throws RemoteException;
 
    public void deleteEdge(int u, int v) throws RemoteException;
}