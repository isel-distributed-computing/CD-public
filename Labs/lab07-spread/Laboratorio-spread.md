# Laboratório 04 — Spread: daemon e comunicação em grupo

**Objetivo:** instalar e configurar o *middleware* Spread (um *daemon*) e usar a API Java para pôr vários processos a comunicar em *multicast* fiável, observando as notificações de *membership*. É a base da fase 3 do TP2, em que as réplicas do agendador formam um grupo Spread para eleger o líder e difundir as alterações de estado.

Neste laboratório usa-se um único daemon Spread. Os processos (membros) correm na máquina do aluno e ligam-se a esse daemon por TCP. A configuração com 3 daemons em 3 VMs, necessária na fase 4, é tratada no guia [`spread-gcp-3vm.md`](spread-gcp-3vm.md).

---

## 1. Cenários

O daemon Spread compila e corre em Linux e macOS. Escolha o cenário de acordo com o sistema operativo da sua máquina:

| Cenário | Máquina do aluno                                                   | Onde corre o daemon         | Os membros ligam-se a |
| ------- | ------------------------------------------------------------------ | --------------------------- | --------------------- |
| **A**   | Linux (nativo ou VM local) ou macOS                                | Na própria máquina          | `localhost`           |
| **B**   | Windows (ou outro caso em que não seja possível compilar o Spread) | Numa VM Ubuntu 24.04 no GCP | IP externo da VM      |

Em ambos os cenários, a aplicação Java (`DemoSpread`) corre na máquina do aluno. Requisitos nessa máquina: **JDK 21 ou superior** e **IntelliJ IDEA**.

> **Windows com WSL2:** em alternativa ao cenário B, pode seguir o cenário A dentro de uma distribuição Ubuntu no WSL2 e correr a aplicação Java no Windows, ligada a `localhost`. Por omissão, o WSL2 encaminha para o Windows os portos TCP que estão à escuta na distribuição Linux.

---

## 2. Instalar o daemon Spread

