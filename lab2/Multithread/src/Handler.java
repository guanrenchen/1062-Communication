import java.io.*; 
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.util.ArrayList;

public class Handler extends Thread {

    InetAddress addr = null;
    int port = 0;
    String filename;

    Handler(DatagramPacket dp, String filename){
        this.addr = dp.getAddress();
        this.port = dp.getPort();
        this.filename = filename;
    }

    public void run(){
        try(DatagramSocket ds = new DatagramSocket()){
            System.out.println("Port " + this.port + " assigned");

            ds.setSoTimeout(MyPacket.TIME_OUT);

            int serial = -1;
            DatagramPacket dp = null;
            Req req = null;
            int timeout_count = 0;
            MyPacket pack = null;

            byte[] sendbuf = new byte[MyPacket.BUF_SIZE*2];
            byte[] recvbuf = new byte[Req.BUF_SIZE*2];

            sendbuf = MyPacket.serialize(new MyPacket(serial, null, 0, 0));
            dp = new DatagramPacket(sendbuf, sendbuf.length, this.addr, this.port);
            ds.send(dp);

            while (true){
                try{
                    ds.receive(new DatagramPacket(recvbuf, recvbuf.length));
                    req = (Req) Req.deserialize(recvbuf);

                    timeout_count = 0;

                    if (req.serial==serial+1) {
                        serial = req.serial;
                        pack = MultithreadServer.packlist.get(serial);
                        sendbuf = MyPacket.serialize(pack);
                        dp = new DatagramPacket(sendbuf, sendbuf.length, this.addr, this.port);
                    }
                    ds.send(dp);
                    if (pack.finish==1){
                        System.out.println("Port " + dp.getPort() + " Transfer complete");
                        return;
                    }
                    
                } catch (SocketTimeoutException ex){
                    ds.send(dp);
                    if (++timeout_count > MyPacket.TIME_OUT_COUNT){
                        System.out.println("Timed out");
                        break;
                    }

                } catch (Exception ex){
                    ex.printStackTrace();
                }
                
            }

        } catch (Exception ex){
            ex.printStackTrace();
            System.exit(0);
        }
        // send file from here
    }
}