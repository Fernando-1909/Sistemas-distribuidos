#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <arpa/inet.h>
#include <sys/socket.h>

#define PORTA 6000
#define TOTAL_LEITURAS 10

int main(int argc, char *argv[]) {
    if (argc != 2) {
        printf("Uso: %s <IP_DO_SERVIDOR>\n", argv[0]);
        return 1;
    }

    int sock_fd = socket(AF_INET, SOCK_DGRAM, 0);

    struct sockaddr_in servidor = {0};
    servidor.sin_family = AF_INET;
    servidor.sin_port = htons(PORTA);
    inet_pton(AF_INET, argv[1], &servidor.sin_addr);

    printf("Enviando leituras para %s:%d (UDP)\n", argv[1], PORTA);
    srand(time(NULL) ^ getpid());

    for (int i = 0; i < TOTAL_LEITURAS; i++) {
        float temperatura = 20.0 + (rand() % 200) / 10.0;

        char msg[32];
        snprintf(msg, sizeof(msg), "%.1f", temperatura);

        sendto(sock_fd, msg, strlen(msg), 0,
               (struct sockaddr *)&servidor, sizeof(servidor));

        char resposta[256];
        struct sockaddr_in origem;
        socklen_t addr_len = sizeof(origem);
        int n = recvfrom(sock_fd, resposta, sizeof(resposta) - 1, 0,
                          (struct sockaddr *)&origem, &addr_len);

        if (n <= 0) {
            printf("Sem resposta do servidor.\n");
            continue;
        }
        resposta[n] = '\0';

        printf("Enviei %.1f C -> %s", temperatura, resposta);

        sleep(2);
    }

    close(sock_fd);
    printf("Encerrado.\n");
    return 0;
}