"""Version 3 contract exercised against the pinned real PocketBase executable."""
import test_backend as backend
import urllib.parse
import uuid

class V3(backend.Contract):
    def test_14_help_channel(self):
        h,p,a=self.signup(); t=self.login(h,p)
        s,r=self.call('POST','/api/collections/threads/records',{'author':a['id'],'channel':'help','title':'Help','body':'Question','client_id':str(uuid.uuid4())},t)
        self.assertEqual(s,200)
        self.assertEqual(r['channel'],'help')

    def test_15_safe_directory(self):
        h,p,a=self.signup(); t=self.login(h,p)
        path='/api/community/users'
        self.assertEqual(self.call('GET',path)[0],401)
        for q in (h,h.upper(),h[:6],h[2:8],h[::2]):
            s,r=self.call('GET',path+'?'+urllib.parse.urlencode({'q':q}),token=t)
            self.assertEqual(s,200)
            self.assertEqual(set(r),{'page','perPage','hasMore','items'})
            self.assertIn(a['id'],[x['id'] for x in r['items']])
            for x in r['items']: self.assertEqual(set(x),{'id','handle','online'})
        for query in ('q='+('a'*25),'q=%27%20OR%201%3D1','perPage=21','page=0','page=1001','page=nan','expand=author','unknown=1','q=a&q=b'):
            self.assertEqual(self.call('GET',path+'?'+query,token=t)[0],400,query)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+a['id'],{'moderator':True},self.admin,True)[0],200)
        self.assertEqual(self.call('GET',path+'?q='+h,token=t)[1]['items'],[{'id':a['id'],'handle':h,'online':False}])
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+a['id'],{'active':False},self.admin,True)[0],200)
        self.assertEqual(self.call('GET',path,token=t)[0],403)
        h2,p2,b=self.signup(); t2=self.login(h2,p2)
        self.assertEqual(self.call('GET',path+'?q='+h,token=t2)[1]['items'],[])
        self.assertEqual(self.call('DELETE','/api/collections/members/records/'+a['id'],token=self.admin,admin=True)[0],204)
        self.assertEqual(self.call('GET',path+'?q='+h,token=t2)[1]['items'],[])

    def test_16_foreground_presence(self):
        import sqlite3
        from datetime import datetime,timezone
        h,p,a=self.signup(); h2,p2,b=self.signup(); t=self.login(h,p); t2=self.login(h2,p2)
        path='/api/community/presence'
        self.assertEqual(self.call('POST',path,{'active':True})[0],401)
        for payload in ({},{'active':'true'},{'active':True,'id':b['id']},{'active':True,'expires_at':'2099-01-01'}):
            self.assertEqual(self.call('POST',path,payload,t)[0],400)
        s,r=self.call('POST',path,{'active':True},t)
        self.assertEqual(s,200); self.assertEqual(set(r),{'online','expires_at'}); self.assertTrue(r['online'])
        delta=(datetime.fromisoformat(r['expires_at'].replace(' ','T').replace('Z','+00:00'))-datetime.now(timezone.utc)).total_seconds()
        self.assertTrue(80<delta<=90)
        def online():
            return self.call('GET','/api/community/users?q='+h,token=t2)[1]['items'][0]['online']
        self.assertTrue(online())
        self.assertEqual(self.call('POST',path,{'active':False},t)[1],{'online':False,'expires_at':''})
        self.assertFalse(online())
        self.assertEqual(self.call('POST',path,{'active':True},t)[0],200)
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            self.assertEqual(db.execute('SELECT member FROM presence WHERE member IN (?,?)',(a['id'],b['id'])).fetchall(),[(a['id'],)])
            db.execute('UPDATE presence SET expires_at=? WHERE member=?',('2000-01-01 00:00:00.000Z',a['id']))
        self.assertFalse(online())
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            db.execute('UPDATE presence SET expires_at=? WHERE member=?',('2099-01-01 00:00:00.000Z',a['id']))
        self.assertFalse(online())
        for _ in range(22): self.assertEqual(self.call('POST',path,{'active':True},t)[0],200)
        self.assertEqual(self.call('POST','/api/collections/messages/records',{'author':a['id'],'body':'quota independent','client_id':str(uuid.uuid4())},t)[0],200)
        self.assertIn(self.call('GET','/api/collections/presence/records',token=t)[0],(403,404))
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+a['id'],{'active':False},self.admin,True)[0],200)
        self.assertEqual(self.call('POST',path,{'active':True},t)[0],403)
        self.assertEqual(self.call('DELETE','/api/collections/members/records/'+a['id'],token=self.admin,admin=True)[0],204)
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM presence WHERE member=?',(a['id'],)).fetchone()[0],0)

    def test_17_mentions_metadata_and_cascade(self):
        import sqlite3
        h,p,a=self.signup(); h2,p2,b=self.signup(); t=self.login(h,p); t2=self.login(h2,p2)
        path='/api/community/mentions'
        self.assertEqual(self.call('GET',path)[0],401)
        def mentions(token=t2):
            s,r=self.call('GET',path,token=token); self.assertEqual(s,200); return r['items']
        payload={'author':a['id'],'body':'@'+h2.upper()+' @'+h2+' @'+h+' x@'+h2+' @no_such_user','client_id':str(uuid.uuid4())}
        s,m=self.call('POST','/api/collections/messages/records',payload,t); self.assertEqual(s,200)
        items=mentions(); self.assertEqual(len(items),1)
        item=items[0]; self.assertEqual(set(item),{'id','kind','source_id','thread_id','channel','author_handle','created','expires_at'})
        self.assertEqual((item['kind'],item['source_id'],item['thread_id'],item['channel'],item['author_handle']),('message',m['id'],'','general',h))
        self.assertTrue(item['expires_at']); self.assertEqual(mentions(t),[])
        self.assertEqual(self.call('POST','/api/collections/messages/records',payload,t)[0],400)
        self.assertEqual([x['id'] for x in mentions()],[item['id']])
        for body in ('x@'+h2,'@'+h2+'extra','@'+h2+'-','mail@'+h2+'.com'):
            self.assertEqual(self.call('POST','/api/collections/messages/records',dict(payload,body=body,client_id=str(uuid.uuid4())),t)[0],200)
        self.assertEqual(len(mentions()),1)
        s,root=self.call('POST','/api/collections/threads/records',{'author':a['id'],'channel':'help','title':'Question','body':'@'+h2,'client_id':str(uuid.uuid4())},t); self.assertEqual(s,200)
        s,reply=self.call('POST','/api/collections/thread_replies/records',{'author':a['id'],'thread':root['id'],'body':'@'+h2,'client_id':str(uuid.uuid4())},t); self.assertEqual(s,200)
        items=mentions(); self.assertEqual(len(items),3)
        bykind={x['kind']:x for x in items}
        for kind,source in (('thread',root),('thread_reply',reply)):
            self.assertEqual((bykind[kind]['source_id'],bykind[kind]['thread_id'],bykind[kind]['channel'],bykind[kind]['expires_at']),(source['id'],root['id'],'help',''))
        page=self.call('GET',path+'?perPage=1',token=t2)[1]; self.assertTrue(page['hasMore']); self.assertEqual(len(page['items']),1)
        self.assertNotEqual(page['items'][0]['id'],self.call('GET',path+'?perPage=1&page=2',token=t2)[1]['items'][0]['id'])
        for query in ('perPage=51','page=0','expand=message','recipient='+a['id']): self.assertEqual(self.call('GET',path+'?'+query,token=t2)[0],400)
        self.assertIn(self.call('GET','/api/collections/mentions/records',token=t2)[0],(403,404))
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            db.execute('UPDATE messages SET created=? WHERE id=? AND author=?',('2000-01-01 00:00:00.000Z',m['id'],a['id']))
        self.assertEqual(len(mentions()),2)
        self.assertEqual(self.call('POST','/api/crons/community_retention',{},self.admin,True)[0],204)
        self.assertEqual(self.call('DELETE','/api/collections/threads/records/'+root['id'],token=self.admin,admin=True)[0],204)
        self.assertEqual(mentions(),[])
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+b['id'],{'active':False},self.admin,True)[0],200)
        self.assertEqual(self.call('GET',path,token=t2)[0],403)
        self.assertEqual(self.call('POST','/api/collections/messages/records',dict(payload,client_id=str(uuid.uuid4())),t)[0],200)
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM mentions WHERE recipient=?',(b['id'],)).fetchone()[0],0)

    def test_18_mention_cap_permissions_and_identity(self):
        import secrets,sqlite3
        h,p,a=self.signup(); t=self.login(h,p)
        targets=[]
        for _ in range(11):
            handle='t_'+secrets.token_hex(6); password=secrets.token_urlsafe(30)
            s,r=self.call('POST','/api/collections/members/records',{'handle':handle,'password':password,'passwordConfirm':password,'active':True},self.admin,True)
            self.assertEqual(s,200); targets.append((handle,password,r))
        body=' '.join('@'+h for h,p,r in targets)
        payload={'author':a['id'],'body':body,'client_id':str(uuid.uuid4())}
        self.assertEqual(self.call('POST','/api/collections/messages/records',payload,t)[0],400)
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM messages WHERE author=? AND client_id=?',(a['id'],payload['client_id'])).fetchone()[0],0)
        payload['body']=' '.join('@'+h for h,p,r in targets[:10])
        s,m=self.call('POST','/api/collections/messages/records',payload,t); self.assertEqual(s,200)
        recipient=targets[0]; token=self.login(recipient[0],recipient[1]); path='/api/community/mentions'
        self.assertEqual(len(self.call('GET',path,token=token)[1]['items']),1)
        if not self.base.startswith('https:'):
            s,schema=self.call('GET','/api/collections/messages',token=self.admin,admin=True); self.assertEqual(s,200)
            try:
                self.assertEqual(self.call('PATCH','/api/collections/messages',{'viewRule':None},self.admin,True)[0],200)
                self.assertEqual(self.call('GET',path,token=token)[1]['items'],[])
            finally: self.assertEqual(self.call('PATCH','/api/collections/messages',{'viewRule':schema['viewRule']},self.admin,True)[0],200)
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            db.execute('UPDATE messages SET created=? WHERE id=? AND author=?',('2099-01-01 00:00:00.000Z',m['id'],a['id']))
        self.assertEqual(self.call('GET',path,token=token)[1]['items'],[])
        self.assertEqual(self.call('DELETE','/api/collections/members/records/'+recipient[2]['id'],token=self.admin,admin=True)[0],204)
        s,new=self.call('POST','/api/collections/members/records',{'handle':recipient[0],'password':recipient[1],'passwordConfirm':recipient[1]},self.admin,True); self.assertEqual(s,200)
        self.assertNotEqual(new['id'],recipient[2]['id'])
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM mentions WHERE recipient IN (?,?)',(new['id'],recipient[2]['id'])).fetchone()[0],0)

    def test_19_expired_presence_purge(self):
        import sqlite3,time
        h,p,a=self.signup(); t=self.login(h,p)
        self.assertEqual(self.call('POST','/api/community/presence',{'active':True},t)[0],200)
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            db.execute('UPDATE presence SET expires_at=? WHERE member=?',('2000-01-01 00:00:00.000Z',a['id']))
        self.assertEqual(self.call('POST','/api/crons/community_retention',{},self.admin,True)[0],204)
        for _ in range(40):
            with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
                count=db.execute('SELECT count(*) FROM presence WHERE member=?',(a['id'],)).fetchone()[0]
            if count==0: break
            time.sleep(.05)
        self.assertEqual(count,0)

    def test_20_directory_ranking_and_auth_revocation(self):
        import secrets,time
        q='s'+secrets.token_hex(5)
        handles=[q,q+'a',q+'b','z'+q,'z'.join(q)]
        password=secrets.token_urlsafe(30); users=[]
        for handle in handles:
            s,r=self.call('POST','/api/collections/members/records',{'handle':handle,'password':password,'passwordConfirm':password,'active':True},self.admin,True)
            self.assertEqual(s,200); users.append(r)
        token=self.login(handles[0],password); online_token=self.login(handles[2],password)
        self.assertEqual(self.call('POST','/api/community/presence',{'active':True},online_token)[0],200)
        found=[]
        for page in (1,2,3):
            s,r=self.call('GET','/api/community/users?'+urllib.parse.urlencode({'q':q,'page':page,'perPage':2}),token=token)
            self.assertEqual(s,200); found.extend(x['id'] for x in r['items']); self.assertEqual(r['hasMore'],page<3)
        self.assertEqual(found,[users[i]['id'] for i in (0,2,1,3,4)])
        s,short=self.call('POST','/api/collections/members/impersonate/'+users[0]['id'],{'duration':1},self.admin,True); self.assertEqual(s,200)
        time.sleep(2)
        endpoints=[('GET','/api/community/users',None),('GET','/api/community/mentions',None),('POST','/api/community/presence',{'active':True})]
        for method,path,body in endpoints: self.assertEqual(self.call(method,path,body,short['token'])[0],401)
        replacement=secrets.token_urlsafe(30)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+users[0]['id'],{'password':replacement,'passwordConfirm':replacement},self.admin,True)[0],200)
        for method,path,body in endpoints: self.assertEqual(self.call(method,path,body,token)[0],401)

    def test_21_mentions_scan_limit_is_not_exhaustion(self):
        import secrets,sqlite3
        if not self.binary or self.base!='http://127.0.0.1:18095':
            self.skipTest('local-only bulk fixture and source permission rule')
        h,p,a=self.signup(); h2,p2,b=self.signup(); token=self.login(h2,p2); author=self.login(h,p)
        roots=[]
        for title in ('accessible','denied'):
            s,r=self.call('POST','/api/collections/threads/records',{'author':a['id'],'channel':'help','title':title,'body':'@'+h2,'client_id':str(uuid.uuid4())},author)
            self.assertEqual(s,200); roots.append(r)
        path='/api/community/mentions'
        s,schema=self.call('GET','/api/collections/threads',token=self.admin,admin=True); self.assertEqual(s,200)
        ids=[secrets.token_hex(7)+'a' for _ in range(1000)]
        with sqlite3.connect(str(backend.Path(self.data_dir)/'data.db')) as db:
            db.execute('DELETE FROM mentions WHERE recipient=?',(b['id'],))
            columns=[row[1] for row in db.execute('PRAGMA table_info(threads)') if row[1]!='id']
            names=','.join('"'+name+'"' for name in columns)
            values=','.join('?' if name=='client_id' else '"'+name+'"' for name in columns)
            db.executemany('INSERT INTO threads (id,'+names+') SELECT ?,'+values+' FROM threads WHERE id=?',
                [(rid,str(uuid.uuid4()),roots[1]['id']) for rid in ids])
            db.executemany('INSERT INTO mentions (id,recipient,kind,threads,created) VALUES (?,?,?,?,?)',
                [(rid,b['id'],'thread',rid,'2026-01-02 00:00:00.000Z') for rid in ids])
            visible=secrets.token_hex(7)+'b'
            db.execute('INSERT INTO mentions (id,recipient,kind,threads,created) VALUES (?,?,?,?,?)',
                (visible,b['id'],'thread',roots[0]['id'],'2026-01-01 00:00:00.000Z'))
        self.fixtures.extend(('threads',rid) for rid in ids)
        self.fixtures.extend(('mentions',rid) for rid in ids+[visible])
        try:
            self.assertEqual(self.call('PATCH','/api/collections/threads',{'viewRule':'id="'+roots[0]['id']+'"'},self.admin,True)[0],200)
            # A denied prefix must not hide the older accessible item behind a false end.
            for query in ('','?perPage=1','?page=1000&perPage=50'):
                s,r=self.call('GET',path+query,token=token)
                self.assertEqual(s,503,r)
                self.assertEqual(r,{'status':503,'message':'Mention scan limit exceeded.','data':{}})
            self.assertEqual(self.call('PATCH','/api/collections/threads',{'viewRule':schema['viewRule']},self.admin,True)[0],200)
            # Deep visible paging also fails explicitly if lookahead exceeds the ceiling.
            self.assertEqual(self.call('GET',path+'?page=20&perPage=50',token=token)[0],503)
            self.assertEqual(self.call('GET',path+'?page=1000&perPage=50',token=token)[0],503)
            self.assertEqual(self.call('PATCH','/api/collections/threads',{'viewRule':'id="'+roots[0]['id']+'"'},self.admin,True)[0],200)
            # Prove actual exhaustion at the exact scan boundary, not just a short batch.
            self.assertEqual(self.call('DELETE','/api/collections/mentions/records/'+visible,token=self.admin,admin=True)[0],204)
            self.assertEqual(self.call('GET',path,token=token),
                (200,{'page':1,'perPage':50,'hasMore':False,'items':[]}))
            # A full visible page plus lookahead still succeeds without scanning the tail.
            self.assertEqual(self.call('PATCH','/api/collections/threads',{'viewRule':schema['viewRule']},self.admin,True)[0],200)
            s,r=self.call('GET',path+'?perPage=50',token=token)
            self.assertEqual(s,200); self.assertTrue(r['hasMore']); self.assertEqual(len(r['items']),50)
            # A deep page may be empty only when raw exhaustion was actually checked.
            self.assertEqual(self.call('GET',path+'?page=1000&perPage=50',token=token),
                (200,{'page':1000,'perPage':50,'hasMore':False,'items':[]}))
            s,r=self.call('GET',path+'?page=20&perPage=50',token=token)
            self.assertEqual(s,200); self.assertFalse(r['hasMore']); self.assertEqual(len(r['items']),50)
        finally:
            self.assertEqual(self.call('PATCH','/api/collections/threads',{'viewRule':schema['viewRule']},self.admin,True)[0],200)

    @classmethod
    def tearDownClass(cls):
        import sqlite3
        with sqlite3.connect(str(backend.Path(cls.data_dir)/'data.db')) as db:
            members=[rid for collection,rid in cls.fixtures if collection=='members']
            marks=','.join('?' for _ in members)
            mention_ids=[r[0] for r in db.execute('SELECT id FROM mentions WHERE recipient IN ('+marks+')',members)] if members else []
        super().tearDownClass()
        with sqlite3.connect(str(backend.Path(cls.data_dir)/'data.db')) as db:
            for mid in members:
                cls().assertEqual(db.execute('SELECT count(*) FROM presence WHERE member=?',(mid,)).fetchone()[0],0)
                cls().assertEqual(db.execute('SELECT count(*) FROM mentions WHERE recipient=?',(mid,)).fetchone()[0],0)
            for mid in mention_ids:
                cls().assertEqual(db.execute('SELECT count(*) FROM mentions WHERE id=?',(mid,)).fetchone()[0],0)
        print('Derived presence/mention exact fixture cleanup verified.')

if __name__ == '__main__':
    backend.Contract=V3
    backend.main()
