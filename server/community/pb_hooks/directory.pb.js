routerAdd("GET", "/api/community/users", (e) => {
    const api = require(`${__hooks}/community-v3.js`);
    api.member(e);
    const p = api.paging(e,20,["q","page","perPage"]);
    const q = (p.query.q ? p.query.q[0] : "").toLowerCase();
    if (q.length>24 || !/^[-_a-z0-9]*$/.test(q)) throw new BadRequestError("Invalid username fragment");
    // The pilot permits a SQL subsequence scan up to 10,000 identities; use a search index above that ceiling.
    const count = new DynamicModel({total:0});
    e.app.db().newQuery("SELECT count(*) AS total FROM members").one(count);
    if (count.total>10000) throw new ApiError(503,"Directory pilot capacity exceeded");
    const fuzzy = "%" + Array.from(q).map(c => c==="_" ? "\\_" : c).join("%") + "%";
    const literal=Array.from(q).map(c=>c==="_" ? "\\_" : c).join("");
    const candidates=q ? "SELECT id,handle,0 AS rank FROM members WHERE active=1 AND handle={:q} COLLATE NOCASE UNION ALL SELECT id,handle,1 FROM members WHERE active=1 AND handle LIKE {:prefix} ESCAPE '\\' AND handle!={:q} UNION ALL SELECT id,handle,2 FROM members WHERE active=1 AND instr(handle,{:q})>0 AND substr(handle,1,length({:q}))!={:q} UNION ALL SELECT id,handle,3 FROM members WHERE active=1 AND handle LIKE {:fuzzy} ESCAPE '\\' AND instr(handle,{:q})=0" : "SELECT id,handle,1 AS rank FROM members WHERE active=1";
    const rows = arrayOf(new DynamicModel({id:"",handle:"",online:false}));
    e.app.db().newQuery("WITH candidates AS ("+candidates+") SELECT m.id,m.handle,COALESCE(p.expires_at>{:now} AND p.expires_at<={:ceiling} AND p.issued_at<={:now} AND p.issued_at>{:floor},0) AS online FROM candidates m LEFT JOIN presence p ON p.member=m.id ORDER BY m.rank,online DESC,m.handle,m.id LIMIT {:lim} OFFSET {:off}")
        .bind({q:q,prefix:literal+'%',fuzzy:fuzzy,now:new Date().toISOString().replace('T',' '),ceiling:new Date(Date.now()+90000).toISOString().replace('T',' '),floor:new Date(Date.now()-90000).toISOString().replace('T',' '),lim:p.perPage+1,off:(p.page-1)*p.perPage}).all(rows);
    return e.json(200,{page:p.page,perPage:p.perPage,hasMore:rows.length>p.perPage,items:rows.slice(0,p.perPage).map(r=>({id:r.id,handle:r.handle,online:Boolean(r.online)}))});
});