Cenário A: na máquina do aluno. Cenário B: na VM, depois de a criar (ver [secção 5](#5-cenário-b-daemon-numa-vm-do-gcp)).

### 2.1 Dependências

```bash
# Linux (Debian / Ubuntu)
sudo apt update
sudo apt install -y build-essential wget

# macOS: ferramentas de linha de comando do Xcode (compilador e make)
xcode-select --install
```

### 2.2 Descarregar, compilar e instalar

```bash
cd ~
wget https://www.spread.org/direct_download/spread-src-5.0.1.tar.gz   # no macOS: curl -LO <mesmo URL>
tar xzf spread-src-5.0.1.tar.gz
cd spread-src-5.0.1
./configure
make
sudo make install          # instala em /usr/local/{sbin,bin,lib,include}
sudo ldconfig              # só em Linux
```

Verificar:

```bash
which spread spuser        # /usr/local/sbin/spread e /usr/local/bin/spuser
```

Se `/usr/local/sbin` não estiver no `PATH` o `spred`não será encontrado (comum no macOS), use o caminho completo `/usr/local/sbin/spread` ou acrescente-o ao `PATH`.

---

## 3. Configuração mínima: `spread.conf`

Um ficheiro de configuração Spread descreve **segmentos** (`Spread_Segment`), e cada segmento lista os daemons que pertencem à configuração, com um nome e um endereço IP. Com um só daemon, basta um segmento com um daemon.

### Cenário A: daemon local

Crie `~/spread.conf`:

```text
Spread_Segment 127.0.0.255:4803 {
    localhost   127.0.0.1
}
```

### Cenário B: daemon na VM

Na VM, com o **IP interno** da VM (ver `hostname -I`), por exemplo `10.132.0.2`:

```text
Spread_Segment 10.132.0.2:4803 {
    vm1   10.132.0.2
}
```

Notas:

- O primeiro campo de cada linha é o **nome do daemon** (`localhost`, `vm1`). Esse nome aparece no nome dos grupos privados dos membros, por exemplo `#user1#vm1`, e é necessário para enviar mensagens *unicast* (ver [7.3](#73-mensagens-unicast)).
- `4803` é o porto do daemon. Os clientes ligam-se a ele por TCP. Entre daemons usam-se os portos UDP 4803 e 4804, mas neste laboratório só há um daemon.
- Num segmento com um único daemon, o endereço do segmento (o que vem depois de `Spread_Segment`) é praticamente ignorado. Na VM usa-se o próprio IP interno, que é a forma que funciona no GCP quando houver vários daemons, porque as redes VPC não suportam *broadcast* nem *multicast* (ver [`spread-gcp-3vm.md`](spread-gcp-3vm.md)).
- Sem mais nada na configuração, o daemon aceita ligações de clientes em **todas as interfaces** da máquina. No cenário B, é isso que permite ligar a partir do portátil através do IP externo da VM.

---

## 4. Lançar o daemon

Lance o daemon **sem `sudo`**, indicando com `-n` o nome com que aparece no `spread.conf`:

```bash
# Cenário A
spread -n localhost -c ~/spread.conf

# Cenário B (na VM)
spread -n vm1 -c ~/spread.conf
```

> Se for lançado como `root`, o daemon tenta fazer `chroot` para `/var/run/spread` e mudar para o utilizador `spread`, e termina se estes não existirem. Como utilizador normal, não o faz, e mostra apenas o aviso `not running as root, won't chroot`, que é esperado.

> No cenário A, o arranque mostra também um `Parse Warning` com `Segment configured with a loopback address 127.0.0.255!` (e `Offending token: }`). É apenas um aviso, a linha seguinte, `Successfully configured Segment 0 ... with 1 procs`, mostra sucesso da operação.

O daemon fica em primeiro plano e escreve o seu log na consola. Deixe este terminal aberto, ou lance-o em segundo plano:

```bash
nohup spread -n vm1 -c ~/spread.conf > ~/spread.out 2>&1 &
```

Confirme que o daemon está à escuta no porto TCP 4803:

```bash
ss -tlnp | grep 4803                     # Linux
lsof -nP -iTCP:4803 -sTCP:LISTEN         # macOS
```

---

## 5. Cenário B: daemon numa VM do GCP

Este passo só é necessário no cenário B. Na consola web do GCP:

1. Em *Compute Engine → Instâncias de VM*, crie uma VM `spread-vm1` (por exemplo, `e2-small` em `europe-west1-b`) com a imagem **Ubuntu 24.04 LTS**.
2. Ligue-se à VM por SSH (botão *SSH* na lista de instâncias), instale o Spread ([secção 2](#2-instalar-o-daemon-spread)), configure-o ([secção 3](#3-configuração-mínima-spreadconf)) e lance-o ([secção 4](#4-lançar-o-daemon)).
3. Em *Rede VPC → Firewall*, crie a regra `allow-spread-client`, na rede `default`, que permite **TCP 4803** com origem `0.0.0.0/0`.

> ⚠️ O protocolo entre clientes e daemon **não tem autenticação nem cifra**. Com esta regra, o daemon fica acessível a qualquer pessoa que saiba o IP externo da VM. Pare a VM quando não a estiver a usar.

O IP externo da VM aparece na coluna *IP externo* da lista de instâncias. Esse IP muda quando a VM é parada e reiniciada. No fim da aula, pare a VM na consola para não gastar créditos.

---

## 6. Primeiro teste com o `spuser`

O `spuser` é um cliente de teste em C, instalado com o Spread. Corre na máquina do daemon: no cenário A, na própria máquina; no cenário B, em sessões SSH na VM.

Abra dois terminais e, em cada um, lance um `spuser` com um nome diferente:

```bash
spuser -u u1 -s 4803      # no segundo terminal: -u u2
```

Comandos do menu:

```text
j <grupo>    join a um grupo
l <grupo>    leave de um grupo
s <grupo>    enviar uma mensagem ao grupo (escreve-se o texto a seguir)
b <grupo>    enviar uma rajada de mensagens
q            sair
```

Faça `j g1` nos dois `spuser` e envie uma mensagem a partir de um deles. As mensagens e as notificações de *membership* aparecem automaticamente em todos os membros do grupo, incluindo no que enviou.

---

## 7. Aplicação Java `DemoSpread` (Gradle)

O projeto [`DemoSpread-Gradle`](DemoSpread-Gradle) contém a aplicação de demonstração apresentada nas aulas. A biblioteca Java do Spread não está no Maven Central, por isso o jar `spread-5.0.1.jar` é distribuído com o projeto, na diretoria `libs/`, e é referido diretamente no `build.gradle.kts`.

### 7.1 Compilar e executar no IntelliJ

1. Abra a diretoria `DemoSpread-Gradle` no IntelliJ (*File → Open*). O IntelliJ reconhece o projeto Gradle e importa-o.
2. Abra a classe `DemoSpread` e execute o `main` (ícone ▶ ao lado do método). A aplicação liga-se por omissão a `localhost` (cenário A).
3. No cenário B, indique o IP externo da VM em *Run → Edit Configurations… → Program arguments*.
4. Cada membro é um processo. Para lançar vários, ative *Modify options → Allow multiple instances* na mesma configuração e execute-a várias vezes: cada membro fica num separador próprio da janela *Run*, onde se interage com o menu.

Ao arrancar, a aplicação pede o **nome privado** do membro. Esse nome tem de ser **único entre os clientes do mesmo daemon** e ter **no máximo 10 caracteres**.

### 7.2 Estrutura do código

| Classe                    | Papel                                                                                                                                                                                |
| ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `DemoSpread`              | Menu de consola: join, envio de mensagens, leave, *unicast*                                                                                                                          |
| `GroupMember`             | Liga-se ao daemon (`SpreadConnection.connect`), faz join/leave (`SpreadGroup`) e envia mensagens para grupos ou em *unicast*                                                         |
| `SpreadMessages`          | Constrói e envia as mensagens (`SpreadMessage`, `connection.multicast`) e lê o seu conteúdo: converte o texto em bytes e vice-versa, sempre em UTF-8                                 |
| `MessageHandling`         | `BasicMessageListener`: recebe todas as mensagens (regulares e de *membership*) e responde a pedidos *unicast*                                                                       |
| `AdvancedMessageHandling` | `AdvancedMessageListener`: alternativa com um método para mensagens regulares e outro para mensagens de *membership* (comentada em `GroupMember`)                                    |
| `PrintMessages`           | Mostra os detalhes de cada mensagem: tipo de serviço, grupos, membros e causa da mudança de *membership*                                                                             |
| `SafeListener`            | Embrulha os listeners para que nenhuma exceção saia dos callbacks: uma exceção não apanhada termina a *thread* de receção do Spread e o membro deixa de receber mensagens, sem aviso |

A ligação ao daemon é feita em `GroupMember`:

```java
connection.connect(InetAddress.getByName(daemonIP), 4803, memberName, false, true);
```

O último argumento (`groupMembership = true`) pede ao daemon que envie também as mensagens de *membership*. As mensagens são enviadas com `msg.setSafe()` (em `SpreadMessages.send`), o serviço com mais garantias de entrega e ordem.

### 7.3 Mensagens *unicast*

Cada ligação tem um **grupo privado**, com o nome `#<nome do membro>#<nome do daemon>` (ex.: `#user1#localhost` ou `#user1#vm1`). Para enviar uma mensagem só a um membro, envia-se para o grupo privado dele (opção 5 do menu: pede o nome do membro e o nome do daemon). Se o texto for `privateRequest`, o destinatário responde ao remetente, também por *unicast*.

---

## 8. Exercícios

Para estes exercícios, o ideal é usar o cenário B: o daemon corre na VM do GCP e todos os membros do grupo, usando cada um a sua máquina, ligam-se a esse mesmo daemon através do IP externo da VM. Assim, a comunicação entre os membros passa de facto pela rede, como acontecerá no TP2.

Lance vários membros ligados ao daemon e use o menu da aplicação para verificar os seguintes comportamentos.

**Envio de mensagens**

1. Todos os membros de um grupo recebem as mensagens enviadas para esse grupo, incluindo o membro que as enviou.
2. Um processo que não pertence ao grupo também lhe pode enviar mensagens: os grupos Spread são abertos.
3. Um processo, pertença ou não a um grupo, pode enviar uma mensagem *unicast* a outro processo, através do grupo privado deste (ver [7.3](#73-mensagens-unicast)).

**Notificações de *membership***

4. Quando um membro entra ou sai do grupo, os restantes são notificados e conseguem identificar qual foi (`JOIN of …`, `LEAVE of …`).
5. Quando um membro falha, sem fazer leave (termine o processo no IntelliJ ou com `kill -9`), os restantes recebem a notificação da saída forçada (`DISCONNECT of …`).
6. Termine o daemon com os membros ligados e observe o que acontece a esses membros.

**Ordem das mensagens**

7. Envie, a partir de dois membros ao mesmo tempo, rajadas de mensagens para o mesmo grupo (opção 3 do menu) e confirme que todos os membros recebem as mensagens pela mesma ordem.

**Contador replicado**

8. Altere a aplicação para que os membros do grupo `counter` mantenham, cada um, uma cópia de um contador inteiro, inicialmente a 0, e fiquem sempre com o mesmo valor:
   - acrescente ao menu as opções *Somar* e *Multiplicar*, que pedem um inteiro `n` e enviam ao grupo a mensagem `ADD n` ou `MUL n`, e a opção *Mostrar*, que escreve o valor local do contador;
   - o membro que envia **não altera o contador** ao enviar: todos os membros, incluindo o que enviou, aplicam a operação quando recebem a mensagem do grupo (em `MessageHandling`);
   - as mensagens são enviadas com `setSafe()`, para que todos as recebam pela mesma ordem.

   Teste com três membros: envie, a partir de dois membros ao mesmo tempo, operações `ADD` e `MUL` e confirme com *Mostrar* que os três ficam com o mesmo valor. Como somar e multiplicar não comutam (`(0 + 2) × 3 ≠ 0 × 3 + 2`), os valores só coincidem porque as operações são aplicadas pela mesma ordem em todos os membros.

   Junte um quarto membro ao grupo depois de já terem sido feitas operações e observe que o seu contador começa em 0, diferente do dos restantes. Para corrigir este aspeto, quando um membro novo entra no grupo, um dos membros antigos tem de lhe enviar o valor atual. Esta é a ideia do *state transfer* que o agendador do TP2 tem de implementar (fase 3).

---

## 9. Resolução de problemas

| Sintoma                                                                    | Causa provável                                                     | O que fazer                                                                                                                                                                         |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `My proc ... is not in configuration!`                                     | O nome passado com `-n` não existe no `spread.conf`                | Usar exatamente o nome da linha do daemon (`localhost`, `vm1`)                                                                                                                      |
| O daemon termina com um erro ao mudar de utilizador ou com `FAILED chroot` | Foi lançado com `sudo`                                             | Lançar como utilizador normal                                                                                                                                                       |
| `unable to bind to port`                                                   | Já há um daemon a correr nesse porto                               | `pkill spread` ou esperar alguns segundos                                                                                                                                           |
| `spread: error while loading shared libraries`                             | Cache do linker desatualizada (Linux)                              | `sudo ldconfig`                                                                                                                                                                     |
| Java: `There was an error connecting to the daemon`                        | Daemon parado, IP errado, porto bloqueado ou nome privado repetido | Confirmar que o daemon está à escuta ([secção 4](#4-lançar-o-daemon)), a regra de firewall e o IP externo da VM ([secção 5](#5-cenário-b-daemon-numa-vm-do-gcp)), e usar outro nome |
| Ligação remota deixou de funcionar                                         | O IP externo da VM mudou                                           | Atualizar o IP passado à aplicação                                                                                                                                                  |

---

## 10. Documentação da API Java

- http://www.spread.org/docs/javadocs/index.html
- http://www.spread.org/docs/javadocs/spread/SpreadConnection.html
- http://www.spread.org/docs/javadocs/spread/SpreadMessage.html
- http://www.spread.org/docs/javadocs/spread/BasicMessageListener.html
- http://www.spread.org/docs/javadocs/spread/AdvancedMessageListener.html
