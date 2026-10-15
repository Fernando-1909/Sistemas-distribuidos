[Link para o vídeo explicando trabalho 2 unidade 1](https://www.youtube.com/watch?v=vmvO6llryic)

# Sistemas Distribuídos — Trabalhos

Autores: Bruno Eduardo, Fernando Macedo, Meni Ramos

Repositório com os trabalhos da disciplina de Sistemas Distribuídos. Os
Trabalhos 2 e 3 usam **a mesma infraestrutura de rede virtualizada** (3 VMs
Linux em dois segmentos de rede, simulando uma rede corporativa com zona
protegida). O que muda de um trabalho para o outro é a aplicação distribuída
que roda por cima dela: **sockets TCP/UDP** no Trabalho 2 e **RPC
síncrono/assíncrono** no Trabalho 3.

<a name="sumario"></a>
## Sumário

- [Estrutura do repositório](#estrutura)
- [Infraestrutura comum (Trabalhos 2 e 3)](#infra)
- [Trabalho 1](#trabalho-1)
- [Trabalho 2: Sockets TCP e UDP](#trabalho-2)
- [Trabalho 3: RPC síncrono e assíncrono](#trabalho-3)
  - [Instalação, configuração e execução](#t3-execucao)
  - [Análise e respostas às perguntas](#t3-respostas)
- [O que mudou entre os trabalhos](#mudancas)
- [Problemas conhecidos e soluções](#problemas)

---

<a name="estrutura"></a>
## Estrutura do repositório

```
.
├── README.md                  (este arquivo)
├── trabalho-1/                [PREENCHER: arquivos do Trabalho 1]
├── Trabalho 2/
│   ├── tcp_server.c
│   ├── tcp_client.c
│   ├── udp_server.c
│   ├── udp_client.c
│   └── (vídeo)
└── Trabalho 1 Unidade 2/
    ├── Log.java
    ├── ServicoRemoto.java
    ├── ServicoImpl.java
    ├── Servidor.java
    ├── ClienteSincrono.java
    ├── ServicoAssincrono.java
    ├── ServicoAssincronoImpl.java
    ├── ServidorAssincrono.java
    ├── ClienteAssincrono.java
    └── (vídeo)
```

---

<a name="infra"></a>
## Infraestrutura comum (Trabalhos 2 e 3)

Hypervisor: **QEMU/KVM com libvirt** (`qemu:///system`), em um notebook com
CachyOS (Arch). Escolhido por ser nativo do Linux (o KVM faz parte do
kernel), sem depender de módulos de terceiros como VirtualBox/VMware.
Sistema das VMs: **Debian 13 (Trixie) netinstall**, instalação mínima, sem
ambiente gráfico, acessada por SSH.

### Arquitetura de rede

```
                     REDE A (externa / NAT)
                     rede libvirt "default"
                     192.168.122.0/24
                              │
                    ┌─────────┴─────────┐
                    │  VM1 (gateway)     │
                    │  enp1s0: DHCP (Rede A)
                    │  enp2s0: 192.168.10.1/24 (Rede B)
                    │  - roteador (ip_forward=1)
                    │  - NAT (iptables MASQUERADE)
                    │  - servidor DHCP (isc-dhcp-server)
                    │  - servidores da aplicação (T2 e T3)
                    └─────────┬─────────┘
                              │
                    REDE B (interna / isolada)
                    rede libvirt "rede-b"
                    192.168.10.0/24
                    (sem forward e sem NAT do libvirt:
                     todo roteamento é feito pela VM1)
                              │
              ┌───────────────┴───────────────┐
              │                                 │
    ┌─────────┴─────────┐           ┌─────────┴─────────┐
    │  VM2 (cliente)     │           │  VM3 (cliente)     │
    │  uma interface      │           │  uma interface      │
    │  IP via DHCP da VM1 │           │  IP via DHCP da VM1 │
    │  (faixa .100-.200)  │           │  (faixa .100-.200)  │
    └─────────────────────┘           └─────────────────────┘
```

| VM | Papel | Interfaces | Recursos |
|---|---|---|---|
| `vm1-gateway` | roteador, NAT, DHCP e servidores da aplicação | `enp1s0` (Rede A, DHCP) e `enp2s0` (Rede B, `192.168.10.1/24`) | 1 vCPU, 1024 MB, disco 8 GB |
| `vm2-cliente` | cliente 1 | `enp1s0` (Rede B, DHCP da VM1) | 1 vCPU, 1024 MB, disco 6 GB |
| `vm3-cliente` | cliente 2 | `enp1s0` (Rede B, DHCP da VM1) | 1 vCPU, 1024 MB, disco 6 GB |

As VMs 2 e 3 **não têm rota direta para a rede externa**: todo acesso à
internet passa pelo roteamento e pelo NAT da VM1 (zona protegida).

### 1. Preparar o host (CachyOS / Arch)

```bash
sudo pacman -S qemu-desktop libvirt dnsmasq virt-install virt-viewer
sudo systemctl enable --now libvirtd
sudo usermod -aG libvirt,kvm $USER      # depois, sair e entrar na sessão

# usar sempre o libvirt do sistema (e não o da sessão do usuário)
mkdir -p ~/.config/libvirt
echo 'uri_default = "qemu:///system"' > ~/.config/libvirt/libvirt.conf
```

Confirmar suporte: `virt-host-validate` (a virtualização precisa estar
habilitada na BIOS/UEFI).

### 2. Criar as redes virtuais

`rede-default.xml` (Rede A, com NAT; só é necessário se a rede `default`
ainda não existir):

```xml
<network>
  <name>default</name>
  <bridge name="virbr0" stp="on" delay="0"/>
  <forward mode="nat"/>
  <ip address="192.168.122.1" netmask="255.255.255.0">
    <dhcp>
      <range start="192.168.122.2" end="192.168.122.254"/>
    </dhcp>
  </ip>
</network>
```

`rede-b.xml` (Rede B, isolada: sem `<forward>`, logo sem NAT e sem rota para
fora):

```xml
<network>
  <name>rede-b</name>
  <bridge name="virbr-b" stp="on" delay="0"/>
</network>
```

```bash
virsh net-define rede-default.xml && virsh net-start default && virsh net-autostart default
virsh net-define rede-b.xml       && virsh net-start rede-b   && virsh net-autostart rede-b
virsh net-list --all
```

### 3. Criar as VMs

A ISO precisa ficar em `/var/lib/libvirt/images/`, porque o usuário que roda
o QEMU não consegue ler a pasta pessoal.

```bash
sudo mv ~/isos/debian-13.*-amd64-netinst.iso /var/lib/libvirt/images/

# VM1: duas interfaces, uma em cada rede
virt-install --connect qemu:///system --name vm1-gateway \
  --memory 1024 --vcpus 1 --disk size=8,pool=default \
  --cdrom /var/lib/libvirt/images/debian-13.6.0-amd64-netinst.iso \
  --network network=default --network network=rede-b \
  --os-variant debian12 --graphics vnc

# VM2 e VM3: só a Rede B (a VM1 já precisa estar ligada e roteando)
virt-install --connect qemu:///system --name vm2-cliente \
  --memory 1024 --vcpus 1 --disk size=6,pool=default \
  --cdrom /var/lib/libvirt/images/debian-13.6.0-amd64-netinst.iso \
  --network network=rede-b --os-variant debian12 --graphics vnc

virt-install --connect qemu:///system --name vm3-cliente \
  --memory 1024 --vcpus 1 --disk size=6,pool=default \
  --cdrom /var/lib/libvirt/images/debian-13.6.0-amd64-netinst.iso \
  --network network=rede-b --os-variant debian12 --graphics vnc
```

Na instalação do Debian: modo texto, fuso Brasil/São Paulo e, na seleção de
pacotes, apenas **SSH server** e **standard system utilities**. Na VM1, usar
`enp1s0` (a que está na rede `default`) como interface primária.

### 4. Configurar a VM1 (gateway, NAT e DHCP)

Dentro da VM1, como root (`su -`):

```bash
# IP fixo na interface da Rede B: acrescentar em /etc/network/interfaces
#   allow-hotplug enp2s0
#   iface enp2s0 inet static
#       address 192.168.10.1
#       netmask 255.255.255.0
systemctl restart networking

# roteamento
echo 'net.ipv4.ip_forward=1' >> /etc/sysctl.conf
sysctl -p

# NAT + persistência das regras
apt install -y iptables iptables-persistent
iptables -t nat -A POSTROUTING -o enp1s0 -j MASQUERADE
iptables -A FORWARD -i enp2s0 -o enp1s0 -j ACCEPT
iptables -A FORWARD -i enp1s0 -o enp2s0 -m state --state ESTABLISHED,RELATED -j ACCEPT
netfilter-persistent save

# servidor DHCP da Rede B
apt install -y isc-dhcp-server
```

Acrescentar ao final de `/etc/dhcp/dhcpd.conf`:

```
subnet 192.168.10.0 netmask 255.255.255.0 {
  range 192.168.10.100 192.168.10.200;
  option routers 192.168.10.1;
  option domain-name-servers 8.8.8.8, 1.1.1.1;
}
```

Em `/etc/default/isc-dhcp-server`, definir `INTERFACESv4="enp2s0"` e reiniciar:

```bash
systemctl restart isc-dhcp-server
```

### 5. Liberar as bridges no firewall do host (UFW)

O UFW descarta por padrão o tráfego das bridges do libvirt (DHCP e
encaminhamento para a internet):

```bash
sudo ufw allow in on virbr0
sudo ufw allow out on virbr0
sudo ufw allow in on virbr-b
sudo ufw allow out on virbr-b
sudo ufw route allow in on virbr0
sudo ufw route allow in on virbr-b out on virbr0
sudo ufw route allow in on virbr0 out on virbr-b
sudo ufw reload
```

### 6. Acessar as VMs

```bash
# IP da VM1 na Rede A
virsh net-dhcp-leases default

# a partir do host
ssh USUARIO@IP_DA_VM1

# a VM1 é o único ponto de entrada para a Rede B: de dentro da VM1
ssh USUARIO@192.168.10.X          # VM2 ou VM3
```

O login SSH direto como root por senha vem bloqueado no Debian: entrar com
o usuário normal e usar `su -` quando precisar de root. Os IPs de VM2/VM3 na
Rede B aparecem na VM1 em `/var/lib/dhcp/dhcpd.leases`.

### 7. Testes de conectividade

Rede interna e rede externa, executados dentro da VM2 (e repetidos na VM3):

```bash
ping -c 3 192.168.10.1        # cliente -> gateway (Rede B)
ping -c 3 192.168.10.X        # cliente -> outro cliente (Rede B)
ping -c 3 8.8.8.8             # cliente -> internet, via NAT da VM1
```

### 8. Checklist ao ligar o ambiente

```bash
virsh start vm1-gateway       # aguardar ~15 s
virsh start vm2-cliente
virsh start vm3-cliente
```

Na VM1, como root, confirmar que o roteamento continua ativo:

```bash
sysctl net.ipv4.ip_forward                 # esperado: 1
iptables -t nat -L POSTROUTING -n -v       # esperado: regra MASQUERADE
```

---

<a name="trabalho-1"></a>
## Trabalho 1

Não completado na primeira unidade.

---

<a name="trabalho-2"></a>
## Trabalho 2: Sockets TCP e UDP

Sistema de monitoramento de sensores de temperatura sobre sockets, com
servidor **multithread** na VM1 e clientes na VM2 e na VM3, em duas versões:
TCP e UDP.

### Aplicação

Cada cliente simula um sensor e envia 10 leituras de temperatura (valores
aleatórios entre 20,0 e 40,0 °C), uma a cada 2 s. O servidor mantém um
histórico por sensor (identificado por IP:porta) e responde a cada leitura
com estatísticas acumuladas:

```
status=NORMAL media=27.3 min=20.1 max=34.8 count=4
```

- `status`: `ALERTA` se a leitura atual passar de 35,0 °C, senão `NORMAL`
- `media`, `min`, `max`: calculados sobre todas as leituras daquele sensor
- `count`: leituras recebidas até o momento

A lógica é a mesma nas duas versões; muda apenas o transporte.

### Implementação (C + pthreads)

Linguagem **C** com `pthreads`: threads reais do sistema operacional, com
paralelismo verdadeiro entre núcleos (Python, por exemplo, seria limitado
pelo GIL).

| Arquivo | Roda em | Descrição |
|---|---|---|
| `tcp_server.c` | VM1, porta 5000 | uma thread por conexão aceita (`accept` + `pthread_create`); `recv`/`send` em loop na conexão persistente |
| `tcp_client.c` | VM2 e VM3 | `connect` uma vez e envia as 10 leituras na mesma conexão |
| `udp_server.c` | VM1, porta 6000 | um socket `SOCK_DGRAM` recebendo com `recvfrom`; uma thread por pacote recebido, que responde com `sendto` ao endereço de origem |
| `udp_client.c` | VM2 e VM3 | sem `connect`: cada leitura é um datagrama enviado com `sendto` |

O histórico de leituras (`sensores[]`) é compartilhado entre as threads e
protegido por `pthread_mutex_t` (evita condição de corrida).
`pthread_detach` faz cada thread liberar seus recursos sozinha ao terminar.

### Instalação, compilação e execução

```bash
# nas VMs (como root)
apt install -y gcc

# VM1
gcc -o tcp_server tcp_server.c -lpthread
gcc -o udp_server udp_server.c -lpthread
./tcp_server          # terminal 1
./udp_server          # terminal 2

# VM2 e VM3, ao mesmo tempo
gcc -o tcp_client tcp_client.c
gcc -o udp_client udp_client.c
./tcp_client 192.168.10.1
./udp_client 192.168.10.1
```

### TCP x UDP na prática

| | TCP | UDP |
|---|---|---|
| Conexão | `connect` uma vez; as 10 leituras seguem na mesma conexão | sem conexão; cada leitura é um datagrama independente |
| Identificação do cliente | cada `accept` entrega um descritor dedicado | o endereço de origem vem em cada pacote (`recvfrom`) |
| Thread no servidor | uma por conexão | uma por pacote recebido |
| Confiabilidade | entrega e ordem garantidas pelo protocolo | sem garantia; o cliente trata a falta de resposta |

---

<a name="trabalho-3"></a>
## Trabalho 3: RPC síncrono e assíncrono

Serviço remoto com operações de durações diferentes, para observar o
comportamento de chamadas **síncronas** e **assíncronas**, com servidores
multithread na VM1 e clientes na VM2 e na VM3.

### Tecnologia

**Java RMI** (Remote Method Invocation), o mecanismo de RPC que já vem no
JDK, sem dependências externas.

- Chamada RMI é síncrona por natureza: a thread do cliente bloqueia até a
  resposta chegar.
- No servidor, o runtime do RMI atende cada chamada recebida em uma thread
  própria (visíveis no log como `RMI TCP Connection(N)-IP`).
- O modo assíncrono usa um pool de threads (`ExecutorService`) no servidor e
  um ticket devolvido ao cliente na hora da submissão.

### Operações remotas

| Operação | O que faz | Duração |
|---|---|---|
| `calcular(a, b, op)` | operação aritmética (`+ - * /`) | instantânea |
| `processarDemorado(segundos)` | "processa" por N segundos (`Thread.sleep`), simulando tarefas de 5, 10 ou mais segundos | controlada pelo parâmetro |
| `processarColecao(valores)` | média, mínimo e máximo de uma coleção de valores | curta |

### Arquitetura da aplicação

| | Servidor síncrono | Servidor assíncrono |
|---|---|---|
| Classe principal | `Servidor` | `ServidorAssincrono` |
| Registro RMI / objeto exportado | portas `1099` / `1100` | portas `2099` / `2100` |
| Nome no registro | `ServicoRPC` | `ServicoRPCAsync` |
| Interface remota | `ServicoRemoto` | `ServicoAssincrono` |
| Implementação | `ServicoImpl` | `ServicoAssincronoImpl` (reaproveita `ServicoImpl`) |
| Cliente | `ClienteSincrono` | `ClienteAssincrono` |

Os dois servidores são processos separados e podem rodar ao mesmo tempo na
VM1. `Log` é o utilitário de log (`HH:mm:ss - mensagem`, em UTF-8).

**Servidor síncrono.** O cliente chama o método e fica bloqueado até a
resposta. Cada chamada é atendida por uma thread RMI diferente, então dois
clientes são atendidos em paralelo. O servidor aceita o argumento `single`,
que coloca uma trava global e força uma chamada por vez, simulando um servidor
de uma thread só (usado para demonstrar o contraste, ver pergunta 5).

**Servidor assíncrono.** O cliente chama `submeter(operacao, argumentos)` e
recebe **imediatamente** um ticket; a tarefa é executada em segundo plano por
uma thread do pool (`pool-1-thread-N` no log). O cliente pode consultar
`pronto(ticket)` sem bloquear e pedir `obterResultado(ticket)`, que bloqueia
até a conclusão e equivale ao `future.get()`.

### Registro das chamadas

Exemplo (síncrono, CLIENTE 1 com a operação de 10 s):

```
14:05:01 - CLIENTE 1
14:05:01 - Enviando requisição RPC
14:05:01 - Operação solicitada: processarDemorado(10)
14:05:11 - Resposta recebida: processamento de 10s concluído
14:05:11 - Tempo total: 10 segundos
```

Exemplo (assíncrono, CLIENTE 1 com a operação de 10 s):

```
14:10:01 - CLIENTE 1
14:10:01 - Operação solicitada: processarDemorado(10)
14:10:01 - Requisição assíncrona enviada (tarefa-1)
14:10:01 - Cliente continuou sua execução
14:10:03 - Executando atividade local 1 (resultado pronto? false)
14:10:05 - Executando atividade local 2 (resultado pronto? false)
14:10:07 - Executando atividade local 3 (resultado pronto? false)
14:10:07 - Pedindo o resultado (equivale ao future.get())
14:10:11 - Resultado RPC disponível: processamento de 10s concluído
14:10:11 - Tempo total: 10 segundos
```

Exemplo do servidor assíncrono (duas threads RMI só recebem os pedidos e o
trabalho roda no pool):

```
14:10:01 - [RMI TCP Connection(3)-192.168.10.102] recebeu demorado(10) -> tarefa-1 (enfileirada)
14:10:01 - [RMI TCP Connection(4)-192.168.10.104] recebeu demorado(5) -> tarefa-2 (enfileirada)
14:10:01 - [pool-1-thread-1] iniciou processarDemorado(10)
14:10:01 - [pool-1-thread-2] iniciou processarDemorado(5)
```

<a name="t3-execucao"></a>
### Instalação, configuração e execução

Pré-requisito: a infraestrutura da seção [Infraestrutura comum](#infra) no ar
(VMs ligadas, roteamento e DHCP funcionando).

**1. Instalar o Java** (como root em cada VM):

```bash
# VM1 (compila)
apt update && apt install -y default-jdk-headless

# VM2 e VM3 (só executam)
apt update && apt install -y default-jre-headless
```

**2. Compilar e empacotar** (na VM1, na pasta com os `.java`):

```bash
javac *.java
jar cf rpc.jar *.class
```

**3. Copiar o jar para as VMs 2 e 3** (da VM1, com o usuário normal):

```bash
scp rpc.jar USUARIO@192.168.10.X:~/
```

Executar os clientes como usuário normal, na mesma pasta onde o `rpc.jar`
foi copiado.

**4. Parte I, RPC síncrono.** Na VM1, subir o servidor e aguardar a linha
`Servidor RPC sincrono pronto`:

```bash
java -cp rpc.jar Servidor 192.168.10.1
```

Nas VMs 2 e 3, **ao mesmo tempo**:

```bash
# VM2
java -cp rpc.jar ClienteSincrono 192.168.10.1 "CLIENTE 1" 10
# VM3
java -cp rpc.jar ClienteSincrono 192.168.10.1 "CLIENTE 2" 5
```

Demonstração com uma thread só (opcional): reiniciar o servidor com
`java -cp rpc.jar Servidor 192.168.10.1 single` e repetir os clientes. Para
ver a espera, comparar os **horários** das linhas do log, e não apenas o
"Tempo total" (que só mede a chamada `processarDemorado`).

**5. Parte II, RPC assíncrono.** Na VM1, aguardar a linha
`Servidor RPC assíncrono pronto`:

```bash
java -cp rpc.jar ServidorAssincrono 192.168.10.1 4      # 4 = threads do pool
```

Nas VMs 2 e 3, **ao mesmo tempo**:

```bash
# VM2
java -cp rpc.jar ClienteAssincrono 192.168.10.1 "CLIENTE 1" 10
# VM3
java -cp rpc.jar ClienteAssincrono 192.168.10.1 "CLIENTE 2" 5
```

Argumentos dos programas: servidores `[ip_da_vm1] [single | threads_do_pool]`;
clientes `[ip_da_vm1] [nome] [segundos_da_operacao_demorada]`.
