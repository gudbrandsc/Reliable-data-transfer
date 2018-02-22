package cs682;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketReciver implements Runnable{
    private boolean running = true;
    private DatagramSocket socket;
    private int expected_packet;
    private ConcurrentLinkedQueue<Chatproto.Data> packetList;
    private HistoryData historyData;
    private UdpHandler udpHandler;
    private Chatproto.ZKData zkData;




    public PacketReciver(DatagramSocket socket, Chatproto.ZKData zkData, HistoryData historyData, UdpHandler udpHandler){
        this.socket = socket;
        this.zkData = zkData;
        this.expected_packet = 1;
        this.historyData = historyData;
        this.udpHandler = udpHandler;
        this.packetList =  new ConcurrentLinkedQueue<>();
    }
    public void sendRequest() throws IOException {
        Chatproto.Data req = Chatproto.Data.newBuilder().setTypeValue(0).build();
        ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
        req.writeDelimitedTo(outstream);

        byte[] item = outstream.toByteArray();
        InetAddress ipAddress = InetAddress.getByName(zkData.getIp());
        int port = Integer.parseInt(zkData.getUdpport());

        DatagramPacket datagramPacket = new DatagramPacket(item, item.length, ipAddress, port);
        socket.send(datagramPacket);

    }


    public void sendACK(int seq_no) throws IOException {
        Random r = new Random();

        float chance = r.nextFloat();

        if (chance <= 0.20f){
        }else {
            Chatproto.Data ack = Chatproto.Data.newBuilder().setTypeValue(1).setSeqNo(seq_no).build();
            ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
            ack.writeDelimitedTo(outstream);

            byte[] item = outstream.toByteArray();
            InetAddress ipAddress = InetAddress.getByName(zkData.getIp());
            int port = Integer.parseInt(zkData.getUdpport());
            //TODO Should this be mine or reciver ip and port

            DatagramPacket datagramPacket = new DatagramPacket(item, item.length, ipAddress, port);
            socket.send(datagramPacket);
        }
    }

    public void addPacket(Chatproto.Data data){
        synchronized (this) {
            try {
                sendACK(data.getSeqNo());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(this.expected_packet == data.getSeqNo()){
                this.packetList.add(data);
                this.notify();
            }
        }
    }


    @Override
    public void run() {
        try {
            sendRequest();
        } catch (IOException e) {
            e.printStackTrace();
        }
        synchronized (this) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            while (running) {
                try {
                    wait(2000);
                    if(!packetList.isEmpty()){
                        for (Chatproto.Data packet : packetList) {
                            if (packet.getSeqNo() == expected_packet) {
                                byte[] dataArray = packet.getData().toByteArray();
                                outputStream.write(dataArray,0,dataArray.length);

                                if (packet.getIsLast()) {
                                    Chatproto.History newHistory = Chatproto.History.parseFrom(outputStream.toByteArray());
                                    historyData.updateHistory(newHistory.getHistoryList());
                                    finish();
                                    System.out.println("List was updated");
                                }
                                expected_packet++;
                            }
                            packetList.remove(packet);
                        }
                    }else{
                        finish();
                        System.out.println("Session timed out");
                    }
                    //Add new data to bytes array
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("Run done");
    }
    private void finish(){
        running = false;
        String key = zkData.getIp()+zkData.getUdpport();
        udpHandler.removeReceiver(key);
    }

}
