import os,sqlite3,tempfile,time,unittest
from pathlib import Path
from operations import rotate,check

class Operations(unittest.TestCase):
    def test_rotation_is_private_selective_and_reconciles_deletions(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);source=root/'source.db';recovery=root/'recovery';recovery.mkdir(mode=0o700)
            with sqlite3.connect(source) as db:
                db.executescript("CREATE TABLE members(id TEXT PRIMARY KEY,handle TEXT,active INTEGER,moderator INTEGER); CREATE TABLE threads(id TEXT PRIMARY KEY,author TEXT); CREATE TABLE thread_replies(id TEXT PRIMARY KEY,author TEXT,thread TEXT); CREATE TABLE messages(id TEXT,created TEXT,body TEXT); CREATE TABLE reports(id TEXT,expires_at TEXT,resolved_at TEXT,created TEXT); CREATE TABLE moderator_requests(id TEXT,expires_at TEXT,resolved_at TEXT,created TEXT); CREATE TABLE presence(id TEXT,expires_at TEXT); INSERT INTO members VALUES('a','dvil',1,1); INSERT INTO members VALUES('b','alice',1,0); INSERT INTO threads VALUES('t','b'); INSERT INTO thread_replies VALUES('r','b','t'); INSERT INTO messages VALUES('m',datetime('now'),'TRANSIENT');")
            now=time.time();first=rotate(source,recovery,now)
            self.assertEqual(first.stat().st_mode & 0o777,0o600)
            with sqlite3.connect(first) as db:self.assertEqual(db.execute('select count(*) from messages').fetchone()[0],0)
            old=recovery/'persistent-20000101T000000Z.db';old.write_bytes(first.read_bytes());os.utime(old,(now-8*86400,now-8*86400))
            unrelated=recovery/'unrelated.db';unrelated.write_bytes(b'keep')
            with sqlite3.connect(source) as db:db.executescript("DELETE FROM thread_replies; DELETE FROM threads; DELETE FROM members WHERE id='b';")
            second=rotate(source,recovery,now+2)
            self.assertFalse(old.exists());self.assertEqual(unrelated.read_bytes(),b'keep')
            for p in (first,second):
                with sqlite3.connect(p) as db:
                    self.assertEqual(db.execute('select id from members').fetchall(),[('a',)])
                    self.assertEqual(db.execute('select count(*) from threads').fetchone()[0],0)
            self.assertTrue(check(source,recovery,False)['healthy'])
            with sqlite3.connect(source) as db:db.execute('UPDATE members SET moderator=0')
            self.assertIn('sole_moderator_missing_or_changed',check(source,recovery,False)['alerts'])

if __name__=='__main__':unittest.main(verbosity=2)
