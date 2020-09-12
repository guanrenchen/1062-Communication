#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <time.h>
#include <fcntl.h>
#include <errno.h>
#include <libgen.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h> 
#include <arpa/inet.h>

#define TIMEOUT 2
#define TIMEOUT_LIMIT 5
#define BUF_SIZE_TCP 4096
#define BUF_SIZE_UDP 65500-16
#define DIVISION 20
#define LOG_UNIT 100/DIVISION

struct ack {
    long serial;
    size_t size;
};

struct udp_pack{
    struct ack ack;
    char data[BUF_SIZE_UDP];
};

struct logger{
    size_t size;
    size_t loss, total;
    float step;
};

void tcp_send(char *ip, int portno, char *fileName);
void tcp_recv(char *ip, int portno);
void udp_send(char *ip, int portno, char *fileName);
void udp_recv(char *ip, int portno);

void error(const char *msg);
void printTimeStr();
size_t getFileSize(FILE *fp);
void initLogger(struct logger *logger, size_t total);
void record(struct logger *logger, size_t size);

int main(int argc, char *argv[])
{
    // argv[1]: tcp / udp
    // argv[2]: send / recv
    // ip: <ip> 
    // portno: <port> 
    // argv[5]: <file_name>

    char *ptcl, *mode, *ip, *fileName;
    int portno;

    if (argc < 5){
        error("Insufficient arguments");
    }

    ptcl = argv[1];
    mode = argv[2];
    ip = argv[3];
    portno = atoi(argv[4]);

    char is_tcp, is_udp, is_send, is_recv;
    is_tcp = strcmp(ptcl,"tcp")==0;
    is_udp = strcmp(ptcl,"udp")==0;
    is_send = strcmp(mode,"send")==0;
    is_recv = strcmp(mode,"recv")==0;

    if(is_tcp && is_send)
        tcp_send(ip, portno, argv[5]);
    else if(is_tcp && is_recv)
        tcp_recv(ip, portno);
    else if(is_udp && is_send)
        udp_send(ip, portno, argv[5]);
    else if(is_udp && is_recv)
        udp_recv(ip, portno);
    else
        error("Invalid argument(s)");

    return 0;
}

void tcp_send(char *ip, int portno, char *fileName)
{
    FILE *fp;
    int sockfd;
    struct hostent *server;
    struct sockaddr_in serv_addr;
    struct logger logger;
    size_t ret;
    char buf[BUF_SIZE_TCP];

    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0) 
        error("ERROR opening socket");

    server = gethostbyname(ip);
    if (server == NULL) {
        fprintf(stderr,"ERROR, no such host\n");
        exit(0);
    }

    bzero((char *) &serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    bcopy((char *)server->h_addr, (char *)&serv_addr.sin_addr.s_addr, server->h_length);
    
    serv_addr.sin_port = htons(portno);
    if (connect(sockfd,(struct sockaddr *) &serv_addr,sizeof(serv_addr)) < 0) 
        error("ERROR connecting");

    fp = fopen(fileName, "rb");
    if(!fp) error("File open error");

    memset(buf, 0, sizeof(buf));

    sprintf(buf, "%ld", getFileSize(fp));
    write(sockfd,buf, sizeof(buf));
    memset(buf, 0, sizeof(buf));

    strncpy(buf, basename(fileName), sizeof(buf));
    write(sockfd,buf, sizeof(buf));
    memset(buf, 0, sizeof(buf));

    initLogger(&logger, getFileSize(fp));

    while(1){
        ret = fread(buf, 1, sizeof(buf), fp);
        record(&logger, ftell(fp));
        if(ret==0) break;
        write(sockfd,buf,ret);
        memset(buf,0,sizeof(buf));
    }

    if(fp) fclose(fp);
    close(sockfd);
}

void tcp_recv(char *ip, int portno)
{
    FILE *fp;
    int sockfd, newsockfd;
    socklen_t clilen;
    struct sockaddr_in serv_addr, cli_addr;
    struct logger logger;
    size_t ret;
    char buf[BUF_SIZE_TCP];

    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0)
        error("ERROR opening socket");

    bzero((char *) &serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = INADDR_ANY;
    serv_addr.sin_port = htons(portno);
    if (bind(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0)
        error("ERROR on binding");
    listen(sockfd,5);

    // while(1){
        clilen = sizeof(cli_addr);
        newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr, &clilen);
        if (newsockfd < 0)
            error("ERROR on accept");

        memset(buf, 0, sizeof(buf));
        read(newsockfd, buf, sizeof(buf));
        initLogger(&logger, (size_t)atoi(buf));

        memset(buf, 0, sizeof(buf));
        read(newsockfd, buf, sizeof(buf));
        fp = fopen(buf, "wb+");

        while(1){
            memset(buf, 0, sizeof(buf));
            ret = read(newsockfd, buf, sizeof(buf));
            if(ret<=0) break;
            fwrite(buf, ret, 1, fp);
            record(&logger, ftell(fp));
        }

        if(fp) fclose(fp);
        close(newsockfd);
    // }
    close(sockfd);
}

