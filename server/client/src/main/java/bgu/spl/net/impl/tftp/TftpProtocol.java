package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TftpProtocol implements MessagingProtocol<byte[]>{
        //added
        private boolean shouldTerminate = false;
        public boolean waitDirq = false;
        public boolean waitData = false;
        public boolean waitUpload = false;
        private int blockNumCounter = 1;
        private final int maxDataSize = 512;
        private List<Byte> SavedData = new ArrayList<>();
        private byte[] fileToSend;
        private String upload = "";
        private String download = "";
        private byte[] dirqList = new byte[0];

        public byte[] process(byte[] message) {
        short opcode = convertToShort(message[0],message[1]);
        if(opcode == 3){
            if(waitDirq){
                int packetSize = convertToShort(message[2],message[3]);
                byte[] newDirqData = new byte[dirqList.length + message.length];
                System.arraycopy(message,0,newDirqData,0,message.length);
                System.arraycopy(dirqList,0,newDirqData,message.length,dirqList.length);
                dirqList = newDirqData;
                
                if(packetSize < maxDataSize){
                    printDirq(message);
                    waitDirq = false;
                    dirqList = new byte[0];
                }
                return null;
            }
            else{
                int blockNumber = convertToShort(message[4],message[5]);
                RRQ(message, blockNumber);
                byte[] ack = {0,4,convertToByte((short)blockNumber)[0],convertToByte((short)blockNumber)[1]};
                return ack;
            }
        }
        else if(opcode == 4){
            int blockNumber = convertToShort(message[2],message[3]);
            System.out.println("ACK " + blockNumber);
            blockNumCounter = blockNumber+1;
            if(waitUpload){
                return WRQ(maxDataSize*(blockNumber-1));
            }
            else{
                waitUpload = false;
                blockNumCounter = 1;
                upload = "";
                fileToSend = new byte[0];
                return null;
            }
        }
        else if(opcode == 5){
            waitDirq = false;
            if(waitUpload){
                File file = new File("." + File.separator + upload);
                file.delete();
            }
            waitUpload = false;
            blockNumCounter = 1;
            if(waitData){
                File file = new File("." + File.separator+download);
                file.delete();
            }
            waitData = false;
            download = "";
            upload = "";
            System.out.println("Error: " + new String(message, 4, message.length-4, StandardCharsets.UTF_8));
            return null;
        }
        else if(opcode ==9){            
            short deletedOrAdded = convertToShort(message[2],0);
            if(deletedOrAdded == 0)
                System.out.println("BCAST del " + new String(message, 3, message.length-3, StandardCharsets.UTF_8));
            else
                System.out.println("BCAST add " + new String(message, 3, message.length-3, StandardCharsets.UTF_8));

            return null;
        }
        else if(opcode == 10){
            shouldTerminate = true;
            return null;
        }
        return null;
    }
    
    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }   
    
    public void printDirq(byte[] message) {
        int lastIndex=6;
        while(lastIndex < message.length){
            int firstIndex = lastIndex;
            for (int i = lastIndex; i < message.length && ((short)(message[i] & 0xff) != 0); i++) {
                lastIndex = i + 1;
            }
            String fileName = new String(message, firstIndex, lastIndex-firstIndex);
            System.out.println(fileName);
            lastIndex++;
        }
    }
    public void RRQ(byte[] message, int blockNum) {
        for(int i = 6; i < message.length; i++){
			SavedData.add(message[i]);
		}  
        if(message.length < maxDataSize){ 
            try { 
                byte[] byteFile = new byte[SavedData.size()];
                for (int i = 0; i < SavedData.size(); i++) {
                    byteFile[i] = SavedData.get(i);
                }
                Files.write(Paths.get("." + File.separator,download), byteFile);
                System.out.println("RRQ " + download + " Complete");
                SavedData.clear();
                waitData = false;
                download = "";
                blockNum = 0;
            }
                catch (IOException e) {}
        }
    }
     
    public byte[] WRQ(int index){	
        Path filePath = Paths.get("." + File.separator,upload);
        if(Files.exists(filePath)){
            try {
                fileToSend = Files.readAllBytes(filePath);
                short dataSize = (short) Math.min(maxDataSize, fileToSend.length - index);
                byte[] dataPacket = new byte[dataSize + 6];
                byte[] dataSecSize = convertToByte(dataSize);
                byte[] blockNumber = convertToByte((short)blockNumCounter);
                dataPacket[0] = 0;
                dataPacket[1] = 3;
                dataPacket[2] = dataSecSize[0];
                dataPacket[3] = dataSecSize[1];
                dataPacket[4] = blockNumber[0];
                dataPacket[5] = blockNumber[1];
                System.arraycopy(fileToSend,index,dataPacket,6,dataPacket.length-6);
                index = index + dataSize;
                if(dataPacket.length < maxDataSize + 6){
                    System.out.println("WRQ "+upload+" complete"); 
                    upload = "";
                    waitUpload = false;
                    blockNumCounter = 1;
                }
                return dataPacket;
            } catch (IOException e) {}
        }
        else{
            System.out.println("Error: File not found");
        }
        return null;
    }
    public byte[] request(String message) {
        String[] splitMessage = split(message);
        byte opcode = -1;
        byte[] dataBytes = null;
        String command = splitMessage[0];
        String data;
        if(splitMessage.length==2){
                if(command.equals("RRQ")) {
                    data = splitMessage[1];
                    if(!existFile(data)){
                        dataBytes = data.getBytes(StandardCharsets.UTF_8);
                        download = data;
                        waitData = true;
                        opcode = 1;
                        File file = new File("." + File.separator + download);
                        try {
                            if (file.createNewFile()) {
                            } else {
                                System.out.println("Error: Not defined, see error message (if any).");
                            }
                            } catch (IOException e) {};
                    }
                    else{
                        System.out.println("Error: File already exists");
                    }
                }
                else if(command.equals("WRQ")){
                    data = splitMessage[1];
                    if(existFile(data)){
                        dataBytes = data.getBytes(StandardCharsets.UTF_8);
                        upload = data;
                        waitUpload =true;
                        opcode = 2;
                    }
                    else{
                        System.out.println("Error: File not found");
                    }
                }
                else if(command.equals("LOGRQ")){
                    data = splitMessage[1];
                    opcode = 7;
                    dataBytes = data.getBytes(StandardCharsets.UTF_8);
                }
                else if(command.equals("DELRQ")) {

                    data = splitMessage[1];
                    opcode = 8;
                    dataBytes = data.getBytes(StandardCharsets.UTF_8);
                }
                else System.out.println("Error: Not defined, see error message (if any).");
            } 
            else if(splitMessage.length==1){
                if(command.equals("DIRQ")) {
                    waitDirq = true;
                    opcode = 6;
                }
                else if(command.equals("DISC")) {    
                    opcode = 10;
                }
                else System.out.println("Error: Not defined, see error message (if any).");
            }
            else System.out.println("Error: Not defined, see error message (if any).");
            
            if(opcode!=-1){
            if(opcode == 1 || opcode == 2 || opcode == 7 || opcode == 8){
                int length = dataBytes.length;
                byte[] msgBytes = new byte[length + 3];
                msgBytes[0] = 0;
                msgBytes[1] = opcode;
                System.arraycopy(dataBytes, 0, msgBytes, 2, length);
                msgBytes[length + 2] = 0;
                return msgBytes;
            }
            else
            {
                byte[] msgBytes = {0,opcode};
                return msgBytes;
            }
        }
        return null;
    }   
    private boolean existFile(String fileName){
        File file = new File("." + File.separator+fileName);
        return file.exists();
    }

    public byte[] convertToByte(short a){
        return new byte []{(byte)(a >> 8) , (byte)(a & 0xff)};
    }

    private short convertToShort(int firstByte, int secondByte) {
        return (short) ((short)((firstByte & 0xFF) << 8) | (short)(secondByte & 0xFF));
    }
    public String[] split(String message) {
        String[] ans = message.split("\\s+", 2);
        return ans.length > 1 ? ans : new String[]{message};
    }
}

