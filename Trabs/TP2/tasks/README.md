# tp2-tasks — imagem de tarefas do TP2

Imagem Docker única com os tipos de tarefa predefinidos do cron distribuído. O worker lança um contentor desta imagem por cada job, indicando o tipo de tarefa, o diretório de entrada do job (`input_dir`) e o diretório onde o resultado deve ser escrito.

## Construir a imagem

```bash
docker build -t tp2-tasks:1.0 .
```

## Tipos de tarefa

Todos os tipos leem um diretório de entrada e só escrevem no diretório de resultado, sem alterar os dados de entrada. A execução de uma tarefa com os mesmos dados de entrada dá sempre o mesmo resultado.

| Tipo       | O que faz                                                         | Resultado em `--output`                        |
| ---------- | ----------------------------------------------------------------- | ---------------------------------------------- |
| `report`   | Agrega os CSV do diretório (`date,product,region,quantity,price`) | `report.csv`: quantidade e receita por produto |
| `archive`  | Cria um arquivo comprimido do diretório (backup)                  | `<diretório>.tar.gz`                           |
| `checksum` | Calcula o SHA-256 de cada ficheiro do diretório (inventário)      | `SHA256SUMS`, no formato do `sha256sum`        |

## Contrato

```
docker run --rm -v /data:/data tp2-tasks:1.0 \
    <tipo> --input <dir> --output <dir> [--duration N] [--fail]
```

Este comando é para Linux (VMs ou WSL2). Em Windows com Docker Desktop, monta-se a pasta local de dados em vez de `/data` (ver [Windows](#windows-docker-desktop)).

| Opção        | Obrigatória | Descrição                                                                             |
| ------------ | ----------- | ------------------------------------------------------------------------------------- |
| `--input`    | sim         | Diretório de entrada: o `input_dir` do job.                                           |
| `--output`   | sim         | Diretório onde o resultado é escrito; criado pelo worker antes de lançar o contentor. |
| `--duration` | não (`0`)   | Segundos de espera antes de executar: simula uma tarefa longa.                        |
| `--fail`     | não         | A tarefa falha de propósito (depois da espera), com código `1`.                       |

- `--duration` e `--fail` não fazem parte do contrato gRPC: servem apenas para o worker simular tarefas longas ou falhas nas demonstrações (ex.: a partir de uma variável de ambiente do worker).
- Para obter uma falha sem `--fail`, basta registar uma tarefa com um `input_dir` que não existe sendo que a imagem termina com código `1`.
- O contentor só lê `--input` e escreve dentro de `--output`. Não cria nem apaga diretórios e por isso toda a gestão dos diretórios de resultado é feita pelo worker (ver [Integração no worker](#integração-no-worker)). Se `--output` não existir, o contentor termina com o código `2`.
- Os caminhos relativos contam a partir de `/data`, o diretório de trabalho do contentor (ex.: `input/sales` é `/data/input/sales`). `--output` não pode ficar dentro de `--input`.

| Código de saída | Significado                                             | Estado a reportar |
| --------------- | ------------------------------------------------------- | ----------------- |
| `0`             | Sucesso; resultado completo em `--output`               | `DONE`            |
| `1`             | A tarefa falhou (mensagem em stderr)                    | `FAILED`          |
| `2`             | Argumentos ou parâmetros inválidos (mensagem em stderr) | `FAILED`          |

Em caso de sucesso, a própria imagem escreve também em `--output` um `summary.json` (tipo, diretório de entrada, instantes de início e fim, contagens), e o mesmo resumo numa linha JSON em . O `summary.json` só é escrito no fim de uma execução bem-sucedida. Se a tarefa falhar ou o contentor for terminado a meio, não existe, e `--output` pode ficar com um resultado incompleto. Por ter os instantes da execução, este é o único ficheiro do resultado que muda de uma execução para outra com os mesmos dados.

## Dados de entrada

Os dados de entrada são fornecidos à parte, num arquivo (`tp2-data.zip`) que deve ser descomprimido para `/data` (ou para a pasta local de dados, em Windows). Contém:

| Diretório         | Conteúdo                        | Para                     |
| ----------------- | ------------------------------- | ------------------------ |
| `input/sales`     | 3 CSV × 50 linhas de vendas     | `report`                 |
| `input/documents` | 11 ficheiros em 3 subdiretórios | `archive`, `checksum`    |
| `results/`        | vazio                           | resultados das execuções |

## Exemplos

```bash
RUN="docker run --rm -v /data:/data tp2-tasks:1.0"
# diretórios de resultado: no sistema real, criados pelo worker
mkdir -p /data/results/t8/1761566465 /data/results/t7/1761566435 /data/results/t9/1761566465 /data/results/t8/1761566500 /data/results/t8/1761566530

$RUN report   --input input/sales     --output results/t8/1761566465
$RUN archive  --input input/documents --output results/t7/1761566435
$RUN checksum --input input/documents --output results/t9/1761566465

# tarefa longa (para terminar o worker a meio) e tarefa que falha
$RUN report --input input/sales --output results/t8/1761566500 --duration 30
$RUN report --input input/sales --output results/t8/1761566530 --fail
```

## Integração no worker

1. Receber o job (`exec_id`, `task_id`, `type`, `scheduled_time`, `input_dir`).
2. Criar o diretório de resultado `results/<taskId>/<scheduledTime>/` (criando também `results/<taskId>/`, se necessário). Se já existir, por o job ter sido reentregue, é reutilizado. A nova execução reescreve o mesmo diretório e, como as tarefas são deterministas, produz o mesmo resultado.
3. Lançar o contentor com `<type> --input <input_dir> --output results/<taskId>/<scheduledTime>`.
4. Se o código de saída for `0`, reportar `DONE` com esse `result_path`; caso contrário, reportar `FAILED` com o conteúdo de stderr no campo `error`. Uma tarefa que falha não é repetida.
5. Publicar o estado e só depois fazer ack do job.


## Windows (Docker Desktop)

Recomenda-se correr o worker dentro do WSL2 (Ubuntu): o ambiente fica igual ao das VMs Linux e todos os comandos deste README aplicam-se sem alterações. Para correr o worker diretamente em Windows:

- **Pasta de dados:** em Windows não existe `/data`. O worker usa uma pasta local configurável (ex.: `C:\tp2\data`) e monta-a no contentor em `/data`. Na linha de comandos (`cmd`):
  ```bat
  mkdir C:\tp2\data\results\t8\1761566465
  docker run --rm -v C:\tp2\data:/data tp2-tasks:1.0 report --input input/sales --output results/t8/1761566465
  ```
  O `mkdir` cria também os diretórios intermédios (`results\t8`) que ainda não existam.
- **Caminhos relativos:** o worker passa `--input` e `--output` relativos a `/data`, porque o mesmo caminho absoluto não existe nos dois lados (`C:\tp2\data\results\...` no worker, `/data/results/...` no contentor).

## Testar sem Docker

O script só precisa de Python 3.9 ou superior. Os caminhos relativos contam a partir do diretório atual, por isso basta correr o script a partir da pasta onde se descomprimiu `tp2-data.zip`:

```bash
cd /tmp/tp2-data
mkdir -p results/t1/100
python3 /caminho/para/tp2-tasks/tasks.py report --input input/sales --output results/t1/100
```
