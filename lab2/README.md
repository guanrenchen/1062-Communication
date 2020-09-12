# F74046022_陳冠仁_電腦通訊網路_lab2

### 環境參數
Ubuntu 16.04 (WSL)

Java 1.8.0_162

---

### 使用方式

(程式在Multicast/bin/和Multithread/bin/)

(程式碼在Multicast/src/和Multithread/src/)

java MulticastServer ip port /path/to/file

java MulticastClient ip port 

以編號缺失計算封包遺失率

java MultithreadServer ip port /path/to/file

java MultithreadClient ip port /path/to/dir

以封包重複計算封包遺失率 (可靠傳輸)

先開啟n個client再開啟server選定要傳輸之檔案
