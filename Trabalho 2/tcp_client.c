#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <time.h>
#include <arpa/inet.h>
#include <sys/socket.h>

#define PORTA 5000
#define TOTAL_LEITURAS 10

int main(int argc, char *argv[]) {
    if (argc != 2) {
        printf("Uso: %s <IP_DO_SERVIDOR>\n", argv[0]);
        return 1;
    }

    int sock_fd = socket(AF_INET, SOCK_STREAM, 0);

    struct sockaddr_in servidor = {0};
    servidor.sin_family = AF_INET;
    servidor.sin_port = htons(PORTA);
    inet_pton(AF_INET, argv[1], &servidor.sin_addr);

    if (connect(sock_fd, (struct sockaddr *)&servidor, sizeof(servidor)) < 0) {
        perror("Falha ao conectar");
        return 1;
    }

    printf("Conectado ao servidor %s:%d\n", argv[1], PORTA);
    srand(time(NULL));

    for (int i = 0; i < TOTAL_LEITURAS; i++) {
        // simula leitura de temperatura entre 20.0 e 40.0 graus
        float temperatura = 20.0 + (rand() % 200) / 10.0;

        char msg[32];
        snprintf(msg, sizeof(msg), "%.1f", temperatura);
        send(sock_fd, msg, strlen(msg), 0);

        char resposta[256];
        int n = recv(sock_fd, resposta, sizeof(resposta) - 1, 0);
        if (n <= 0) break;
        resposta[n] = '\0';

        printf("Enviei %.1f C -> %s", temperatura, resposta);

        sleep(2); // espera 2s entre leituras
    }

    close(sock_fd);
    printf("Encerrado.\n");
    return 0;
}