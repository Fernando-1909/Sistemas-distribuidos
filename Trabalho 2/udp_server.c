#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <time.h>

#define PORTA 6000
#define MAX_SENSORES 32
#define MAX_LEITURAS 256
#define LIMITE_ALERTA 35.0

typedef struct {
    char sensor_id[64];
    float leituras[MAX_LEITURAS];
    int total;
} Sensor;

Sensor sensores[MAX_SENSORES];
int total_sensores = 0;
pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

int servidor_fd;

typedef struct {
    struct sockaddr_in origem;
    char mensagem[1024];
    ssize_t tamanho;
} Pacote;

void log_msg(const char *msg) {
    time_t agora = time(NULL);
    struct tm *t = localtime(&agora);
    printf("[%02d:%02d:%02d] %s\n", t->tm_hour, t->tm_min, t->tm_sec, msg);
    fflush(stdout);
}

Sensor *obter_sensor(const char *id) {
    for (int i = 0; i < total_sensores; i++) {
        if (strcmp(sensores[i].sensor_id, id) == 0) {
            return &sensores[i];
        }
    }
    Sensor *novo = &sensores[total_sensores++];
    strncpy(novo->sensor_id, id, sizeof(novo->sensor_id) - 1);
    novo->total = 0;
    return novo;
}

void processar_leitura(const char *sensor_id, float temperatura, char *resposta) {
    pthread_mutex_lock(&lock);

    Sensor *s = obter_sensor(sensor_id);
    if (s->total < MAX_LEITURAS) {
        s->leituras[s->total++] = temperatura;
    }

    float soma = 0, minimo = s->leituras[0], maximo = s->leituras[0];
    for (int i = 0; i < s->total; i++) {
        soma += s->leituras[i];
        if (s->leituras[i] < minimo) minimo = s->leituras[i];
        if (s->leituras[i] > maximo) maximo = s->leituras[i];
    }
    float media = soma / s->total;
    int count = s->total;

    pthread_mutex_unlock(&lock);

    const char *status = (temperatura > LIMITE_ALERTA) ? "ALERTA" : "NORMAL";
    snprintf(resposta, 256,
             "status=%s media=%.1f min=%.1f max=%.1f count=%d\n",
             status, media, minimo, maximo, count);
}

void *tratar_pacote(void *arg) {
    Pacote *pkt = (Pacote *)arg;

    char sensor_id[64];
    snprintf(sensor_id, sizeof(sensor_id), "%s:%d",
             inet_ntoa(pkt->origem.sin_addr), ntohs(pkt->origem.sin_port));

    char buffer[1024];
    memcpy(buffer, pkt->mensagem, pkt->tamanho);
    buffer[pkt->tamanho] = '\0';

    float temperatura;
    char resposta[256];

    if (sscanf(buffer, "%f", &temperatura) != 1) {
        snprintf(resposta, sizeof(resposta), "erro: valor invalido\n");
    } else {
        char msg[128];
        snprintf(msg, sizeof(msg), "%s enviou %.1f C", sensor_id, temperatura);
        log_msg(msg);
        processar_leitura(sensor_id, temperatura, resposta);
    }

    sendto(servidor_fd, resposta, strlen(resposta), 0,
           (struct sockaddr *)&pkt->origem, sizeof(pkt->origem));

    free(pkt);
    return NULL;
}

int main() {
    servidor_fd = socket(AF_INET, SOCK_DGRAM, 0);

    struct sockaddr_in endereco = {0};
    endereco.sin_family = AF_INET;
    endereco.sin_addr.s_addr = INADDR_ANY;
    endereco.sin_port = htons(PORTA);

    bind(servidor_fd, (struct sockaddr *)&endereco, sizeof(endereco));

    char msg[64];
    snprintf(msg, sizeof(msg), "Servidor UDP escutando na porta %d", PORTA);
    log_msg(msg);

    while (1) {
        Pacote *pkt = malloc(sizeof(Pacote));
        socklen_t addr_len = sizeof(pkt->origem);

        pkt->tamanho = recvfrom(servidor_fd, pkt->mensagem, sizeof(pkt->mensagem) - 1,
                                 0, (struct sockaddr *)&pkt->origem, &addr_len);

        if (pkt->tamanho <= 0) {
            free(pkt);
            continue;
        }

        pthread_t thread;
        pthread_create(&thread, NULL, tratar_pacote, pkt);
        pthread_detach(thread);
    }

    return 0;
}