function member(e) {
    if (!e.auth || e.auth.collection().name !== "members") throw new UnauthorizedError("Sign in required");
    const current = e.app.findRecordById("members", e.auth.id);
    if (!current.getBool("active")) throw new ForbiddenError("Account inactive");
    return current;
}
function paging(e, max, allowed) {
    const query = JSON.parse(toString(e.request.url.query()));
    for (const key of Object.keys(query)) {
        if (!allowed.includes(key) || query[key].length !== 1) throw new BadRequestError("Invalid query parameter");
    }
    const page = query.page ? query.page[0] : "1";
    const size = query.perPage ? query.perPage[0] : String(max);
    if (!/^[1-9][0-9]{0,3}$/.test(page) || Number(page)>1000 || !/^[1-9][0-9]*$/.test(size) || Number(size)>max) throw new BadRequestError("Invalid pagination");
    return {page:Number(page),perPage:Number(size),query:query};
}
module.exports = {member:member,paging:paging};
