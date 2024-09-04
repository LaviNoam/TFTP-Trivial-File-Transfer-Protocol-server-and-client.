package bgu.spl.net.impl.tftp;

import java.util.ArrayList;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder
    private ArrayList<Byte> bytes = new ArrayList<Byte>();
    private int opcode = -1;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        bytes.add(nextByte);
        if ( bytes.size() == 2 && opcode == -1 ) { // opcode has not been determined yet
            opcode = convertToShort(bytes.get(0) , bytes.get(1));
            if( opcode == 6 || opcode == 10 ) {
                byte[] ans = arrayListToByteArray(bytes);
                bytes.clear();
                opcode = -1;
                return ans;
            }
        }
        else if ( opcode != -1 ) { // opcode is already known
            if ( opcode == 1 || opcode == 2 || opcode == 7 || opcode == 8){
                if(nextByte == 0) { // packet ends with zero
                    byte[] ans = arrayListToByteArray(bytes);
                    bytes.clear();
                    opcode = -1;
                    return ans;
                }
            }
            else if(opcode ==5 || opcode ==9){
                if (nextByte == (byte) 0 && bytes.size() > 3) {
                    byte[] ans = arrayListToByteArray(bytes);
                    bytes.clear();
                    opcode = -1;
                    return ans;
                }
            }
            else if ( opcode == 3) { // Data packet
                if ( bytes.size() > 4 ) {
                    int totalDataPacketSize = convertToShort(bytes.get(2),bytes.get(3));
                    if ( bytes.size() == totalDataPacketSize + 6 ){
                        byte[] ans = arrayListToByteArray(bytes);
                        bytes.clear();
                        opcode = -1;
                        totalDataPacketSize = 0;
                        return ans;
                    }
                }
            }else if ( opcode == 4 ) { // Ack packet
                if ( bytes.size() == 4 ) {
                    byte[] ans = arrayListToByteArray(bytes);
                    bytes.clear();
                    opcode = -1;
                    return ans;
                }
            }
        }
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        return message;
    }

    //added
    // convert 2-byte to int
    private int convertToShort(int firstByte, int secondByte) {
        return (short) ((short)((firstByte & 0xFF) << 8) | (short)(secondByte & 0xFF));
    }

    // convert arraylist to an array of bytes
    public static byte[] arrayListToByteArray(ArrayList<Byte> arrayList) {
        byte[] byteArray = new byte[arrayList.size()];
        for (int i = 0; i < arrayList.size(); i++) {
            byteArray[i] = arrayList.get(i);
        }
        return byteArray;
    }
}