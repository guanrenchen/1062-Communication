import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Objects;

public class MulticastClient {

    public static void main(String[] args) throws UnknownHostException {
        InetAddress addr = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);

        Boolean running = false;
        int time_out_count = 0;
        int total_count = 0;
        int loss_count = 0;

        int total_packets = 0;
        ArrayList<Integer> seriallist = new ArrayList<>();

        try (MulticastSocket ms = new MulticastSocket(port)){
            ms.joinGroup(addr);

            while(true) {
                try{
                    total_count++;

                    byte[] recvbuf = new byte[MyPacket.BUF_SIZE*2];
                    DatagramPacket dp = new DatagramPacket(recvbuf, recvbuf.length);
                    ms.receive(dp);
                    
                    
                    MyPacket recvpack = (MyPacket) MyPacket.deserialize(recvbuf);
                    if (recvpack.serial==0){
                        if (!running){
                            running = true;
                            total_packets = recvpack.finish;
                            for (int i=0; i<total_packets; ++i){
                                seriallist.add(new Integer(i));
                            }
                            seriallist.remove(new Integer(0));
                        }
                    }else if (recvpack.finish==1) {
                        seriallist.remove(new Integer(recvpack.serial));
                        break;
                    }else {
                        seriallist.remove(new Integer(recvpack.serial));
                    }

                    time_out_count = 0;

                }catch (SocketTimeoutException ex) {
                    if (!running && ++time_out_count >= MyPacket.TIME_OUT_COUNT){
                        break;
                    }
                }catch (Exception ex){
                    ex.printStackTrace();
                    System.exit(0);
                }
            }

            System.out.println("Loss rate : " + (float)seriallist.size()/total_packets + " %");

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }
    }
}

