from pathlib import Path
import sqlite3
import tempfile
import unittest
from selective_backup import snapshot


class SelectiveBackup(unittest.TestCase):
    def test_persistent_only_snapshot_and_no_overwrite(self):
        with tempfile.TemporaryDirectory(prefix='community-recovery-') as tmp:
            directory=Path(tmp); directory.chmod(0o700)
            source=directory/'source.db'; target=directory/'recovery.db'
            with sqlite3.connect(source) as db:
                db.executescript('''
                    CREATE TABLE members (id TEXT PRIMARY KEY, password TEXT);
                    CREATE TABLE threads (id TEXT PRIMARY KEY, author TEXT REFERENCES members(id), body TEXT);
                    CREATE INDEX thread_author ON threads(author);
                    CREATE TABLE thread_replies (id TEXT PRIMARY KEY, thread TEXT REFERENCES threads(id), body TEXT);
                    CREATE TABLE messages (id TEXT PRIMARY KEY, body TEXT);
                    CREATE TABLE reports (id TEXT PRIMARY KEY, snapshot_body TEXT);
                    CREATE TABLE moderator_requests (id TEXT PRIMARY KEY, body TEXT);
                    CREATE TABLE presence (id TEXT PRIMARY KEY);
                    CREATE TABLE mentions (id TEXT PRIMARY KEY);
                    INSERT INTO members VALUES ('member','password-hash');
                    INSERT INTO threads VALUES ('thread','member','persistent discussion');
                    INSERT INTO thread_replies VALUES ('reply','thread','persistent reply');
                    INSERT INTO messages VALUES ('message','TRANSIENT_CHAT_DO_NOT_BACK_UP');
                    INSERT INTO reports VALUES ('report','PRIVATE_REPORT_DO_NOT_BACK_UP');
                    INSERT INTO moderator_requests VALUES ('request','PRIVATE_REQUEST_DO_NOT_BACK_UP');
                ''')
            self.assertEqual(snapshot(source,target),{'members':1,'threads':1,'thread_replies':1})
            self.assertEqual(target.stat().st_mode & 0o777,0o600)
            with sqlite3.connect(target) as db:
                for table in ('messages','reports','moderator_requests','presence','mentions'):
                    self.assertEqual(db.execute('SELECT count(*) FROM '+table).fetchone()[0],0)
                self.assertEqual(db.execute('SELECT body FROM threads').fetchone()[0],'persistent discussion')
            for marker in (b'TRANSIENT_CHAT',b'PRIVATE_REPORT',b'PRIVATE_REQUEST'):
                self.assertNotIn(marker,target.read_bytes())
            with self.assertRaises(FileExistsError): snapshot(source,target)
            with self.assertRaises(ValueError): snapshot(source,source)
            with sqlite3.connect(source) as db: self.assertEqual(db.execute('SELECT count(*) FROM messages').fetchone()[0],1)

    def test_refuses_shared_backup_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory=Path(tmp); directory.chmod(0o755)
            with self.assertRaises(ValueError): snapshot(directory/'source.db',directory/'backup.db')


if __name__ == '__main__': unittest.main(verbosity=2)
