"""Disposable local backend only: policy, sole moderator, deletion and abuse boundaries."""
import sys,uuid
import test_backend as backend

class Release(backend.Contract):
    def test_gate_policy_cannot_be_forged_or_bypassed(self):
        h,p,a=self.signup()
        s,auth=self.call('POST','/api/collections/members/auth-with-password',{'identity':h,'password':p})
        self.assertEqual(s,200); token=auth['token']
        payload={'author':a['id'],'body':'requires acceptance','client_id':str(uuid.uuid4())}
        self.assertEqual(self.call('POST','/api/collections/messages/records',payload,token)[0],428)
        self.assertEqual(self.call('POST','/api/community/policy',{'version':'future'},token)[0],400)
        self.assertEqual(self.call('POST','/api/community/policy',{'version':'2026-10-10','moderator':True},token)[0],400)
        self.assertEqual(self.call('POST','/api/community/policy',{'version':'2026-10-10'},token)[0],200)
        self.assertEqual(self.call('POST','/api/collections/messages/records',payload,token)[0],200)
        self.assertEqual(self.call('POST','/api/community/policy',{'version':'2026-10-10'})[0],401)

    def test_gate_only_dvil_can_review_remove_ban_and_resolve(self):
        mh,mp,mod=self.signup('dvil'); mt=self.login(mh,mp)
        h,p,a=self.signup(); t=self.login(h,p)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+mod['id'],{'moderator':True},self.admin,True)[0],200)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+a['id'],{'moderator':True},self.admin,True)[0],200)
        self.assertEqual(self.call('GET','/api/community/moderation',token=t)[0],403)
        self.assertEqual(self.call('GET','/api/collections/reports/records',token=t)[0],403)
        payload={'author':a['id'],'channel':'help','title':'Reported thread','body':'reported text','client_id':str(uuid.uuid4())}
        s,thread=self.call('POST','/api/collections/threads/records',payload,t); self.assertEqual(s,200)
        s,report=self.call('POST','/api/community/report',{'collection':'threads','id':thread['id'],'reason':'abuse'},t); self.assertEqual(s,200)
        s,queue=self.call('GET','/api/community/moderation',token=mt); self.assertEqual(s,200)
        self.assertIn(report['id'],[r['id'] for r in queue['items']])
        action={'queue':'moderator_requests','id':report['id'],'action':'remove'}
        self.assertEqual(self.call('POST','/api/community/moderation/action',action,t)[0],403)
        self.assertEqual(self.call('POST','/api/community/moderation/action',action,mt)[0],200)
        self.assertEqual(self.call('POST','/api/community/moderation/action',action,mt)[0],200)
        self.assertEqual(self.call('GET','/api/collections/threads/records/'+thread['id'],token=t)[0],404)
        stored=self.call('GET','/api/collections/moderator_requests/records/'+report['id'],token=self.admin,admin=True)[1]
        self.assertEqual(stored['snapshot_body'],'reported text'); self.assertTrue(stored['resolved_at'])
        self.assertNotIn(report['id'],[r['id'] for r in self.call('GET','/api/community/moderation',token=mt)[1]['items']])
        s,message=self.call('POST','/api/collections/messages/records',{'author':a['id'],'body':'native report source','client_id':str(uuid.uuid4())},t);self.assertEqual(s,200)
        s,native=self.call('POST','/api/collections/reports/records',{'reporter':a['id'],'message':message['id'],'reason':'native report'},t);self.assertEqual(s,200)
        self.assertEqual(native['source_collection'],'messages');self.assertEqual(native['source_author_id'],a['id'])
        self.assertEqual(self.call('DELETE','/api/collections/messages/records/'+message['id'],token=self.admin,admin=True)[0],204)
        payload['client_id']=str(uuid.uuid4());s,thread=self.call('POST','/api/collections/threads/records',payload,t);self.assertEqual(s,200)
        s,report=self.call('POST','/api/community/report',{'collection':'threads','id':thread['id'],'reason':'ban author'},t);self.assertEqual(s,200)
        action.update(queue='reports',id=native['id'],action='ban')
        self.assertEqual(self.call('POST','/api/community/moderation/action',action,mt)[0],200)
        self.assertEqual(self.call('GET','/api/collections/messages/records',token=t)[0],403)
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+mod['id'],{'moderator':False},self.admin,True)[0],200)
        self.assertEqual(self.call('GET','/api/community/moderation',token=mt)[0],403)

    def test_gate_deletion_requires_password_and_removes_owned_data(self):
        h,p,a=self.signup(); t=self.login(h,p); h2,p2,b=self.signup(); t2=self.login(h2,p2)
        s,thread=self.call('POST','/api/collections/threads/records',{'author':a['id'],'channel':'help','title':'Delete me','body':'owned','client_id':str(uuid.uuid4())},t);self.assertEqual(s,200)
        s,report=self.call('POST','/api/community/report',{'collection':'threads','id':thread['id'],'reason':'retained evidence'},t2);self.assertEqual(s,200)
        path='/api/community/account-delete'
        self.assertEqual(self.call('POST',path,{'confirm':h,'password':'wrong'},t)[0],400)
        self.assertEqual(self.call('POST',path,{'confirm':h,'password':p},t2)[0],400)
        self.assertEqual(self.call('POST',path,{'confirm':h,'password':p,'id':b['id']},t)[0],400)
        self.assertEqual(self.call('POST',path,{'confirm':h,'password':p},t)[0],200); self.users.remove(a['id'])
        self.assertEqual(self.call('GET','/api/collections/messages/records',token=t)[0],401)
        self.assertEqual(self.call('GET','/api/collections/threads/records/'+thread['id'],token=t2)[0],404)
        stored=self.call('GET','/api/collections/moderator_requests/records/'+report['id'],token=self.admin,admin=True)[1]
        self.assertEqual(stored['snapshot_body'],'owned')
        self.assertEqual(self.call('PATCH','/api/collections/members/records/'+b['id'],{'active':False},self.admin,True)[0],200)
        self.assertEqual(self.call('POST',path,{'confirm':h2,'password':p2})[0],200); self.users.remove(b['id'])

if __name__=='__main__':
    if '--binary' not in sys.argv or '--base' in sys.argv: raise SystemExit('Use --binary with a disposable local backend only')
    if '--tests' not in sys.argv: sys.argv.extend(['--tests','test_gate_*'])
    backend.Contract=Release; backend.main()
