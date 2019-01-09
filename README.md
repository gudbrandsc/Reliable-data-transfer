Reliable Data Transfer
==================================

### Demonstration Due - Thursday, Feb 22 - start of class
### Code Due - Friday, Feb 23 - 5PM

For this project you will extend your Project 1 Chat application to include a UDP client and server to support reliable bulk download of the `History` data structure of another chat peer in the network. You will use the following technologies:

- UDP Sockets

You must follow the specifications below *exactly* as a large part of your grade will depend upon whether your solution is able to communicate with solutions implemented by other students. We will have an in-class demo session where everyone will execute their applications!

### Application Interface

You will extend your application interface to support the following functionality:

**Download history** - The user may request to download the entire broadcast chat history from another peer. If requested, you will send a UDP request to another peer in the network and implement a reliable download protocol (as described below) to receive the history and store it locally.

It is up to you to determine how to select the server from which the data will be requested.

After the new history is retrieved you will replace any previous history data structure with the new data.

You may optionally add the functionality to fetch the existing history during your peer's startup process.

**Show history** - The user must be able to display the contents of its current history data structure.

### Discovery

Each peer will need to identify the UDP port on which the history download *service* is running.

You will extend your use ZooKeeper for this purpose. You will modify the `ZKData` message to also specify the UDP port as follows:

```
syntax = "proto3";

message ZKData {
    string ip = 1;
    string port = 2;
    string udpport = 3;
}
```


### Messaging

Recall that UDP is *connectionless*. This means that you will use **one** UDP socket on each host, and this socket will be used for *both sending and receiving messages*.

A host may concurrently be acting as a receiver, for a download request it initiated, or a sender, for one or more requests it has received. Each time a new packet is received you will perform a demultiplexing operation to determine how to handle the incoming packet.

A packet is specified as follows:

```
message Data {
    packetType type = 1;
    int32 seq_no = 2;
    bytes data = 3;
    bool is_last = 4;

    enum packetType {
        REQUEST = 0;
        ACK = 1;
        DATA = 2;
    }
}
```

**REQUEST** - A message with type REQUEST is the initial message that will begin a new download operation. You will need to get the IP and port of the requester from the `DatagramPacket` and set up internal state to keep track of the in-progress download. The reply to this message will be the first data packet. No fields other than `type` will be relevant for this message.

**ACK** - A message with type ACK will acknowledge a correctly received data packet using the `seq_no` field. You will need to get the IP and port of the requester from the packet; update the sending window for that download as appropriate; and possibly send additional data packets. 

**DATA** - A message with type DATA is a new data packet with sequence number `seq_no`. If this is the last data packet `is_last` will be `true`. As described below, if the `seq_no` is the expected sequence number the `data` will be saved and an ACK will be sent in reply. If the data is out of order it will be discarded.

### Data Format and Framing

A UDP sender will send the history data in the following format:

```
message History {
    repeated Chat history = 1;
}
```

The data, however, will be sent in frames of 10 bytes. You will create a byte array of the entire structure and send 10 bytes in each `Packet` as defined above.

The last packet may have fewer than 10 bytes if the entire length of the array is not a multiple of 10. It should be flagged with `is_last = true`.

### Reliability

You will implement a Go-Back-N algorithm for ensuring reliable delivery of all data. 

You will use a window size of four packets.

The general algorithm for the receiver is as follows:

```
initiate download with REQUEST packet
until all DATA packets correctly received
	read next packet
	if seq_no is the expected sequence number
		save data
		send ACK for seq_no 
		increment expected sequence number
```

The general algorithm for the sender is as follows:

```
on REQUEST received
	initialize state for download
	send window of data
	start timer

on timer expiration
	resend outstanding window of data
	start timer

on ACK received
	if seq_no >= base outstanding seq_no
		stop timer
		slide window
		send new packet(s)
		start timer
```

### Application Execution

- Your main method must be in a class `cs682.Chat`. 
- Your program will take three command line arguments:
  * `-user <username>` - The username that will be used for your client.
  * `-port <port>` - The port on which your server will listen.
  * `-udpport <port>` - The UDP port on which your download service will listen.

Your program *must* run exactly as follows, where name, 9900, and 9901 will be replaced with appropriate values:
```
java -cp project2.jar cs682.Chat -user name -port 9900 -udpport 9901
```


