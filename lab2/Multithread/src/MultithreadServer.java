import java.io.*; 
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.util.ArrayList;

public class MultithreadServer {

    public static ArrayList<MyPacket> packlist;

    public static void main(String[] args) throws UnknownHostException, InterruptedException {
        
        InetAddress addr_in = null;
        int port_in = 0;
        File file = null;
        FileInputStream fis = null;
        String filename = "";
        try {
            addr_in = InetAddress.getByName(args[0]);
            port_in = Integer.parseInt(args[1]);
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


        try(DatagramSocket hostSocket = new DatagramSocket(port_in, addr_in))
        {
            hostSocket.setSoTimeout(MyPacket.TIME_OUT);
            byte[] recvbuf = null;
            DatagramPacket dp = null;
            Req req = null;
            int time_out_count = 0;

            ArrayList<Handler> handlers = new ArrayList<>();
            ArrayList<String> connected = new ArrayList<>();
            Handler handler = null;

            while(true) {
                try{
                    recvbuf = new byte[Req.BUF_SIZE*2];
                    dp = new DatagramPacket(recvbuf, recvbuf.length);
                    hostSocket.receive(dp);
                    
                    time_out_count = 0;

                    String id = dp.getAddress().toString() + dp.getPort();
                    if(connected.contains(id)) continue;
                    connected.add(id);
                    
                    handler = new Handler(dp, filename);
                    handler.start();
                    handlers.add(handler);

                } catch (SocketTimeoutException ex) {
                    if(++time_out_count >= MyPacket.TIME_OUT_COUNT){
                        System.out.println("Server closed");
                        break;
                    }
                } catch (Exception ex){
                    ex.printStackTrace();
                    System.exit(0);
                }
            }

            for(i=0; i<handlers.size(); ++i){
                handlers.get(i).join();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    
}