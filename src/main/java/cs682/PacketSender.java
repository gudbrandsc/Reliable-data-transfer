package cs682;

import com.google.protobuf.ByteString;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

public class PacketSender implements Runnable{
    private static boolean running = true;
    private DatagramSocket socket;
    private UdpListener listener;
    private Chatproto.Data data;
    private int expected_ack_no;
    private int last_available;
    private String ip;
    private int port;
    private int seqNum;
    private Chatproto.History history;
    private boolean isLast;
    private boolean isFirst;
    private int start;
    private  int end;



    public PacketSender(String ip, int port, DatagramSocket socket, Chatproto.Data data, UdpListener listener){
        this.ip = ip;
        this.port = port;
        this.socket = socket;
        this.data = data;
        this.listener = listener;
        this.expected_ack_no = 0;
        this.last_available = 5;
        this.seqNum = 0;
        this.history = this.listener.getHistory();
        System.out.println("length " + this.history.toByteArray().length);
        this.isLast = false;
        this.isFirst = true;
        this.start = 0;
        this.end = 10;
    }

    //Check if the message that is recived is a new ACK and call notify to send new packets
    public  void getAck(Chatproto.Data data){
        if(data.getSeqNo() >= this.expected_ack_no){
            this.expected_ack_no = data.getSeqNo()+1;
            this.last_available += this.expected_ack_no;


            if((last_available*10)-1 > getByteArraySize()){
                setFlag();
                last_available = getByteArraySize();
            }

            ByteString b = data.toByteString();
            notify();
        }
    }
    private void setFlag(){
        this.isLast = true;
    }
    public void sendPacket(Chatproto.Data packet){
        ByteArrayOutputStream outstream = new ByteArrayOutputStream(1024);
        try {
            packet.writeDelimitedTo(outstream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] item = outstream.toByteArray();

        //send
        try {
            DatagramPacket datagramPacket = new DatagramPacket(item, item.length, InetAddress.getLocalHost(), port);
            socket.send(datagramPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private int getByteArraySize(){
        byte[] bytes = this.history.toByteArray();
        return bytes.length;
    }

    public void createPackets(){
        byte [] bytes = history.toByteArray();
        byte[] packetdata  = new byte[10];
        int counter = 0;

        for(int i = start; i <= (end-1); i++){
            packetdata[counter] = bytes[i];
            if(i==getByteArraySize()){
                isLast = true;
            }

            if((counter+1)%10 == 0){
                System.out.println("Created packet");
                Chatproto.Data packet = Chatproto.Data.newBuilder()
                        .setType(Chatproto.Data.packetType.DATA)
                        .setSeqNo(seqNum)
                        .setData(ByteString.copyFrom(packetdata))
                        .setIsLast(isLast)
                        .build();
                sendPacket(packet);
                counter = 0;
            }else{
                counter++;
            }
        }
    }

    @Override
    public void run() {
        synchronized (this) {
            while(running) {
                try {
                    wait();
                     if (isFirst){
                        setFirstMaxNum();
                    }
                    createPackets();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            finish();
        }
    }

    private void setFirstMaxNum() {
        this.isFirst=false;
        if(getByteArraySize() < 49 && isFirst){
            this.end = getByteArraySize();
        }else{
            this.end = 49;
        }


    }

    private void finish(){
        this.listener.removeReciver(ip);
    }
}
