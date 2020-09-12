import java.io.*;

class MyPacket implements Serializable{


    static final long serialVersionUID = 0;

    public final static int TIME_OUT = 1000;
    public final static int TIME_OUT_COUNT = 5;
    public final static int BUF_SIZE = 4096;

    public int finish;
    public int data_len;
    public int serial;
    public byte[] data;

    MyPacket(int serial, byte[] data, int data_len, int finish){
        this.serial = serial;
        this.data = data;
        this.data_len = data_len;
        this.finish = finish;
    }

    public static byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(obj);
        return baos.toByteArray();
    }
    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);
        return ois.readObject();
    }
}