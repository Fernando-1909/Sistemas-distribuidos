# Trabalho 2 — Sistemas Distribuídos: Infraestrutura de Rede Virtualizada com Sockets TCP/UDP

Sistema de monitoramento de sensores de temperatura, implementado sobre uma
infraestrutura de rede virtualizada com 3 VMs em dois segmentos de rede
distintos (zona externa e zona interna protegida), usando QEMU/KVM + libvirt.

## Arquitetura de rede

```
                     REDE A (externa / NAT)
                     rede libvirt "default"
                     192.168.122.0/24
                              │
                    ┌─────────┴─────────┐
                    │      VM1 (gateway) │
                    │  enp1s0: DHCP (Rede A)
                    │  enp2s0: 192.168.10.1/24 (Rede B)
                    │  - roteador (ip_forward=1)
                    │  - NAT (iptables MASQUERADE)
                    │  - servidor DHCP (isc-dhcp-server)
                    │  - servidor TCP porta 5000
                    │  - servidor UDP porta 6000
                    └─────────┬─────────┘
                              │
                    REDE B (interna / isolada)
                    rede libvirt "rede-b"
                    192.168.10.0/24
                    (sem forward, sem NAT do libvirt —
                     todo roteamento é feito pela VM1)
                              │
              ┌───────────────┴───────────────┐
              │                                 │
    ┌─────────┴─────────┐           ┌─────────┴─────────┐
    │   VM2 (cliente)    │           │   VM3 (cliente)    │
    │  única interface    │           │  única interface    │
    │  IP via DHCP da VM1 │           │  IP via DHCP da VM1 │
    │  (faixa .100-.200)  │           │  (faixa .100-.200)  │
    └─────────────────────┘           └─────────────────────┘
```

- **VM1 (vm1-gateway)**: roteador/gateway entre as duas redes, servidor DHCP
  da Rede B, e roda os dois servidores de socket (TCP e UDP).
- **VM2 (vm2-cliente) e VM3 (vm3-cliente)**: só têm interface na Rede B,
  simulam sensores enviando leituras de temperatura para a VM1. Não têm
  rota direta para a internet — todo tráfego externo passa pelo NAT da VM1.

Todas as VMs rodam **Debian 13 (Trixie) netinstall**, instalação mínima
(sem ambiente gráfico), acesso via SSH.

## Aplicação: monitoramento de sensores de temperatura

Cada cliente simula um sensor que envia 10 leituras de temperatura
(valores aleatórios entre 20.0°C e 40.0°C), uma a cada 2 segundos. O
servidor mantém um histórico por sensor (identificado por IP:porta) e
responde a cada leitura com estatísticas acumuladas:

```
status=NORMAL media=27.3 min=20.1 max=34.8 count=4
```

- `status`: `ALERTA` se a leitura atual passar de 35.0°C, senão `NORMAL`
- `media` / `min` / `max`: calculados sobre todas as leituras já recebidas
  daquele sensor
- `count`: quantidade de leituras recebidas até agora

Isso vale tanto para a versão TCP quanto para a UDP — a diferença está no
transporte, não na lógica da aplicação.

## Implementação (C + pthreads)

Linguagem escolhida: **C**, usando `pthreads` para paralelismo real entre
as threads que atendem cada cliente/pacote (sem as limitações de GIL que
uma linguagem como Python teria).

### Servidor TCP (`tcp_server.c`) — roda na VM1, porta 5000

- Uma thread (`pthread_create`) é criada por conexão aceita (`accept`).
- Cada thread fica em loop de `recv`/`send` enquanto a conexão do cliente
  estiver aberta (conexão persistente, característica do TCP).
- O histórico de leituras (`sensores[]`) é uma estrutura compartilhada
  entre todas as threads, protegida por `pthread_mutex_t` para evitar
  condição de corrida.
- `pthread_detach` é usado para que cada thread libere seus próprios
  recursos ao terminar, sem precisar de `pthread_join`.

```bash
gcc -o tcp_server tcp_server.c -lpthread
./tcp_server
```

### Cliente TCP (`tcp_client.c`) — roda na VM2 e VM3

- Conecta uma única vez ao servidor (`connect`) e mantém a conexão aberta
  durante as 10 leituras.

```bash
gcc -o tcp_client tcp_client.c
./tcp_client 192.168.10.1
```

### Servidor UDP (`udp_server.c`) — roda na VM1, porta 6000

- Um único socket (`SOCK_DGRAM`) recebe todos os datagramas
  (`recvfrom`), sem conexão.
- Para cada pacote recebido, uma thread nova é criada para processar e
  responder (`sendto`), liberando a thread principal para já receber o
  próximo pacote — é assim que o "multithread" se aplica a um protocolo
  sem conexão.
- Mesma estrutura de histórico e mutex do servidor TCP.

```bash
gcc -o udp_server udp_server.c -lpthread
./udp_server
```

### Cliente UDP (`udp_client.c`) — roda na VM2 e VM3

- Não há `connect()`: cada leitura é enviada como um datagrama solto via
  `sendto`, e a resposta é lida com `recvfrom`.

```bash
gcc -o udp_client udp_client.c
./udp_client 192.168.10.1
```

## Configuração da infraestrutura (resumo)

Feita via `libvirt`/`virt-install` no host (CachyOS), usando
`qemu:///system` (não `qemu:///session`).

1. Duas redes libvirt: `default` (NAT, Rede A) e `rede-b` (isolada, Rede B).
2. Três VMs Debian netinstall: `vm1-gateway` (2 interfaces, uma em cada
   rede), `vm2-cliente` e `vm3-cliente` (só na `rede-b`).
3. Na VM1: IP estático na interface da Rede B, `ip_forward=1`, regras de
   `iptables` (MASQUERADE + FORWARD) persistidas com
   `iptables-persistent`, e `isc-dhcp-server` distribuindo IPs na Rede B.
4. No host: regras de UFW liberando as bridges (`virbr0`, `virbr-b`) e o
   forward de `virbr0` para a internet.

## Observações / problemas conhecidos

- **UFW do host** bloqueia por padrão o tráfego das bridges do libvirt —
  foi necessário liberar `virbr0`/`virbr-b` e o `route allow in on
  virbr0` para DHCP e acesso à internet funcionarem.
- **Login SSH root direto por senha é bloqueado** em todas as VMs
  (`PermitRootLogin` restrito por padrão no Debian) — acesso é feito com
  usuário normal + `su -`.
- **VM2 e VM3 só são alcançáveis via jump host pela VM1** (a Rede B é
  isolada do host físico).
- As regras de `ip_forward` e `iptables` da VM1 já resetaram após reboot
  por falha na persistência do `iptables-persistent` — ver o roteiro de
  reinicialização para o procedimento de verificação.
