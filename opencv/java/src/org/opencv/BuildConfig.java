package org.opencv;


public final class BuildConfig {
    public static final boolean DEBUG;

    static {
        Boolean.parseBoolean("true");
        DEBUG = true;
    }

    public static final String LIBRARY_PACKAGE_NAME = "org.opencv";
    /**
     * @deprecated APPLICATION_ID is misleading in libraries. For the library package name use LIBRARY_PACKAGE_NAME
     */
    @Deprecated
    public static final String APPLICATION_ID = "org.opencv";
    public static final String BUILD_TYPE = "debug";
    public static final String FLAVOR = "";
    public static final int VERSION_CODE = 408000;
    public static final String VERSION_NAME = "4.8.0";
}