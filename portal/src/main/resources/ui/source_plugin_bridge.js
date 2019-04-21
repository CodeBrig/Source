var eb = new EventBus('http://localhost:7529/eventbus');
eb.enableReconnect(true);

var getPortalId = findGetParameter("portal_id");
var getAppUuid = findGetParameter("app_uuid");
var portalId = (getPortalId) ? parseInt(getPortalId) : 1;
var appUuid = (getAppUuid) ? getAppUuid : null;
var traceOrderType = findGetParameter("order_type");
if (traceOrderType) {
    traceOrderType = traceOrderType.toUpperCase();
}

var mainGetQuery = '?portal_id=' + portalId + '&app_uuid=' + appUuid;

function findGetParameter(parameterName) {
    var result = null,
        tmp = [];
    location.search
        .substr(1)
        .split("&")
        .forEach(function (item) {
            tmp = item.split("=");
            if (tmp[0] === parameterName) result = decodeURIComponent(tmp[1]);
        });
    return result;
}