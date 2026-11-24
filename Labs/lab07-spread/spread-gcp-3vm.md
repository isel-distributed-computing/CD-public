# Spread com 3 daemons em 3 VMs no GCP

Este guia complementa o [laboratório do Spread](Laboratorio-spread.md) e assume que:

- as 3 VMs (`spread-vm1`, `spread-vm2`, `spread-vm3`) **já estão criadas** no GCP, na rede `default`;
- o Spread já está instalado em cada uma, como na [secção 2 do laboratório](Laboratorio-spread.md#2-instalar-o-daemon-spread).

> **Ponto essencial:** as redes VPC do GCP **não suportam *broadcast* nem *multicast* IP**. Por isso, um único `Spread_Segment` com endereço de *broadcast* (ex.: `10.132.0.255`) **não funciona**: os daemons arrancam sem erros, mas nunca se veem. Cada daemon tem de ficar **no seu próprio segmento**, com o IP interno da VM como endereço do segmento. O Spread passa então a usar apenas *unicast* UDP entre daemons.

---

## 1. IPs internos das VMs

Na consola web do GCP, em *Compute Engine → Instâncias de VM*, anote o **IP interno** de cada VM (ou use `hostname -I` dentro de cada VM). O `spread.conf` usa **sempre os IPs internos**, nunca os externos.

Neste guia usam-se os nomes de daemon `vm1`, `vm2`, `vm3` e os IPs `10.132.0.2`, `10.132.0.3`, `10.132.0.4`. Substitua pelos seus valores.

---

## 2. Regras de firewall

Na consola web, em *Rede VPC → Firewall*, acrescente as regras seguintes:

| Regra | Protocolo e portos | Origem | Para quê |
|---|---|---|---|
| `allow-spread-internal` | UDP 4803–4804 | IPs internos das 3 VMs | comunicação entre daemons |
| `allow-spread-client` | TCP 4803 | `0.0.0.0/0` | clientes no portátil, como no [cenário B](Laboratorio-spread.md#5-cenário-b-daemon-numa-vm-do-gcp) |

Se a regra `default-allow-internal` da rede `default` ainda existir, já autoriza o tráfego entre as VMs, e a primeira regra não é necessária.

---

## 3. Ficheiro de configuração `spread.conf`

Crie **o mesmo ficheiro** em `~/spread.conf` nas três VMs, porque os daemons verificam que a configuração é igual em todos:

```text
# spread.conf — 3 daemons, um por segmento (sem broadcast/multicast)

Spread_Segment 10.132.0.2:4803 {
    vm1    10.132.0.2
}

Spread_Segment 10.132.0.3:4803 {
    vm2    10.132.0.3
}

Spread_Segment 10.132.0.4:4803 {
    vm3    10.132.0.4
}

EventLogFile = /tmp/spread.log
```

Com `EventLogFile`, o log do daemon, incluindo as mudanças de *membership* entre daemons, passa a ser escrito em `/tmp/spread.log`.

---

## 4. Lançar os daemons

Em cada VM, como na [secção 4 do laboratório](Laboratorio-spread.md#4-lançar-o-daemon), com o nome do daemon correspondente:

```bash
spread -n vm1 -c ~/spread.conf     # na spread-vm1
spread -n vm2 -c ~/spread.conf     # na spread-vm2
spread -n vm3 -c ~/spread.conf     # na spread-vm3
```

Quando os três estão ativos, `tail -f /tmp/spread.log` mostra uma *membership* com os 3 daemons.

---

## 5. Testar

**Com o `spuser`** ([secção 6 do laboratório](Laboratorio-spread.md#6-primeiro-teste-com-o-spuser)): abra uma sessão SSH em cada VM, lance um `spuser` ligado ao daemon local e faça `j g1` em todos. Uma mensagem enviada a partir de qualquer VM chega aos membros das outras VMs.

**Com a aplicação `DemoSpread`** ([secção 7 do laboratório](Laboratorio-spread.md#7-aplicação-java-demospread-gradle)): lance membros no portátil, cada um ligado a um daemon diferente, indicando o **IP externo** da VM em *Program arguments*. O nome do grupo privado de cada membro inclui o nome do daemon a que está ligado (ex.: `#user1#vm2`), e é esse nome que se usa para enviar mensagens *unicast*.

Observe também o que acontece aos membros ligados aos restantes daemons quando um daemon é terminado (`Ctrl+C`) e quando volta a ser lançado.

---

## 6. Resolução de problemas

Além dos problemas da [secção 9 do laboratório](Laboratorio-spread.md#9-resolução-de-problemas):

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| `My proc id (...) is not in configuration` | O nome passado com `-n` ou o IP não batem certo com o `spread.conf` | Confirmar o IP interno (`hostname -I`) e o nome passado com `-n` |
| Os daemons arrancam, mas a *membership* tem só 1 daemon | Segmento com endereço de *broadcast*, ou firewall | Usar um segmento por daemon (secção 3); verificar a regra UDP 4803–4804 (secção 2) |
| Os daemons recusam juntar-se | `spread.conf` diferente entre VMs | Usar exatamente o mesmo ficheiro nas três |

No fim da aula, pare as três VMs na consola web para não gastar créditos.