void udp_send(char *ip, int portno, char *fileName)
{
    FILE* fp;
    int ret, sockfd;
    struct timeval timeout = {TIMEOUT,0};
    struct sockaddr_in serv_addr;
    struct udp_pack pack;
    struct ack ack;
    struct logger logger;
    size_t count;

    fp = fopen(fileName, "rb");
    if(!fp) error("File open error");

    sockfd = socket(PF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) error("socket");
    
    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, (char*)&timeout, sizeof(struct timeval));

    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(portno);
    serv_addr.sin_addr.s_addr = inet_addr(ip);

    memset(&pack, 0, sizeof(pack));
    pack.ack.serial = 0;
    pack.ack.size = 0;

    count = 0;
    while (1){
        if(pack.ack.serial==0){
            strncpy(pack.data, basename(fileName), sizeof(pack.data));
            pack.ack.size = getFileSize(fp);
            initLogger(&logger, pack.ack.size);
        }else{
            pack.ack.size = fread(pack.data, 1, sizeof(pack.data), fp);
        }

        while(1){
            sendto(sockfd, &pack, sizeof(pack), 0, (struct sockaddr *)&serv_addr, sizeof(serv_addr));
            logger.total++;
            memset(&ack, 0, sizeof(ack));
            if (recvfrom(sockfd, &ack, sizeof(ack), 0, NULL, NULL) == -1) {
                if (errno == EINTR){
                    continue;
                }else if (errno == EAGAIN){
                    logger.loss++;
                    if (ack.serial >= 0 && ++count > TIMEOUT_LIMIT){
                        if (pack.ack.size==0) break;
                        if (fp) fclose(fp);
                        close(sockfd);
                        error("timed out");
                    }
                }else{
                    if (fp) fclose(fp);
                    close(sockfd);
                    error("recvfrom error");
                }
            }else{
                count = 0;
                if (ack.serial == pack.ack.serial)
                    break;
            }
        }
        record(&logger, ftell(fp));

        if(ack.size == 0) break;

        pack.ack.serial++;
        memset(pack.data, 0, sizeof(pack.data));
    }
    if(fp)
        fclose(fp);
    if(logger.total>0)
        printf("loss rate : %f%%\n", (float)logger.loss/logger.total*100);

    close(sockfd);
}

void udp_recv(char *ip, int portno)
{
    FILE* fp;
    int sockfd;
    struct timeval timeout = {TIMEOUT,0};
    struct sockaddr_in serv_addr, peer_addr;
    socklen_t peerlen;
    struct udp_pack pack;    
    struct ack ack;
    struct logger logger;
    size_t count;
    
    if ((sockfd = socket(PF_INET, SOCK_DGRAM, 0)) < 0)
        error("socket error");

    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, (char*)&timeout, sizeof(struct timeval));

    memset(&serv_addr, 0, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(portno);
    serv_addr.sin_addr.s_addr = htonl(INADDR_ANY);
    if (bind(sockfd, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0)
        error("bind error");

    // while(1){
        memset(&ack, 0, sizeof(ack));
        ack.serial = -1;
        ack.size = 0;
        count = 0;

        while (1){
            peerlen = sizeof(peer_addr);
            memset(&pack, 0, sizeof(pack));
            if (recvfrom(sockfd, &pack, sizeof(pack), 0, (struct sockaddr *)&peer_addr, &peerlen) == -1){
                if (errno == EINTR){
                    continue;
                }else if (errno == EAGAIN){
                    if(ack.serial >= 0){
                        if (++count > TIMEOUT_LIMIT){
                            printf("timed out\n");
                            break;
                        }
                        sendto(sockfd, &ack, sizeof(ack), 0, (struct sockaddr *)&peer_addr, peerlen);    
                        logger.loss++;
                        logger.total++;
                    }
                }else{
                    error("recvfrom error");
                }
            }else{
                count = 0;
                if (pack.ack.serial == ack.serial + 1){
                    if(ack.serial==-1){
                        fp = fopen(pack.data, "wb+");
                        if(!fp) error("File open error");
                        initLogger(&logger, pack.ack.size);
                    }else{
                        fwrite(pack.data, pack.ack.size, 1, fp);
                        record(&logger, ftell(fp));
                    }
                    ack = pack.ack;
                    if(ack.size==0) {
                        sendto(sockfd, &ack, sizeof(ack), 0, (struct sockaddr *)&peer_addr, peerlen);
                        logger.total++;
                        break;
                    }
                }
                sendto(sockfd, &ack, sizeof(ack), 0, (struct sockaddr *)&peer_addr, peerlen);
                logger.total++;
            }
        }
        if(fp)
            fclose(fp);
        if(logger.total>0)
            printf("loss rate : %f%%\n", (float)logger.loss/logger.total*100);
    // }
    close(sockfd);
}

void error(const char *msg)
{
    perror(msg);
    exit(0);
}

void printTimeStr()
{
    time_t rawtime;
    struct tm *timeinfo;            
    char buf[26];
    memset(buf, 0, sizeof(buf));
    time ( &rawtime );
    timeinfo = localtime ( &rawtime );
    strftime(buf, sizeof(buf), "%Y/%m/%d %H:%M:%S", timeinfo);
    printf("%s", buf);
}

size_t getFileSize(FILE *fp){
    size_t sz;
    fseek(fp, 0L, SEEK_END);
    sz = ftell(fp);
    fseek(fp, 0L, SEEK_SET);
    return sz;
}

void initLogger(struct logger *logger, size_t total){
    logger->size = 0;
    logger->step = (float)total/DIVISION;
    logger->loss = 0;
    logger->total = 0;
}

void record(struct logger *logger, size_t size){
    size_t tmp0 = logger->size / logger->step;
    size_t tmp1 = size / logger->step;
    if(tmp0 < tmp1){
        printf("%3ld%% ", tmp1 * LOG_UNIT);
        printTimeStr();
        printf("\n");
    }
    logger->size = size;
}
