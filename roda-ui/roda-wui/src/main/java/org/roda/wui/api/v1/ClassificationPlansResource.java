/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.api.v1;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.roda.core.common.StreamResponse;
import org.roda.core.common.UserUtility;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.v2.user.User;
import org.roda.wui.api.controllers.Browser;
import org.roda.wui.api.v1.utils.ApiUtils;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;

/**
 * @author Hélder Silva <hsilva@keep.pt>
 */
@Path(ClassificationPlansResource.ENDPOINT)
@Api(value = ClassificationPlansResource.SWAGGER_ENDPOINT)
public class ClassificationPlansResource {
  public static final String ENDPOINT = "/v1/classification_plans";
  public static final String SWAGGER_ENDPOINT = "v1 classification plans";

  @Context
  private HttpServletRequest request;

  @GET
  @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
  public Response getClassificationPlan(
    @ApiParam(value = "Type of classification plan to produce.", allowMultiple = false, allowableValues = "bagit", required = true) @QueryParam(RodaConstants.API_QUERY_PARAM_TYPE) String type)
    throws RODAException {

    // get user
    User user = UserUtility.getApiUser(request);

    // delegate action to controller
    StreamResponse streamResponse = Browser.retrieveClassificationPlan(user, type);

    return ApiUtils.okResponse(streamResponse);
  }

}
