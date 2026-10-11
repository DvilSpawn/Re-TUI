"""Private logical recovery snapshot; no transient chat/moderation bodies.

Restore only to a separate directory, never over live data. This includes password
hashes and auth secrets: keep root-only and encrypt before any off-site transfer.
"""
import argparse
import json
import os
from pathlib import Path
import sqlite3

PERSISTENT = {'_collections', '_params', '_migrations', '_superusers', 'members', 'threads', 'thread_replies'}


def snapshot(source, destination):
    source, destination = Path(source).resolve(), Path(destination).resolve()
    if source == destination: raise ValueError('Backup must not replace its source')
    if not destination.parent.is_dir() or destination.parent.stat().st_mode & 0o077:
        raise ValueError('Create a private backup directory (0700) first')
    os.close(os.open(destination, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600))
    counts = {}
    with sqlite3.connect(source.as_uri() + '?mode=ro', uri=True) as original, sqlite3.connect(destination) as backup:
        original.execute('PRAGMA query_only=ON'); original.execute('BEGIN')
        schema = original.execute("SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY type='table' DESC").fetchall()
        tables = [(name, sql) for kind, name, sql in schema if kind == 'table']
        for _, sql in tables: backup.execute(sql)
        for name, _ in tables:
            if name not in PERSISTENT: continue
            quoted = '"' + name.replace('"', '""') + '"'
            rows = original.execute('SELECT * FROM ' + quoted)
            columns = ','.join('"' + column[0].replace('"', '""') + '"' for column in rows.description)
            placeholders = ','.join('?' for _ in rows.description)
            count = 0
            while batch := rows.fetchmany(100):
                backup.executemany('INSERT INTO '+quoted+' ('+columns+') VALUES ('+placeholders+')', batch)
                count += len(batch)
            counts[name] = count
        for kind, _, sql in schema:
            if kind != 'table': backup.execute(sql)
        if backup.execute('PRAGMA integrity_check').fetchone()[0] != 'ok': raise RuntimeError('Invalid recovery snapshot')
        if backup.execute('PRAGMA foreign_key_check').fetchall(): raise RuntimeError('Broken recovery references')
    return counts


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--destination', type=Path, required=True)
    args=parser.parse_args()
    print(json.dumps({'persistent_counts':snapshot(args.source,args.destination),'transient_bodies_copied':False}))
