/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.client.router.impl;

import org.gridgain.client.router.*;
import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.spring.*;
import org.gridgain.grid.lang.*;
import org.gridgain.grid.logger.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;

import java.net.*;
import java.text.*;
import java.util.*;

import static org.gridgain.grid.kernal.GridComponentType.*;
import static org.gridgain.grid.kernal.GridProductImpl.*;

/**
 * Loader class for router.
 */
public class GridRouterCommandLineStartup {
    /** Logger. */
    @SuppressWarnings("FieldCanBeLocal")
    private GridLogger log;

    /** TCP router. */
    private GridTcpRouterImpl tcpRouter;

    /**
     * Search given context for required configuration and starts router.
     *
     * @param beans Beans loaded from spring configuration file.
     */
    public void start(Map<Class<?>, Object> beans) {
        log = (GridLogger)beans.get(GridLogger.class);

        if (log == null) {
            U.error(log, "Failed to find logger definition in application context. Stopping the router.");

            return;
        }

        GridTcpRouterConfiguration tcpCfg = (GridTcpRouterConfiguration)beans.get(GridTcpRouterConfiguration.class);

        if (tcpCfg == null)
            U.warn(log, "TCP router startup skipped (configuration not found).");
        else {
            tcpRouter = new GridTcpRouterImpl(tcpCfg);

            try {
                tcpRouter.start();
            }
            catch (GridException e) {
                U.error(log, "Failed to start TCP router on port " + tcpCfg.getPort() + ": " + e.getMessage(), e);

                tcpRouter = null;
            }
        }
    }

    /**
     * Stops router.
     */
    public void stop() {
        if (tcpRouter != null)
            tcpRouter.stop();
    }

    /**
     * Wrapper method to run router from command-line.
     *
     * @param args Command-line arguments.
     * @throws GridException If failed.
     */
    public static void main(String[] args) throws GridException {
        String buildDate = new SimpleDateFormat("yyyyMMdd").format(new Date(BUILD_TSTAMP * 1000));

        String rev = REV_HASH.length() > 8 ? REV_HASH.substring(0, 8) : REV_HASH;
        String ver = "ver. " + VER + '#' + buildDate + "-sha1:" + rev;

        X.println(
            "  _____     _     _______      _         ",
            " / ___/____(_)___/ / ___/___ _(_)___     ",
            "/ (_ // __/ // _  / (_ // _ `/ // _ \\   ",
            "\\___//_/ /_/ \\_,_/\\___/ \\_,_/_//_//_/",
            " ",
            "GridGain Router Command Line Loader",
            ver,
            COPYRIGHT,
            " "
        );

        GridSpringProcessor spring = SPRING.create(false);

        if (args.length < 1) {
            X.error("Missing XML configuration path.");

            System.exit(1);
        }

        String cfgPath = args[0];

        URL cfgUrl = U.resolveGridGainUrl(cfgPath);

        if (cfgUrl == null) {
            X.error("Spring XML file not found (is GRIDGAIN_HOME set?): " + cfgPath);

            System.exit(1);
        }

        boolean isLog4jUsed = U.gridClassLoader().getResource("org/apache/log4j/Appender.class") != null;

        GridBiTuple<Object, Object> t = null;

        if (isLog4jUsed)
            t = U.addLog4jNoOpLogger();

        Map<Class<?>, Object> beans;

        try {
            beans = spring.loadBeans(cfgUrl, GridLogger.class, GridTcpRouterConfiguration.class);
        }
        finally {
            if (isLog4jUsed && t != null)
                U.removeLog4jNoOpLogger(t);
        }

        final GridRouterCommandLineStartup routerStartup = new GridRouterCommandLineStartup();

        routerStartup.start(beans);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override public void run() {
                routerStartup.stop();
            }
        });
    }
}
