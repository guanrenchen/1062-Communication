import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Objects;

public class MultithreadClient {

    public static void main(String[] args) throws UnknownHostException {
        InetAddress addr = InetAddress.getByName(args[0]);
        int port = Integer.parseInt(args[1]);
        String path = args[2];

        File file = null;
        FileOutputStream fos = null;

        MyPacket recvpack;
        byte[] recvbuf = new byte[MyPacket.BUF_SIZE*2];
        byte[] sendbuf = new byte[Req.BUF_SIZE*2];

        Boolean running = false;
        int time_out_count = 0;
        int req_serial = -1;
        int total_count = 0;
        int loss_count = 0;

        InetAddress newaddr = null;
        int newport = -1;


        DatagramPacket dp_in = null;
        DatagramPacket dp_out = null;


        try (DatagramSocket ds = new DatagramSocket())
        {
            ds.setSoTimeout(MyPacket.TIME_OUT);
            sendbuf = Req.serialize(new Req(req_serial));
            dp_out = new DatagramPacket(sendbuf, sendbuf.length, addr, port);
            ds.send(dp_out);
            
            while (true) {
                try{
                    recvbuf = new byte[MyPacket.BUF_SIZE*2];
                    dp_in = new DatagramPacket(recvbuf, recvbuf.length);
                    ds.receive(dp_in);
                    recvpack = (MyPacket) MyPacket.deserialize(recvbuf);
                    total_count++;
                    if (recvpack.serial == req_serial) {
                        if (recvpack.serial == -1){
                            newaddr = dp_in.getAddress();
                            newport = dp_in.getPort();

                        } else if (recvpack.serial == 0) {
                            if (!running){
                                String filename = new String(recvpack.data, 0, recvpack.data.length);
                                file = new File(path + filename);
                                fos = new FileOutputStream(file);
                                running = true;
                            }
                        } else {
                            if (recvpack.finish==1){
                                System.out.println("File transfer complete");
                                System.out.println(file.length() + " bytes transfered");
                                System.out.println("Loss rate : " + (float)loss_count/total_count + " %");
                                fos.close();
                                break;
                            } else {
                                fos.write(recvpack.data, 0, recvpack.data_len);
                            } 
                        }
                        req_serial++;
                        sendbuf = Req.serialize(new Req(req_serial));
                        dp_out = new DatagramPacket(sendbuf, sendbuf.length, newaddr, newport);
                    } else {
                        loss_count++;
                    }
                    ds.send(dp_out);
                    time_out_count = 0;
                } catch (SocketTimeoutException ex){
                    ds.send(dp_out);
                    if(running){
                        loss_count++;
                        if (++time_out_count >= MyPacket.TIME_OUT_COUNT){
                            System.out.println("TIME_OUT_LIMIT EXCEEDED");
                            fos.close();
                            System.exit(0);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.exit(0);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(0);
        }
    }
}

