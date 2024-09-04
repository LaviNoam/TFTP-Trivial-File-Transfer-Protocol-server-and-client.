package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

// added
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.io.FileInputStream;
import java.io.FileOutputStream;


class holder{
    static final ConcurrentHashMap<Integer,String> users = new ConcurrentHashMap<>();
    static ArrayList<File> files = new ArrayList<File>();
    static FileInputStream fileIn;
    static FileOutputStream fileOut;
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    private String path;
    private short blockNumCounter = 1;
    private final int maxDataSize = 512;
    private boolean sending = false;
    private boolean listing = false;
    private File fileWip;
    private byte[] dirqArr;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        this.connectionId = connectionId;
        this.connections = connections;
        path = System.getProperty("user.dir") + "/Flies";
        holder.users.put(connectionId,"");
        File dir = new File(path);
        File[] listFiles = dir.listFiles();
        if ( listFiles != null ) {
            for (File file : listFiles) {
                if (file.isFile()) { 
                    holder.files.add(file);
                }
            }
        }
    }

    @Override
    public void process(byte[] message) {
        // TODO implement this
        short opcode = convertToShort(message[0],message[1]);
        if ( opcode == 7){
            logrq(message);
        }
        else if ( holder.users.get(connectionId) == "" ) { //user not logged in
            sendErr((short)6,"User not logged in");
        }
        else{
            if ( opcode == 1){
                rrq(message);
            } else if ( opcode ==  2 ) {
                wrq(message);
            } else if ( opcode ==  3 ) {
                data(message);
            } else if ( opcode ==  4 ) {
                ack(message);
            } else if ( opcode ==  6 ) {
                dirq();
            } else if ( opcode ==  8 ) {
                delrq(message);
            } else if ( opcode ==  10 ) {
                disc(message);
            }
        }
    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        if(shouldTerminate){
            this.connections.disconnect(connectionId);
            holder.users.remove(connectionId);
        }
        return shouldTerminate;
    } 

    // added
    private void rrq(byte[] message){
        String filename  = new String(message,2,message.length-2, StandardCharsets.UTF_8).trim();
        fileWip = new File(path + "/" + filename);
        if ( !fileWip.exists() ) { // file doesn't exist
            sendErr((short) 1, "File not found");
        }else{
            try { 
                holder.fileIn = new FileInputStream(fileWip);
                blockNumCounter = 1;
                byte[] dataBlock = new byte[Math.min((int)fileWip.length(),maxDataSize)];
                holder.fileIn.read(dataBlock);
                byte[] dataPacket = sendData( dataBlock,blockNumCounter,dataBlock.length );
                connections.send(connectionId, dataPacket);
                if ( dataBlock.length < maxDataSize ){ // if block size smaller than 512 than done
                    sending = false;
                    blockNumCounter = 1;
                    holder.fileIn.close();
                    fileWip = null;
                } else {
                    sending = true;
                }
            }
            catch ( IOException e ){}
        }
    }

    private void wrq(byte[] message){
        String filename  = new String(message,2,message.length-2, StandardCharsets.UTF_8).trim();
        fileWip = new File(path + "/" + filename);
        if ( fileWip.exists() ) { // file exists
            sendErr((short) 1, "File not found");
        }else{
            try {    
                if ( fileWip.createNewFile() ){
                    holder.fileOut = new FileOutputStream(fileWip);
                    sendAck((short) 1);
                } else {
                    // Failed to create the file, send an appropriate error
                    sendErr((short) 1, "Failed to create file.");
                }    
            }catch ( IOException e ){}
        }
    }

    private void data(byte[] message){
        short packetSize = convertToShort(message[2],message[3]);
        short blockNum = convertToShort(message[4],message[5]);
        byte[] data = new byte[packetSize];
        System.arraycopy(message, 6, data, 0, packetSize);
        try {
            holder.fileOut.write(data);
            if ( packetSize < maxDataSize ) { // if block size smaller than 512 than done 
                blockNumCounter = 1;
                holder.fileOut.close();
                System.out.println("WRQ " + fileWip.getName() + " complete");
                sendBcast(fileWip.getName(),(byte) 1);
                holder.files.add(fileWip);
                sendAck((short) 0);
                fileWip = null;
            }else {
                sendAck(blockNum);
                blockNumCounter=blockNum++;
            }
        } catch (IOException e) { }
    }

    private void continueRrq(){
        try { 
                blockNumCounter++;
                long pos = holder.fileIn.getChannel().position(); // number of bytes read from the file so far
                byte[] dataBlock = new byte[Math.min((int)(fileWip.length()-pos),maxDataSize)]; // how many more bytes left to read 
                holder.fileIn.read(dataBlock);
                byte[] dataPacket = sendData( dataBlock,blockNumCounter,dataBlock.length);
                connections.send(connectionId, dataPacket);
                if ( dataBlock.length < maxDataSize ){ // if block size smaller than 512 than done sending
                    sending = false;
                    blockNumCounter = 1;
                    holder.fileIn.close();
                    System.out.println("RRQ " + fileWip.getName() + " complete");
                    fileWip = null;
                }
            }
        catch ( IOException e ){}
    }

    private void ack(byte[] message){
        short blockNum = convertToShort(message[2],message[3]);
        if( blockNum == 0 ){
            System.out.println("ACK 0");
        }
        else {
            if ( sending == true ){
                continueRrq();
            }
            if ( listing == true ){
                if ( dirqArr.length > maxDataSize*blockNumCounter){
                    connections.send(connectionId, continuedirq(dirqArr, maxDataSize*(blockNumCounter-1)));
                }else{ //done
                    listing = false;
                    blockNumCounter = 1;
                    dirqArr = null;
                }
            }
        }
    }

    private void delrq(byte[] message) {
        String filename  = new String(message,2,message.length-2, StandardCharsets.UTF_8).trim();
        File file = new File(path + "/" + filename);
        if (file.exists()) {
            if (file.delete()){ // file was deleted
                holder.files.remove(file);
                sendBcast(filename,(byte) 0);
                sendAck((short)0);
            }else {
                sendErr((short) 2, "File cannot be deleted");
            }
        } else {
            sendErr((short) 1, "File not found");
        }
    }

    private void dirq(){
        if (holder.files != null) {
            String filesStr = new String();
            for (File file: holder.files) {
                filesStr += file.getName() + '\0';
            }
            blockNumCounter = 1;
            byte[] dirqArr = filesStr.getBytes();
            byte[] packet = continuedirq(dirqArr, 0);
            connections.send(connectionId, packet);
            if ( ! ( dirqArr.length < maxDataSize ) ){
                listing = true;
                blockNumCounter++;
            }else{ //done
                listing = false;
                blockNumCounter = 1;
            }
        }
    }

    private byte[] continuedirq(byte[] data, int pos){
        int packetSize = Math.min(data.length - pos, maxDataSize);
        ArrayList<Byte> dataArr = new ArrayList<Byte>();
        // opcode
        dataArr.add((byte)0);
        dataArr.add((byte)3);
        byte[] size = convertToByte((short)packetSize);
        // packet size
        dataArr.add(size[0]);
        dataArr.add(size[1]);
        byte[] block = convertToByte((short)blockNumCounter);
        // block number
        dataArr.add(block[0]);
        dataArr.add(block[1]);
        // actual data
        for ( byte b: data){
            dataArr.add(b);
        }
        byte[] packet = arrayListToByteArray(dataArr);
        blockNumCounter++;
        return packet;
    }

    private void logrq(byte[] message){
        String username  = new String(message,2,message.length-2, StandardCharsets.UTF_8).trim();
        if( holder.users.containsValue(username) ){ //user already logged in
            sendErr((short)7, "User already logged in");
        }else {
            holder.users.put(connectionId,username);
            sendAck((short)0);
        }
    }

    public void sendAck(short blockNum) {
        ArrayList<Byte> ackArr = new ArrayList<Byte>();
        // opcode
        ackArr.add((byte)0);
        ackArr.add((byte)4);
        byte[] block = convertToByte(blockNum);
        // block number
        ackArr.add(block[0]);
        ackArr.add(block[1]);
        byte[] packet = arrayListToByteArray(ackArr);
        connections.send(connectionId, packet);
    }

    private void sendErr(short code, String err){
        ArrayList<Byte> errArr = new ArrayList<Byte>();
        // opcode
        errArr.add((byte)0);
        errArr.add((byte)5);
        byte[] errCode = convertToByte(code);
        // error code
        errArr.add(errCode[0]);
        errArr.add(errCode[1]);
        // error msg
        for ( byte b: (err).getBytes(StandardCharsets.UTF_8)){
            errArr.add(b);
        }
        errArr.add((byte)0);
        byte[] packet = arrayListToByteArray(errArr);
        connections.send(connectionId, packet);
    }

    private byte[] sendData( byte[] data, short blockNum, int packetSize ) {
        ArrayList<Byte> dataArr = new ArrayList<Byte>();
        // opcode
        dataArr.add((byte)0);
        dataArr.add((byte)3);
        byte[] size = convertToByte((short)packetSize);
        // packet size
        dataArr.add(size[0]);
        dataArr.add(size[1]);
        byte[] block = convertToByte(blockNum);
        // block number
        dataArr.add(block[0]);
        dataArr.add(block[1]);
        // actual data
        for ( byte b: data){
            dataArr.add(b);
        }
        byte[] packet = arrayListToByteArray(dataArr);
        return packet;
    }

    // convert arraylist to an array of bytes
    public static byte[] arrayListToByteArray(ArrayList<Byte> arrayList) {
        byte[] byteArray = new byte[arrayList.size()];
        for (int i = 0; i < arrayList.size(); i++) {
            byteArray[i] = arrayList.get(i);
        }
        return byteArray;
    }

    private void disc(byte[] message){
        this.shouldTerminate = true;
        sendAck((byte)0);
    }

    private void sendBcast(String filename,byte fileStatus) {
        ArrayList<Byte> bcastArr = new ArrayList<Byte>();
        bcastArr.add((byte)0);
        bcastArr.add((byte)9);
        bcastArr.add((byte)(fileStatus));
        byte[] filenambytes = filename.getBytes(StandardCharsets.UTF_8);
        for ( byte b: filenambytes){
            bcastArr.add(b);
        }
        bcastArr.add((byte)0);
        byte[] packet = arrayListToByteArray(bcastArr);
        holder.users.forEach((key, value) -> {
            if ( value != "" ){
                this.connections.send(key,packet);
            }
        });
    }

    private short convertToShort(int firstByte, int secondByte) {
        return (short) ((short)((firstByte & 0xFF) << 8) | (short)(secondByte & 0xFF));
    }

    public byte[] convertToByte(short a){
        return new byte []{(byte)(a >> 8) , (byte)(a & 0xff)};
    }

}
