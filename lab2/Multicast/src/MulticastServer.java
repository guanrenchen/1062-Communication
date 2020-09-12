import java.io.*; 
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.util.ArrayList;

public class MulticastServer {

    public static ArrayList<MyPacket> packlist;

    public static void main(String[] args) throws UnknownHostException, InterruptedException {
        
        InetAddress addr = null;
        int port = 0;
        File file = null;
        FileInputStream fis = null;
        String filename = "";
        try {
            addr = InetAddress.getByName(args[0]);
            port = Integer.parseInt(args[1]);
            file = new File(args[2]);
            filename = file.getName();
            fis = new FileInputStream(file);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }

        int i=0;
        byte[] data = null;
        packlist = new ArrayList<>();
        data = filename.getBytes();
        packlist.add(new MyPacket(i++, data, data.length, 0));
        try{
            while (true) {
                data = new byte[MyPacket.BUF_SIZE];
                int data_len = fis.read(data, 0, data.length);
                if (data_len < 0) break;
                packlist.add(new MyPacket(i++, data, data_len, 0));
            }
            packlist.add(new MyPacket(i++, null, 0, 1));

            fis.close();
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }
        packlist.get(0).finish = packlist.size();


        try(DatagramSocket hostSocket = new DatagramSocket())
        {
            for (i=0; i<packlist.size(); ++i){
                byte[] sendbuf = MyPacket.serialize(packlist.get(i));
                hostSocket.send(new DatagramPacket(sendbuf, sendbuf.length, addr, port));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    
}