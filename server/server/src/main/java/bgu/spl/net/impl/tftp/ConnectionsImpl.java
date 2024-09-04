package bgu.spl.net.impl.tftp;
//added
import java.util.concurrent.ConcurrentHashMap;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class ConnectionsImpl<T> implements Connections <T> {
    public ConcurrentHashMap <Integer,ConnectionHandler<T>> connectMap = new ConcurrentHashMap<>();
    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        connectMap.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
       if(connectMap.containsKey(connectionId)){
        connectMap.get(connectionId).send(msg);
        return true;
       }
       else return false;
    }

    @Override
    public void disconnect(int connectionId) {
        connectMap.remove(connectionId);
    }

    //added
    public void broadcast (T msg){
		for (int i =0 ;i<connectMap.size();i++) {
			connectMap.get(i).send(msg);
		}
	}
}












    

