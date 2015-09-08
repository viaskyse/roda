package org.roda.api;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;

public class RestApplication extends ResourceConfig {
	public RestApplication() {
		super();
		// packages("io.swagger.jaxrs.listing", "org.roda.api",
		// "org.roda.api.v1");
		packages("org.roda.api");
		register(MultiPartFeature.class);
		register(JacksonFeature.class);
		// https://github.com/swagger-api/swagger-core/wiki/Java-JAXRS-Quickstart
		// BeanConfig beanConfig = new BeanConfig();
		// beanConfig.setVersion("1");
		// beanConfig.setBasePath("/rest");
		// beanConfig.setResourcePackage("org.roda.api");
		// beanConfig.setScan(true);
	}
}