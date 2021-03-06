package au.org.ala.profile.hub

import au.org.ala.profile.hub.util.DateRangeType
import au.org.ala.profile.security.Role
import au.org.ala.profile.security.Secured

class ReportController extends BaseController {

    ProfileService profileService

    @Secured(role = Role.ROLE_PROFILE_AUTHOR)
    def loadReport() {
        DateRangeType period;
        Date to, from;
        boolean countOnly = false;
        if (!params.opusId || !params.reportId) {
            badRequest()
        } else {
            period = DateRangeType.byId(params.period) ?: DateRangeType.LAST_30DAYS;
            if (params.to && params.from) {
                try {
                    to = new Date(Long.parseLong(params.to));
                    from = new Date(Long.parseLong(params.from));
                    countOnly = Boolean.parseBoolean(params.countOnly) ?: false;
                } catch (Exception e) {
                    badRequest();
                }
            }

            Map dates = ['period': period, 'from': from, 'to': to]
            def response = profileService.loadReport(params.opusId, params.reportId,
                    params.pageSize, params.offset, dates, countOnly)

            handle response
        }
    }

}
