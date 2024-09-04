package bgu.spl.net.impl.tftp;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.net.Socket;



public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) throws IOException{
        try (Socket sock = new Socket(args[0],Integer.valueOf(args[1]));
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream())){
                System.out.println("connected to server");
                ListeningHandler listenHandler =new ListeningHandler(sock,new TftpEncoderDecoder(), new TftpProtocol(), in, out);
                Thread listeningThread = new Thread(listenHandler);
                listeningThread.start();
                
                Thread keyboardThread = new Thread(new KeyboardHandler(listenHandler, out, new BufferedReader(new java.io.InputStreamReader(System.in))));
                keyboardThread.start();

                try{
                    listeningThread.join();
                    keyboardThread.interrupt();
                }
                catch (InterruptedException e) {}
                sock.close();
            }
        System.exit(0);
        }
    }

