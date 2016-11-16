import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.*;

/**
 *   Assignment #2
 *   Program    Title: ChatServer
 *   Author:    Lars Martin G. Risdal
 *   Class:     CSCI 3550, Fall 2016 		                     *
 *   Purpose:   Simple Chat Client and Server
 */

public class ChatServer {

    private static int argport;
    private static int isDebugging;

    private static int cid = 0;
    private static String rcvStr1 = "";
    private static String rcvStr2 = "";

    private static byte[] sendData;

    /**
     * Main run method.
     * 1. Check for the required parameters.
     * 2. Creates the stuctures used for connected clients
     * 3. Open the Datagram socket.
     * 4. Receive data
     * 5. Check if JOIN, QUIT or a normal message
     * 6. Send the message to the clients that should receive it.
     *
     * @param args The input parameters. The server requires a port number and a debug option.
     * @throws Exception
     */
    public static void main(String args[]) throws Exception {

        // Check for parameter errors. There must me 2 parameters.
        if (args.length != 2) {
            System.err.println("Usage: chatServer <port> debug_option.");
            System.exit(0);
        } else {
            argport = Integer.parseInt(args[0]);
            isDebugging = Integer.parseInt(args[1]);
        }

        // Allow for more than 516 (4+256+256) bytes to be received so the server doesn't crash if client sends more
        // than 256 bytes of message. Only the first 256 of the message will be relayed to the other clients however.
        byte[] receiveData = new byte[1024];

        // Create a hashmap for our connected clients. The CID will be the key and the IP and Port will be the values.
        // The IP-address and port number will be stored in the PortandIP utility class.
        Map<Integer, PortAndIP> clientMap = new HashMap<>();

        // Create a list for available CIDs. The server should only be able to have 10 clients connected
        // at the same time. CIDs range from 1 through 10.
        List<Integer> cidList = new LinkedList<>();
        for (int i = 1; i <= 10; i++) {
            cidList.add(i);
        }

        // Open the server socket
        DatagramSocket serverSocket = new DatagramSocket(argport);
        System.out.println("Server started on port '" + argport+"'");

        // Keep server waiting for messages
        while(true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            // Receive packet
            serverSocket.receive(receivePacket);

            // Get sender's ip address and port number
            InetAddress rcvIP = receivePacket.getAddress();
            int port = receivePacket.getPort();

            // Deserialize the receive message so we can read it.
            deserialize(receivePacket.getData());

            // Print to the server what was received
            if (isDebugging == 1) {
                System.out.println("DEBUG: Received <" + cid + " " + rcvStr1 + " " + rcvStr2 + "> ");
            }

            // Check for JOIN message
            if (rcvStr1.equals("JOIN")) {
                if (cidList.size() > 0) {
                    // Take a CID from the list of available CIDs and assign to the client.
                    cid = cidList.get(0);
                    // Remove that CID from the list of available CIDs.
                    cidList.remove(0);
                } else {
                    // Maximum number of concurrent clients reached. Exit out of operation.
                    System.err.println("ERROR: Maximum number of clients reached.");
                    continue;
                }

                // Store the ip-address and port number inside a custom object
                PortAndIP ipandport = new PortAndIP(rcvIP, port);
                // Put CID and object into a map
                clientMap.put(cid, ipandport);

                if (isDebugging == 1) {
                    System.out.println("DEBUG: Sending <" + cid + " " + rcvStr1 + " " + rcvStr2 + ">");
                }

                // Send the JOIN-ACK to all connected users, including the sender.
                for (int i : clientMap.keySet()) {
                    createMessage("JOIN");

                    DatagramPacket sendPacket =
                            new DatagramPacket(sendData, sendData.length, clientMap.get(i).getIp(),
                                    clientMap.get(i).getPort());
                    serverSocket.send(sendPacket);
                }
            }

            // Check for QUIT message
            if (rcvStr1.equals("QUIT") && cid < 0) {

                // Reverse the negative CID incoming from QUIT message, e.g. -4 to 4.
                cid = Math.abs(cid);

                if (isDebugging == 1) {
                    System.out.println("DEBUG: Sending <" + cid + " " + rcvStr1 + " " + rcvStr2 + ">");
                }

                // Send the QUIT-ACK to all connected users, including the sender.
                for (int i : clientMap.keySet()) {
                    createMessage("QUIT");

                    DatagramPacket sendPacket =
                            new DatagramPacket(sendData, sendData.length, clientMap.get(i).getIp(),
                                    clientMap.get(i).getPort());
                    serverSocket.send(sendPacket);
                }

                // Remove the client's CID from the map
                clientMap.remove(cid);
                // Add the CID back to the available CIDs and sort the list
                cidList.add(cid);
                Collections.sort(cidList);
            }

            // Check for normal message
            if (!rcvStr1.equals("QUIT") && (!rcvStr1.equals("JOIN"))) {

                if (isDebugging == 1) {
                    System.out.println("DEBUG: Sending <" + cid + " " + rcvStr1 + " " + rcvStr2 + ">");
                }

                // Relays the message to all connected clients, EXCEPT the sender.
                for (int i : clientMap.keySet()) {
                    if (cid != i) {

                        createMessage("NORMAL");

                        DatagramPacket sendPacket =
                                new DatagramPacket(sendData, sendData.length, clientMap.get(i).getIp(),
                                        clientMap.get(i).getPort());
                        serverSocket.send(sendPacket);
                    }
                }
            }
        }
    }

