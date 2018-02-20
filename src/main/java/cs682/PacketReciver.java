package cs682;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class PacketReciver implements Runnable{
    private Chatproto.Data packet;
    private DatagramSocket socket;
    private int port;
    private String ip;
    int expected_packet;
    byte[] data;
    private int lastAddedByte;



    public PacketReciver(DatagramSocket socket,int port, String ip){
    this.socket = socket;
    this.packet = null;
    this.port = port;
    this.ip = ip;
    this.expected_packet = 0;
    this.data = new byte[1024];
    this.lastAddedByte = 0;
    }

    public void sendACK(int seq_no) throws IOException {
        System.out.println("here");
        Chatproto.Data ack = Chatproto.Data.newBuilder().setTypeValue(1).setSeqNo(seq_no).build();
        ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
        ack.writeDelimitedTo(outstream);
        byte[] item = outstream.toByteArray();
        InetAddress ipAddress = InetAddress.getByName(ip);
        //TODO Should this be mine or reciver ip and port
        DatagramPacket datagramPacket = new DatagramPacket(item, item.length, ipAddress,port);
        socket.send(datagramPacket);

    }
    public void setPacket(Chatproto.Data packet) throws IOException {
        if(packet.getSeqNo()>= expected_packet){
            this.packet = packet;
            expected_packet = packet.getSeqNo()+1;
            sendACK(packet.getSeqNo());
            notify();
        }else if (packet.getSeqNo()< expected_packet){
            sendACK(packet.getSeqNo());
        }
    }

    @Override
    public void run() {
        while(!packet.getIsLast()) {
                try {
                    wait();
                    byte[] packetData = packet.getData().toByteArray();
                    for (int i = 0; i <= packetData.length; i++){
                        data[lastAddedByte] = packetData[i];
                        this.lastAddedByte++;
                    }
                    //Add new data to bytes array
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

        }

    }
}
