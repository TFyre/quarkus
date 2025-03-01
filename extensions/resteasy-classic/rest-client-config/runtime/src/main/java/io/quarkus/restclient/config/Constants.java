package io.quarkus.restclient.config;

public final class Constants {

    public static final String QUARKUS_CONFIG_PREFIX = "quarkus.rest-client.";
    public static final String MP_REST = "/mp-rest/";
    public static final String MP_REST_SCOPE_FORMAT = "%s" + MP_REST + "scope";
    public static final String QUARKUS_REST_SCOPE_FORMAT = QUARKUS_CONFIG_PREFIX + "%s.scope";

    /**
     * Set the configKey property from {@link org.eclipse.microprofile.rest.client.inject.RegisterRestClient#configKey}.
     */
    public static final String CONFIG_KEY = "io.quarkus.rest.client.config-key";

    private Constants() {
    }

}