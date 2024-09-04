package bgu.spl.net.impl.tftp;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.io.IOException;

public class KeyboardHandler implements Runnable {
    private final TftpProtocol protocol;
    private final TftpEncoderDecoder encdec;
    private BufferedReader keyboard;
    private BufferedOutputStream out;

    public KeyboardHandler(ListeningHandler ListenHandler, BufferedOutputStream out, BufferedReader keyboard){
        this.protocol=ListenHandler.protocol;
        this.encdec=ListenHandler.encdec;
        this.keyboard=keyboard;
        this.out=out;
    }
    @Override
    public void run() {
            while (!protocol.shouldTerminate()){
                try{
                    String input = keyboard.readLine();	
                    byte [] byteMsg = protocol.request(input);
                    if(byteMsg != null){
                        out.write((encdec.encode(byteMsg)));
                        out.flush();
                    }
                }    
            catch (IOException e) {}			
	    }
    }
}