    /**
     * Method for creating a message and writing it to a byte array.
     *   Purpose of the method is to improve code reusability.
     *
     * @param msgType Check what type of message is being sent. If the message is a QUIT message,
     *                we reverse the negative CID, e.g. '-4' become '4'.
     * @throws Exception
     */
    public static void createMessage(String msgType) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Find the offset needed to make the strings 256 bytes.
        int str1offset = 256 - rcvStr1.getBytes().length;
        int str2offset = 256 - rcvStr2.getBytes().length;

        // Make the array 256 bytes.
        byte[] bytes = Arrays.copyOf(rcvStr1.getBytes(), rcvStr1.getBytes().length + str1offset);
        byte[] bytes2 = Arrays.copyOf(rcvStr2.getBytes(), rcvStr2.getBytes().length + str2offset);

        baos.write(intToByte(cid));
        baos.write(bytes);
        baos.write(bytes2);

        sendData = baos.toByteArray();
        baos.reset();
    }

    /**
     * Converts a bytestream into an int and two strings.
     * This method was provided by instructor.
     * @param msg The byte array to convert
     */

    public static void deserialize(byte[] msg) {

        cid = 0;
        cid |= ((int) msg[3]) << 24;
        cid |= ((int) msg[2]) << 16;
        cid |= ((int) msg[1]) << 8;
        cid |= ((int) msg[0]);

        String str1 = "";
        String str2 = "";

        for (int i = 0; i < 256 && msg[4 + i] != 0; ++i) {
            str1 += (char) msg[4 + i];
        }
        rcvStr1 = str1;
        for (int i = 0; i < 256 && msg[260 + i] != 0; ++i) {
            str2 += (char) msg[260 + i];
        }
        rcvStr2 = str2;
    }

    /**
     * Method for serializing integers (reversal of provided deserialization method)
     * @param value The value to be converted.
     * @return byte[] from the int value.
     */
    public static final byte[] intToByte(int value) {
        return new byte[] {
                (byte)value,
                (byte)(value >> 8),
                (byte)(value >> 16),
                (byte)(value >> 24),
        };
    }

    /**
     * A class to function as a pair containing an ip address and a port number.
     * Use this class to have an object representing ip and port in the map of connected clients
     */
    private static class PortAndIP {
        public PortAndIP(InetAddress ipaddress, int port) {
            this.ipaddress = ipaddress;
            this.port = port;
        }

        private InetAddress ipaddress;
        private int port;

        public InetAddress getIp() { return ipaddress; }
        public int getPort() { return port; }
    }
}
