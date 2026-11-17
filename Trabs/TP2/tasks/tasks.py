#!/usr/bin/env python3
"""tp2-tasks: implementação dos tipos de tarefa do TP2 (cron distribuído).

Uso:
    tasks <tipo> --input <dir> --output <dir> [--duration N] [--fail]

Tipos (todos leem um diretório de entrada e só escrevem em --output):
    report    agrega os CSV de vendas do diretório num CSV de totais por produto
    archive   cria um .tar.gz do diretório (backup)
    checksum  calcula o SHA-256 de cada ficheiro do diretório (manifesto SHA256SUMS)

Opções:
    --input     diretório de entrada (relativo ao diretório atual, /data no contentor)
    --output    diretório onde o resultado é escrito (já tem de existir: é criado pelo worker)
    --duration  segundos de espera antes de executar (simula uma tarefa longa)
    --fail      a tarefa falha de propósito (depois da espera)

Códigos de saída:
    0  sucesso               -> o worker reporta DONE
    1  falha da tarefa       -> o worker reporta FAILED (mensagem em stderr)
    2  erro de utilização    -> o worker reporta FAILED (argumentos inválidos)

Em caso de sucesso, --output contém o resultado e um summary.json, e o mesmo
resumo é escrito numa linha JSON em stdout. A tarefa não cria, apaga nem muda o
nome de diretórios de resultado: essa gestão é toda do worker.
"""

import argparse
import csv
import gzip
import hashlib
import json
import signal
import sys
import tarfile
import time
from datetime import datetime, timezone
from pathlib import Path

# Códigos de saída (ver docstring)
EXIT_OK = 0
EXIT_FAILED = 1
EXIT_USAGE = 2  # o mesmo que o argparse usa para argumentos inválidos


class TaskError(Exception):
    """Falha da tarefa: o worker deve reportar FAILED."""


class UsageError(Exception):
    """Argumentos ou parâmetros inválidos."""


# --------------------------------------------------------------- utilitários

def iso(ts):
    return datetime.fromtimestamp(ts, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def files_under(directory):
    """Ficheiros de um diretório (recursivamente), por ordem, para resultados reprodutíveis."""
    return sorted(p for p in directory.rglob("*") if p.is_file())


# -------------------------------------------------------------- tipos de tarefa

def task_report(src, out):
    """Agrega os CSV (date,product,region,quantity,price) por produto."""
    files = sorted(src.glob("*.csv"))
    if not files:
        raise TaskError(f"report: nenhum ficheiro CSV em {src}")

    totals = {}  # produto -> [quantidade, receita]
    rows = 0
    for f in files:
        try:
            with f.open(newline="", encoding="utf-8") as fh:
                reader = csv.DictReader(fh)
                missing = {"product", "quantity", "price"} - set(reader.fieldnames or [])
                if missing:
                    raise TaskError(f"report: {f.name} não tem as colunas {sorted(missing)}")
                for row in reader:
                    try:
                        quantity = int(row["quantity"])
                        price = float(row["price"])
                    except (ValueError, TypeError):  # TypeError: linha com colunas a menos
                        raise TaskError(f"report: {f.name}, linha {reader.line_num}: valor inválido")
                    t = totals.setdefault(row["product"], [0, 0.0])
                    t[0] += quantity
                    t[1] += quantity * price
                    rows += 1
        except (UnicodeDecodeError, csv.Error) as e:
            raise TaskError(f"report: {f.name} não é um CSV UTF-8 válido ({e})")

    with (out / "report.csv").open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(["product", "quantity", "revenue"])
        for product in sorted(totals):
            quantity, revenue = totals[product]
            w.writerow([product, quantity, f"{revenue:.2f}"])
    return {"files": len(files), "rows": rows, "products": len(totals)}


def task_archive(src, out):
    """Cria <nome>.tar.gz com todo o conteúdo do diretório de entrada."""
    files = files_under(src)
    archive = out / f"{src.name}.tar.gz"
    # mtime=0 no gzip e entradas ordenadas: os mesmos dados dão o mesmo arquivo
    with archive.open("wb") as raw, \
            gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz, \
            tarfile.open(fileobj=gz, mode="w") as tar:
        for f in files:
            tar.add(f, arcname=str(Path(src.name) / f.relative_to(src)), recursive=False)
    return {"files": len(files), "bytes": archive.stat().st_size}


def task_checksum(src, out):
    """Escreve SHA256SUMS (formato do sha256sum) com um hash por ficheiro."""
    files = files_under(src)
    lines = []
    for f in files:
        h = hashlib.sha256()
        with f.open("rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                h.update(chunk)
        lines.append(f"{h.hexdigest()}  {f.relative_to(src).as_posix()}\n")
    (out / "SHA256SUMS").write_text("".join(lines), encoding="utf-8")
    return {"files": len(files)}


TASKS = {"report": task_report, "archive": task_archive, "checksum": task_checksum}


# ---------------------------------------------------------------------- main

def main(argv=None):
    # PID 1 num contentor ignora SIGTERM sem handler; assim, `docker stop` termina de imediato
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))

    parser = argparse.ArgumentParser(prog="tasks", description="Executa um tipo de tarefa do TP2.")
    parser.add_argument("type", choices=sorted(TASKS), help="tipo de tarefa")
    parser.add_argument("--input", required=True, help="diretório de entrada")
    parser.add_argument("--output", required=True,
                        help="diretório onde o resultado é escrito (tem de existir)")
    parser.add_argument("--duration", type=float, default=0, metavar="SEGUNDOS",
                        help="espera antes de executar (simula uma tarefa longa)")
    parser.add_argument("--fail", action="store_true", help="falha de propósito, depois da espera")
    args = parser.parse_args(argv)  # termina com código 2 se os argumentos forem inválidos
    started = time.time()
    try:
        src = Path(args.input).resolve()
        out = Path(args.output).resolve()
        # o archive não pode incluir o próprio resultado
        if out == src or src in out.parents:
            raise UsageError("output não pode ficar dentro do diretório de entrada")
        if args.duration < 0:
            raise UsageError("duration não pode ser negativo")
        # os diretórios de resultado são geridos pelo worker: --output já tem de existir
        if not out.is_dir():
            raise UsageError(f"output: o diretório {out} não existe (deve ser criado pelo worker)")

        if args.duration > 0:
            time.sleep(args.duration)
        if args.fail:
            raise TaskError("falha simulada (--fail)")
        if not src.is_dir():
            raise TaskError(f"input: o diretório {src} não existe")

        details = TASKS[args.type](src, out)
    except (UsageError, TaskError, OSError) as e:
        usage = isinstance(e, UsageError)
        print(f"ERRO{' de utilização' if usage else ''}: {e}", file=sys.stderr)
        return EXIT_USAGE if usage else EXIT_FAILED

    finished = time.time()
    summary = {
        "type": args.type,
        "input": args.input,
        "started_at": iso(started),
        "finished_at": iso(finished),
        "elapsed_seconds": round(finished - started, 3),
        **details,
    }
    (out / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False) + "\n",
                                      encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
