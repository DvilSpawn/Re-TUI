function targets(app,record) {
    const handles=[];
    const regex=/(^|[^-_a-z0-9@])@([-_a-z0-9]{3,24})(?![-_a-z0-9])/gi;
    let match;
    while ((match=regex.exec(record.getString("body")))!==null) {
        const h=match[2].toLowerCase();
        if (!handles.includes(h)) handles.push(h);
    }
    const ids=[];
    for (const handle of handles) {
        const users=app.findRecordsByFilter("members","active=true && handle={:handle}","",1,0,{handle:handle});
        if (users.length && users[0].id!==record.getString("author")) ids.push(users[0].id);
    }
    if (ids.length>10) throw new BadRequestError("At most 10 active mention recipients are allowed");
    return ids;
}
module.exports={targets:targets};
