package bgu.spl.net.impl.tftp;
import java.io.IOException;
import java.net.Socket;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;


public class ListeningHandler implements Runnable {

public final TftpProtocol protocol;
public final TftpEncoderDecoder encdec;
public BufferedInputStream in;
public BufferedOutputStream out;
private Socket sock;
    
public ListeningHandler(Socket sock,TftpEncoderDecoder encdec, TftpProtocol protocol, BufferedInputStream in, BufferedOutputStream out) {
        this.encdec = encdec;
        this.protocol = protocol;
        this.in = in;
        this.out = out;
    }
public void run() {
        int read;
        try (Socket sock = this.sock) { //just for automatic closing
            while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                byte[] serverResponse = encdec.decodeNextByte((byte) read);
                if (serverResponse != null) {
                    if(protocol.waitUpload || protocol.waitData || protocol.waitDirq){
                        byte[] msg=protocol.process(serverResponse);
                        try{
                            if (msg != null) {
                                out.write(encdec.encode(msg));
                                out.flush();
                            }
                        }catch (IOException e){}
                    }
                    else{
                        protocol.process(serverResponse);
                    }
                }
            }
        } catch (IOException e) {}
    }
}